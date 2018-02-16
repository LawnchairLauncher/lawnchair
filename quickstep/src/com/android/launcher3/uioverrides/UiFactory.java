/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.NORMAL;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.RecentsView;
import com.android.systemui.shared.recents.view.RecentsTransition;

public class UiFactory {

    private static final String CONTROL_REMOTE_APP_TRANSITION_PERMISSION =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";

    public static final boolean USE_HARDWARE_BITMAP = false; // FeatureFlags.IS_DOGFOOD_BUILD;

    public static TouchController[] createTouchControllers(Launcher launcher) {
        if (FeatureFlags.ENABLE_TWO_SWIPE_TARGETS) {
            return new TouchController[] {
                    new IgnoreTouchesInQuickScrub(),
                    new EdgeSwipeController(launcher),
                    new TwoStepSwipeController(launcher),
                    new OverviewSwipeController(launcher)};
        } else {
            return new TouchController[] {
                    new IgnoreTouchesInQuickScrub(),
                    new TwoStepSwipeController(launcher),
                    new OverviewSwipeController(launcher)};
        }
    }

    public static AccessibilityDelegate newPageIndicatorAccessibilityDelegate() {
        return null;
    }

    public static StateHandler[] getStateHandler(Launcher launcher) {
        return new StateHandler[] {
                launcher.getAllAppsController(), launcher.getWorkspace(),
                new RecentsViewStateController(launcher)};
    }

    public static void onWorkspaceLongPress(Launcher launcher, PointF touchPoint) {
        OptionsPopupView.show(launcher, touchPoint.x, touchPoint.y);
    }

    public static void onLauncherStateOrFocusChanged(Launcher launcher) {
        OverviewInteractionState.setBackButtonVisible(launcher, !launcher.isInState(NORMAL)
                || !launcher.hasWindowFocus());
    }

    public static Bitmap createFromRenderer(int width, int height, boolean forceSoftwareRenderer,
            BitmapRenderer renderer) {
        if (USE_HARDWARE_BITMAP && !forceSoftwareRenderer) {
            return RecentsTransition.createHardwareBitmap(width, height, renderer::render);
        } else {
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            renderer.render(new Canvas(result));
            return result;
        }
    }

    public static void resetOverview(Launcher launcher) {
        RecentsView recents = launcher.getOverviewPanel();
        recents.reset();
    }
}
