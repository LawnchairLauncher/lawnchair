/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import android.os.RemoteException;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.quickstep.QuickScrubController;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.ISystemUiProxy;

/**
 * State indicating that the Launcher is behind an app
 */
public class BackgroundAppState extends OverviewState {

    private static final int STATE_FLAGS =
            FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public BackgroundAppState(int id) {
        super(id, QuickScrubController.QUICK_SCRUB_FROM_HOME_START_DURATION, STATE_FLAGS);
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return super.getVerticalProgress(launcher);
        }
        int transitionLength = LayoutUtils.getShelfTrackingDistance(launcher.getDeviceProfile());
        AllAppsTransitionController controller = launcher.getAllAppsController();
        float scrollRange = Math.max(controller.getShiftRange(), 1);
        float progressDelta = (transitionLength / scrollRange);
        return super.getVerticalProgress(launcher) + progressDelta;
    }

    @Override
    public float[] getOverviewScaleAndTranslationYFactor(Launcher launcher) {
        // Initialize the recents view scale to what it would be when starting swipe up/quickscrub
        RecentsView recentsView = launcher.getOverviewPanel();
        recentsView.getTaskSize(sTempRect);
        int appWidth = launcher.getDragLayer().getWidth();
        if (recentsView.shouldUseMultiWindowTaskSizeStrategy()) {
            ISystemUiProxy sysUiProxy = RecentsModel.INSTANCE.get(launcher).getSystemUiProxy();
            if (sysUiProxy != null) {
                try {
                    // Try to use the actual non-minimized app width (launcher will be resized to
                    // the non-minimized bounds, which differs from the app width in landscape
                    // multi-window mode
                    appWidth = sysUiProxy.getNonMinimizedSplitScreenSecondaryBounds().width();
                } catch (RemoteException e) {
                    // Ignore, fall back to just using the drag layer width
                }
            }
        }
        float scale = (float) appWidth / sTempRect.width();
        return new float[] { scale, 0f };
    }
}
