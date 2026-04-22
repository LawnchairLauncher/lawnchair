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

import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.SystemClock;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.SplashScreenView;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;

class WindowlessSplashWindowCreator extends AbsSplashWindowCreator {

    private final TransactionPool mTransactionPool;

    WindowlessSplashWindowCreator(SplashscreenContentDrawer contentDrawer,
            Context context,
            ShellExecutor splashScreenExecutor,
            DisplayManager displayManager,
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager,
            TransactionPool pool) {
        super(contentDrawer, context, splashScreenExecutor, displayManager,
                startingWindowRecordManager);
        mTransactionPool = pool;
    }

    void addSplashScreenStartingWindow(StartingWindowInfo windowInfo, SurfaceControl rootSurface) {
        final ActivityManager.RunningTaskInfo taskInfo = windowInfo.taskInfo;
        final ActivityInfo activityInfo = windowInfo.targetActivityInfo != null
                ? windowInfo.targetActivityInfo
                : taskInfo.topActivityInfo;
        if (activityInfo == null || activityInfo.packageName == null) {
            return;
        }

        final int displayId = taskInfo.displayId;
        final Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            // Can't show splash screen on requested display, so skip showing at all.
            return;
        }
        final int theme = getSplashScreenTheme(0 /* splashScreenThemeResId */, activityInfo);
        final Context myContext = SplashscreenContentDrawer.createContext(mContext, windowInfo,
                theme, STARTING_WINDOW_TYPE_SPLASH_SCREEN, mDisplayManager);
        if (myContext == null) {
            return;
        }
        final StartingSurfaceDrawer.WindowlessStartingWindow wlw =
                new StartingSurfaceDrawer.WindowlessStartingWindow(
                        mContext.getResources().getConfiguration(), rootSurface);
        final SurfaceControlViewHost viewHost = new SurfaceControlViewHost(
                myContext, display, wlw, "WindowlessSplashWindowCreator");
        final String title = "Windowless Splash " + taskInfo.taskId;
        final WindowManager.LayoutParams lp = SplashscreenContentDrawer.createLayoutParameters(
                myContext, windowInfo, STARTING_WINDOW_TYPE_SPLASH_SCREEN, title,
                PixelFormat.TRANSLUCENT, new Binder());
        final Rect windowBounds = taskInfo.configuration.windowConfiguration.getBounds();
        lp.width = windowBounds.width();
        lp.height = windowBounds.height();

        final FrameLayout rootLayout = new FrameLayout(
                mSplashscreenContentDrawer.createViewContextWrapper(myContext));
        viewHost.setView(rootLayout, lp);
        final int bgColor = mSplashscreenContentDrawer.estimateTaskBackgroundColor(myContext);
        final SplashScreenView splashScreenView = mSplashscreenContentDrawer
                .makeSimpleSplashScreenContentView(myContext, windowInfo, bgColor);
        rootLayout.addView(splashScreenView);
        final SplashWindowRecord record = new SplashWindowRecord(viewHost, splashScreenView,
                wlw.mChildSurface, bgColor);
        mStartingWindowRecordManager.addRecord(taskInfo.taskId, record);
        windowInfo.notifyAddComplete(wlw.mChildSurface);
    }

    private class SplashWindowRecord extends StartingSurfaceDrawer.StartingWindowRecord {
        private SurfaceControlViewHost mViewHost;
        private final long mCreateTime;
        private SurfaceControl mChildSurface;
        private final SplashScreenView mSplashView;

        SplashWindowRecord(SurfaceControlViewHost viewHost, SplashScreenView splashView,
                SurfaceControl childSurface, int bgColor) {
            mViewHost = viewHost;
            mSplashView = splashView;
            mChildSurface = childSurface;
            mBGColor = bgColor;
            mCreateTime = SystemClock.uptimeMillis();
        }

        @Override
        public boolean removeIfPossible(StartingWindowRemovalInfo info, boolean immediately) {
            if (!immediately) {
                mSplashscreenContentDrawer.applyExitAnimation(mSplashView,
                        info.windowAnimationLeash, info.mainFrame,
                        this::release, mCreateTime, 0 /* roundedCornerRadius */);
            } else {
                release();
            }
            return true;
        }

        void release() {
            if (mChildSurface != null) {
                final SurfaceControl.Transaction t = mTransactionPool.acquire();
                t.remove(mChildSurface).apply();
                mTransactionPool.release(t);
                mChildSurface = null;
            }
            if (mViewHost != null) {
                mViewHost.release();
                mViewHost = null;
            }
        }
    }
}
