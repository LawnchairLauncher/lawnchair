/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManagerGlobal;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.WindowAnimationState;

import androidx.annotation.UiThread;

import com.android.internal.jank.Cuj;
import com.android.internal.os.IResultReceiver;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.systemui.animation.TransitionAnimator;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.wm.shell.recents.IRecentsAnimationController;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Wrapper around RecentsAnimationControllerCompat to help with some synchronization
 */
public class RecentsAnimationController {

    private static final String TAG = "RecentsAnimationController";
    private final RecentsAnimationControllerCompat mController;
    private final Consumer<RecentsAnimationController> mOnFinishedListener;

    private boolean mUseLauncherSysBarFlags = false;
    private boolean mFinishRequested = false;
    // Only valid when mFinishRequested == true.
    private boolean mFinishTargetIsLauncher;
    // Only valid when mFinishRequested == true
    private boolean mLauncherIsVisibleAtFinish;
    private RunnableList mPendingFinishCallbacks = new RunnableList();

    public RecentsAnimationController(RecentsAnimationControllerCompat controller,
            Consumer<RecentsAnimationController> onFinishedListener) {
        mController = controller;
        mOnFinishedListener = onFinishedListener;
    }

    /**
     * Synchronously takes a screenshot of the task with the given {@param taskId} if the task is
     * currently being animated.
     */
    public ThumbnailData screenshotTask(int taskId) {
        return ActivityManagerWrapper.getInstance().takeTaskThumbnail(taskId);
    }

    /**
     * Indicates that the gesture has crossed the window boundary threshold and system UI can be
     * update the system bar flags accordingly.
     */
    public void setUseLauncherSystemBarFlags(boolean useLauncherSysBarFlags) {
        if (mUseLauncherSysBarFlags != useLauncherSysBarFlags) {
            mUseLauncherSysBarFlags = useLauncherSysBarFlags;
            UI_HELPER_EXECUTOR.execute(() -> {
                try {
                    WindowManagerGlobal.getWindowManagerService().setRecentsAppBehindSystemBars(
                            useLauncherSysBarFlags);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to reach window manager", e);
                }
            });
        }
    }

    @UiThread
    public void handOffAnimation(RemoteAnimationTarget[] targets, WindowAnimationState[] states) {
        if (TransitionAnimator.Companion.longLivedReturnAnimationsEnabled()) {
            UI_HELPER_EXECUTOR.execute(() -> mController.handOffAnimation(targets, states));
        } else {
            Log.e(TAG, "Tried to hand off the animation, but the feature is disabled",
                    new Exception());
        }
    }

