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

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ost.walletsdk.OstConfigs;
import com.ost.walletsdk.OstConstants;
import com.ost.walletsdk.ecKeyInteracts.OstKeyManager;
import com.ost.walletsdk.models.entities.OstDevice;
import com.ost.walletsdk.models.entities.OstDeviceManager;
import com.ost.walletsdk.models.entities.OstRule;
import com.ost.walletsdk.models.entities.OstToken;
import com.ost.walletsdk.models.entities.OstUser;
import com.ost.walletsdk.network.OstApiClient;
import com.ost.walletsdk.network.OstApiError;
import com.ost.walletsdk.utils.AsyncStatus;
import com.ost.walletsdk.utils.CommonUtils;
import com.ost.walletsdk.workflows.OstWorkflowContext.WORKFLOW_TYPE;
import com.ost.walletsdk.workflows.errors.OstError;
import com.ost.walletsdk.workflows.errors.OstErrors.ErrorCode;
import com.ost.walletsdk.workflows.interfaces.OstWorkFlowCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static com.ost.walletsdk.workflows.WorkflowStateManager.INITIAL;

abstract class OstWorkFlowEngine {
    private static final String TAG = "OstWorkFlowEngine";
    private final static ThreadPoolExecutor THREAD_POOL_EXECUTOR = (ThreadPoolExecutor) Executors
            .newFixedThreadPool(1);


    //region - Class variables
    private final Handler mHandler;
    private WorkflowStateManager stateManager;
    private final WeakReference <OstWorkFlowCallback> workFlowCallbackWeakReference;

    final String mUserId;
    OstApiClient mOstApiClient;
    //endregion


    //region - Getters
    public Handler getHandler() {
        return mHandler;
    }

    protected OstWorkFlowCallback getCallback() {
        return workFlowCallbackWeakReference.get();
    }

    ThreadPoolExecutor getAsyncQueue() {
        return THREAD_POOL_EXECUTOR;
    }

    public WORKFLOW_TYPE getWorkflowType() {
        return WORKFLOW_TYPE.UNKNOWN;
    }
    //endregion


    /**
     * @param userId - Ost Platform user-id
     * @param callback - callback handler of the application.
     */
    OstWorkFlowEngine(String userId, OstWorkFlowCallback callback) {
        mUserId = userId;

        mHandler = new Handler(Looper.getMainLooper());
        workFlowCallbackWeakReference = new WeakReference<>(callback);
        setStateManager();
        initApiClient();
    }

    private void setStateManager() {
        stateManager = new WorkflowStateManager(getOrderedState());
    }


    //region - Core state machine apis
    public Future<AsyncStatus> perform() {
        return getAsyncQueue().submit(new Callable<AsyncStatus>() {
            @Override
            public AsyncStatus call() {
                return process();
            }
        });
    }

    synchronized protected AsyncStatus process() {
        AsyncStatus status;
        String currentState = stateManager.getCurrentState();
        Object currentStateObject = stateManager.getStateObject();
        status = onStateChanged(currentState, currentStateObject);
        if ( null != status ) {
            return status;
        }
        return new AsyncStatus(true);
    }

    protected AsyncStatus onStateChanged(String state, Object stateObject) {
        try {
            switch (state) {
                case WorkflowStateManager.INITIAL:
                    return performValidations(stateObject);

                case WorkflowStateManager.PARAMS_VALIDATED:
                    return performUserDeviceValidation(stateObject);

                case WorkflowStateManager.DEVICE_VALIDATED:
                    return performOnUserDeviceValidation(stateObject);

                case WorkflowStateManager.CANCELLED:
                    if ( stateObject instanceof OstError) {
                        return postErrorInterrupt( (OstError) stateObject );
                    } else {
                        OstError error = new OstError("wf_wfe_osc_canceled", ErrorCode.UNKNOWN);
                        return postErrorInterrupt(error);
                    }

                case WorkflowStateManager.COMPLETED:
                    return new AsyncStatus(true);

                case WorkflowStateManager.COMPLETED_WITH_ERROR:
                    return new AsyncStatus(false);
                case WorkflowStateManager.CALLBACK_LOST:
                    Log.w(TAG, "The callback instance has been lost. Workflow class name: " + getClass().getName());
                    return new AsyncStatus(false);
            }
        } catch (OstError ostError) {
            return postErrorInterrupt(ostError);
        } catch (Throwable throwable) {
            OstError ostError = new OstError("wf_wfe_osc_1", ErrorCode.UNCAUGHT_EXCEPTION_HANDELED);
            ostError.setStackTrace(throwable.getStackTrace());
            return postErrorInterrupt(ostError);
        }
        return new AsyncStatus(true);
    }
    //endregion


