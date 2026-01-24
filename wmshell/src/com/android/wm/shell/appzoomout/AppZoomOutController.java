/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.appzoomout;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.Flags.spatialModelAppPushback;
import static com.android.systemui.Flags.spatialModelPushbackInShader;
import static com.android.systemui.shared.Flags.enableLppAssistInvocationEffect;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.util.Slog;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellInit;

/** Class that manages the app zoom out UI and states. */
public class AppZoomOutController implements RemoteCallable<AppZoomOutController>,
        ShellTaskOrganizer.FocusListener, DisplayChangeController.OnDisplayChangingListener {

    private static final String TAG = "AppZoomOutController";

    private final Context mContext;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final DisplayController mDisplayController;
    private final AppZoomOutDisplayAreaOrganizer mAppDisplayAreaOrganizer;
    private final TopLevelZoomOutDisplayAreaOrganizer mTopLevelDisplayAreaOrganizer;
    private final ShellExecutor mMainExecutor;
    private final AppZoomOutImpl mImpl = new AppZoomOutImpl();

    private final DisplayController.OnDisplaysChangedListener mDisplaysChangedListener =
            new DisplayController.OnDisplaysChangedListener() {
                @Override
                public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
                    if (displayId != DEFAULT_DISPLAY) {
                        return;
                    }
                    updateDisplayLayout(displayId);
                }

                @Override
                public void onDisplayAdded(int displayId) {
                    if (displayId != DEFAULT_DISPLAY) {
                        return;
                    }
                    updateDisplayLayout(displayId);
                }
            };


    public static AppZoomOutController create(Context context, ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer, DisplayController displayController,
            DisplayLayout displayLayout, @ShellMainThread ShellExecutor mainExecutor,
            InteractionJankMonitor interactionJankMonitor) {
        AppZoomOutDisplayAreaOrganizer appDisplayAreaOrganizer = new AppZoomOutDisplayAreaOrganizer(
                context, displayLayout, mainExecutor);
        TopLevelZoomOutDisplayAreaOrganizer topLevelDisplayAreaOrganizer =
                new TopLevelZoomOutDisplayAreaOrganizer(displayLayout, context, mainExecutor,
                        interactionJankMonitor);
        return new AppZoomOutController(context, shellInit, shellTaskOrganizer, displayController,
                appDisplayAreaOrganizer, topLevelDisplayAreaOrganizer, mainExecutor);
    }

    @VisibleForTesting
    AppZoomOutController(Context context, ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer, DisplayController displayController,
            AppZoomOutDisplayAreaOrganizer appDisplayAreaOrganizer,
            TopLevelZoomOutDisplayAreaOrganizer topLevelDisplayAreaOrganizer,
            @ShellMainThread ShellExecutor mainExecutor) {
        mContext = context;
        mTaskOrganizer = shellTaskOrganizer;
        mDisplayController = displayController;
        mAppDisplayAreaOrganizer = appDisplayAreaOrganizer;
        mTopLevelDisplayAreaOrganizer = topLevelDisplayAreaOrganizer;
        mMainExecutor = mainExecutor;

        if (spatialModelAppPushback() || enableLppAssistInvocationEffect()) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        mTaskOrganizer.addFocusListener(this);

        mDisplayController.addDisplayWindowListener(mDisplaysChangedListener);
        mDisplayController.addDisplayChangingController(this);
        updateDisplayLayout(mContext.getDisplayId());

        if (spatialModelAppPushback()) {
            mAppDisplayAreaOrganizer.registerOrganizer();
        }
        if (enableLppAssistInvocationEffect()) {
            mTopLevelDisplayAreaOrganizer.registerOrganizer();
        }
    }

    public AppZoomOut asAppZoomOut() {
        return mImpl;
    }

    public void setProgress(float progress) {
        if (!spatialModelPushbackInShader()) {
            mAppDisplayAreaOrganizer.setProgress(progress);
        }
    }

    /**
     * Scales all content on the screen belonging to
     * {@link DisplayAreaOrganizer#FEATURE_WINDOWED_MAGNIFICATION} and applies a cropping.
     *
     * @param progress progress to be applied to the top-level zoom effect.
     * @param vsyncId The vsync id to align the frame to.
     * @param sysuiMainHandler The main handler from SystemUI (required for CUJ tracking)
     */
    private void setTopLevelProgress(float progress, long vsyncId, Handler sysuiMainHandler) {
        if (enableLppAssistInvocationEffect()) {
            mTopLevelDisplayAreaOrganizer.setProgress(progress, vsyncId, sysuiMainHandler);
        }
    }

    void updateDisplayLayout(int displayId) {
        final DisplayLayout newDisplayLayout = mDisplayController.getDisplayLayout(displayId);
        if (newDisplayLayout == null) {
            Slog.w(TAG, "Failed to get new DisplayLayout.");
            return;
        }
        mAppDisplayAreaOrganizer.setDisplayLayout(newDisplayLayout);
        if (enableLppAssistInvocationEffect()) {
            mTopLevelDisplayAreaOrganizer.setDisplayLayout(newDisplayLayout);
        }
    }

    @Override
    public void onFocusTaskChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return;
        }
        if (taskInfo.getActivityType() == WindowConfiguration.ACTIVITY_TYPE_HOME) {
            mAppDisplayAreaOrganizer.setIsHomeTaskFocused(taskInfo.isFocused);
        }
    }

    @Override
    public void onDisplayChange(int displayId, int fromRotation, int toRotation,
            @Nullable DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction wct) {
        // TODO: verify if there is synchronization issues.
        if (toRotation != ROTATION_UNDEFINED) {
            mAppDisplayAreaOrganizer.onRotateDisplay(mContext, toRotation);
            if (enableLppAssistInvocationEffect()) {
                mTopLevelDisplayAreaOrganizer.onRotateDisplay(mContext, toRotation);
            }
        }
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    @ExternalThread
    private class AppZoomOutImpl implements AppZoomOut {
        @Override
        public void setProgress(float progress) {
            mMainExecutor.execute(() -> AppZoomOutController.this.setProgress(progress));
        }

        @Override
        public void setTopLevelProgress(float progress, long vsyncId, Handler sysuiMainHandler) {
            mMainExecutor.execute(() -> AppZoomOutController.this.setTopLevelProgress(progress,
                    vsyncId, sysuiMainHandler));
        }
    }
}
