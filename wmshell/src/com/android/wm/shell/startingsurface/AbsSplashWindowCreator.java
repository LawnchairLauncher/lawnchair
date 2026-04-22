/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.startingsurface;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.android.wm.shell.common.ShellExecutor;

// abstract class to create splash screen window(or windowless window)
abstract class AbsSplashWindowCreator {
    protected static final String TAG = StartingWindowController.TAG;
    protected final SplashscreenContentDrawer mSplashscreenContentDrawer;
    protected final Context mContext;
    protected final DisplayManager mDisplayManager;
    protected final ShellExecutor mSplashScreenExecutor;
    protected final StartingSurfaceDrawer.StartingWindowRecordManager mStartingWindowRecordManager;

    private StartingSurface.SysuiProxy mSysuiProxy;

    AbsSplashWindowCreator(SplashscreenContentDrawer contentDrawer, Context context,
            ShellExecutor splashScreenExecutor, DisplayManager displayManager,
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager) {
        mSplashscreenContentDrawer = contentDrawer;
        mContext = context;
        mSplashScreenExecutor = splashScreenExecutor;
        mDisplayManager = displayManager;
        mStartingWindowRecordManager = startingWindowRecordManager;
    }

    int getSplashScreenTheme(int splashScreenThemeResId, ActivityInfo activityInfo) {
        return splashScreenThemeResId != 0
                ? splashScreenThemeResId
                : activityInfo.getThemeResource() != 0 ? activityInfo.getThemeResource()
                        : com.android.internal.R.style.Theme_DeviceDefault_DayNight;
    }

    protected Display getDisplay(int displayId) {
        return mDisplayManager.getDisplay(displayId);
    }

    void setSysuiProxy(StartingSurface.SysuiProxy sysuiProxy) {
        mSysuiProxy = sysuiProxy;
    }

    protected void requestTopUi(boolean requestTopUi) {
        if (mSysuiProxy != null) {
            mSysuiProxy.requestTopUi(requestTopUi, TAG);
        }
    }
}