    //region - init Methods for overriding
    void initApiClient() {
        mOstApiClient = new OstApiClient(mUserId);
    }

    List<String> getOrderedState() {
        List<String> orderedStates = new ArrayList<>();
        orderedStates.add(WorkflowStateManager.INITIAL);
        orderedStates.add(WorkflowStateManager.PARAMS_VALIDATED);
        orderedStates.add(WorkflowStateManager.DEVICE_VALIDATED);

        orderedStates.add(WorkflowStateManager.CANCELLED);
        orderedStates.add(WorkflowStateManager.COMPLETED);
        orderedStates.add(WorkflowStateManager.COMPLETED_WITH_ERROR);
        orderedStates.add(WorkflowStateManager.CALLBACK_LOST);
        return orderedStates;
    }
    //endregion


    //region - State machine methods
    protected AsyncStatus performNext(Object stateObject) {
        stateManager.setNextState(stateObject);
        return process();
    }

    protected AsyncStatus goToState(String state, Object stateObject) {
        stateManager.setState(state, stateObject);
        return process();
    }

    protected void performWithState(String state, Object stateObject) {
        stateManager.setState(state, stateObject);
        perform();
    }

    protected AsyncStatus goToState(String state) {
        return goToState(state, null);
    }
    protected AsyncStatus performNext() {
        return performNext(null);
    }
    protected void performWithState(String state) {
        performWithState(state, null);
    }
    //endregion


    //region - state machine lifecycle methods
    private AsyncStatus performOnUserDeviceValidation(Object stateObject) {
        try {
            onUserDeviceValidated(stateObject);
        } catch (OstError err) {
            return postErrorInterrupt(err);
        }
        return new AsyncStatus(true);
    }

    AsyncStatus onUserDeviceValidated(Object stateObject) {
        return new AsyncStatus(true);
    }

    private AsyncStatus performValidations(Object stateObject) {
        try {
            ensureValidParams();
        } catch (OstError ostError) {
            return postErrorInterrupt(ostError);
        }
        return afterParamsValidation();
    }

    AsyncStatus afterParamsValidation() {
        return performNext();
    }

    AsyncStatus beforeUserDeviceValidation(Object stateObject) {
        return new AsyncStatus(true);
    }

    AsyncStatus afterUserDeviceValidation(Object stateObject) {
        return performNext();
    }

    private AsyncStatus performUserDeviceValidation(Object stateObject) {

        try {
            beforeUserDeviceValidation(stateObject);
            //Ensure sdk can make Api calls
            ensureApiCommunication();

            // Ensure we have OstUser complete entity.
            ensureOstUser( isAuthenticationFlow() );

            // Ensure we have OstToken complete entity.
            ensureOstToken();

            if ( shouldCheckCurrentDeviceAuthorization() ) {
                //Ensure Device is Authorized.
                ensureDeviceAuthorized();

                //Ensures Device Manager is present as derived classes are likely going to need nonce.
                ensureDeviceManager();
            }

            return afterUserDeviceValidation(stateObject);

        } catch (OstError err) {
            return postErrorInterrupt(err);
        }
    }
    //endregion


    //region - Flow deciding methods methods
    protected boolean isAuthenticationFlow() {
        return false;
    }

    private boolean shouldCheckCurrentDeviceAuthorization() {
        return false;
    }
    //endregion


