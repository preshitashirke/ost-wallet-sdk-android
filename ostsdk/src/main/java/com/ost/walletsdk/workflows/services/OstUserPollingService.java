/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ost.walletsdk.workflows.services;

import android.os.Bundle;

import com.ost.walletsdk.OstSdk;
import com.ost.walletsdk.models.entities.OstBaseEntity;
import com.ost.walletsdk.models.entities.OstUser;
import com.ost.walletsdk.network.OstApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class OstUserPollingService extends OstPollingService {

    private static final String TAG = "OstUserPollingService";

    private OstUserPollingService(String userId, String entityId, String successStatus, String failureStatus) {
        super(userId, entityId, successStatus, failureStatus);
    }

    public static Bundle startPolling(String userId, String entityId, String successStatus, String failureStatus) {
        OstUserPollingService ostUserPollingService = new OstUserPollingService(userId, entityId, successStatus, failureStatus);
        return ostUserPollingService.waitForUpdate();
    }

    @Override
    protected OstBaseEntity parseEntity(JSONObject entityObject) throws JSONException {
        return OstUser.parse(entityObject);
    }

    @Override
    protected String getEntityName() {
        return OstSdk.USER;
    }

    @Override
    protected JSONObject poll(String userId, String entityId) throws IOException {
        return new OstApiClient(userId).getUser();
    }

    @Override
    protected boolean validateParams(String entityId, String successStatus, String failureStatus) {
        return null != OstUser.getById(entityId) && OstUser.isValidStatus(successStatus) && OstUser.isValidStatus(failureStatus);
    }
}