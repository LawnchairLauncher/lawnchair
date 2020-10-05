/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.LauncherUIHelper.doLayout;
import static com.android.launcher3.util.Preconditions.assertNotNull;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.shadows.ShadowOverrides;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowUserManager;

/**
 * Tests for {@link SecondaryDisplayLauncher} with work profile
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class SDWorkModeTest {

    private static final int SYSTEM_USER = 0;
    private static final int FLAG_SYSTEM = 0x00000800;
    private static final int WORK_PROFILE_ID = 10;
    private static final int FLAG_PROFILE = 0x00001000;

    private Context mTargetContext;
    private InvariantDeviceProfile mIdp;
    private LauncherModelHelper mModelHelper;

    private LauncherLayoutBuilder mLayoutBuilder;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mTargetContext = RuntimeEnvironment.application;
        mIdp = InvariantDeviceProfile.INSTANCE.get(mTargetContext);
        ShadowOverrides.setProvider(UserEventDispatcher.class,
                c -> mock(UserEventDispatcher.class));
        Settings.Global.putFloat(mTargetContext.getContentResolver(),
                Settings.Global.WINDOW_ANIMATION_SCALE, 0);

        mModelHelper.installApp(TEST_PACKAGE);
        mLayoutBuilder = new LauncherLayoutBuilder();
    }

    @Test
    public void testAllAppsList_noWorkProfile() throws Exception {
        SecondaryDisplayLauncher launcher = loadLauncher();
        launcher.showAppDrawer(true);
        doLayout(launcher);

        verifyRecyclerViewCount(launcher.getAppsView().getActiveRecyclerView());
    }

    @Test
    public void testAllAppsList_workProfile() throws Exception {
        ShadowUserManager sum = Shadow.extract(mTargetContext.getSystemService(UserManager.class));
        sum.addUser(SYSTEM_USER, "me", FLAG_SYSTEM);
        sum.addUser(WORK_PROFILE_ID, "work", FLAG_PROFILE);

        SecondaryDisplayLauncher launcher = loadLauncher();
        launcher.showAppDrawer(true);
        doLayout(launcher);

        AllAppsRecyclerView rv1 = launcher.getAppsView().getActiveRecyclerView();
        verifyRecyclerViewCount(rv1);

        assertNotNull(launcher.getAppsView().getWorkModeSwitch());
        assertTrue(launcher.getAppsView().getRecyclerViewContainer() instanceof AllAppsPagedView);

        AllAppsPagedView pagedView =
                (AllAppsPagedView) launcher.getAppsView().getRecyclerViewContainer();
        pagedView.snapToPageImmediately(1);
        doLayout(launcher);

        AllAppsRecyclerView rv2 = launcher.getAppsView().getActiveRecyclerView();
        verifyRecyclerViewCount(rv2);
        assertNotSame(rv1, rv2);
    }

    private SecondaryDisplayLauncher loadLauncher() throws Exception {
        // Install 100 apps
        for (int i = 0; i < 100; i++) {
            mModelHelper.installApp(TEST_PACKAGE + i);
        }
        mModelHelper.setupDefaultLayoutProvider(new LauncherLayoutBuilder()).loadModelSync();
        SecondaryDisplayLauncher launcher =
                Robolectric.buildActivity(SecondaryDisplayLauncher.class).setup().get();
        doLayout(launcher);
        return launcher;
    }

    private void verifyRecyclerViewCount(AllAppsRecyclerView rv) {
        int childCount = rv.getChildCount();
        assertTrue(childCount > 0);
        assertTrue(childCount < 100);
    }
}
