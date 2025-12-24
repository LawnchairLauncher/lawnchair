/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

import static junit.framework.Assert.assertEquals;

import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;
import com.android.systemui.shared.system.SysUiStatsLog;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppEventProducerTest {

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();

    @Rule public SandboxApplication mContext = new SandboxApplication();
    @Rule public MockUsersRule mMockUsersRule = new MockUsersRule(mContext);

    private AppEventProducer mAppEventProducer;

    @Before
    public void setUp() {
        mAppEventProducer = new AppEventProducer(mContext, null);
    }

    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE)
    @Test
    public void buildAppTarget_containsCorrectUser() {
        ComponentName gmailComponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailAppInfo = new
                AppInfo(gmailComponentName, "Gmail", MAIN_HANDLE, new Intent());
        gmailAppInfo.container = CONTAINER_ALL_APPS;
        gmailAppInfo.itemType = ITEM_TYPE_APPLICATION;

        UserHandle privateUserHandler = mMockUsersRule.findUser(UserIconInfo::isPrivate);

        AppTarget gmailTarget = mAppEventProducer
                .toAppTarget(buildItemInfoProtoForAppInfo(gmailAppInfo, privateUserHandler));

        assert gmailTarget != null;
        assertEquals(gmailTarget.getUser(), MAIN_HANDLE);

        AppInfo gmailAppInfoPrivate = new
                AppInfo(gmailComponentName, "Gmail", privateUserHandler, new Intent());
        gmailAppInfoPrivate.container = CONTAINER_ALL_APPS;
        gmailAppInfoPrivate.itemType = ITEM_TYPE_APPLICATION;

        AppTarget gmailPrivateTarget = mAppEventProducer
                .toAppTarget(buildItemInfoProtoForAppInfo(gmailAppInfoPrivate, privateUserHandler));

        assert gmailPrivateTarget != null;
        assertEquals(gmailPrivateTarget.getUser(), privateUserHandler);
    }

    private LauncherAtom.ItemInfo buildItemInfoProtoForAppInfo(
            AppInfo appInfo, UserHandle privateUserHandler) {
        LauncherAtom.ItemInfo.Builder itemBuilder = LauncherAtom.ItemInfo.newBuilder();
        if (appInfo.user.equals(privateUserHandler)) {
            itemBuilder.setUserType(SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE);
        } else {
            itemBuilder.setUserType(SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_MAIN);
        }
        itemBuilder.setApplication(LauncherAtom.Application.newBuilder()
                .setComponentName(appInfo.componentName.flattenToShortString())
                .setPackageName(appInfo.componentName.getPackageName()));
        itemBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                .setAllAppsContainer(LauncherAtom.AllAppsContainer.getDefaultInstance())
                .build());
        return itemBuilder.build();
    }
}
