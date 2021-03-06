/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ost.walletsdk.repositories;

import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.arch.persistence.room.testing.MigrationTestHelper;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.ost.walletsdk.OstSdk;
import com.ost.walletsdk.database.OstSdkDatabase;
import com.ost.walletsdk.models.Impls.OstModelFactory;
import com.ost.walletsdk.models.OstUserModel;
import com.ost.walletsdk.models.entities.OstUser;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class OstUserModelTest {


    @ClassRule
    public static MigrationTestHelper testHelper =
            new MigrationTestHelper(
                    InstrumentationRegistry.getInstrumentation(),
                    OstSdkDatabase.class.getCanonicalName(),
                    new FrameworkSQLiteOpenHelperFactory());

    @BeforeClass
    public static void setUp() throws IOException {
        Context appContext = InstrumentationRegistry.getTargetContext();
        testHelper.createDatabase("ostsdk_db", 1);
        OstSdk.initialize(appContext.getApplicationContext(), "");

        OstUserModel ostUserModel = OstModelFactory.getUserModel();
        ostUserModel.deleteAllEntities();
    }


    @Test
    public void testUserInsertion() throws JSONException, InterruptedException {
        // Context of the app under test.
        insertUserData();

        OstUser ostUser = OstSdk.getUser("1");
        assertNotNull(ostUser);
        assertEquals(OstUser.TYPE_VALUE.USER, ostUser.getType());
        assertEquals("1", ostUser.getId());
    }


    @Test
    public void testUserDeletion() throws JSONException, InterruptedException {
        // Context of the app under test.
        OstUser ostUser = insertUserData();

//        final CountDownLatch countDownLatch = new CountDownLatch(1);
        OstModelFactory.getUserModel().deleteEntity(ostUser.getId());
//        countDownLatch.await(5, TimeUnit.SECONDS);

        ostUser = OstSdk.getUser("1");
        assertEquals(OstUser.TYPE_VALUE.USER, ostUser.getType());
        assertNull(ostUser);
    }

    @Test
    public void testUserInsertionInCache() throws JSONException, InterruptedException {
        // Context of the app under test.
        OstUser ostUser = insertUserData();

        OstSdkDatabase.getDatabase().userDao().delete(ostUser.getId());
        ostUser = OstSdk.getUser("1");
        assertNotNull(ostUser);
        assertEquals(OstUser.TYPE_VALUE.USER, ostUser.getType());
        assertEquals("1", ostUser.getId());
    }

    @Test
    public void testUserInsertionInMemory() throws JSONException, InterruptedException {
        // Context of the app under test.
        OstUser ostUser = insertUserData();

        populateCache(2);

        OstSdkDatabase.getDatabase().userDao().delete(ostUser.getId());

        ostUser = OstSdk.getUser("1");
        assertNotNull(ostUser);
        assertEquals(OstUser.TYPE_VALUE.USER, ostUser.getType());
        assertEquals("1", ostUser.getId());
    }

    private void populateCache(int cacheSizeToPopulate) throws JSONException, InterruptedException {

        for (int i = 0; i < cacheSizeToPopulate; i++) {
            insertUserData(i + 10);
        }
    }

    private OstUser insertUserData() throws JSONException, InterruptedException {
        return insertUserData(1);
    }

    private OstUser insertUserData(int param) throws JSONException {
        JSONObject userObj = new JSONObject();

        userObj.put(OstUser.ID, String.valueOf(param));
        userObj.put(OstUser.TOKEN_ID, "1");
        userObj.put(OstUser.TYPE, OstUser.TYPE_VALUE.USER);
        userObj.put(OstUser.TOKEN_HOLDER_ADDRESS, "1");
        userObj.put(OstUser.DEVICE_MANAGER_ADDRESS, "1");

//        final CountDownLatch countDownLatch = new CountDownLatch(1);

        OstUser ostUser = OstUser.parse(userObj);

//        countDownLatch.await(5, TimeUnit.SECONDS);

        return ostUser;
    }
}