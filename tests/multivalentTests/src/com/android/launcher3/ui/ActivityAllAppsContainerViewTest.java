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
package com.android.launcher3.ui;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.WorkProfileManager;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.UserIconInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityAllAppsContainerViewTest {

    private static final UserHandle WORK_HANDLE = new UserHandle(13);
    @Mock
    private StatsLogManager mStatsLogManager;
    @Mock
    private UserCache mUserCache;
    @Mock
    private UserManager mUserManager;
    private AppInfo[] mWorkAppInfo;
    private ActivityAllAppsContainerView<?> mActivityAllAppsContainerView;
    private WorkProfileManager mWorkManager;
    private Context mContext;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        mActivityAllAppsContainerView = new ActivityAllAppsContainerView(mContext);
        when(mUserCache.getUserProfiles())
                .thenReturn(Arrays.asList(Process.myUserHandle(), WORK_HANDLE));
        when(mUserCache.getUserInfo(Process.myUserHandle()))
                .thenReturn(new UserIconInfo(Process.myUserHandle(), 0));
        when(mUserCache.getUserInfo(WORK_HANDLE))
                .thenReturn(new UserIconInfo(WORK_HANDLE, 1));
        mWorkManager = new WorkProfileManager(mUserManager, mActivityAllAppsContainerView,
                mStatsLogManager, mUserCache);
        mActivityAllAppsContainerView.setWorkManager(mWorkManager);
        ComponentName componentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailWorkAppInfo = new AppInfo(componentName, "Gmail", WORK_HANDLE, new Intent());
        mWorkAppInfo = new AppInfo[]{gmailWorkAppInfo};
    }

    @Test
    public void testOnAppsUpdatedWithoutWorkApps_shouldShowTabsIsFalse() {
        mActivityAllAppsContainerView.getAppsStore().setApps(EMPTY_ARRAY, 0, null);

        mActivityAllAppsContainerView.onAppsUpdated();

        assertThat(mActivityAllAppsContainerView.shouldShowTabs()).isEqualTo(false);
    }

    @Test
    public void testOnAppsUpdatedWithWorkApps_shouldShowTabsIsTrue() {
        mActivityAllAppsContainerView.getAppsStore().setApps(mWorkAppInfo, 0, null);

        mActivityAllAppsContainerView.onAppsUpdated();

        assertThat(mActivityAllAppsContainerView.shouldShowTabs()).isEqualTo(true);
    }

    @Test
    public void testWorkProfileEnabled_requestQuietModeCalledCorrectly() throws Exception {
        /* Setup */
        when(mUserManager.requestQuietModeEnabled(false, WORK_HANDLE))
                .thenReturn(true);

        /* Execution */
        mWorkManager.setWorkProfileEnabled(true);

        /* Assertion */
        awaitTasksCompleted();
        Mockito.verify(mUserManager).requestQuietModeEnabled(false, WORK_HANDLE);
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }
}
