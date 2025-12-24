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
package com.android.launcher3.secondarydisplay;

import static androidx.lifecycle.Lifecycle.State.RESUMED;

import static com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags.enableAutoStashConnectedDisplayTaskbar;
import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;
import static com.android.window.flags.Flags.useInputReportedFocusForAccessibility;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.window.BackEvent;
import android.window.DesktopExperienceFlags;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.dagger.ActivityContextSingleton;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.window.RecentsWindowManager;

import javax.inject.Inject;

/**
 * Implementation of {@link SecondaryDisplayDelegate}.
 */
@ActivityContextSingleton
public final class SecondaryDisplayQuickstepDelegateImpl extends SecondaryDisplayDelegate {

    private static final String TAG = "SecondaryDisplayQuickstepDelegateImpl";

    private final ActivityContext mActivityContext;
    private final Context mContext;
    private final TISBindHelper mTISBindHelper;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final OnBackAnimationCallback mOnBackInvokedCallback = new OnBackAnimationCallback() {
        @Override
        public void onBackInvoked() {
            Log.d(TAG, "onBackInvoked, displayId=" + mContext.getDisplayId());
            RecentsWindowManager recentsWindowManager = getVisibleRecentsWindowManager();
            if (recentsWindowManager != null) {
                recentsWindowManager.getOnBackInvokedCallback().onBackInvoked();
            }
        }

        @Override
        public void onBackStarted(@NonNull BackEvent event) {
            Log.d(TAG, "onBackStarted, displayId=" + mContext.getDisplayId());
            RecentsWindowManager recentsWindowManager = getVisibleRecentsWindowManager();
            if (recentsWindowManager != null) {
                if (recentsWindowManager.getOnBackInvokedCallback()
                        instanceof OnBackAnimationCallback onBackAnimationCallback) {
                    onBackAnimationCallback.onBackStarted(event);
                }
            }
        }

        @Override
        public void onBackProgressed(@NonNull BackEvent event) {
            Log.d(TAG, "onBackProgressed, displayId=" + mContext.getDisplayId());
            RecentsWindowManager recentsWindowManager = getVisibleRecentsWindowManager();
            if (recentsWindowManager != null) {
                if (recentsWindowManager.getOnBackInvokedCallback()
                        instanceof OnBackAnimationCallback onBackAnimationCallback) {
                    onBackAnimationCallback.onBackProgressed(event);
                }
            }
        }
        @Override
        public void onBackCancelled() {
            Log.d(TAG, "onBackCancelled, displayId=" + mContext.getDisplayId());
            RecentsWindowManager recentsWindowManager = getVisibleRecentsWindowManager();
            if (recentsWindowManager != null) {
                if (recentsWindowManager.getOnBackInvokedCallback()
                        instanceof OnBackAnimationCallback onBackAnimationCallback) {
                    onBackAnimationCallback.onBackCancelled();
                }
            }
        }
    };


    @Inject
    public SecondaryDisplayQuickstepDelegateImpl(ActivityContext activityContext,
            OverviewComponentObserver overviewComponentObserver) {
        mContext = activityContext.asContext();
        mActivityContext = activityContext;
        mTISBindHelper = new TISBindHelper(mContext, this::onTISConnected);
        mOverviewComponentObserver = overviewComponentObserver;
    }

    void onDestroy() {
        mTISBindHelper.onDestroy();
        if (mActivityContext instanceof Activity activity) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    mOnBackInvokedCallback);
        }
    }

    @Override
    void updateAppDivider() {
        mActivityContext.getAppsView().getFloatingHeaderView()
                .findFixedRowByType(AppsDividerView.class)
                .setShowAllAppsLabel(!ALL_APPS_VISITED_COUNT.hasReachedMax(mContext));
        ALL_APPS_VISITED_COUNT.increment(mContext);
    }

    @Override
    public void setPredictedApps(PredictedContainerInfo info) {
        mActivityContext.getAppsView().getFloatingHeaderView()
                .findFixedRowByType(PredictionRowView.class)
                .setPredictedApps(info.getContents());
    }

    @Override
    boolean enableTaskbarConnectedDisplays() {
        return DesktopExperienceFlags.ENABLE_TASKBAR_CONNECTED_DISPLAYS.isTrue();
    }

    @Override
    void openAllAppsForDisplay(int displayId) {
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager == null) {
            return;
        }
        TaskbarActivityContext currentDisplayTaskbarContext =
                taskbarManager.getTaskbarForDisplay(displayId);
        if (currentDisplayTaskbarContext != null) {
            currentDisplayTaskbarContext.openTaskbarAllApps();
        }
    }

    @Override
    void updateStashControllerStateFlags(int displayId, boolean isVisible) {
        if (displayId == Display.DEFAULT_DISPLAY
                || !enableAutoStashConnectedDisplayTaskbar.isTrue()) {
            return;
        }

        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager == null) {
            return;
        }
        TaskbarActivityContext tac =
                taskbarManager.getTaskbarForDisplay(displayId);
        if (tac == null) {
            return;
        }
        tac.updateStashControllerLauncherStateFlag(isVisible);
    }

    @Override
    boolean dispatchKeyEvent(KeyEvent event) {
        if (useInputReportedFocusForAccessibility()) {
            return false;
        }
        RecentsWindowManager recentsWindowManager = getVisibleRecentsWindowManager();
        if (recentsWindowManager != null) {
            return recentsWindowManager.dispatchKeyEvent(event);
        }
        return false;
    }

    @Nullable
    RecentsWindowManager getVisibleRecentsWindowManager() {
        BaseContainerInterface<?, ?> baseContainerInterface =
                mOverviewComponentObserver.getContainerInterface(mContext.getDisplayId());
        if (baseContainerInterface != null) {
            RecentsViewContainer container = baseContainerInterface.getCreatedContainer();
            if (container instanceof RecentsWindowManager recentsWindowManager
                    && recentsWindowManager.isRecentsViewVisible()) {
                return recentsWindowManager;
            }
        }
        return null;
    }

    @Override
    void onCreate() {
        if (mActivityContext instanceof Activity activity) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    mOnBackInvokedCallback);
        }
    }

    private void onTISConnected(TISBinder binder) {
        boolean isVisible = mActivityContext.getLifecycle().getCurrentState().isAtLeast(RESUMED);
        int displayId = mActivityContext.asContext().getDisplay().getDisplayId();
        updateStashControllerStateFlags(displayId, isVisible);
    }
}
