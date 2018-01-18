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

import static com.android.launcher3.LauncherState.OVERVIEW;

import android.app.ActivityOptions;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.util.TouchController;

public class UiFactory {

    public static final boolean USE_HARDWARE_BITMAP = false;

    public static TouchController[] createTouchControllers(Launcher launcher) {
        return new TouchController[] {
                new AllAppsSwipeController(launcher), new PinchToOverviewListener(launcher)};
    }

    public static AccessibilityDelegate newPageIndicatorAccessibilityDelegate() {
        return new OverviewAccessibilityDelegate();
    }

    public static StateHandler[] getStateHandler(Launcher launcher) {
        return new StateHandler[] {
                (OverviewPanel) launcher.getOverviewPanel(),
                launcher.getAllAppsController(), launcher.getWorkspace() };
    }

    public static void onWorkspaceLongPress(Launcher launcher) {
        launcher.getStateManager().goToState(OVERVIEW);
    }

    public static Bitmap createFromRenderer(int width, int height, boolean forceSoftwareRenderer,
            BitmapRenderer renderer) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.render(new Canvas(result));
        return result;
    }

    public static void resetOverview(Launcher launcher) { }

    public static Bundle getActivityLaunchOptions(Launcher launcher, View v) {
        if (Utilities.ATLEAST_MARSHMALLOW) {
            int left = 0, top = 0;
            int width = v.getMeasuredWidth(), height = v.getMeasuredHeight();
            if (v instanceof BubbleTextView) {
                // Launch from center of icon, not entire view
                Drawable icon = ((BubbleTextView) v).getIcon();
                if (icon != null) {
                    Rect bounds = icon.getBounds();
                    left = (width - bounds.width()) / 2;
                    top = v.getPaddingTop();
                    width = bounds.width();
                    height = bounds.height();
                }
            }
            return ActivityOptions.makeClipRevealAnimation(v, left, top, width, height).toBundle();
        } else if (Utilities.ATLEAST_LOLLIPOP_MR1) {
            // On L devices, we use the device default slide-up transition.
            // On L MR1 devices, we use a custom version of the slide-up transition which
            // doesn't have the delay present in the device default.
            return ActivityOptions.makeCustomAnimation(
                    launcher, R.anim.task_open_enter, R.anim.no_anim).toBundle();
        }
        return null;
    }
}
