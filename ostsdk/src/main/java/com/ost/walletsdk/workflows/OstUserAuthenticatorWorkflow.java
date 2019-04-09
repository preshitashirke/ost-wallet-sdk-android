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

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.util.Log;

import com.ost.walletsdk.OstConfigs;
import com.ost.walletsdk.OstSdk;
import com.ost.walletsdk.biometric.OstBiometricAuthentication;
import com.ost.walletsdk.ecKeyInteracts.OstRecoveryManager;
import com.ost.walletsdk.ecKeyInteracts.UserPassphrase;
import com.ost.walletsdk.utils.AsyncStatus;
import com.ost.walletsdk.workflows.errors.OstError;
import com.ost.walletsdk.workflows.errors.OstErrors.ErrorCode;
import com.ost.walletsdk.workflows.interfaces.OstPinAcceptInterface;
import com.ost.walletsdk.workflows.interfaces.OstWorkFlowCallback;

import java.util.ArrayList;
import java.util.List;


abstract public class OstUserAuthenticatorWorkflow extends OstWorkFlowEngine implements
        OstPinAcceptInterface,
        OstBiometricAuthentication.Callback {
    private static String TAG = "OstUAWorkFlow";
    private int mPinAskCount = 0;
    private OstBiometricAuthentication.Callback mBioMetricCallBack;

    OstUserAuthenticatorWorkflow(String userId, OstWorkFlowCallback callback) {
        super(userId, callback);
    }

    List<String> getOrderedState() {
        int DEVICE_VALIDATION_INDEX = 3;
        List<String> orderedStates = super.getOrderedState();

        List<String> authStates = new ArrayList<>();
        authStates.add(WorkflowStateManager.PIN_AUTHENTICATION_REQUIRED);
        authStates.add(WorkflowStateManager.PIN_INFO_RECEIVED);
        authStates.add(WorkflowStateManager.AUTHENTICATED);

        orderedStates.addAll(DEVICE_VALIDATION_INDEX, authStates);
        return orderedStates;
    }

    protected AsyncStatus onStateChanged(String state, Object stateObject) {
        try {
            switch (state) {
                case WorkflowStateManager.PIN_AUTHENTICATION_REQUIRED:
                    postGetPin(this);
                    break;

                case WorkflowStateManager.PIN_INFO_RECEIVED:
                    return verifyUserPin( (UserPassphrase) stateObject );

                case WorkflowStateManager.AUTHENTICATED:
                    //Call the abstract method.
                    AsyncStatus status = performOnAuthenticated();
                    if ( !status.isSuccess() ) {
                        goToState(WorkflowStateManager.COMPLETED_WITH_ERROR);
                    }
                    return status;
            }
        } catch (OstError ostError) {
            return postErrorInterrupt(ostError);
        } catch (Throwable throwable) {
            OstError ostError = new OstError("bua_wf_osc_1", ErrorCode.UNCAUGHT_EXCEPTION_HANDELED);
            ostError.setStackTrace(throwable.getStackTrace());
            return postErrorInterrupt(ostError);
        }
        return super.onStateChanged(state, stateObject);
    }

    @Override
    AsyncStatus afterUserDeviceValidation(Object stateObject) {
        Log.i(TAG, "Ask for authentication");
        if (shouldAskForBioMetric()) {
            new OstBiometricAuthentication(OstSdk.getContext(), getBioMetricCallBack());
        } else {
            return goToState(WorkflowStateManager.PIN_AUTHENTICATION_REQUIRED);
        }
        return super.afterUserDeviceValidation(stateObject);
    }

    @Override
    public void pinEntered(UserPassphrase passphrase) {
        performWithState(WorkflowStateManager.PIN_INFO_RECEIVED, passphrase);

    }

    private AsyncStatus verifyUserPin(UserPassphrase passphrase) {

        OstRecoveryManager recoveryManager = new OstRecoveryManager(mUserId);
        boolean isValid = recoveryManager.validatePassphrase(passphrase);

        if ( isValid ) {
            postPinValidated();
            recoveryManager = null;
            return goToState(WorkflowStateManager.AUTHENTICATED);
        }

        mPinAskCount = mPinAskCount + 1;
        if (mPinAskCount < OstConfigs.getInstance().PIN_MAX_RETRY_COUNT) {
            Log.d(TAG, "Pin InValidated ask for pin again");
            OstPinAcceptInterface me = this;
            return postInvalidPin(me);
        }
        Log.d(TAG, "Max pin ask limit reached");
        return postErrorInterrupt("bpawf_vup_2", ErrorCode.MAX_PASSPHRASE_VERIFICATION_LIMIT_REACHED);
    }

    private AsyncStatus postPinValidated() {
        Log.i(TAG, "Pin validated");
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.pinValidated(new OstWorkflowContext(getWorkflowType()), mUserId);
                }
            }
        });
        return new AsyncStatus(true);
    }

    private AsyncStatus postInvalidPin(OstPinAcceptInterface pinAcceptInterface) {
        Log.i(TAG, "Invalid Pin");
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.invalidPin(new OstWorkflowContext(getWorkflowType()), mUserId, pinAcceptInterface);
                } else {
                    goToState(WorkflowStateManager.CALLBACK_LOST);
                }
            }
        });
        return new AsyncStatus(true);
    }


    public void cancelFlow() {
        performWithState(WorkflowStateManager.CANCELLED);
    }

    private void onBioMetricAuthenticationSuccess() {
        performWithState(WorkflowStateManager.AUTHENTICATED);
    }

    private void onBioMetricAuthenticationFail() {
        //Ask for pin.
        performWithState(WorkflowStateManager.PIN_AUTHENTICATION_REQUIRED);
    }

    private AsyncStatus postGetPin(OstPinAcceptInterface pinAcceptInterface) {
        Log.i(TAG, "get Pin");
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.getPin(new OstWorkflowContext(getWorkflowType()), mUserId, pinAcceptInterface);
                } else {
                    goToState(WorkflowStateManager.CALLBACK_LOST);
                }
            }
        });
        return new AsyncStatus(true);
    }

    private boolean shouldAskForBioMetric() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Fingerprint API only available on from Android 6.0 (M)
            FingerprintManager fingerprintManager = (FingerprintManager) OstSdk.getContext()
                    .getSystemService(Context.FINGERPRINT_SERVICE);
            return null != fingerprintManager && fingerprintManager.isHardwareDetected()
                    && fingerprintManager.hasEnrolledFingerprints();
        }
        return false;
    }

    private OstBiometricAuthentication.Callback getBioMetricCallBack() {
        if (null == mBioMetricCallBack) {
            mBioMetricCallBack = new OstBiometricAuthentication.Callback() {
                @Override
                public void onAuthenticated() {
                    Log.d(TAG, "Biometric authentication success");
                    onBioMetricAuthenticationSuccess();
                }

                @Override
                public void onError() {
                    Log.d(TAG, "Biometric authentication fail");
                    onBioMetricAuthenticationFail();
                }
            };
        }
        return mBioMetricCallBack;
    }

    @Override
    void ensureOstUser() throws OstError {
        super.ensureOstUser(true);
    }

    AsyncStatus performOnAuthenticated() {
        return new AsyncStatus(true);
    }
}