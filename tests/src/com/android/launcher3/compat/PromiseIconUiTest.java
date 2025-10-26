/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.compat;

import static android.os.Process.myUserHandle;

import static com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.TestUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Test to verify promise icon flow.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PromiseIconUiTest extends BaseLauncherActivityTest<Launcher> {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    public static final String PACKAGE_NAME = "test.promise.app";
    public static final String DUMMY_PACKAGE = "com.example.android.aardwolf";
    public static final String DUMMY_LABEL = "Aardwolf";

    private int mSessionId = -1;

    @Before
    public void setUp() throws Exception {
        loadLauncherSync();
        goToState(LauncherState.NORMAL);
        mSessionId = -1;
    }

    @After
    public void tearDown() throws IOException {
        if (mSessionId > -1) {
            targetContext().getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        }
        TestUtil.uninstallDummyApp();
    }

    /**
     * Create a session and return the id.
     */
    private int createSession(String packageName, String label, Bitmap icon) throws Throwable {
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        params.setAppLabel(label);
        params.setAppIcon(icon);
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        return targetContext().getPackageManager().getPackageInstaller().createSession(params);
    }

    @Test
    public void testPromiseIcon_addedFromEligibleSession() throws Throwable {
        final String appLabel = "Test Promise App " + UUID.randomUUID().toString();
        final ItemOperator findPromiseApp = (info, view) ->
                info != null && TextUtils.equals(info.title, appLabel);

        // Create and add test session
        mSessionId = createSession(PACKAGE_NAME, appLabel,
                Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8));

        // Verify promise icon is added
        waitForLauncherCondition("Test Promise App not found on workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) != null);

        // Remove session
        targetContext().getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        mSessionId = -1;

        // Verify promise icon is removed
        waitForLauncherCondition("Test Promise App not removed from workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) == null);
    }

    @Test
    public void testPromiseIcon_notAddedFromIneligibleSession() throws Throwable {
        final String appLabel = "Test Promise App " + UUID.randomUUID().toString();
        final ItemOperator findPromiseApp = (info, view) ->
                info != null && TextUtils.equals(info.title, appLabel);

        // Create and add test session without icon or label
        mSessionId = createSession(PACKAGE_NAME, null, null);

        // Sleep for duration of animation if a view was to be added + some buffer time.
        Thread.sleep(Launcher.NEW_APPS_PAGE_MOVE_DELAY + Launcher.NEW_APPS_ANIMATION_DELAY + 500);

        // Verify promise icon is not added
        waitForLauncherCondition("Test Promise App not found on workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) == null);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_SUPPORT_FOR_ARCHIVING)
    public void testPromiseIcon_addedArchivedApp() throws Throwable {
        installDummyAppAndWaitForUIUpdate();
        assertThat(executeShellCommand(
                String.format("pm archive --user %d %s",
                        myUserHandle().getIdentifier(), DUMMY_PACKAGE)))
                .isEqualTo("Success\n");

        // Create and add test session
        mSessionId = createSession(DUMMY_PACKAGE, /* label= */ "",
                Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8));

        // Verify promise icon is added to all apps view. The icon may not be added to the
        // workspace even if there might be no icon present for archived app. But icon will
        // always be in all apps view. In case an icon is not added, an exception would be thrown.
        goToState(LauncherState.ALL_APPS);

        // Wait for the promise icon to be added.
        waitForLauncherCondition(
                DUMMY_PACKAGE + " app was not found on all apps after being archived",
                launcher -> Arrays.stream(launcher.getAppsView().getAppsStore().getApps())
                        .filter(info -> DUMMY_LABEL.equals(info.title.toString()))
                        .findAny()
                        .isPresent());
    }

    private void installDummyAppAndWaitForUIUpdate() throws IOException {
        TestUtil.installDummyApp();
        loadLauncherSync();
    }
}
