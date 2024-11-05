/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.pip;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_PIP;

import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_REMOVE_STACK;
import static com.android.wm.shell.pip.PipAnimationController.isInPipDirection;

import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.Flags;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.split.SplitScreenUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible supplying PiP Transitions.
 */
public abstract class PipTransitionController implements Transitions.TransitionHandler {

    protected final PipBoundsAlgorithm mPipBoundsAlgorithm;
    protected final PipBoundsState mPipBoundsState;
    protected final ShellTaskOrganizer mShellTaskOrganizer;
    protected final PipMenuController mPipMenuController;
    protected final Transitions mTransitions;
    private final List<PipTransitionCallback> mPipTransitionCallbacks = new ArrayList<>();
    protected PipTaskOrganizer mPipOrganizer;
    protected DefaultMixedHandler mMixedHandler;

    protected final PipAnimationController.PipAnimationCallback mPipAnimationCallback =
            new PipAnimationController.PipAnimationCallback() {
                @Override
                public void onPipAnimationStart(TaskInfo taskInfo,
                        PipAnimationController.PipTransitionAnimator animator) {
                    final int direction = animator.getTransitionDirection();
                    sendOnPipTransitionStarted(direction);
                }

                @Override
                public void onPipAnimationEnd(TaskInfo taskInfo, SurfaceControl.Transaction tx,
                        PipAnimationController.PipTransitionAnimator animator) {
                    final int direction = animator.getTransitionDirection();
                    mPipBoundsState.setBounds(animator.getDestinationBounds());
                    if (direction == TRANSITION_DIRECTION_REMOVE_STACK) {
                        return;
                    }
                    if (isInPipDirection(direction) && mPipOrganizer.mPipOverlay != null) {
                        mPipOrganizer.fadeOutAndRemoveOverlay(mPipOrganizer.mPipOverlay,
                                null /* callback */, true /* withStartDelay*/);
                    }
                    onFinishResize(taskInfo, animator.getDestinationBounds(), direction, tx);
                    sendOnPipTransitionFinished(direction);
                }

                @Override
                public void onPipAnimationCancel(TaskInfo taskInfo,
                        PipAnimationController.PipTransitionAnimator animator) {
                    final int direction = animator.getTransitionDirection();
                    if (isInPipDirection(direction) && mPipOrganizer.mPipOverlay != null) {
                        mPipOrganizer.fadeOutAndRemoveOverlay(mPipOrganizer.mPipOverlay,
                                null /* callback */, true /* withStartDelay */);
                    }
                    sendOnPipTransitionCancelled(animator.getTransitionDirection());
                }
            };

    /**
     * Called when transition is about to finish. This is usually for performing tasks such as
     * applying WindowContainerTransaction to finalize the PiP bounds and send to the framework.
     */
    public void onFinishResize(TaskInfo taskInfo, Rect destinationBounds,
            @PipAnimationController.TransitionDirection int direction,
            SurfaceControl.Transaction tx) {
    }

    /**
     * Called when the Shell wants to start an exit Pip transition/animation.
     */
    public void startExitTransition(int type, WindowContainerTransaction out,
            @Nullable Rect destinationBounds) {
        // Default implementation does nothing.
    }

    /**
     * Called when the Shell wants to start resizing Pip transition/animation.
     */
    public void startResizeTransition(WindowContainerTransaction wct) {
        // Default implementation does nothing.
    }

    /**
     * Called when the transition animation can't continue (eg. task is removed during
     * animation)
     */
    public void forceFinishTransition() {
    }

    /** Called when the fixed rotation started. */
    public void onFixedRotationStarted() {
    }

    /** Called when the fixed rotation finished. */
    public void onFixedRotationFinished() {
    }