    //region - Post methods to Application
    AsyncStatus postFlowComplete(OstContextEntity ostContextEntity) {
        Log.i(TAG, "Flow complete");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.flowComplete(new OstWorkflowContext(getWorkflowType()), ostContextEntity);
                }
            }
        });
        return new AsyncStatus(true);
    }

    AsyncStatus postFlowComplete() {
        return postFlowComplete(null);
    }

    AsyncStatus postErrorInterrupt(String internalErrCode, ErrorCode errorCode) {
        Log.i(TAG, "Flow Error");
        OstError error = new OstError(internalErrCode, errorCode);
        return postErrorInterrupt(error);
    }
    AsyncStatus postErrorInterrupt(OstError error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.flowInterrupt(new OstWorkflowContext(getWorkflowType()), error);
                }
            }
        });
        return new AsyncStatus(false);
    }

    void postRequestAcknowledge(OstContextEntity ostContextEntity) {
        OstWorkflowContext workflowContext = new OstWorkflowContext(getWorkflowType());
        postRequestAcknowledge(workflowContext, ostContextEntity);
    }

    void postRequestAcknowledge(OstWorkflowContext workflowContext, OstContextEntity ostContextEntity) {
        Log.i(TAG, "Request Acknowledge");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                OstWorkFlowCallback callback = getCallback();
                if ( null != callback ) {
                    callback.requestAcknowledged(workflowContext, ostContextEntity);
                }
            }
        });
    }
    //endregion


    //region - Condition Checks
    boolean hasDeviceApiKey(OstDevice ostDevice) {
        OstKeyManager ostKeyManager = new OstKeyManager(mUserId);
        return ostKeyManager.getApiKeyAddress().equalsIgnoreCase(ostDevice.getApiSignerAddress());
    }

    boolean canDeviceMakeApiCall(OstDevice ostDevice) {
        //Must have Device Api Key which should have been registered.
        return hasDeviceApiKey(ostDevice) && ostDevice.canMakeApiCall();
    }
    //endregion


    //region - Ensure Data methods

    /**
     * Method that can be called to validate and params.
     * @Dev: Please make sure this method is only used to perform validations
     * that do not need API calls. For any validation that needs API call, please
     * use afterUserDeviceValidation.
     */
    void ensureValidParams() {
        if ( TextUtils.isEmpty(mUserId) ) {
            throw new OstError("wf_bwf_evp_1", ErrorCode.INVALID_USER_ID);
        }

        if ( null == getCallback() ) {
            throw new OstError("wf_bwf_evp_2", ErrorCode.INVALID_WORKFLOW_CALLBACK);
        }
    }

    OstDevice mCurrentDevice;
    boolean hasSyncedDeviceToEnsureApiCommunication = false;
    void ensureApiCommunication() throws OstError {
        OstUser ostUser = OstUser.getById(mUserId);

        if ( null == ostUser ) {
            throw new OstError("wp_base_apic_1", ErrorCode.DEVICE_NOT_SETUP);
        }

        OstDevice ostDevice = ostUser.getCurrentDevice();
        if ( null == ostDevice) {
            throw new OstError("wp_base_apic_2", ErrorCode.DEVICE_NOT_SETUP);
        }
        else if ( !canDeviceMakeApiCall(ostDevice) ) {
            String deviceAddress = ostDevice.getAddress();
            // Lets try and make an api call.
            hasSyncedDeviceToEnsureApiCommunication = true;
            try {
                syncCurrentDevice();
            } catch (OstError ostError) {

                if ( ostError.isApiError() ) {
                    OstApiError apiError = (OstApiError) ostError;
                    if ( apiError.isApiSignerUnauthorized() ) {
                        //We know this could happen. Lets ignore the error given by syncCurrentDevice.
                        throw new OstError("wp_base_apic_3", ErrorCode.DEVICE_NOT_SETUP);
                    }
                }
                throw ostError;
            }

            ostDevice = OstDevice.getById(deviceAddress);

            //Check again.
            if ( !canDeviceMakeApiCall(ostDevice) ) {
                throw new OstError("wp_base_apic_4", ErrorCode.DEVICE_NOT_SETUP);
            }
        }
        mCurrentDevice = ostDevice;
    }

    OstUser mOstUser;
    void ensureOstUser() throws OstError {
        ensureOstUser(false);
    }
    void ensureOstUser(boolean forceSync) throws OstError {
        mOstUser = OstUser.getById(mUserId);
        if ( forceSync || null == mOstUser || TextUtils.isEmpty(mOstUser.getTokenHolderAddress()) || TextUtils.isEmpty(mOstUser.getDeviceManagerAddress())) {
            try {
                mOstApiClient.getUser();
                mOstUser = OstUser.getById(mUserId);
            } catch (IOException e) {
                Log.d(TAG, "Encountered IOException while fetching user.");
                OstError ostError = new OstError("wp_base_eou_1", ErrorCode.GET_USER_API_FAILED);
                throw ostError;
            }
        }
    }

    OstToken mOstToken;
    void ensureOstToken()  throws OstError {
        if (null == mOstUser) {
            ensureOstUser();
        }
        String tokenId = mOstUser.getTokenId();
        mOstToken = OstToken.getById(tokenId);
        if (null == mOstToken || TextUtils.isEmpty(mOstToken.getChainId()) ||
                TextUtils.isEmpty(mOstToken.getBtDecimals())) {
            //Make API Call.
            try {
                mOstApiClient.getToken();
                mOstToken = OstToken.getById(tokenId);
            } catch (IOException e) {
                Log.i(TAG, "Encountered IOException while fetching token.");
                throw new OstError("wp_base_eot_1", ErrorCode.TOKEN_API_FAILED);
            }
        }
    }

    void ensureDeviceAuthorized() throws OstError {

        if ( null == mCurrentDevice ) {  ensureApiCommunication(); }

        if ( !mCurrentDevice.isAuthorized() && !hasSyncedDeviceToEnsureApiCommunication ) {
            //Lets sync Device Information.
            syncCurrentDevice();

            //Check Again
            if ( !mCurrentDevice.isAuthorized() ) {
                throw new OstError("wp_base_eda_1", ErrorCode.DEVICE_UNAUTHORIZED);
            }
        }
    }

    void syncCurrentDevice() throws OstError {
        OstUser ostUser = OstUser.getById(mUserId);
        if ( null == ostUser ) {
            throw new OstError("wp_base_scd_1", ErrorCode.DEVICE_NOT_SETUP);
        }
        OstDevice device = ostUser.getCurrentDevice();
        if ( null == device ) {
            throw new OstError("wp_base_scd_1", ErrorCode.DEVICE_NOT_SETUP);
        }
        String currentDeviceAddress = device.getAddress();
        try {
            mOstApiClient.getDevice( currentDeviceAddress );
        } catch (IOException e) {
            throw new OstError("wp_base_scd_3", ErrorCode.GET_DEVICE_API_FAILED);
        }
    }

    OstDeviceManager mDeviceManager;
    void ensureDeviceManager() throws OstError {
        if ( null == mOstUser ) {  ensureOstUser(); }
        String deviceManagerAddress = mOstUser.getDeviceManagerAddress();

        if ( null == deviceManagerAddress ) {
            throw new OstError("wp_base_edm_1", ErrorCode.USER_NOT_ACTIVATED);
        }

        mDeviceManager = OstDeviceManager.getById( deviceManagerAddress );
        if ( null == mDeviceManager ) {
            mDeviceManager = syncDeviceManager();
        }
    }

    OstDeviceManager syncDeviceManager()  throws OstError {
        if ( null == mOstUser ) {  ensureOstUser(); }
        String deviceManagerAddress = mOstUser.getDeviceManagerAddress();
        if ( null == deviceManagerAddress ) {
            throw new OstError("wp_base_sdm_1", ErrorCode.USER_NOT_ACTIVATED);
        }
        try {
            mOstApiClient.getDeviceManager();
            mDeviceManager = OstDeviceManager.getById( deviceManagerAddress );
            return mDeviceManager;
        } catch (IOException e) {
            throw new OstError("wp_base_sdm_2", ErrorCode.DEVICE_MANAGER_API_FAILED);
        }
    }

    OstRule[] mOstRules;
    OstRule[] ensureOstRules(String ruleName)  throws OstError {
        if ( null == mOstToken ) {  ensureOstToken(); }

        mOstRules = mOstToken.getAllRules();
        if ( null != mOstRules && mOstRules.length > 0 ) {
            int len = mOstRules.length;
            for(int cnt = 0; cnt < len; cnt++ ) {
                OstRule rule = mOstRules[cnt];
                if ( rule.getName().equalsIgnoreCase(ruleName) ) {
                    return mOstRules;
                }
            }
        }

        //Fetch the rules.
        try {
            JSONObject rulesResponseObject = mOstApiClient.getAllRules();
            JSONArray rulesJsonArray = (JSONArray)new CommonUtils().parseResponseForResultType(rulesResponseObject);

            int numberOfRules = rulesJsonArray.length();
            OstRule[] ostRules = new OstRule[numberOfRules];
            for (int i=0; i<numberOfRules; i++) {
                ostRules[i] = OstRule.parse(
                        rulesJsonArray.getJSONObject(i)
                );
            }
            mOstRules = ostRules;
        } catch (Exception e) {
            OstError ostError = new OstError("wp_base_eot_1", ErrorCode.RULES_API_FAILED);
            throw ostError;
        }
        return mOstRules;
    }
    //endregion


    //region - Helper methods
    String calculateExpirationHeight(long expiresInSecs) {
        JSONObject jsonObject = null;
        long currentBlockNumber, blockGenerationTime;
        String strCurrentBlockNumber;
        String strBlockGenerationTime;
        try {
            jsonObject = mOstApiClient.getCurrentBlockNumber();
            CommonUtils commonUtils = new CommonUtils();
            strCurrentBlockNumber = commonUtils.parseStringResponseForKey(jsonObject, OstConstants.BLOCK_HEIGHT);
            strBlockGenerationTime = commonUtils.parseStringResponseForKey(jsonObject, OstConstants.BLOCK_TIME);
        } catch (Throwable e) {
            throw new OstError("wf_bwf_ceh_1", ErrorCode.CHAIN_API_FAILED);
        }

        currentBlockNumber = Long.parseLong(strCurrentBlockNumber);
        blockGenerationTime = Long.parseLong(strBlockGenerationTime);
        long bufferBlocks = (OstConfigs.getInstance().SESSION_BUFFER_TIME) / blockGenerationTime;
        long expiresAfterBlocks = expiresInSecs / blockGenerationTime;
        long expirationHeight = currentBlockNumber + expiresAfterBlocks + bufferBlocks;

        return String.valueOf(expirationHeight);
    }
    //endregion

}