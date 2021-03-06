/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ost.walletsdk.workflows;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.ost.walletsdk.OstSdk;
import com.ost.walletsdk.ecKeyInteracts.OstKeyManager;
import com.ost.walletsdk.ecKeyInteracts.OstRecoveryManager;
import com.ost.walletsdk.ecKeyInteracts.UserPassphrase;
import com.ost.walletsdk.models.entities.OstSession;
import com.ost.walletsdk.models.entities.OstUser;
import com.ost.walletsdk.utils.AsyncStatus;
import com.ost.walletsdk.workflows.errors.OstError;
import com.ost.walletsdk.workflows.errors.OstErrors.ErrorCode;
import com.ost.walletsdk.workflows.interfaces.OstWorkFlowCallback;
import com.ost.walletsdk.workflows.services.OstPollingService;
import com.ost.walletsdk.workflows.services.OstUserPollingService;

import org.json.JSONObject;

import java.io.IOException;

public class OstActivateUser extends OstBaseWorkFlow {

    private static final String TAG = "OstActivateUser";
    private final UserPassphrase mPassphrase;
    private final String mSpendingLimit;
    private final long mExpiresAfterInSecs;

    public OstActivateUser(UserPassphrase passphrase, long expiresAfterInSecs,
                           String spendingLimitInWei, OstWorkFlowCallback callback) {
        super(passphrase.getUserId(), callback);
        mPassphrase = passphrase;
        mExpiresAfterInSecs = expiresAfterInSecs;
        mSpendingLimit = spendingLimitInWei;
    }

    @Override
    public OstWorkflowContext.WORKFLOW_TYPE getWorkflowType() {
        return OstWorkflowContext.WORKFLOW_TYPE.ACTIVATE_USER;
    }

    @Override
    boolean shouldCheckCurrentDeviceAuthorization() {
        return false;
    }

    @Override
    protected boolean shouldAskForAuthentication() {
        return false;
    }

    @Override
    void ensureValidParams() {
        super.ensureValidParams();
        if (null == mPassphrase) {
            throw new OstError("wf_au_evp_1", ErrorCode.INVALID_USER_PASSPHRASE);
        }
        if (TextUtils.isEmpty(mSpendingLimit)) {
            throw new OstError("wf_au_evp_2", ErrorCode.INVALID_SESSION_SPENDING_LIMIT);
        }
        if (mExpiresAfterInSecs < 0) {
            throw new OstError("wf_au_evp_3", ErrorCode.INVALID_SESSION_EXPIRY_TIME);
        }
    }


    @Override
    protected AsyncStatus onUserDeviceValidationPerformed(Object stateObject) {
        try {

            assertUserInCreatedState();

            String expirationHeight = calculateExpirationHeight(mExpiresAfterInSecs);

            // Compute recovery address.
            String recoveryAddress = new OstRecoveryManager(mUserId).getRecoveryAddressFor(mPassphrase);

            // Create session key
            String sessionAddress = new OstKeyManager(mUserId).createSessionKey();

            // Post the Api call.
            Log.i(TAG, "Activate user");
            Log.d(TAG, String.format("SessionAddress: %s, expirationHeight: %s,"
                            + " SpendingLimit: %s, RecoveryAddress: %s", sessionAddress,
                    expirationHeight, mSpendingLimit, recoveryAddress));

            JSONObject response = mOstApiClient.postUserActivate(sessionAddress,
                    expirationHeight, mSpendingLimit, recoveryAddress);

            // Let the app know that kit has accepted the request.
            OstWorkflowContext workflowContext = new OstWorkflowContext(getWorkflowType());
            OstContextEntity ostContextEntity = new OstContextEntity(OstUser.getById(mUserId), OstSdk.USER);
            postRequestAcknowledge(workflowContext, ostContextEntity);

            // Create session locally if the request is accepted.
            // For polling purpose
            OstSession.init(sessionAddress, mUserId);

        } catch (OstError error) {
            return postErrorInterrupt(error);
        } catch (IOException e) {
            OstError error = new OstError("wf_au_udvp_2", ErrorCode.ACTIVATE_USER_API_FAILED);
            return postErrorInterrupt(error);
        } finally {
            mPassphrase.wipe();
        }

        //Activate the user if otherwise.
        Log.i(TAG, "Starting user polling service");
        Log.i(TAG, "Waiting for update");
        Bundle bundle = OstUserPollingService.startPolling(mUserId, mUserId, OstUser.CONST_STATUS.ACTIVATED,
                OstUser.CONST_STATUS.CREATED);
        if (bundle.getBoolean(OstPollingService.EXTRA_IS_POLLING_TIMEOUT, true)) {
            Log.d(TAG, String.format("Polling time out for user Id: %s", mUserId));
            return postErrorInterrupt("wf_au_udvp_3", ErrorCode.ACTIVATE_USER_API_POLLING_FAILED);
        }
        Log.i(TAG, "Syncing Entities: User, Device, Sessions");
        new OstSdkSync(mUserId, OstSdkSync.SYNC_ENTITY.USER, OstSdkSync.SYNC_ENTITY.DEVICE,
                OstSdkSync.SYNC_ENTITY.SESSION).perform();

        Log.i(TAG, "Response received for post Token deployment");
        return postFlowComplete( new OstContextEntity(mOstUser, OstSdk.USER) );
    }

    private void assertUserInCreatedState() {
        OstUser ostUser = OstUser.getById(mUserId);
        if (ostUser.isActivated()) {
            throw new OstError("wf_ac_nua_1", ErrorCode.USER_ALREADY_ACTIVATED);
        } else if (ostUser.isActivating()) {
            throw new OstError("wf_ac_nua_1", ErrorCode.USER_ACTIVATING);
        }
    }
}