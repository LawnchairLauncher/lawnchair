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

import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.WorkProfileManager;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
@MockUser(userType = UserIconInfo.TYPE_MAIN)
@MockUser(userType = UserIconInfo.TYPE_WORK)
public class ActivityAllAppsContainerViewTest {

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public SandboxApplication app = spy(new SandboxApplication().withModelDependency());
    @Rule public MockUsersRule mockUserRule = new MockUsersRule(app);
    @Rule public TestActivityContext mContext = new TestActivityContext(app);

    private UserHandle mWorkHandle;

    @Mock
    private StatsLogManager mStatsLogManager;
    private AppInfo[] mWorkAppInfo;
    private ActivityAllAppsContainerView<?> mActivityAllAppsContainerView;
    private WorkProfileManager mWorkManager;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mActivityAllAppsContainerView = new ActivityAllAppsContainerView(mContext);
        mWorkHandle = mockUserRule.findUser(UserIconInfo::isWork);

        mWorkManager = new WorkProfileManager(mActivityAllAppsContainerView,
                mStatsLogManager, UserCache.getInstance(app));
        mActivityAllAppsContainerView.setWorkManager(mWorkManager);
        ComponentName componentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        AppInfo gmailWorkAppInfo = new AppInfo(componentName, "Gmail", mWorkHandle, new Intent());
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
        UserManager userManager = app.spyService(UserManager.class);
        doReturn(true).when(userManager).requestQuietModeEnabled(false, mWorkHandle);

        /* Execution */
        mWorkManager.setWorkProfileEnabled(true);

        /* Assertion */
        awaitTasksCompleted();
        Mockito.verify(userManager).requestQuietModeEnabled(false, mWorkHandle);
    }

    private static void awaitTasksCompleted() throws Exception {
        UI_HELPER_EXECUTOR.submit(() -> null).get();
    }
}
