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

import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.UserIconInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PrivateProfileManagerTest {

    private static final UserHandle MAIN_HANDLE = Process.myUserHandle();
    private static final UserHandle PRIVATE_HANDLE = new UserHandle(11);
    private static final UserIconInfo MAIN_ICON_INFO =
            new UserIconInfo(MAIN_HANDLE, UserIconInfo.TYPE_MAIN);
    private static final UserIconInfo PRIVATE_ICON_INFO =
            new UserIconInfo(PRIVATE_HANDLE, UserIconInfo.TYPE_PRIVATE);
    private static final String SAFETY_CENTER_INTENT = Intent.ACTION_SAFETY_CENTER;
    private static final String PS_SETTINGS_FRAGMENT_KEY = ":settings:fragment_args_key";
    private static final String PS_SETTINGS_FRAGMENT_VALUE = "AndroidPrivateSpace_personal";

    private PrivateProfileManager mPrivateProfileManager;
    @Mock
    private ActivityAllAppsContainerView mActivityAllAppsContainerView;
    @Mock
    private StatsLogManager mStatsLogManager;
    @Mock
    private UserCache mUserCache;
    @Mock
    private UserManager mUserManager;
    @Mock
    private Context mContext;
    @Mock
    private AllAppsStore mAllAppsStore;
    @Mock
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mUserCache.getUserProfiles())
                .thenReturn(Arrays.asList(MAIN_HANDLE, PRIVATE_HANDLE));
        when(mUserCache.getUserInfo(Process.myUserHandle())).thenReturn(MAIN_ICON_INFO);
        when(mUserCache.getUserInfo(PRIVATE_HANDLE)).thenReturn(PRIVATE_ICON_INFO);
        when(mActivityAllAppsContainerView.getContext()).thenReturn(mContext);
        when(mActivityAllAppsContainerView.getAppsStore()).thenReturn(mAllAppsStore);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.resolveActivity(any(), any())).thenReturn(new ResolveInfo());
        mPrivateProfileManager = new PrivateProfileManager(mUserManager,
                mActivityAllAppsContainerView, mStatsLogManager, mUserCache);
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
        when(mAllAppsStore.hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED))
                .thenReturn(false, true);

        // In first call the state should be disabled.
        privateProfileManager.reset();
        assertEquals(STATE_ENABLED, privateProfileManager.getCurrentState());

        // In the next call the state should be disabled.
        privateProfileManager.reset();
        assertEquals(STATE_DISABLED, privateProfileManager.getCurrentState());
    }

    @Test
    public void openPrivateSpaceSettings_triggersSecurityAndPrivacyIntent() {
        Intent expectedIntent = new Intent(SAFETY_CENTER_INTENT);
        expectedIntent.putExtra(PS_SETTINGS_FRAGMENT_KEY, PS_SETTINGS_FRAGMENT_VALUE);
        ArgumentCaptor<Intent> acIntent = ArgumentCaptor.forClass(Intent.class);

        mPrivateProfileManager.openPrivateSpaceSettings();

        Mockito.verify(mContext).startActivity(acIntent.capture());
        Intent actualIntent = acIntent.getValue();
        assertEquals(expectedIntent.getAction(), actualIntent.getAction());
        assertEquals(expectedIntent.getStringExtra(PS_SETTINGS_FRAGMENT_KEY),
                actualIntent.getStringExtra(PS_SETTINGS_FRAGMENT_KEY));
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }
}