    public PipTransitionController(
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            PipBoundsState pipBoundsState,
            PipMenuController pipMenuController, PipBoundsAlgorithm pipBoundsAlgorithm) {
        mPipBoundsState = pipBoundsState;
        mPipMenuController = pipMenuController;
        mShellTaskOrganizer = shellTaskOrganizer;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mTransitions = transitions;
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    protected void onInit() {
        mTransitions.addHandler(this);
    }

    void setPipOrganizer(PipTaskOrganizer pto) {
        mPipOrganizer = pto;
    }

    public void setMixedHandler(DefaultMixedHandler mixedHandler) {
        mMixedHandler = mixedHandler;
    }

    public void applyTransaction(WindowContainerTransaction wct) {
        mShellTaskOrganizer.applyTransaction(wct);
    }

    /**
     * Registers {@link PipTransitionCallback} to receive transition callbacks.
     */
    public void registerPipTransitionCallback(PipTransitionCallback callback) {
        mPipTransitionCallbacks.add(callback);
    }

    protected void sendOnPipTransitionStarted(
            @PipAnimationController.TransitionDirection int direction) {
        final Rect pipBounds = mPipBoundsState.getBounds();
        for (int i = mPipTransitionCallbacks.size() - 1; i >= 0; i--) {
            final PipTransitionCallback callback = mPipTransitionCallbacks.get(i);
            callback.onPipTransitionStarted(direction, pipBounds);
        }
        if (isInPipDirection(direction) && Flags.enablePipUiStateCallbackOnEntering()) {
            try {
                ActivityTaskManager.getService().onPictureInPictureUiStateChanged(
                        new PictureInPictureUiState.Builder()
                                .setTransitioningToPip(true)
                                .build());
            } catch (RemoteException | IllegalStateException e) {
                ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Failed to set alert PiP state change.");
            }
        }
    }

    protected void sendOnPipTransitionFinished(
            @PipAnimationController.TransitionDirection int direction) {
        for (int i = mPipTransitionCallbacks.size() - 1; i >= 0; i--) {
            final PipTransitionCallback callback = mPipTransitionCallbacks.get(i);
            callback.onPipTransitionFinished(direction);
        }
        if (isInPipDirection(direction) && Flags.enablePipUiStateCallbackOnEntering()) {
            try {
                ActivityTaskManager.getService().onPictureInPictureUiStateChanged(
                        new PictureInPictureUiState.Builder()
                                .setTransitioningToPip(false)
                                .build());
            } catch (RemoteException | IllegalStateException e) {
                ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Failed to set alert PiP state change.");
            }
        }
    }

    protected void sendOnPipTransitionCancelled(
            @PipAnimationController.TransitionDirection int direction) {
        for (int i = mPipTransitionCallbacks.size() - 1; i >= 0; i--) {
            final PipTransitionCallback callback = mPipTransitionCallbacks.get(i);
            callback.onPipTransitionCanceled(direction);
        }
    }

    /**
     * The windowing mode to restore to when resizing out of PIP direction. Defaults to undefined
     * and can be overridden to restore to an alternate windowing mode.
     */
    public int getOutPipWindowingMode() {
        // By default, simply reset the windowing mode to undefined.
        return WINDOWING_MODE_UNDEFINED;
    }

    protected void setBoundsStateForEntry(ComponentName componentName,
            PictureInPictureParams params,
            ActivityInfo activityInfo) {
        mPipBoundsState.setBoundsStateForEntry(componentName, activityInfo, params,
                mPipBoundsAlgorithm);
    }

    /**
     * Called when the display is going to rotate.
     *
     * @return {@code true} if it was handled, otherwise the existing pip logic
     *                      will deal with rotation.
     */
    public boolean handleRotateDisplay(int startRotation, int endRotation,
            WindowContainerTransaction wct) {
        return false;
    }

    /** @return whether the transition-request represents a pip-entry. */
    public boolean requestHasPipEnter(@NonNull TransitionRequestInfo request) {
        return request.getType() == TRANSIT_PIP;
    }

    /** Whether a particular change is a window that is entering pip. */
    public boolean isEnteringPip(@NonNull TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        return false;
    }

    /** Whether a particular package is same as current pip package. */
    public boolean isPackageActiveInPip(String packageName) {
        final TaskInfo inPipTask = mPipOrganizer.getTaskInfo();
        return packageName != null && inPipTask != null && mPipOrganizer.isInPip()
                && packageName.equals(SplitScreenUtils.getPackageName(inPipTask.baseIntent));
    }

    /** Add PiP-related changes to `outWCT` for the given request. */
    public void augmentRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request, @NonNull WindowContainerTransaction outWCT) {
        throw new IllegalStateException("Request isn't entering PiP");
    }

    /** Sets the type of animation when a PiP task appears. */
    public void setEnterAnimationType(@PipAnimationController.AnimationType int type) {
    }

    /** Play a transition animation for entering PiP on a specific PiP change. */
    public void startEnterAnimation(@NonNull final TransitionInfo.Change pipChange,
            @NonNull final SurfaceControl.Transaction startTransaction,
            @NonNull final SurfaceControl.Transaction finishTransaction,
            @NonNull final Transitions.TransitionFinishCallback finishCallback) {
    }

    /**
     * Applies the proper surface states (rounded corners/shadows) to pip surfaces in `info`.
     * This is intended to be used when PiP is part of another animation but isn't, itself,
     * animating (eg. unlocking).
     * @return `true` if there was a pip in `info`.
     */
    public boolean syncPipSurfaceState(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        return false;
    }

    /** End the currently-playing PiP animation. */
    public void end() {
    }

    /**
     * Finish the current transition if possible.
     *
     * @param tx transaction to be applied with a potentially new draw after finishing.
     */
    public void finishTransition(@Nullable SurfaceControl.Transaction tx) {
    }

    /**
     * End the currently-playing PiP animation.
     *
     * @param onTransitionEnd callback to run upon finishing the playing transition.
     */
    public void end(@Nullable Runnable onTransitionEnd) {
    }

    /** Starts the {@link android.window.SystemPerformanceHinter.HighPerfSession}. */
    public void startHighPerfSession() {}

    /** Closes the {@link android.window.SystemPerformanceHinter.HighPerfSession}. */
    public void closeHighPerfSession() {}

    /**
     * Callback interface for PiP transitions (both from and to PiP mode)
     */
    public interface PipTransitionCallback {
        /**
         * Callback when the pip transition is started.
         */
        void onPipTransitionStarted(int direction, Rect pipBounds);

        /**
         * Callback when the pip transition is finished.
         */
        void onPipTransitionFinished(int direction);

        /**
         * Callback when the pip transition is cancelled.
         */
        void onPipTransitionCanceled(int direction);
    }

    /**
     * Dumps internal states.
     */
    public void dump(PrintWriter pw, String prefix) {}
}
