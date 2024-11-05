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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;
import com.android.launcher3.util.UserIconInfo;
import com.android.systemui.shared.system.SysUiStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppEventProducerTest {

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();
    private static final UserHandle PRIVATE_HANDLE = new UserHandle(11);

    private static final UserIconInfo MAIN_ICON_INFO =
            new UserIconInfo(MAIN_HANDLE, UserIconInfo.TYPE_MAIN);
    private static final UserIconInfo PRIVATE_ICON_INFO =
            new UserIconInfo(PRIVATE_HANDLE, UserIconInfo.TYPE_PRIVATE);

    private SandboxContext mContext;
    private AppEventProducer mAppEventProducer;
    @Mock
    private UserCache mUserCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new SandboxContext(getApplicationContext());
        mContext.putObject(UserCache.INSTANCE, mUserCache);
        mAppEventProducer = new AppEventProducer(mContext, null);
    }

    @After
    public void tearDown() {
        mContext.onDestroy();
    }

    @Test
    public void buildAppTarget_containsCorrectUser() {
        when(mUserCache.getUserProfiles())
                .thenReturn(Arrays.asList(MAIN_HANDLE, PRIVATE_HANDLE));
        when(mUserCache.getUserInfo(any(UserHandle.class)))
                .thenReturn(MAIN_ICON_INFO, PRIVATE_ICON_INFO);
        ComponentName gmailComponentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailAppInfo = new
                AppInfo(gmailComponentName, "Gmail", MAIN_HANDLE, new Intent());
        gmailAppInfo.container = CONTAINER_ALL_APPS;
        gmailAppInfo.itemType = ITEM_TYPE_APPLICATION;

        AppTarget gmailTarget = mAppEventProducer
                .toAppTarget(buildItemInfoProtoForAppInfo(gmailAppInfo));

        assert gmailTarget != null;
        assertEquals(gmailTarget.getUser(), MAIN_HANDLE);

        when(mUserCache.getUserInfo(any(UserHandle.class)))
                .thenReturn(MAIN_ICON_INFO, PRIVATE_ICON_INFO);
        AppInfo gmailAppInfoPrivate = new
                AppInfo(gmailComponentName, "Gmail", PRIVATE_HANDLE, new Intent());
        gmailAppInfoPrivate.container = CONTAINER_ALL_APPS;
        gmailAppInfoPrivate.itemType = ITEM_TYPE_APPLICATION;

        AppTarget gmailPrivateTarget = mAppEventProducer
                .toAppTarget(buildItemInfoProtoForAppInfo(gmailAppInfoPrivate));

        assert gmailPrivateTarget != null;
        assertEquals(gmailPrivateTarget.getUser(), PRIVATE_HANDLE);
    }

    private LauncherAtom.ItemInfo buildItemInfoProtoForAppInfo(AppInfo appInfo) {
        LauncherAtom.ItemInfo.Builder itemBuilder = LauncherAtom.ItemInfo.newBuilder();
        if (appInfo.user.equals(PRIVATE_HANDLE)) {
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
