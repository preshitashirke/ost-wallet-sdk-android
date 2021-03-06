/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ost.walletsdk.ecKeyInteracts.structs;

public class SignedRevokeDeviceStruct extends BaseDeviceManagerOperationStruct {
    private String deviceToBeRevoked;

    public SignedRevokeDeviceStruct(String deviceTobeRevoked) {
        this.deviceToBeRevoked = deviceTobeRevoked;
    }

    public String getDeviceToBeRevoked() {
        return toSafeCheckSumAddress(deviceToBeRevoked);
    }

    public void setDeviceToBeRevoked(String deviceToBeRevoked) {
        this.deviceToBeRevoked = deviceToBeRevoked;
    }
}