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

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.SnapshotDrawerUtils;
import android.window.StartingWindowInfo;
import android.window.TaskSnapshot;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TransactionPool;

class WindowlessSnapshotWindowCreator {
    private static final int DEFAULT_FADEOUT_DURATION = 233;
    private final StartingSurfaceDrawer.StartingWindowRecordManager
            mStartingWindowRecordManager;
    private final DisplayManager mDisplayManager;
    private final Context mContext;
    private final SplashscreenContentDrawer mSplashscreenContentDrawer;
    private final TransactionPool mTransactionPool;

    WindowlessSnapshotWindowCreator(
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager,
            Context context,
            DisplayManager displayManager, SplashscreenContentDrawer splashscreenContentDrawer,
            TransactionPool transactionPool) {
        mStartingWindowRecordManager = startingWindowRecordManager;
        mContext = context;
        mDisplayManager = displayManager;
        mSplashscreenContentDrawer = splashscreenContentDrawer;
        mTransactionPool = transactionPool;
    }

    void makeTaskSnapshotWindow(StartingWindowInfo info, SurfaceControl rootSurface,
            TaskSnapshot snapshot, ShellExecutor removeExecutor) {
        final ActivityManager.RunningTaskInfo runningTaskInfo = info.taskInfo;
        final int taskId = runningTaskInfo.taskId;
        final String title = "Windowless Snapshot " + taskId;
        final WindowManager.LayoutParams lp = SnapshotDrawerUtils.createLayoutParameters(
                info, title, TYPE_APPLICATION_OVERLAY, snapshot.getHardwareBuffer().getFormat(),
                null /* token */);
        if (lp == null) {
            return;
        }
        final Display display = mDisplayManager.getDisplay(runningTaskInfo.displayId);
        final StartingSurfaceDrawer.WindowlessStartingWindow wlw =
                new StartingSurfaceDrawer.WindowlessStartingWindow(
                runningTaskInfo.configuration, rootSurface);
        final SurfaceControlViewHost mViewHost = new SurfaceControlViewHost(
                mContext, display, wlw, "WindowlessSnapshotWindowCreator");
        final Rect windowBounds = runningTaskInfo.configuration.windowConfiguration.getBounds();
        final InsetsState topWindowInsetsState = info.topOpaqueWindowInsetsState;
        final FrameLayout rootLayout = new FrameLayout(
                mSplashscreenContentDrawer.createViewContextWrapper(mContext));
        mViewHost.setView(rootLayout, lp);
        SnapshotDrawerUtils.drawSnapshotOnSurface(info, lp, wlw.mChildSurface, snapshot,
                windowBounds, topWindowInsetsState, false /* releaseAfterDraw */);

        final ActivityManager.TaskDescription taskDescription =
                SnapshotDrawerUtils.getOrCreateTaskDescription(runningTaskInfo);

        final SnapshotWindowRecord record = new SnapshotWindowRecord(mViewHost, wlw.mChildSurface,
                taskDescription.getBackgroundColor(), snapshot.hasImeSurface(),
                runningTaskInfo.topActivityType, removeExecutor,
                taskId, mStartingWindowRecordManager);
        mStartingWindowRecordManager.addRecord(taskId, record);
        info.notifyAddComplete(wlw.mChildSurface);
    }

    private class SnapshotWindowRecord extends StartingSurfaceDrawer.SnapshotRecord {
        private SurfaceControlViewHost mViewHost;
        private SurfaceControl mChildSurface;
        private final boolean mHasImeSurface;

        SnapshotWindowRecord(SurfaceControlViewHost viewHost, SurfaceControl childSurface,
                int bgColor, boolean hasImeSurface, int activityType,
                ShellExecutor removeExecutor, int id,
                StartingSurfaceDrawer.StartingWindowRecordManager recordManager) {
            super(activityType, removeExecutor, id, recordManager);
            mViewHost = viewHost;
            mChildSurface = childSurface;
            mBGColor = bgColor;
            mHasImeSurface = hasImeSurface;
        }

        @Override
        protected void removeImmediately() {
            super.removeImmediately();
            fadeoutThenRelease();
        }

        void fadeoutThenRelease() {
            final ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
            fadeOutAnimator.setDuration(DEFAULT_FADEOUT_DURATION);
            final SurfaceControl.Transaction t = mTransactionPool.acquire();
            fadeOutAnimator.addUpdateListener(animation -> {
                if (mChildSurface == null || !mChildSurface.isValid()) {
                    fadeOutAnimator.cancel();
                    return;
                }
                t.setAlpha(mChildSurface, (float) animation.getAnimatedValue());
                t.apply();
            });

            fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mChildSurface == null || !mChildSurface.isValid()) {
                        fadeOutAnimator.cancel();
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mTransactionPool.release(t);
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
            });
            fadeOutAnimator.start();
        }

        @Override
        protected boolean hasImeSurface() {
            return mHasImeSurface;
        }
    }
}
