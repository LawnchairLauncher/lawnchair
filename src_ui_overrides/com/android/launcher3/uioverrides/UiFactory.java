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
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
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

    public static void onWorkspaceLongPress(Launcher launcher, PointF touchPoint) {
        launcher.getStateManager().goToState(OVERVIEW);
    }

    public static Bitmap createFromRenderer(int width, int height, boolean forceSoftwareRenderer,
            BitmapRenderer renderer) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.render(new Canvas(result));
        return result;
    }

    public static void resetOverview(Launcher launcher) { }

    public static void onLauncherStateOrFocusChanged(Launcher launcher) { }

    public static void onStart(Launcher launcher) { }

    public static void onTrimMemory(Launcher launcher, int level) { }
}
