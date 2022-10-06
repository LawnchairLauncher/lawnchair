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
package com.android.launcher3.deviceemulator;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.deviceemulator.models.DeviceEmulationData;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.util.concurrent.Callable;


public class DisplayEmulator {
    Context mContext;
    LauncherInstrumentation mLauncher;
    DisplayEmulator(Context context, LauncherInstrumentation launcher) {
        mContext = context;
        mLauncher = launcher;
    }

    /**
     * By changing the WindowManagerProxy we can override the window insets information
     **/
    private IWindowManager changeWindowManagerInstance(DeviceEmulationData deviceData) {
        WindowManagerProxy.INSTANCE.initializeForTesting(new TestWindowManagerProxy(deviceData));
        return WindowManagerGlobal.getWindowManagerService();
    }

    public <T> T emulate(DeviceEmulationData device, String grid, Callable<T> runInEmulation)
            throws Exception {
        WindowManagerProxy original = WindowManagerProxy.INSTANCE.get(mContext);
        // Set up emulation
        final int userId = UserHandle.myUserId();
        WindowManagerProxy.INSTANCE.initializeForTesting(new TestWindowManagerProxy(device));
        IWindowManager wm = changeWindowManagerInstance(device);
        // Change density twice to force display controller to reset its state
        wm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, device.density / 2, userId);
        wm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, device.density, userId);
        wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, device.width, device.height);
        wm.setForcedDisplayScalingMode(Display.DEFAULT_DISPLAY, 1);

        // Set up grid
        setGrid(grid);
        try {
            return runInEmulation.call();
        } finally {
            // Clear emulation
            WindowManagerProxy.INSTANCE.initializeForTesting(original);
            UiDevice.getInstance(getInstrumentation()).executeShellCommand("cmd window reset");
        }
    }

    private void setGrid(String gridType) {
        // When the grid changes, the desktop arrangement get stored in SQL and we need to wait to
        // make sure there is no SQL operations running and get SQL_BUSY error, that's why we need
        // to call mLauncher.waitForLauncherInitialized();
        mLauncher.waitForLauncherInitialized();
        String testProviderAuthority = mContext.getPackageName() + ".grid_control";
        Uri gridUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(testProviderAuthority)
                .appendPath("default_grid")
                .build();
        ContentValues values = new ContentValues();
        values.put("name", gridType);
        mContext.getContentResolver().update(gridUri, values, null, null);
    }
}
