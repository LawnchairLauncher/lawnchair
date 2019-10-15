/**
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

package com.android.quickstep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.pm.LauncherActivityInfo;
import android.os.Process;
import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.model.AppLaunchTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AppPredictionsUITests extends AbstractQuickStepTest {

    private LauncherActivityInfo mSampleApp1;
    private LauncherActivityInfo mSampleApp2;
    private LauncherActivityInfo mSampleApp3;

    private AppPredictor.Callback mCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        List<LauncherActivityInfo> activities = LauncherAppsCompat.getInstance(mTargetContext)
                .getActivityList(null, Process.myUserHandle());
        mSampleApp1 = activities.get(0);
        mSampleApp2 = activities.get(1);
        mSampleApp3 = activities.get(2);

        // Disable app tracker
        AppLaunchTracker.INSTANCE.initializeForTesting(new AppLaunchTracker());
        PredictionUiStateManager.INSTANCE.initializeForTesting(null);

        mCallback = PredictionUiStateManager.INSTANCE.get(mTargetContext).appPredictorCallback(
                Client.HOME);

        mDevice.setOrientationNatural();
    }

    @After
    public void tearDown() throws Throwable {
        AppLaunchTracker.INSTANCE.initializeForTesting(null);
        PredictionUiStateManager.INSTANCE.initializeForTesting(null);
        mDevice.unfreezeRotation();
    }

    /**
     * Test that prediction UI is updated as soon as we get predictions from the system
     */
    @Test
    public void testPredictionExistsInAllApps() {
        mDevice.pressHome();
        mLauncher.pressHome().switchToAllApps();

        // Dispatch an update
        sendPredictionUpdate(mSampleApp1, mSampleApp2);
        // The first update should apply immediately.
        waitForLauncherCondition("Predictions were not updated in loading state",
                launcher -> getPredictedApp(launcher).size() == 2);
    }

    /**
     * Test that prediction update is deferred if it is already visible
     */
    @Test
    public void testPredictionsDeferredUntilHome() {
        mDevice.pressHome();
        sendPredictionUpdate(mSampleApp1, mSampleApp2);
        mLauncher.pressHome().switchToAllApps();
        waitForLauncherCondition("Predictions were not updated in loading state",
                launcher -> getPredictedApp(launcher).size() == 2);

        // Update predictions while all-apps is visible
        sendPredictionUpdate(mSampleApp1, mSampleApp2, mSampleApp3);
        assertEquals(2, getFromLauncher(this::getPredictedApp).size());

        // Go home and go back to all-apps
        mLauncher.pressHome().switchToAllApps();
        assertEquals(3, getFromLauncher(this::getPredictedApp).size());
    }

    @Test
    public void testPredictionsDisabled() {
        mDevice.pressHome();
        sendPredictionUpdate();
        mLauncher.pressHome().switchToAllApps();

        waitForLauncherCondition("Predictions were not updated in loading state",
                launcher -> launcher.getAppsView().getFloatingHeaderView()
                        .findFixedRowByType(PredictionRowView.class).getVisibility() == View.GONE);
        assertFalse(PredictionUiStateManager.INSTANCE.get(mTargetContext)
                .getCurrentState().isEnabled);
    }

    public ArrayList<BubbleTextView> getPredictedApp(Launcher launcher) {
        PredictionRowView container = launcher.getAppsView().getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class);

        ArrayList<BubbleTextView> predictedAppViews = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View view = container.getChildAt(i);
            if (view instanceof BubbleTextView && view.getVisibility() == View.VISIBLE) {
                predictedAppViews.add((BubbleTextView) view);
            }
        }
        return predictedAppViews;
    }

    private void sendPredictionUpdate(LauncherActivityInfo... activities) {
        getOnUiThread(() -> {
            List<AppTarget> targets = new ArrayList<>(activities.length);
            for (LauncherActivityInfo info : activities) {
                ComponentName cn = info.getComponentName();
                AppTarget target = new AppTarget.Builder(
                        new AppTargetId("app:" + cn), cn.getPackageName(), info.getUser())
                        .setClassName(cn.getClassName())
                        .build();
                targets.add(target);
            }
            mCallback.onTargetsAvailable(targets);
            return null;
        });
    }
}
