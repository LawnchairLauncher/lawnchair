/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.compat;

import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.rule.ViewCaptureRule;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;


/**
 * Test to verify promise icon flow.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplPromiseIconUiTest extends AbstractLauncherUiTest {

    private int mSessionId = -1;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", launcher -> launcher != null);
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
        mSessionId = -1;
    }

    @After
    public void tearDown() {
        if (mSessionId > -1) {
            mTargetContext.getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        }
    }

    /**
     * Create a session and return the id.
     */
    private int createSession(String label, Bitmap icon) throws Throwable {
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName("test.promise.app");
        params.setAppLabel(label);
        params.setAppIcon(icon);
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        return mTargetContext.getPackageManager().getPackageInstaller().createSession(params);
    }

    @Test
    public void testPromiseIcon_addedFromEligibleSession() throws Throwable {
        final String appLabel = "Test Promise App " + UUID.randomUUID().toString();
        final ItemOperator findPromiseApp = (info, view) ->
                info != null && TextUtils.equals(info.title, appLabel);

        // Create and add test session
        mSessionId = createSession(appLabel, Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8));

        // Verify promise icon is added
        waitForLauncherCondition("Test Promise App not found on workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) != null);

        // Remove session
        mTargetContext.getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        mSessionId = -1;

        // Verify promise icon is removed
        waitForLauncherCondition("Test Promise App not removed from workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) == null);
    }

    @Test
    @ViewCaptureRule.MayProduceNoFrames
    public void testPromiseIcon_notAddedFromIneligibleSession() throws Throwable {
        final String appLabel = "Test Promise App " + UUID.randomUUID().toString();
        final ItemOperator findPromiseApp = (info, view) ->
                info != null && TextUtils.equals(info.title, appLabel);

        // Create and add test session without icon or label
        mSessionId = createSession(null, null);

        // Sleep for duration of animation if a view was to be added + some buffer time.
        Thread.sleep(Launcher.NEW_APPS_PAGE_MOVE_DELAY + Launcher.NEW_APPS_ANIMATION_DELAY + 500);

        // Verify promise icon is not added
        waitForLauncherCondition("Test Promise App not found on workspace", launcher ->
                launcher.getWorkspace().getFirstMatch(findPromiseApp) == null);
    }
}
