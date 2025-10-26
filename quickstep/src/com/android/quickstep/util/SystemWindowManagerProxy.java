/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.internal.policy.SystemBarUtils;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.quickstep.SystemUiProxy;
import com.android.window.flags.Flags;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Extension of {@link WindowManagerProxy} with some assumption for the default system Launcher
 */
@LauncherAppSingleton
public class SystemWindowManagerProxy extends WindowManagerProxy {
    // LC-Note: This is pretty much unused by Launcher3, see [LawnchairWindowManagerProxy]

    private final DesktopVisibilityController mDesktopVisibilityController;


    @Inject
    public SystemWindowManagerProxy(DesktopVisibilityController desktopVisibilityController) {
        super(true);
        mDesktopVisibilityController = desktopVisibilityController;
    }

    @Override
    public Rect getCurrentBounds(Context displayInfoContext) {
        return displayInfoContext.getResources().getConfiguration().windowConfiguration
                .getMaxBounds();
    }

    @Override
    public void registerDesktopVisibilityListener(DesktopVisibilityListener listener) {
        mDesktopVisibilityController.registerDesktopVisibilityListener(listener);
    }

    @Override
    public void unregisterDesktopVisibilityListener(DesktopVisibilityListener listener) {
        mDesktopVisibilityController.unregisterDesktopVisibilityListener(listener);
    }

    @Override
    public boolean isInDesktopMode(int displayId) {
        return mDesktopVisibilityController.isInDesktopMode(displayId);
    }

    @Override
    public boolean showLockedTaskbarOnHome(Context displayInfoContext) {
        if (!DesktopModeStatus.canEnterDesktopMode(displayInfoContext)) {
            return false;
        }
        if (!DesktopModeStatus.enterDesktopByDefaultOnFreeformDisplay(displayInfoContext)) {
            return false;
        }
        final boolean isFreeformDisplay = displayInfoContext.getResources().getConfiguration()
                .windowConfiguration.getWindowingMode() == WINDOWING_MODE_FREEFORM;
        return isFreeformDisplay;
    }

    @Override
    public boolean showDesktopTaskbarForFreeformDisplay(Context displayInfoContext) {
        if (!DesktopModeStatus.canEnterDesktopMode(displayInfoContext)) {
            return false;
        }

        if (!DesktopModeStatus.enterDesktopByDefaultOnFreeformDisplay(displayInfoContext)) {
            return false;
        }

        if (!Flags.enableDesktopTaskbarOnFreeformDisplays()) {
            return false;
        }

        final boolean isFreeformDisplay = displayInfoContext.getResources().getConfiguration()
                .windowConfiguration.getWindowingMode() == WINDOWING_MODE_FREEFORM;
        return isFreeformDisplay;
    }

    @Override
    public boolean isHomeVisible(Context context) {
        return SystemUiProxy.INSTANCE.get(context).getHomeVisibilityState().isHomeVisible();
    }

    @Override
    public int getRotation(Context displayInfoContext) {
        return displayInfoContext.getResources().getConfiguration().windowConfiguration
                .getRotation();
    }

    @Override
    protected int getStatusBarHeight(Context context, boolean isPortrait, int statusBarInset) {
        // See b/264656380, calculate the status bar height manually as the inset in the system
        // server might not be updated by this point yet causing extra DeviceProfile updates
        return SystemBarUtils.getStatusBarHeight(context);
    }

    @Override
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> result = new ArrayMap<>();
        WindowManager windowManager = displayInfoContext.getSystemService(WindowManager.class);
        Set<WindowMetrics> possibleMaximumWindowMetrics =
            null;
        try {
            possibleMaximumWindowMetrics = windowManager.getPossibleMaximumWindowMetrics(DEFAULT_DISPLAY);
        } catch (Throwable t) {
            possibleMaximumWindowMetrics = Collections.singleton(
                windowManager.getMaximumWindowMetrics());
        }
        for (WindowMetrics windowMetrics : possibleMaximumWindowMetrics) {
            CachedDisplayInfo info = getDisplayInfo(windowMetrics, Surface.ROTATION_0);
            List<WindowBounds> bounds = estimateWindowBounds(displayInfoContext, info);
            result.put(info, bounds);
        }
        return result;
    }

    @Override
    protected DisplayCutout rotateCutout(DisplayCutout original, int startWidth, int startHeight,
            int fromRotation, int toRotation) {
        return original.getRotated(startWidth, startHeight, fromRotation, toRotation);
    }
}
