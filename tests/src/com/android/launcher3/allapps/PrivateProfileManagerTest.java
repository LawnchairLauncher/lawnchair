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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PrivateProfileManagerTest {

    @Rule(order = 0)
    public TestRule testStabilityRule = new TestStabilityRule();

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();
    private static final UserHandle PRIVATE_HANDLE = new UserHandle(11);
    private static final UserIconInfo MAIN_ICON_INFO =
            new UserIconInfo(MAIN_HANDLE, UserIconInfo.TYPE_MAIN);
    private static final UserIconInfo PRIVATE_ICON_INFO =
            new UserIconInfo(PRIVATE_HANDLE, UserIconInfo.TYPE_PRIVATE);

    private PrivateProfileManager mPrivateProfileManager;
    @Mock
    private ActivityAllAppsContainerView mAllApps;
    @Mock
    private StatsLogManager mStatsLogManager;
    @Mock
    private UserCache mUserCache;
    @Mock
    private UserManager mUserManager;
    @Mock
    private Context mContext;
    @Mock
    private AllAppsStore<?> mAllAppsStore;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private LauncherApps mLauncherApps;
    @Mock
    private AllAppsRecyclerView mAllAppsRecyclerView;
    @Mock
    private Resources mResources;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mUserCache.getUserProfiles())
                .thenReturn(Arrays.asList(MAIN_HANDLE, PRIVATE_HANDLE));
        when(mUserCache.getUserInfo(Process.myUserHandle())).thenReturn(MAIN_ICON_INFO);
        when(mUserCache.getUserInfo(PRIVATE_HANDLE)).thenReturn(PRIVATE_ICON_INFO);
        when(mAllApps.getContext()).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getApplicationContext()).thenReturn(getApplicationContext());
        when(mAllApps.getAppsStore()).thenReturn(mAllAppsStore);
        when(mAllApps.getActiveRecyclerView()).thenReturn(mAllAppsRecyclerView);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.resolveActivity(any(), any())).thenReturn(new ResolveInfo());
        when(mContext.getSystemService(LauncherApps.class)).thenReturn(mLauncherApps);
        when(mLauncherApps.getAppMarketActivityIntent(any(), any())).thenReturn(PendingIntent
                .getActivity(new ActivityContextWrapper(getApplicationContext()), 0,
                        new Intent(), PendingIntent.FLAG_IMMUTABLE).getIntentSender());
        when(mContext.getPackageName())
                .thenReturn("com.android.launcher3.tests.privateProfileManager");
        when(mLauncherApps.getPreInstalledSystemPackages(any())).thenReturn(new ArrayList<>());
        mPrivateProfileManager = new PrivateProfileManager(mUserManager,
                mAllApps, mStatsLogManager, mUserCache);
    }

    @Test
    public void lockPrivateProfile_requestsQuietModeAsTrue() throws Exception {
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED)).thenReturn(false);

        mPrivateProfileManager.lockPrivateProfile();

        awaitTasksCompleted();
        Mockito.verify(mUserManager).requestQuietModeEnabled(true, PRIVATE_HANDLE);
    }

    @Test
    public void unlockPrivateProfile_requestsQuietModeAsFalse() throws Exception {
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED)).thenReturn(true);

        mPrivateProfileManager.unlockPrivateProfile();

        awaitTasksCompleted();
        Mockito.verify(mUserManager).requestQuietModeEnabled(false, PRIVATE_HANDLE);
    }

    @Test
    public void quietModeFlagPresent_privateSpaceIsResetToDisabled() {
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        doNothing().when(privateProfileManager).resetPrivateSpaceDecorator(anyInt());
        doNothing().when(privateProfileManager).executeLock();
        doReturn(mAllAppsRecyclerView).when(privateProfileManager).getMainRecyclerView();
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED))
                .thenReturn(false, true);

        // In first call the state should be disabled.
        privateProfileManager.reset();
        assertEquals("Profile State is not Disabled", STATE_ENABLED,
                privateProfileManager.getCurrentState());

        // In the next call the state should be disabled.
        privateProfileManager.reset();
        assertEquals("Profile State is not Disabled", STATE_DISABLED,
                privateProfileManager.getCurrentState());
    }

    @Test
    public void transitioningToUnlocked_resetCallsPostUnlock() throws Exception {
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        doNothing().when(privateProfileManager).resetPrivateSpaceDecorator(anyInt());
        doReturn(mAllAppsRecyclerView).when(privateProfileManager).getMainRecyclerView();
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED))
                .thenReturn(false);
        doNothing().when(privateProfileManager).expandPrivateSpace();
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_DISABLED);

        privateProfileManager.unlockPrivateProfile();
        privateProfileManager.reset();

        awaitTasksCompleted();
        Mockito.verify(privateProfileManager).postUnlock();
    }

    @Test
    public void transitioningToLocked_resetCallsExecuteLock() throws Exception {
        PrivateProfileManager privateProfileManager = spy(mPrivateProfileManager);
        doNothing().when(privateProfileManager).resetPrivateSpaceDecorator(anyInt());
        doNothing().when(privateProfileManager).executeLock();
        doReturn(mAllAppsRecyclerView).when(privateProfileManager).getMainRecyclerView();
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED))
                .thenReturn(true);
        doNothing().when(privateProfileManager).expandPrivateSpace();
        when(privateProfileManager.getCurrentState()).thenReturn(STATE_ENABLED);

        privateProfileManager.lockPrivateProfile();
        privateProfileManager.reset();

        awaitTasksCompleted();
        Mockito.verify(privateProfileManager).executeLock();
    }

    @Test
    public void openPrivateSpaceSettings_triggersCorrectIntent() {
        Intent expectedIntent = ApiWrapper.INSTANCE.get(mContext).getPrivateSpaceSettingsIntent();
        ArgumentCaptor<Intent> acIntent = ArgumentCaptor.forClass(Intent.class);
        mPrivateProfileManager.setPrivateSpaceSettingsAvailable(true);

        mPrivateProfileManager.openPrivateSpaceSettings(null);

        Mockito.verify(mContext).startActivity(acIntent.capture());
        assertEquals("Intent Action is different",
                expectedIntent == null ? null : expectedIntent.toUri(0),
                acIntent.getValue() == null ? null : acIntent.getValue().toUri(0));
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }
}
