/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ost.walletsdk.models.entities;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.util.Log;

import com.ost.walletsdk.ecKeyInteracts.OstKeyManager;
import com.ost.walletsdk.models.Impls.OstModelFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.Keys;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * To hold User info
 */
@Entity(tableName = "user")
public class OstUser extends OstBaseEntity {

    private static final String TAG = "OstUser";

    public static final String TOKEN_ID = "token_id";
    public static final String TOKEN_HOLDER_ADDRESS = "token_holder_address";
    public static final String DEVICE_MANAGER_ADDRESS = "device_manager_address";
    public static final String RECOVERY_ADDRESS = "recovery_address";
    public static final String RECOVERY_OWNER_ADDRESS = "recovery_owner_address";
    public static final String TYPE = "type";

    public static String getIdentifier() {
        return OstUser.ID;
    }

    @Ignore
    private OstDevice currentDevice = null;

    public static OstUser getById(String id) {
        return OstModelFactory.getUserModel().getEntityById(id);
    }

    public static OstUser init(String id, String tokenId) {
        OstUser ostUser = OstUser.getById(id);
        if (null != ostUser) {
            Log.d(TAG, String.format("OstUser with id %s already exist", id));
            return ostUser;
        }

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(OstUser.ID, id);
            jsonObject.put(OstUser.TOKEN_ID, tokenId);
            return OstUser.parse(jsonObject);
        } catch (JSONException e) {
            Log.e(TAG, "Unexpected error: OstUser json updateWithApiResponse exception");
        }
        return null;
    }

    public OstDevice getCurrentDevice() {
        if (null == currentDevice) {
            OstKeyManager ostKeyManager = new OstKeyManager(getId());
            String currentDeviceAddress = ostKeyManager.getDeviceAddress();
            if (null != currentDeviceAddress) {
                currentDevice = OstDevice.getById(currentDeviceAddress);
                Log.d(TAG, String.format("currentDeviceAddress: %s", currentDeviceAddress));
            }
        }
        return currentDevice;
    }

    public OstSession getActiveSession(String spendingBtAmountInWei) {
        List<OstSession> ostActiveSessionList = OstSession.getActiveSessions(getId());
        String currentTime = String.valueOf(System.currentTimeMillis());
        for (OstSession ostSession : ostActiveSessionList) {
            String expirationTimestamp = ostSession.getExpirationTimestamp();
            BigInteger spendingLimitBI = new BigInteger(ostSession.getSpendingLimit());
            BigInteger spendingBtAmountInWeiBI = new BigInteger(spendingBtAmountInWei);
            if (spendingLimitBI.compareTo(spendingBtAmountInWeiBI) >= 0 && expirationTimestamp.compareTo(currentTime) > 0) {
                return ostSession;
            }
        }
        Log.e(TAG, "No Active session key available");
        return null;
    }

    public static class CONST_STATUS {
        public static final String CREATED = "created";
        public static final String ACTIVATING = "activating";
        public static final String ACTIVATED = "activated";
    }

    public static boolean isValidStatus(String status) {
        return Arrays.asList(CONST_STATUS.CREATED, CONST_STATUS.ACTIVATING, CONST_STATUS.ACTIVATED).contains(status);
    }

    public static class TYPE_VALUE {
        public static final String USER = "admin";
        public static final String ADMIN = "user";
    }


    private static EntityFactory entityFactory;
    private static EntityFactory getEntityFactory() {
        if ( null == entityFactory ) {
            entityFactory = new EntityFactory() {
                @Override
                public OstBaseEntity createEntity(JSONObject jsonObject) throws JSONException {
                    return new OstUser(jsonObject);
                }
            };
        }
        return entityFactory;
    }


    public static OstUser parse(JSONObject jsonObject) throws JSONException {
        return (OstUser) OstBaseEntity.insertOrUpdate(jsonObject, OstModelFactory.getUserModel(), getIdentifier(), getEntityFactory());
    }

    @Override
    protected OstUser updateWithJsonObject(JSONObject jsonObject) throws JSONException {
        return OstUser.parse(jsonObject);
    }


    public OstUser(String id, String parentId, JSONObject data, String status, double updatedTimestamp) {
        super(id, parentId, data, status, updatedTimestamp);
    }

    @Ignore
    public OstUser(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
    }

    public String getTokenId() {
        return this.getParentId();
    }

    public String getTokenHolderAddress() {
        String tokenHolderAddress = this.getJsonDataPropertyAsString(OstUser.TOKEN_HOLDER_ADDRESS);
        if (null != tokenHolderAddress) {
            tokenHolderAddress = Keys.toChecksumAddress(tokenHolderAddress);
        }
        return tokenHolderAddress;
    }


    public String getDeviceManagerAddress() {
        String deviceManagerAddress = this.getJsonDataPropertyAsString(OstUser.DEVICE_MANAGER_ADDRESS);
        if (null != deviceManagerAddress) {
            deviceManagerAddress = Keys.toChecksumAddress(deviceManagerAddress);
        }
        return deviceManagerAddress;
    }

    public String getRecoveryOwnerAddress() {
        String recoveryOwnerAddress = this.getJsonDataPropertyAsString(OstUser.RECOVERY_OWNER_ADDRESS);
        if (null != recoveryOwnerAddress) {
            recoveryOwnerAddress = Keys.toChecksumAddress(recoveryOwnerAddress);
        }
        return recoveryOwnerAddress;

    }

    public String getRecoveryAddress() {
        String recoveryAddress = this.getJsonDataPropertyAsString(OstUser.RECOVERY_ADDRESS);
        if (null != recoveryAddress) {
            recoveryAddress = Keys.toChecksumAddress(recoveryAddress);
        }
        return recoveryAddress;
    }

    public String getType() {
        return this.getJsonDataPropertyAsString(OstUser.TYPE);
    }

    public OstDevice createDevice() {
        OstKeyManager ostKeyManager = new OstKeyManager(getId(), true);
        String apiAddress = ostKeyManager.getApiKeyAddress();
        String address = ostKeyManager.getDeviceAddress();
        Log.d(TAG, "Create new device.");
        OstDevice ostDevice = OstDevice.init(address, apiAddress, getId());
        Log.d(TAG, "- address: " + address);
        Log.d(TAG, "- apiAddress: " + apiAddress);
        Log.d(TAG, "- getId: " + getId());
        return ostDevice;
    }

    @Override
    public void processJson(JSONObject data) throws JSONException {
        super.processJson(data);
    }

    @Override
    boolean validate(JSONObject jsonObject) {
        return super.validate(jsonObject) &&
                jsonObject.has(OstUser.ID) &&
                jsonObject.has(OstUser.TOKEN_ID);
    }

    public OstTokenHolder getTokenHolder() {
        String tokenHolderAddress = getTokenHolderAddress();
        if ( null == tokenHolderAddress ) {
            return null;
        }
        return OstModelFactory.getTokenHolderModel().getEntityById(tokenHolderAddress);
    }


    @Override
    String getEntityIdKey() {
        return getIdentifier();
    }

    @Override
    public String getParentIdKey() {
        return OstUser.TOKEN_ID;
    }

    public boolean isActivated() {
        return OstUser.CONST_STATUS.ACTIVATED.equalsIgnoreCase( this.getStatus() );
    }

    public boolean isActivating() {
        return OstUser.CONST_STATUS.ACTIVATING.equalsIgnoreCase( this.getStatus() );
    }

    public void flushCurrentDevice() {
        this.currentDevice = null;
    }
}