    @UiThread
    public void finishAnimationToHome() {
        finishController(true /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    @UiThread
    public void finishAnimationToApp() {
        finishController(false /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    /** See {@link #finish(boolean, Runnable, boolean)} */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete) {
        finish(toRecents, onFinishComplete, false /* sendUserLeaveHint */);
    }

    /**
     * @param onFinishComplete A callback that runs on the main thread after the animation
     *                         controller has finished on the background thread.
     * @param sendUserLeaveHint Determines whether userLeaveHint flag will be set on the pausing
     *                          activity. If userLeaveHint is true, the activity will enter into
     *                          picture-in-picture mode upon being paused.
     */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete, boolean sendUserLeaveHint) {
        Preconditions.assertUIThread();
        finishController(toRecents, onFinishComplete, sendUserLeaveHint);
    }

    @UiThread
    public void finish(boolean toRecents, boolean launcherIsVisibleAtFinish,
            Runnable onFinishComplete, boolean sendUserLeaveHint) {
        Preconditions.assertUIThread();
        finishController(toRecents, launcherIsVisibleAtFinish, onFinishComplete, sendUserLeaveHint,
                false);
    }

    @UiThread
    public void finishController(boolean toRecents, Runnable callback, boolean sendUserLeaveHint) {
        finishController(toRecents, false, callback, sendUserLeaveHint, false /* forceFinish */);
    }

    @UiThread
    public void finishController(boolean toRecents, Runnable callback, boolean sendUserLeaveHint,
            boolean forceFinish) {
        finishController(toRecents, toRecents, callback, sendUserLeaveHint, forceFinish);
    }

    @UiThread
    public void finishController(boolean toRecents, boolean launcherIsVisibleAtFinish,
            Runnable callback, boolean sendUserLeaveHint, boolean forceFinish) {
        mPendingFinishCallbacks.add(callback);
        if (!forceFinish && mFinishRequested) {
            // If finish has already been requested, then add the callback to the pending list.
            // If already finished, then adding it to the destroyed RunnableList will just 
            // trigger the callback to be called immediately
            return;
        }
        ActiveGestureProtoLogProxy.logFinishRecentsAnimation(toRecents);
        // Finish not yet requested
        mFinishRequested = true;
        mFinishTargetIsLauncher = toRecents;
        mLauncherIsVisibleAtFinish = launcherIsVisibleAtFinish;
        mOnFinishedListener.accept(this);
        Runnable finishCb = () -> {
            mController.finish(toRecents, sendUserLeaveHint, new IResultReceiver.Stub() {
                @Override
                public void send(int i, Bundle bundle) throws RemoteException {
                    ActiveGestureProtoLogProxy.logFinishRecentsAnimationCallback();
                    MAIN_EXECUTOR.execute(() -> {
                        mPendingFinishCallbacks.executeAllAndDestroy();
                    });
                }
            });
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_QUICK_SWITCH);
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME);
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_APP_SWIPE_TO_RECENTS);
        };
        if (forceFinish) {
            finishCb.run();
        } else {
            UI_HELPER_EXECUTOR.execute(finishCb);
        }
    }

    /**
     * @see RecentsAnimationControllerCompat#detachNavigationBarFromApp
     */
    @UiThread
    public void detachNavigationBarFromApp(boolean moveHomeToTop) {
        UI_HELPER_EXECUTOR.execute(() -> mController.detachNavigationBarFromApp(moveHomeToTop));
    }

    /**
     * @see IRecentsAnimationController#setWillFinishToHome(boolean)
     */
    @UiThread
    public void setWillFinishToHome(boolean willFinishToHome) {
        UI_HELPER_EXECUTOR.execute(() -> mController.setWillFinishToHome(willFinishToHome));
    }

    /**
     * Sets the final surface transaction on a Task. This is used by Launcher to notify the system
     * that animating Activity to PiP has completed and the associated task surface should be
     * updated accordingly. This should be called before `finish`
     * @param taskId for which the leash should be updated
     * @param finishTransaction the transaction to transfer to the task surface control after the
     *                          leash is removed
     * @param overlay the surface control for an overlay being shown above the pip (can be null)
     */
    public void setFinishTaskTransaction(int taskId,
            PictureInPictureSurfaceTransaction finishTransaction,
            SurfaceControl overlay) {
        UI_HELPER_EXECUTOR.execute(
                () -> mController.setFinishTaskTransaction(taskId, finishTransaction, overlay));
    }

    /**
     * Enables the input consumer to start intercepting touches in the app window.
     */
    public void enableInputConsumer() {
        UI_HELPER_EXECUTOR.submit(() -> {
            mController.setInputConsumerEnabled(true);
        });
    }

    /** @return wrapper controller. */
    public RecentsAnimationControllerCompat getController() {
        return mController;
    }

    /**
     * RecentsAnimationListeners can check this in onRecentsAnimationFinished() to determine whether
     * the animation was finished to launcher vs an app.
     */
    public boolean getFinishTargetIsLauncher() {
        return mFinishTargetIsLauncher;
    }

    /**
     * RecentsAnimationListeners can check this in onRecentsAnimationFinished() to determine whether
     * the animation was finished to launcher vs an app.
     */
    public boolean getLauncherIsVisibleAtFinish() {
        return mLauncherIsVisibleAtFinish;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "RecentsAnimationController:");

        pw.println(prefix + "\tmUseLauncherSysBarFlags=" + mUseLauncherSysBarFlags);
        pw.println(prefix + "\tmFinishRequested=" + mFinishRequested);
        pw.println(prefix + "\tmFinishTargetIsLauncher=" + mFinishTargetIsLauncher);
    }
}
