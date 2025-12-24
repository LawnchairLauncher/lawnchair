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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.os.UserHandle.USER_NULL;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DesktopModeFlags.ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;

import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;
import static com.android.wm.shell.transition.Transitions.TransitionObserver;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.sysui.ShellInit;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link TransitionObserver} that observes for transitions involving the home
 * activity on the {@link android.view.Display#DEFAULT_DISPLAY} only.
 * It reports transitions to the caller via {@link IHomeTransitionListener}.
 */
public class HomeTransitionObserver implements TransitionObserver,
        RemoteCallable<HomeTransitionObserver> {
    private SingleInstanceRemoteListener<HomeTransitionObserver, IHomeTransitionListener>
            mListener;

    private @NonNull final Context mContext;
    private @NonNull final ShellExecutor mMainExecutor;
    private @NonNull final DisplayInsetsController mDisplayInsetsController;
    private IBinder mPendingStartDragTransition;

    private int mListenerUserId = USER_NULL; // UserId associated with the registered listener.
    private final Map<Integer, Boolean> mIsHomeVisibleForUser = new HashMap<>();

    public HomeTransitionObserver(@NonNull Context context,
            @NonNull ShellExecutor mainExecutor,
            @NonNull DisplayInsetsController displayInsetsController,
            @NonNull ShellInit shellInit) {
        mContext = context;
        mMainExecutor = mainExecutor;
        mDisplayInsetsController = displayInsetsController;

        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mDisplayInsetsController.addInsetsChangedListener(DEFAULT_DISPLAY,
                new DisplayInsetsController.OnInsetsChangedListener() {
                    @Override
                    public void insetsChanged(InsetsState insetsState) {
                        if (mListener == null) return;
                        mListener.call(l -> l.onDisplayInsetsChanged(insetsState));
                    }
                });
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        Boolean homeVisibilityUpdate = updateHomeVisibilityForUser(info);

        if (info.getType() == TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
                && homeVisibilityUpdate != null) {
            // Do not apply at the start of desktop drag as that updates launcher UI visibility.
            // Store the value and apply with a next transition or when cancelling the
            // desktop-drag transition.
            storePendingStartDragTransition(transition);
            return;
        }

        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                && info.getType() == TRANSIT_CONVERT_TO_BUBBLE
                && homeVisibilityUpdate == null
                && mPendingStartDragTransition != null) {
            // We are converting to bubble and we did not get a change to home visibility in this
            // transition. Apply the value from start of drag.
            homeVisibilityUpdate = mIsHomeVisibleForUser.get(mListenerUserId);
        }

        if (homeVisibilityUpdate != null) {
            mPendingStartDragTransition = null;
            notifyHomeVisibilityChanged(homeVisibilityUpdate);
        }
    }

    private void storePendingStartDragTransition(IBinder transition) {
        if (!BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                && !ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX.isTrue()) {
            return;
        }
        mPendingStartDragTransition = transition;
    }

    /**
     * Determines if a given transition represents a change in home visibility for the current user.
     * <p>
     * Only returns the visibility for the current user if it is in the transition.
     * <p>
     * If a change is a visibility change for any user, it is cached in
     * {@link #mIsHomeVisibleForUser} for pending transitions or when registering a listener.
     *
     * @param info The information about the transition.
     * @return Considering the current user, {@code true} if its home activity is becoming visible,
     *         {@code false} if invisible, or {@code null} if this change does not involve its home
     *         visibility.
     */
    private Boolean updateHomeVisibilityForUser(TransitionInfo info) {
        Boolean homeVisibilityUpdate = null;
        for (TransitionInfo.Change change : info.getChanges()) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null
                    || taskInfo.displayId != DEFAULT_DISPLAY
                    || taskInfo.taskId == -1
                    || !taskInfo.isRunning) {
                continue;
            }
            Boolean update = getHomeVisibilityUpdateForChange(info, change, taskInfo);
            if (update != null) {
                mIsHomeVisibleForUser.put(taskInfo.userId, update);
                if (taskInfo.userId == mListenerUserId) {
                    homeVisibilityUpdate = update;
                }
            }
        }
        return homeVisibilityUpdate;
    }

    /**
     * Determines if a given transition change for a task represents a change in home visibility.
     *
     * @param info The information about the transition.
     * @param change The specific change within the transition.
     * @param taskInfo The information about the task associated with the change.
     * @return {@code true} if the home activity is becoming visible, {@code false} if it's becoming
     *         invisible, or {@code null} if this change does not affect home visibility.
     */
    private Boolean getHomeVisibilityUpdateForChange(TransitionInfo info,
            TransitionInfo.Change change, ActivityManager.RunningTaskInfo taskInfo) {
        final int mode = change.getMode();
        final boolean isBackGesture = change.hasFlags(FLAG_BACK_GESTURE_ANIMATED);
        if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME) {
            final boolean gestureToHomeTransition = isBackGesture
                    && TransitionUtil.isClosingType(info.getType());
            if (gestureToHomeTransition || TransitionUtil.isClosingMode(mode)
                    || (!isBackGesture && TransitionUtil.isOpeningMode(mode))) {
                return gestureToHomeTransition || TransitionUtil.isOpeningType(mode);
            }
        }
        return null;
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {}

    @Override
    public void onTransitionMerged(@NonNull IBinder merged,
            @NonNull IBinder playing) {}

    @Override
    public void onTransitionFinished(@NonNull IBinder transition,
            boolean aborted) {
        if (!ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX.isTrue()) {
            return;
        }
        // Handle the case where the DragToDesktop START transition is interrupted and we never
        // receive a CANCEL/END transition.
        if (mPendingStartDragTransition == null
                || mPendingStartDragTransition != transition) {
            return;
        }
        mPendingStartDragTransition = null;

        Boolean pendingVisibility = mIsHomeVisibleForUser.get(mListenerUserId);
        if (pendingVisibility != null) {
            notifyHomeVisibilityChanged(pendingVisibility);
        }
    }

    /**
     * Sets the home transition listener that receives any transitions resulting in a change of
     *
     */
    public void setHomeTransitionListener(Transitions transitions, IHomeTransitionListener listener,
            int userId) {
        if (mListener == null) {
            mListener = new SingleInstanceRemoteListener<>(this,
                    c -> transitions.registerObserver(this),
                    c -> transitions.unregisterObserver(this));
        }

        if (listener != null) {
            mListenerUserId = userId;
            mListener.register(listener);
            Boolean isVisible = mIsHomeVisibleForUser.get(userId);
            if (isVisible != null) {
                notifyHomeVisibilityChanged(isVisible);
            }
        } else {
            mListener.unregister();
            mListenerUserId = USER_NULL;
        }
    }

    /**
     * Notifies the listener that the home visibility has changed.
     * @param isVisible true when home activity is visible, false otherwise.
     */
    public void notifyHomeVisibilityChanged(boolean isVisible) {
        if (mListener != null) {
            mListener.call(l -> l.onHomeVisibilityChanged(isVisible));
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

    /**
     * Invalidates this controller, preventing future calls to send updates.
     */
    public void invalidate(Transitions transitions) {
        transitions.unregisterObserver(this);
        if (mListener != null) {
            // Unregister the listener to ensure any registered binder death recipients are unlinked
            mListener.unregister();
        }
    }
}
