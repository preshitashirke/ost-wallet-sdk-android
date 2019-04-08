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

import android.text.TextUtils;
import android.util.Log;

import com.ost.walletsdk.OstSdk;
import com.ost.walletsdk.models.entities.OstDevice;
import com.ost.walletsdk.models.entities.OstToken;
import com.ost.walletsdk.models.entities.OstUser;
import com.ost.walletsdk.utils.AsyncStatus;
import com.ost.walletsdk.workflows.errors.OstError;
import com.ost.walletsdk.workflows.errors.OstErrors.ErrorCode;
import com.ost.walletsdk.workflows.interfaces.OstDeviceRegisteredInterface;
import com.ost.walletsdk.workflows.interfaces.OstWorkFlowCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * To Register current device on OST Platform through App
 */
public class OstRegisterDevice extends OstWorkFlowEngine implements OstDeviceRegisteredInterface {

    private static final String TAG = "OstRegisterDevice";
    private final boolean mForceSync;
    private final String mTokenId;

    @Override
    public OstWorkflowContext.WORKFLOW_TYPE getWorkflowType() {
        return OstWorkflowContext.WORKFLOW_TYPE.SETUP_DEVICE;
    }

    public OstRegisterDevice(String userId, String tokenId, boolean forceSync, OstWorkFlowCallback callback) {
        super(userId, callback);

        mTokenId = tokenId;
        mForceSync = forceSync;
    }


    //region - State machine code
    @Override
    List<String> getOrderedState() {
        int PARAMS_VALIDATED_INDEX = 1;
        List<String> orderedStates = super.getOrderedState();

        orderedStates.remove(PARAMS_VALIDATED_INDEX);
        orderedStates.remove(PARAMS_VALIDATED_INDEX);
        List<String> authStates = new ArrayList<>();
        authStates.add(WorkflowStateManager.REGISTERED);

        orderedStates.addAll(PARAMS_VALIDATED_INDEX, authStates);
        return orderedStates;
    }

    @Override
    protected AsyncStatus onStateChanged(String state, Object stateObject) {
        try {
            switch (state) {
                case WorkflowStateManager.REGISTERED:
                    Log.i(TAG, "Device registered");
                    // Verify Device Registration before sync.
                    AsyncStatus verificationStatus = verifyDeviceRegistered();

                    if ( verificationStatus.isSuccess() ) {
                        //Sync Registered Entities.
                        syncRegisteredEntities();
                    }

                    //Lets verify if device was registered.
                    return verificationStatus;
            }
        } catch (OstError ostError) {
            return postErrorInterrupt(ostError);
        } catch (Throwable throwable) {
            OstError ostError = new OstError("wf_rd_osc_1", ErrorCode.UNCAUGHT_EXCEPTION_HANDELED);
            ostError.setStackTrace(throwable.getStackTrace());
            return postErrorInterrupt(ostError);
        }
        return super.onStateChanged(state, stateObject);
    }
    //endregion


    //region - Overridden methods
    @Override
    void ensureValidParams() {
        Log.i(TAG, "Validating user Id");
        super.ensureValidParams();
        if (TextUtils.isEmpty(mTokenId)) {
            throw new OstError("wf_rd_evp_1", ErrorCode.INVALID_TOKEN_ID);
        }
    }

    @Override
    AsyncStatus afterParamsValidation() {
        return performRegisterDeviceOperation();
    }

    //endregion


    //region - Interface methods
    @Override
    public void deviceRegistered(JSONObject apiResponse) {
        performWithState(WorkflowStateManager.REGISTERED, apiResponse);
    }

    @Override
    public void cancelFlow() {
        performWithState(WorkflowStateManager.CANCELLED);
    }
    //endregion


    //region - Helper methods
    private AsyncStatus performRegisterDeviceOperation() {
        Log.i(TAG, "Initializing User and Token");
        OstUser ostUser;
        ostUser = OstUser.init(mUserId, mTokenId);
        OstToken.init(mTokenId);
        initApiClient();

        Log.i(TAG, "Creating current device if does not exist");
        OstDevice ostDevice = createOrGetCurrentDevice(ostUser);
        if (null == ostDevice) {
            return postErrorInterrupt("wf_rd_pr_2" , ErrorCode.CREATE_DEVICE_FAILED);
        }

        Log.i(TAG, "Check we are able to access device keys");
        if (!hasDeviceApiKey(ostDevice)) {
            return postErrorInterrupt("wf_rd_pr_3", ErrorCode.CREATE_DEVICE_FAILED);
        }

        Log.i(TAG, "Check if device has been registered.");
        if (OstDevice.CONST_STATUS.CREATED.equalsIgnoreCase( ostDevice.getStatus() )  ) {
            Log.i(TAG, "Registering device");
            registerDevice(ostDevice);
            return new AsyncStatus(true);
        }
        Log.i(TAG, "Device is already registered. ostDevice.status:" + ostDevice.getStatus() );

        // Verify Device Registration before sync.
        AsyncStatus status = verifyDeviceRegistered();

        //Sync if needed.
        if ( status.isSuccess() ) {
            sync();
        }

        //Lets verify if device was registered.
        return status;
    }

    private void syncRegisteredEntities() {
        Log.i(TAG, "Syncing registered entities.");
        ensureOstUser(true);
        ensureOstToken();
    }

    private void sync() {
        Log.i(TAG, String.format("Syncing sdk: %b", mForceSync));
        if (mForceSync) {
            new OstSdkSync(mUserId).perform();
        }
    }

    private void registerDevice(OstDevice ostDevice) {
        final JSONObject apiResponse = buildApiResponse(ostDevice);
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.registerDevice(apiResponse, OstRegisterDevice.this);
                } else {
                    //Do Nothing, let the workflow die.
                }
            }
        });
    }

    private OstDevice createOrGetCurrentDevice(OstUser ostUser) {
        OstDevice ostDevice;
        ostDevice = ostUser.getCurrentDevice();
        if (null == ostDevice) {
            Log.d(TAG, "currentDevice is null");
            ostDevice = ostUser.createDevice();
        }
        return ostDevice;
    }

    private JSONObject buildApiResponse(OstDevice ostDevice) {
        JSONObject jsonObject = new JSONObject();
        if (null == ostDevice) {
            return jsonObject;

        } else {
            try {
                jsonObject.put(OstSdk.DEVICE, ostDevice.getData());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonObject;
        }
    }

    private AsyncStatus verifyDeviceRegistered() {
        try {
            //Just sync current device.
            syncCurrentDevice();

            //Get the currentDevice
            OstUser ostUser = OstUser.getById(mUserId);
            OstDevice device = ostUser.getCurrentDevice();

            //Forward it.
            return postFlowComplete( new OstContextEntity(
                device,
                OstSdk.DEVICE
            ));

        } catch (OstError error) {
            //This could happen.
            return postErrorInterrupt( error );
        } catch (Exception ex) {
            //Catch all unexpected errors.
            OstError error = new OstError("wf_rd_vdr_1", ErrorCode.UNCAUGHT_EXCEPTION_HANDELED);
            error.setStackTrace( ex.getStackTrace() );
            return postErrorInterrupt( error );
        }
    }
    //endregion
}