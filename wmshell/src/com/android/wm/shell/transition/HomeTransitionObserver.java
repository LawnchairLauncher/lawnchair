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
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;

import static com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP;
import static com.android.wm.shell.transition.Transitions.TransitionObserver;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.TransitionUtil;

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
    public HomeTransitionObserver(@NonNull Context context,
            @NonNull ShellExecutor mainExecutor) {
        mContext = context;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        for (TransitionInfo.Change change : info.getChanges()) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (info.getType() == TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
                    || taskInfo == null
                    || taskInfo.displayId != DEFAULT_DISPLAY
                    || taskInfo.taskId == -1
                    || !taskInfo.isRunning) {
                continue;
            }

            final int mode = change.getMode();
            final boolean isBackGesture = change.hasFlags(FLAG_BACK_GESTURE_ANIMATED);
            if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME
                    && (TransitionUtil.isOpenOrCloseMode(mode) || isBackGesture)) {
                notifyHomeVisibilityChanged(TransitionUtil.isOpeningType(mode) || isBackGesture);
            }
        }
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {}

    @Override
    public void onTransitionMerged(@NonNull IBinder merged,
            @NonNull IBinder playing) {}

    @Override
    public void onTransitionFinished(@NonNull IBinder transition,
            boolean aborted) {}

    /**
     * Sets the home transition listener that receives any transitions resulting in a change of
     *
     */
    public void setHomeTransitionListener(Transitions transitions,
            IHomeTransitionListener listener) {
        if (mListener == null) {
            mListener = new SingleInstanceRemoteListener<>(this,
                    c -> transitions.registerObserver(this),
                    c -> transitions.unregisterObserver(this));
        }

        if (listener != null) {
            mListener.register(listener);
        } else {
            mListener.unregister();
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
