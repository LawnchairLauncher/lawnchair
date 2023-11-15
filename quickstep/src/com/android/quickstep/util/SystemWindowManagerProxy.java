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

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.os.Build;
import android.util.ArrayMap;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.internal.policy.SystemBarUtils;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.util.Set;

/**
 * Extension of {@link WindowManagerProxy} with some assumption for the default system Launcher
 */
public class SystemWindowManagerProxy extends WindowManagerProxy {

    public SystemWindowManagerProxy(Context context) {
        super(true);
    }

    @Override
    public int getRotation(Context displayInfoContext) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            displayInfoContext.getResources().getConfiguration().windowConfiguration
                    .getRotation();
        }
        return super.getRotation(displayInfoContext);
    }

    @Override
    protected int getStatusBarHeight(Context context, boolean isPortrait, int statusBarInset) {
        // See b/264656380, calculate the status bar height manually as the inset in the system
        // server might not be updated by this point yet causing extra DeviceProfile updates
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            return SystemBarUtils.getStatusBarHeight(context);
        }
        return super.getStatusBarHeight(context, isPortrait, statusBarInset);
    }

    @Override
    public ArrayMap<CachedDisplayInfo, WindowBounds[]> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayMap<CachedDisplayInfo, WindowBounds[]> result = new ArrayMap<> ( );
            WindowManager windowManager = displayInfoContext.getSystemService (WindowManager.class);
            Set<WindowMetrics> possibleMaximumWindowMetrics =
                    windowManager.getPossibleMaximumWindowMetrics (DEFAULT_DISPLAY);
            for (WindowMetrics windowMetrics : possibleMaximumWindowMetrics) {
                CachedDisplayInfo info = getDisplayInfo (windowMetrics , Surface.ROTATION_0);
                WindowBounds[] bounds = estimateWindowBounds (displayInfoContext , info);
                result.put (info , bounds);
            }
            return result;
        }
        return super.estimateInternalDisplayBounds(displayInfoContext);
    }
}
