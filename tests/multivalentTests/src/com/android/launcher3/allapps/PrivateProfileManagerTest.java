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

package com.android.launcher3.allapps;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.model.data.AppsListData.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@MockUser(userType = UserIconInfo.TYPE_MAIN)
@MockUser(userType = UserIconInfo.TYPE_PRIVATE)
public class PrivateProfileManagerTest {

    @Rule public TestRule testStabilityRule = new TestStabilityRule();
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public SandboxApplication app = spy(new SandboxApplication().withModelDependency());
    @Rule public MockUsersRule mockUserRule = new MockUsersRule(app);
    @Rule public TestActivityContext context = new TestActivityContext(app);

    private UserHandle mPrivateUser;

    private ActivityAllAppsContainerView<?> mActivityAllAppsContainerView;
    private PrivateProfileManager mPrivateProfileManager;

    @Mock
    private StatsLogManager mStatsLogManager;
    @Mock
    private AllAppsRecyclerView mAllAppsRecyclerView;

    private UserManager mUserManager;

    @Before
    public void setUp() {
        mPrivateUser = mockUserRule.findUser(UserIconInfo::isPrivate);
        mActivityAllAppsContainerView = new ActivityAllAppsContainerView(context);

        PackageManager pm = app.getPackageManager();
        doReturn(new ResolveInfo()).when(pm).resolveActivity(any(), any());

        LauncherApps launcherApps = app.spyService(LauncherApps.class);
        doReturn(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), FLAG_IMMUTABLE)
                .getIntentSender()).when(launcherApps).getAppMarketActivityIntent(any(), any());

        mPrivateProfileManager = spy(new PrivateProfileManager(
                mActivityAllAppsContainerView, mStatsLogManager, UserCache.getInstance(app)));
        doReturn(mAllAppsRecyclerView).when(mPrivateProfileManager).getMainRecyclerView();

        mUserManager = app.spyService(UserManager.class);
        doReturn(true).when(mUserManager).requestQuietModeEnabled(anyBoolean(), eq(mPrivateUser));
    }

    @Test
    public void lockPrivateProfile_requestsQuietModeAsTrue() throws Exception {
        context.getActivityComponent().getAppsStore().setApps(AppInfo.EMPTY_ARRAY, 0, null);


        mPrivateProfileManager.setQuietMode(true /* lock */);

        awaitTasksCompleted();
        Mockito.verify(mUserManager).requestQuietModeEnabled(true, mPrivateUser);
    }

    @Test
    public void unlockPrivateProfile_requestsQuietModeAsFalse() throws Exception {
        context.getActivityComponent().getAppsStore().setApps(
                AppInfo.EMPTY_ARRAY, FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, null);

        mPrivateProfileManager.setQuietMode(false /* unlock */);

        awaitTasksCompleted();
        Mockito.verify(mUserManager).requestQuietModeEnabled(false, mPrivateUser);
    }

    @Test
    public void quietModeFlagPresent_privateSpaceIsResetToDisabled() {
        doNothing().when(mPrivateProfileManager).addPrivateSpaceDecorator();
        doNothing().when(mPrivateProfileManager).executeLock();
        context.getActivityComponent().getAppsStore().setApps(AppInfo.EMPTY_ARRAY, 0, null);

        // In first call the state should be disabled.
        mPrivateProfileManager.reset();
        assertEquals("Profile State is not Disabled", STATE_ENABLED,
                mPrivateProfileManager.getCurrentState());

        // In the next call the state should be disabled.
        context.getActivityComponent().getAppsStore().setApps(
                AppInfo.EMPTY_ARRAY, FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, null);
        mPrivateProfileManager.reset();
        assertEquals("Profile State is not Disabled", STATE_DISABLED,
                mPrivateProfileManager.getCurrentState());
    }

    @Test
    public void transitioningToUnlocked_resetCallsPostUnlock() throws Exception {
        doNothing().when(mPrivateProfileManager).addPrivateSpaceDecorator();
        context.getActivityComponent().getAppsStore().setApps(AppInfo.EMPTY_ARRAY, 0, null);
        doNothing().when(mPrivateProfileManager).expandPrivateSpace();
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_DISABLED);

        mPrivateProfileManager.setQuietMode(false /* unlock */);
        mPrivateProfileManager.reset();

        awaitTasksCompleted();
        Mockito.verify(mPrivateProfileManager).postUnlock();
    }

    @Test
    public void transitioningToLocked_resetCallsExecuteLock() throws Exception {
        doNothing().when(mPrivateProfileManager).addPrivateSpaceDecorator();
        doNothing().when(mPrivateProfileManager).executeLock();
        context.getActivityComponent().getAppsStore().setApps(
                AppInfo.EMPTY_ARRAY, FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, null);
        doNothing().when(mPrivateProfileManager).expandPrivateSpace();
        when(mPrivateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);

        mPrivateProfileManager.setQuietMode(true /* lock */);
        mPrivateProfileManager.reset();

        awaitTasksCompleted();
        Mockito.verify(mPrivateProfileManager).executeLock();
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }
}
