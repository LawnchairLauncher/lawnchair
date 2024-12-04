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

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.util.RotationUtils.deltaRotation;
import static android.util.RotationUtils.rotateBounds;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.view.WindowManager.transitTypeToString;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;

import static com.android.wm.shell.pip.PipAnimationController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.pip.PipAnimationController.ANIM_TYPE_BOUNDS;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_LEAVE_PIP;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_SAME;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;
import static com.android.wm.shell.pip.PipAnimationController.isInPipDirection;
import static com.android.wm.shell.pip.PipAnimationController.isOutPipDirection;
import static com.android.wm.shell.pip.PipTransitionState.ENTERED_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.annotation.IntDef;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TaskSnapshot;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.CounterRotatorHelper;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

/**
 * Implementation of transitions for PiP on phone. Responsible for enter (alpha, bounds) and
 * exit animation.
 */
public class PipTransition extends PipTransitionController {

    private static final String TAG = PipTransition.class.getSimpleName();

    /** No fixed rotation, or fixed rotation state is undefined. */
    private static final int FIXED_ROTATION_UNDEFINED = 0;
    /**
     * Fixed rotation detected via callbacks (see PipController#startSwipePipToHome());
     * this is used in the swipe PiP to home case, since the transitions itself isn't supposed to
     * see the fixed rotation.
     */
    private static final int FIXED_ROTATION_CALLBACK = 1;

    /** Fixed rotation detected in the incoming transition. */
    private static final int FIXED_ROTATION_TRANSITION = 2;

    private final Context mContext;
    private final PipTransitionState mPipTransitionState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final int mEnterExitAnimationDuration;
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;
    private final HomeTransitionObserver mHomeTransitionObserver;
    private final Optional<SplitScreenController> mSplitScreenOptional;
    private final PipAnimationController mPipAnimationController;
    private @PipAnimationController.AnimationType int mEnterAnimationType = ANIM_TYPE_BOUNDS;
    private Transitions.TransitionFinishCallback mFinishCallback;
    private SurfaceControl.Transaction mFinishTransaction;
    private final Rect mExitDestinationBounds = new Rect();
    @Nullable
    private IBinder mExitTransition;
    @Nullable
    private IBinder mMoveToBackTransition;
    private IBinder mRequestedEnterTransition;
    private WindowContainerToken mRequestedEnterTask;
    /** The Task window that is currently in PIP windowing mode. */
    @Nullable
    private WindowContainerToken mCurrentPipTaskToken;

    @IntDef(prefix = { "FIXED_ROTATION_" }, value =  {
            FIXED_ROTATION_UNDEFINED,
            FIXED_ROTATION_CALLBACK,
            FIXED_ROTATION_TRANSITION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FixedRotationState {}

    /** Fixed rotation state of the display. */
    private @FixedRotationState int mFixedRotationState = FIXED_ROTATION_UNDEFINED;
    /**
     * The rotation that the display will apply after expanding PiP to fullscreen. This is only
     * meaningful if {@link #mFixedRotationState} is {@link #FIXED_ROTATION_TRANSITION}.
     */
    @Surface.Rotation
    private int mEndFixedRotation;
    /** Whether the PIP window has fade out for fixed rotation. */
    private boolean mHasFadeOut;

    /** Used for setting transform to a transaction from animator. */
    private final PipAnimationController.PipTransactionHandler mTransactionConsumer =
            new PipAnimationController.PipTransactionHandler() {
                @Override
                public boolean handlePipTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, Rect destinationBounds, float alpha) {
                    // Only set the operation to transaction but do not apply.
                    return true;
                }
            };

    public PipTransition(Context context,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            PipBoundsState pipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipTransitionState pipTransitionState,
            PipMenuController pipMenuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipAnimationController pipAnimationController,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            HomeTransitionObserver homeTransitionObserver,
            Optional<SplitScreenController> splitScreenOptional) {
        super(shellInit, shellTaskOrganizer, transitions, pipBoundsState, pipMenuController,
                pipBoundsAlgorithm);
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipAnimationController = pipAnimationController;
        mEnterExitAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipResizeAnimationDuration);
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mHomeTransitionObserver = homeTransitionObserver;
        mSplitScreenOptional = splitScreenOptional;
    }

    @Override
    protected void onInit() {
        if (!PipUtils.isPip2ExperimentEnabled()) {
            mTransitions.addHandler(this);
        }
    }

    @Override
    public void startExitTransition(int type, WindowContainerTransaction out,
            @Nullable Rect destinationBounds) {
        if (destinationBounds != null) {
            mExitDestinationBounds.set(destinationBounds);
        }
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getCurrentAnimator();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        mExitTransition = mTransitions.startTransition(type, out, this);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo.Change currentPipTaskChange = findCurrentPipTaskChange(info);
        final TransitionInfo.Change fixedRotationChange = findFixedRotationChange(info);
        if (mFixedRotationState == FIXED_ROTATION_TRANSITION) {
            // If we are just about to process potential fixed rotation information,
            // then fixed rotation state should either be UNDEFINED or CALLBACK.
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "%s: startAnimation() should start with clear fixed rotation state", TAG);
            mFixedRotationState = FIXED_ROTATION_UNDEFINED;
        }
        mFixedRotationState = fixedRotationChange != null
                ? FIXED_ROTATION_TRANSITION : mFixedRotationState;
        mEndFixedRotation = mFixedRotationState == FIXED_ROTATION_TRANSITION
                ? fixedRotationChange.getEndFixedRotation()
                : ROTATION_UNDEFINED;

        // Exiting PIP.
        final int type = info.getType();
        if (transition.equals(mExitTransition) || transition.equals(mMoveToBackTransition)) {
            mExitDestinationBounds.setEmpty();
            mExitTransition = null;
            mMoveToBackTransition = null;
            mHasFadeOut = false;
            if (mFinishCallback != null) {
                callFinishCallback(null /* wct */);
                mFinishTransaction = null;
                throw new RuntimeException("Previous callback not called, aborting exit PIP.");
            }

            // PipTaskChange can be null if the PIP task has been detached, for example, when the
            // task contains multiple activities, the PIP will be moved to a new PIP task when
            // entering, and be moved back when exiting. In that case, the PIP task will be removed
            // immediately.
            final TaskInfo pipTaskInfo = currentPipTaskChange != null
                    ? currentPipTaskChange.getTaskInfo()
                    : mPipOrganizer.getTaskInfo();
            if (pipTaskInfo == null) {
                throw new RuntimeException("Cannot find the pip task for exit-pip transition.");
            }

            switch (type) {
                case TRANSIT_EXIT_PIP:
                    startExitAnimation(info, startTransaction, finishTransaction, finishCallback,
                            pipTaskInfo, currentPipTaskChange);
                    break;
                case TRANSIT_EXIT_PIP_TO_SPLIT:
                    startExitToSplitAnimation(info, startTransaction, finishTransaction,
                            finishCallback, pipTaskInfo);
                    break;
                case TRANSIT_TO_BACK:
                    // pass through here is intended
                case TRANSIT_REMOVE_PIP:
                    removePipImmediately(info, startTransaction, finishTransaction, finishCallback,
                            pipTaskInfo);
                    break;
                default:
                    throw new IllegalStateException("mExitTransition with unexpected transit type="
                            + transitTypeToString(type));
            }
            mCurrentPipTaskToken = null;
            return true;
        } else if (transition == mRequestedEnterTransition) {
            mRequestedEnterTransition = null;
            mRequestedEnterTask = null;
        }

        // The previous PIP Task is no longer in PIP, but this is not an exit transition (This can
        // happen when a new activity requests enter PIP). In this case, we just show this Task in
        // its end state, and play other animation as normal.
        if (currentPipTaskChange != null
                && currentPipTaskChange.getTaskInfo().getWindowingMode() != WINDOWING_MODE_PINNED) {
            resetPrevPip(currentPipTaskChange, startTransaction);
        }

        // Entering PIP.
        if (isEnteringPip(info)) {
            if (handleEnteringPipWithDisplayChange(transition, info, startTransaction,
                    finishTransaction, finishCallback)) {
                // The destination position is applied directly and let default transition handler
                // run the display change animation.
                return true;
            }
            startEnterAnimation(info, startTransaction, finishTransaction, finishCallback);
            return true;
        }

        // For transition that we don't animate, but contains the PIP leash, we need to update the
        // PIP surface, otherwise it will be reset after the transition.
        if (currentPipTaskChange != null) {
            updatePipForUnhandledTransition(currentPipTaskChange, startTransaction,
                    finishTransaction);
        }

        return false;
    }

    private boolean handleEnteringPipWithDisplayChange(@NonNull IBinder transition,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (mFixedRotationState != FIXED_ROTATION_UNDEFINED
                || !TransitionUtil.hasDisplayChange(info)) {
            return false;
        }
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: handle entering PiP with display change", TAG);
        mMixedHandler.animateEnteringPipWithDisplayChange(transition, info, pipChange,
                startT, finishT, finishCallback);
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        end();
    }

    /** Helper to identify whether this handler is currently the one playing an animation */
    private boolean isAnimatingLocally() {
        return mFinishTransaction != null;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (requestHasPipEnter(request)) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP enter request", TAG);
            WindowContainerTransaction wct = new WindowContainerTransaction();
            augmentRequest(transition, request, wct);
            return wct;
        } else if (request.getType() == TRANSIT_TO_BACK && request.getTriggerTask() != null
                && request.getTriggerTask().getWindowingMode() == WINDOWING_MODE_PINNED) {
            // if we receive a TRANSIT_TO_BACK type of request while in PiP
            mMoveToBackTransition = transition;
            // update the transition state to avoid {@link PipTaskOrganizer#onTaskVanished()} calls
            mPipTransitionState.setTransitionState(PipTransitionState.EXITING_PIP);

            // return an empty WindowContainerTransaction so that we don't check other handlers
            return new WindowContainerTransaction();
        } else {
            return null;
        }
    }

    @Override
    public void augmentRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request, @NonNull WindowContainerTransaction outWCT) {
        if (!requestHasPipEnter(request)) {
            throw new IllegalStateException("Called PiP augmentRequest when request has no PiP");
        }
        if (mEnterAnimationType == ANIM_TYPE_ALPHA) {
            mRequestedEnterTransition = transition;
            mRequestedEnterTask = request.getTriggerTask().token;
            outWCT.setActivityWindowingMode(request.getTriggerTask().token,
                    WINDOWING_MODE_UNDEFINED);
            final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
            outWCT.setBounds(request.getTriggerTask().token, destinationBounds);
        }
    }

    @Override
    public void end() {
        end(null);
    }

    @Override
    public void end(@Nullable Runnable onTransitionEnd) {
        if (mPipAnimationController.isAnimating()) {
            mPipAnimationController.getCurrentAnimator().end();
        }
        if (onTransitionEnd != null) {
            onTransitionEnd.run();
        }
    }

    @Override
    public boolean handleRotateDisplay(int startRotation, int endRotation,
            WindowContainerTransaction wct) {
        if (mRequestedEnterTransition != null && mEnterAnimationType == ANIM_TYPE_ALPHA) {
            // A fade-in was requested but not-yet started. In this case, just recalculate the
            // initial state under the new rotation.
            int rotationDelta = deltaRotation(startRotation, endRotation);
            if (rotationDelta != Surface.ROTATION_0) {
                mPipDisplayLayoutState.rotateTo(endRotation);

                final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
                wct.setBounds(mRequestedEnterTask, destinationBounds);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        // Transition either finished pre-emptively, got merged, or aborted,
        // so update fixed rotation state to default.
        mFixedRotationState = FIXED_ROTATION_UNDEFINED;

        if (transition != mExitTransition) {
            return;
        }
        // This means an expand happened before enter-pip finished and we are now "merging" a
        // no-op transition that happens to match our exit-pip.
        // Or that the keyguard is up and preventing the transition from applying, in which case we
        // want to manually reset pip. (b/283783868)
        boolean cancelled = false;
        if (mPipAnimationController.getCurrentAnimator() != null) {
            mPipAnimationController.getCurrentAnimator().cancel();
            mPipAnimationController.resetAnimatorState();
            cancelled = true;
        }

        // Unset exitTransition AFTER cancel so that finishResize knows we are merging.
        mExitTransition = null;
        if (!cancelled) return;
        final ActivityManager.RunningTaskInfo taskInfo = mPipOrganizer.getTaskInfo();
        if (taskInfo != null) {
            if (aborted) {
                // keyguard case - the transition got aborted, so we want to reset state and
                // windowing mode before reapplying the resize transaction
                sendOnPipTransitionFinished(TRANSITION_DIRECTION_LEAVE_PIP);
                mPipOrganizer.onExitPipFinished(taskInfo);

                WindowContainerTransaction wct = new WindowContainerTransaction();
                mPipOrganizer.applyWindowingModeChangeOnExit(wct, TRANSITION_DIRECTION_LEAVE_PIP);
                wct.setBounds(taskInfo.token, null);
                mPipOrganizer.applyFinishBoundsResize(wct, TRANSITION_DIRECTION_LEAVE_PIP, false);
            } else {
                // merge case
                startExpandAnimation(taskInfo, mPipOrganizer.getSurfaceControl(),
                        mPipBoundsState.getBounds(), mPipBoundsState.getBounds(),
                        new Rect(mExitDestinationBounds), Surface.ROTATION_0, null /* startT */);
            }
        }
        mExitDestinationBounds.setEmpty();
        mCurrentPipTaskToken = null;
    }

    @Override
    public void onFinishResize(TaskInfo taskInfo, Rect destinationBounds,
            @PipAnimationController.TransitionDirection int direction,
            @NonNull SurfaceControl.Transaction tx) {
        final boolean enteringPip = isInPipDirection(direction);
        if (enteringPip) {
            mPipTransitionState.setTransitionState(ENTERED_PIP);
        }
        // If we have an exit transition, but aren't playing a transition locally, it
        // means we're expecting the exit transition will be "merged" into another transition
        // (likely a remote like launcher), so don't fire the finish-callback here -- wait until
        // the exit transition is merged.
        if ((mExitTransition == null || isAnimatingLocally()) && mFinishCallback != null) {
            final SurfaceControl leash = mPipOrganizer.getSurfaceControl();
            final boolean hasValidLeash = leash != null && leash.isValid();
            WindowContainerTransaction wct = null;
            if (isOutPipDirection(direction)) {
                // Only need to reset surface properties. The server-side operations were already
                // done at the start. But if it is running fixed rotation, there will be a seamless
                // display transition later. So the last rotation transform needs to be kept to
                // avoid flickering, and then the display transition will reset the transform.
                if (mFixedRotationState != FIXED_ROTATION_TRANSITION
                        && mFinishTransaction != null) {
                    mFinishTransaction.merge(tx);
                }
            } else {
                wct = new WindowContainerTransaction();
                if (isInPipDirection(direction)) {
                    // If we are animating from fullscreen using a bounds animation, then reset the
                    // activity windowing mode, and set the task bounds to the final bounds
                    wct.setActivityWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED);
                    wct.setBounds(taskInfo.token, destinationBounds);
                    // If the animation is only used to apply destination bounds immediately and
                    // invisibly, then reshow it until the pip is drawn with the bounds.
                    final PipAnimationController.PipTransitionAnimator<?> animator =
                            mPipAnimationController.getCurrentAnimator();
                    if (animator != null && animator.getEndValue().equals(0f)) {
                        tx.addTransactionCommittedListener(mTransitions.getMainExecutor(),
                                () -> fadeExistingPip(true /* show */));
                    }
                } else {
                    wct.setBounds(taskInfo.token, null /* bounds */);
                }
                // Reset the scale with bounds change synchronously.
                if (hasValidLeash) {
                    mSurfaceTransactionHelper.crop(tx, leash, destinationBounds)
                            .resetScale(tx, leash, destinationBounds)
                            .round(tx, leash, true /* applyCornerRadius */);
                    final Rect appBounds = mPipOrganizer.mAppBounds;
                    if (mPipOrganizer.mPipOverlay != null && !appBounds.isEmpty()) {
                        // Resetting the scale for pinned task while re-adjusting its crop,
                        // also scales the overlay. So we need to update the overlay leash too.
                        Rect overlayBounds = new Rect(destinationBounds);
                        final int overlaySize = PipContentOverlay.PipAppIconOverlay
                                .getOverlaySize(appBounds, destinationBounds);

                        overlayBounds.offsetTo(
                                (destinationBounds.width() - overlaySize) / 2,
                                (destinationBounds.height() - overlaySize) / 2);
                        mSurfaceTransactionHelper.resetScale(tx,
                                mPipOrganizer.mPipOverlay, overlayBounds);
                    }
                }
                wct.setBoundsChangeTransaction(taskInfo.token, tx);
            }
            final int displayRotation = taskInfo.getConfiguration().windowConfiguration
                    .getDisplayRotation();
            if (enteringPip && mFixedRotationState == FIXED_ROTATION_TRANSITION
                    && mEndFixedRotation != displayRotation
                    && hasValidLeash) {
                // Launcher may update the Shelf height during the animation, which will update the
                // destination bounds. Because this is in fixed rotation, We need to make sure the
                // finishTransaction is using the updated bounds in the display rotation.
                final PipAnimationController.PipTransitionAnimator<?> animator =
                        mPipAnimationController.getCurrentAnimator();
                final Rect displayBounds = mPipDisplayLayoutState.getDisplayBounds();
                final Rect finishBounds = new Rect(destinationBounds);
                rotateBounds(finishBounds, displayBounds, mEndFixedRotation, displayRotation);
                if (!finishBounds.equals(animator.getEndValue())) {
                    ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Destination bounds were changed during animation", TAG);
                    rotateBounds(finishBounds, displayBounds, mEndFixedRotation, displayRotation);
                    mSurfaceTransactionHelper.crop(mFinishTransaction, leash, finishBounds);
                }
            }
            mFinishTransaction = null;
            callFinishCallback(wct);
        }
        // This is the end of transition on the Shell side so update the fixed rotation state.
        mFixedRotationState = FIXED_ROTATION_UNDEFINED;
        finishResizeForMenu(destinationBounds);
    }

    private void callFinishCallback(WindowContainerTransaction wct) {
        // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
        // handler if there is a pending PiP animation.
        final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
        mFinishCallback = null;
        finishCallback.onTransitionFinished(wct);
    }

    @Override
    public void forceFinishTransition() {
        // mFinishCallback might be null with an outdated mCurrentPipTaskToken
        // for example, when app crashes while in PiP and exit transition has not started
        mCurrentPipTaskToken = null;
        mFixedRotationState = FIXED_ROTATION_UNDEFINED;
        if (mFinishCallback == null) return;
        mFinishCallback.onTransitionFinished(null /* wct */);
        mFinishCallback = null;
        mFinishTransaction = null;
    }

    @Override
    public void onFixedRotationStarted() {
        if (mFixedRotationState == FIXED_ROTATION_UNDEFINED) {
            mFixedRotationState = FIXED_ROTATION_CALLBACK;
        }
        fadeEnteredPipIfNeed(false /* show */);
    }

    @Override
    public void onFixedRotationFinished() {
        fadeEnteredPipIfNeed(true /* show */);
    }

    private void fadeEnteredPipIfNeed(boolean show) {
        // The transition with this fixed rotation may be handled by other handler before reaching
        // PipTransition, so we cannot do this in #startAnimation.
        if (!mPipTransitionState.hasEnteredPip()) {
            return;
        }
        if (show && mHasFadeOut) {
            // If there is a pending transition, then let startAnimation handle it. And if it is
            // handled, mHasFadeOut will be set to false and this runnable will be no-op. Otherwise
            // make sure the PiP will reshow, e.g. swipe-up with fixed rotation (fade-out) but
            // return to the current app (only finish the recent transition).
            mTransitions.runOnIdle(() -> {
                if (mHasFadeOut && mPipTransitionState.hasEnteredPip()) {
                    fadeExistingPip(true /* show */);
                }
            });
        } else if (!show && !mHasFadeOut) {
            // Fade out the existing PiP to avoid jump cut during seamless rotation.
            fadeExistingPip(false /* show */);
        }
    }

    @Nullable
    private TransitionInfo.Change findCurrentPipTaskChange(@NonNull TransitionInfo info) {
        if (mCurrentPipTaskToken == null) {
            return null;
        }
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (mCurrentPipTaskToken.equals(change.getContainer())) {
                return change;
            }
        }
        return null;
    }

    @Nullable
    private TransitionInfo.Change findFixedRotationChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getEndFixedRotation() != ROTATION_UNDEFINED) {
                return change;
            }
        }
        return null;
    }

    private void startExitAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull TaskInfo taskInfo, @Nullable TransitionInfo.Change pipTaskChange) {
        TransitionInfo.Change pipChange = pipTaskChange;
        SurfaceControl activitySc = null;
        if (mCurrentPipTaskToken == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: There is no existing PiP Task for TRANSIT_EXIT_PIP", TAG);
        } else if (pipChange == null) {
            // The pipTaskChange is null, this can happen if we are reparenting the PIP activity
            // back to its original Task. In that case, we should animate the activity leash
            // instead, which should be the change whose last parent is the recorded PiP Task.
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (mCurrentPipTaskToken.equals(change.getLastParent())) {
                    // Find the activity that is exiting PiP.
                    pipChange = change;
                    activitySc = change.getLeash();
                    break;
                }
            }
        }
        // if overlay is present remove it immediately, as exit transition came before it faded out
        if (mPipOrganizer.mPipOverlay != null) {
            startTransaction.remove(mPipOrganizer.mPipOverlay);
            mPipOrganizer.clearContentOverlay();
        }
        if (mPipOrganizer.getOutPipWindowingMode() == WINDOWING_MODE_UNDEFINED) {
            mHomeTransitionObserver.notifyHomeVisibilityChanged(false /* isVisible */);
        }
        if (pipChange == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: No window of exiting PIP is found. Can't play expand animation", TAG);
            removePipImmediately(info, startTransaction, finishTransaction, finishCallback,
                    taskInfo);
            return;
        }

        // When exiting PiP, the PiP leash may be an Activity of a multi-windowing Task, for which
        // case it may not be in the screen coordinate.
        // Reparent the pip leash to the root with max layer so that we can animate it outside of
        // parent crop, and make sure it is not covered by other windows.
        final TransitionInfo.Root root = TransitionUtil.getRootFor(pipChange, info);
        final SurfaceControl pipLeash;
        if (activitySc != null) {
            // Use a local leash to animate activity in case the activity has letterbox which may
            // be broken by PiP animation, e.g. always end at 0,0 in parent and unable to include
            // letterbox area in crop bounds.
            final SurfaceControl activitySurface = pipChange.getLeash();
            pipLeash = new SurfaceControl.Builder()
                    .setName(activitySc + "_pip-leash")
                    .setContainerLayer()
                    .setHidden(false)
                    .setParent(root.getLeash())
                    .setCallsite("PipTransition.startExitAnimation")
                    .build();
            startTransaction.reparent(activitySurface, pipLeash);
            // Put the activity at local position with offset in case it is letterboxed.
            final Point activityOffset = pipChange.getEndRelOffset();
            startTransaction.setPosition(activitySc, activityOffset.x, activityOffset.y);
        } else {
            pipLeash = pipChange.getLeash();
            startTransaction.reparent(pipLeash, root.getLeash());
        }
        startTransaction.setLayer(pipLeash, Integer.MAX_VALUE);
        // Note: because of this, the bounds to animate should be translated to the root coordinate.
        final Point offset = root.getOffset();
        final Rect currentBounds = mPipBoundsState.getBounds();
        currentBounds.offset(-offset.x, -offset.y);
        startTransaction.setPosition(pipLeash, currentBounds.left, currentBounds.top);

        final WindowContainerToken pipTaskToken = pipChange.getContainer();
        final boolean useLocalLeash = activitySc != null;
        final boolean toFullscreen = pipChange.getEndAbsBounds().equals(
                mPipBoundsState.getDisplayBounds());
        mFinishCallback = (wct) -> {
            mPipOrganizer.onExitPipFinished(taskInfo);

            // TODO(b/286346098): remove the OPEN app flicker completely
            // not checking if we go to fullscreen helps avoid getting pip into an inconsistent
            // state after the flicker occurs. This is a temp solution until flicker is removed.
            if (!Transitions.SHELL_TRANSITIONS_ROTATION) {
                // will help to debug the case when we are not exiting to fullscreen
                if (!toFullscreen) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: startExitAnimation() not exiting to fullscreen", TAG);
                }
                wct = wct != null ? wct : new WindowContainerTransaction();
                wct.setBounds(pipTaskToken, null);
                mPipOrganizer.applyWindowingModeChangeOnExit(wct, TRANSITION_DIRECTION_LEAVE_PIP);
            }
            if (useLocalLeash) {
                if (mPipAnimationController.isAnimating()) {
                    mPipAnimationController.getCurrentAnimator().end();
                }
                // Make sure the animator don't use the released leash, e.g. mergeAnimation.
                mPipAnimationController.resetAnimatorState();
                finishTransaction.remove(pipLeash);
            }
            finishCallback.onTransitionFinished(wct);
        };
        mFinishTransaction = finishTransaction;

        // Check if it is Shell rotation.
        if (Transitions.SHELL_TRANSITIONS_ROTATION) {
            TransitionInfo.Change displayRotationChange = null;
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change.getMode() == TRANSIT_CHANGE
                        && (change.getFlags() & FLAG_IS_DISPLAY) != 0
                        && change.getStartRotation() != change.getEndRotation()) {
                    displayRotationChange = change;
                    break;
                }
            }
            if (displayRotationChange != null) {
                // Exiting PIP to fullscreen with orientation change.
                startExpandAndRotationAnimation(info, startTransaction, finishTransaction,
                        displayRotationChange, taskInfo, pipChange, offset);
                return;
            }
        }

        final Rect destinationBounds = new Rect(pipChange.getEndAbsBounds());
        destinationBounds.offset(-offset.x, -offset.y);

        // Check if it is fixed rotation.
        final int rotationDelta;
        if (mFixedRotationState == FIXED_ROTATION_TRANSITION) {
            final int startRotation = pipChange.getStartRotation();
            final int endRotation = mEndFixedRotation;
            rotationDelta = deltaRotation(startRotation, endRotation);
            final Rect endBounds = new Rect(destinationBounds);

            // Set the end frame since the display won't rotate until fixed rotation is finished
            // in the next display change transition.
            rotateBounds(endBounds, destinationBounds, rotationDelta);
            final int degree, x, y;
            if (rotationDelta == ROTATION_90) {
                degree = 90;
                x = destinationBounds.right;
                y = destinationBounds.top;
            } else {
                degree = -90;
                x = destinationBounds.left;
                y = destinationBounds.bottom;
            }
            mSurfaceTransactionHelper.rotateAndScaleWithCrop(finishTransaction,
                    pipLeash, endBounds, endBounds, new Rect(), degree, x, y,
                    true /* isExpanding */, rotationDelta == ROTATION_270 /* clockwise */);
        } else {
            rotationDelta = Surface.ROTATION_0;
        }
        startExpandAnimation(taskInfo, pipLeash, currentBounds, currentBounds, destinationBounds,
                rotationDelta, startTransaction);
    }

    private void startExpandAndRotationAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionInfo.Change displayRotationChange,
            @NonNull TaskInfo taskInfo, @NonNull TransitionInfo.Change pipChange,
            @NonNull Point offset) {
        final int rotateDelta = deltaRotation(displayRotationChange.getStartRotation(),
                displayRotationChange.getEndRotation());

        // Counter-rotate all "going-away" things since they are still in the old orientation.
        final CounterRotatorHelper rotator = new CounterRotatorHelper();
        rotator.handleClosingChanges(info, startTransaction, displayRotationChange);

        // Get the start bounds in new orientation.
        final Rect startBounds = new Rect(pipChange.getStartAbsBounds());
        rotateBounds(startBounds, displayRotationChange.getStartAbsBounds(), rotateDelta);
        final Rect endBounds = new Rect(pipChange.getEndAbsBounds());
        startBounds.offset(-offset.x, -offset.y);
        endBounds.offset(-offset.x, -offset.y);

        // Reverse the rotation direction for expansion.
        final int pipRotateDelta = deltaRotation(rotateDelta, 0);

        // Set the start frame.
        final int degree, x, y;
        if (pipRotateDelta == ROTATION_90) {
            degree = 90;
            x = startBounds.right;
            y = startBounds.top;
        } else {
            degree = -90;
            x = startBounds.left;
            y = startBounds.bottom;
        }
        mSurfaceTransactionHelper.rotateAndScaleWithCrop(startTransaction, pipChange.getLeash(),
                endBounds, startBounds, new Rect(), degree, x, y, true /* isExpanding */,
                pipRotateDelta == ROTATION_270 /* clockwise */);
        startTransaction.apply();
        rotator.cleanUp(finishTransaction);

        // Expand and rotate the pip window to fullscreen.
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getAnimator(taskInfo, pipChange.getLeash(),
                        startBounds, startBounds, endBounds, null, TRANSITION_DIRECTION_LEAVE_PIP,
                        0 /* startingAngle */, pipRotateDelta);
        animator.setTransitionDirection(TRANSITION_DIRECTION_LEAVE_PIP)
                .setPipAnimationCallback(mPipAnimationCallback)
                .setDuration(mEnterExitAnimationDuration)
                .start();
    }

    private void startExpandAnimation(final TaskInfo taskInfo, final SurfaceControl leash,
            final Rect baseBounds, final Rect startBounds, final Rect endBounds,
            final int rotationDelta, @Nullable SurfaceControl.Transaction startTransaction) {
        final Rect sourceHintRect = PipBoundsAlgorithm.getValidSourceHintRect(
                taskInfo.pictureInPictureParams, endBounds);
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getAnimator(taskInfo, leash, baseBounds, startBounds,
                        endBounds, sourceHintRect, TRANSITION_DIRECTION_LEAVE_PIP,
                        0 /* startingAngle */, rotationDelta);
        animator.setTransitionDirection(TRANSITION_DIRECTION_LEAVE_PIP)
                .setDuration(mEnterExitAnimationDuration);
        if (startTransaction != null) {
            animator.setPipTransactionHandler(mTransactionConsumer).applySurfaceControlTransaction(
                    leash, startTransaction, PipAnimationController.FRACTION_START);
            startTransaction.apply();
        }
        animator.setPipAnimationCallback(mPipAnimationCallback)
                .setPipTransactionHandler(mPipOrganizer.getPipTransactionHandler())
                .start();
    }

    /** For {@link Transitions#TRANSIT_REMOVE_PIP}, we just immediately remove the PIP Task. */
    private void removePipImmediately(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull TaskInfo taskInfo) {
        startTransaction.apply();
        final TransitionInfo.Change pipChange = findCurrentPipTaskChange(info);
        if (pipChange == null) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "removePipImmediately is called without pip change");
        }
        mPipOrganizer.onExitPipFinished(taskInfo);
        finishCallback.onTransitionFinished(null);
    }

    /** Whether we should handle the given {@link TransitionInfo} animation as entering PIP. */
    private boolean isEnteringPip(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (isEnteringPip(change, info.getType())) return true;
        }
        return false;
    }

    /** Whether a particular change is a window that is entering pip. */
    @Override
    public boolean isEnteringPip(@NonNull TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        if (change.getTaskInfo() != null
                && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED
                && !change.getContainer().equals(mCurrentPipTaskToken)) {
            // We support TRANSIT_PIP type (from RootWindowContainer) or TRANSIT_OPEN (from apps
            // that enter PiP instantly on opening, mostly from CTS/Flicker tests).
            // TRANSIT_TO_FRONT, though uncommon with triggering PiP, should semantically also
            // be allowed to animate if the task in question is pinned already - see b/308054074.
            if (transitType == TRANSIT_PIP || transitType == TRANSIT_OPEN
                    || transitType == TRANSIT_TO_FRONT) {
                return true;
            }
            // This can happen if the request to enter PIP happens when we are collecting for
            // another transition, such as TRANSIT_CHANGE (display rotation).
            if (transitType == TRANSIT_CHANGE) {
                return true;
            }

            // Please file a bug to handle the unexpected transition type.
            android.util.Slog.e(TAG, "Found new PIP in transition with mis-matched type="
                    + transitTypeToString(transitType), new Throwable());
        }
        return false;
    }

    @Override
    public void setEnterAnimationType(@PipAnimationController.AnimationType int type) {
        mEnterAnimationType = type;
    }

    @Nullable
    private static TransitionInfo.Change getPipChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
                return change;
            }
        }
        return null;
    }

    private void startEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // Search for an Enter PiP transition
        final TransitionInfo.Change enterPip = getPipChange(info);
        if (enterPip == null) {
            throw new IllegalStateException("Trying to start PiP animation without a pip"
                    + "participant");
        }

        // Make sure other non-pip changes are handled correctly.
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change == enterPip) continue;
            if (TransitionUtil.isOpeningType(change.getMode())) {
                // For other open changes that are visible when entering PIP, some may be hidden in
                // Transitions#setupStartState because the transition type is OPEN (such as
                // auto-enter).
                final SurfaceControl leash = change.getLeash();
                startTransaction.show(leash).setAlpha(leash, 1.f);
            } else if (TransitionUtil.isClosingType(change.getMode())) {
                // For other close changes that are invisible as entering PIP, hide them immediately
                // to avoid showing a freezing surface.
                // Ideally, we should let other handler to handle them (likely RemoteHandler by
                // Launcher).
                final SurfaceControl leash = change.getLeash();
                startTransaction.hide(leash);
            }
        }

        startEnterAnimation(enterPip, startTransaction, finishTransaction, finishCallback);
    }

    @Override
    public void startEnterAnimation(@NonNull final TransitionInfo.Change pipChange,
            @NonNull final SurfaceControl.Transaction startTransaction,
            @NonNull final SurfaceControl.Transaction finishTransaction,
            @NonNull final Transitions.TransitionFinishCallback finishCallback) {
        if (mFinishCallback != null) {
            callFinishCallback(null /* wct */);
            mFinishTransaction = null;
            throw new RuntimeException("Previous callback not called, aborting entering PIP.");
        }

        // Keep track of the PIP task and animation.
        mCurrentPipTaskToken = pipChange.getContainer();
        mHasFadeOut = false;
        mPipTransitionState.setTransitionState(PipTransitionState.ENTERING_PIP);
        mFinishCallback = finishCallback;
        mFinishTransaction = finishTransaction;

        final ActivityManager.RunningTaskInfo taskInfo = pipChange.getTaskInfo();
        final SurfaceControl leash = pipChange.getLeash();
        final int startRotation = pipChange.getStartRotation();
        // Check again in case some callers use startEnterAnimation directly so the flag was not
        // set in startAnimation, e.g. from DefaultMixedHandler.
        if (mFixedRotationState != FIXED_ROTATION_TRANSITION) {
            mEndFixedRotation = pipChange.getEndFixedRotation();
            mFixedRotationState = mEndFixedRotation != ROTATION_UNDEFINED
                    ? FIXED_ROTATION_TRANSITION : mFixedRotationState;
        }
        final int endRotation = mFixedRotationState == FIXED_ROTATION_TRANSITION
                ? mEndFixedRotation : pipChange.getEndRotation();

        setBoundsStateForEntry(taskInfo.topActivity, taskInfo.pictureInPictureParams,
                taskInfo.topActivityInfo);

        if (mPipOrganizer.shouldAttachMenuEarly()) {
            mPipMenuController.attach(leash);
        }

        final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        final Rect currentBounds = pipChange.getStartAbsBounds();

        int rotationDelta = deltaRotation(startRotation, endRotation);
        Rect sourceHintRect = mPipOrganizer.takeSwipeSourceRectHint();
        if (sourceHintRect == null) {
            sourceHintRect = PipBoundsAlgorithm.getValidSourceHintRect(
                    taskInfo.pictureInPictureParams, currentBounds, destinationBounds);
        }
        if (rotationDelta != Surface.ROTATION_0
                && endRotation != mPipDisplayLayoutState.getRotation()) {
            // Computes the destination bounds in new rotation.
            computeEnterPipRotatedBounds(rotationDelta, startRotation, endRotation, taskInfo,
                    destinationBounds, sourceHintRect);
        }
        if (!mPipOrganizer.shouldAttachMenuEarly()) {
            mTransitions.getMainExecutor().executeDelayed(
                    () -> mPipMenuController.attach(leash), 0);
        }

        if (taskInfo.pictureInPictureParams != null
                && taskInfo.pictureInPictureParams.isAutoEnterEnabled()
                && mPipTransitionState.getInSwipePipToHomeTransition()) {
            handleSwipePipToHomeTransition(startTransaction, finishTransaction, leash,
                    sourceHintRect, destinationBounds, taskInfo);
            return;
        }

        final int enterAnimationType = mEnterAnimationType;
        if (enterAnimationType == ANIM_TYPE_ALPHA) {
            startTransaction.setAlpha(leash, 0f);
        } else {
            // set alpha to 1, because for multi-activity PiP it will create a new task with alpha 0
            startTransaction.setAlpha(leash, 1f);
        }
        startTransaction.apply();

        int animationDuration = mEnterExitAnimationDuration;
        PipAnimationController.PipTransitionAnimator animator;
        if (enterAnimationType == ANIM_TYPE_BOUNDS) {
            animator = mPipAnimationController.getAnimator(taskInfo, leash, currentBounds,
                    currentBounds, destinationBounds, sourceHintRect, TRANSITION_DIRECTION_TO_PIP,
                    0 /* startingAngle */, rotationDelta);
            if (sourceHintRect == null) {
                // We use content overlay when there is no source rect hint to enter PiP use bounds
                // animation. We also temporarily disallow app icon overlay and use color overlay
                // instead when in fixed rotation enter PiP in button nav with no sourceRectHint.
                // TODO(b/319286295): Fix App Icon Overlay animation in fixed rotation in btn nav.
                // TODO(b/272819817): cleanup the null-check and extra logging.
                final boolean hasTopActivityInfo = taskInfo.topActivityInfo != null;
                if (hasTopActivityInfo && mFixedRotationState != FIXED_ROTATION_TRANSITION) {
                    animator.setAppIconContentOverlay(
                            mContext, currentBounds, destinationBounds, taskInfo.topActivityInfo,
                            mPipBoundsState.getLauncherState().getAppIconSizePx());
                } else {
                    ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "%s: TaskInfo.topActivityInfo is null", TAG);
                    animator.setColorContentOverlay(mContext);
                }
            } else {
                final TaskSnapshot snapshot = PipUtils.getTaskSnapshot(
                        taskInfo.launchIntoPipHostTaskId, false /* isLowResolution */);
                if (snapshot != null) {
                    // use the task snapshot during the animation, this is for
                    // launch-into-pip aka. content-pip use case.
                    animator.setSnapshotContentOverlay(snapshot, sourceHintRect);
                }
            }
        } else if (enterAnimationType == ANIM_TYPE_ALPHA) {
            // In case augmentRequest() is unable to apply the entering bounds (e.g. the request
            // info only contains display change), keep the animation invisible (alpha 0) and
            // duration 0 to apply the destination bounds. The actual fade-in animation will be
            // done in onFinishResize() after the bounds are applied.
            final boolean fadeInAfterOnFinishResize = rotationDelta != Surface.ROTATION_0
                    && mFixedRotationState == FIXED_ROTATION_CALLBACK;
            animator = mPipAnimationController.getAnimator(taskInfo, leash, destinationBounds,
                    0f, fadeInAfterOnFinishResize ? 0f : 1f);
            if (fadeInAfterOnFinishResize) {
                animationDuration = 0;
            }
            mSurfaceTransactionHelper
                    .crop(finishTransaction, leash, destinationBounds)
                    .round(finishTransaction, leash, true /* applyCornerRadius */);
            // Always reset to bounds animation type afterwards.
            setEnterAnimationType(ANIM_TYPE_BOUNDS);
        } else {
            throw new RuntimeException("Unrecognized animation type: " + enterAnimationType);
        }
        mPipOrganizer.setContentOverlay(animator.getContentOverlayLeash(), currentBounds);
        animator.setTransitionDirection(TRANSITION_DIRECTION_TO_PIP)
                .setPipAnimationCallback(mPipAnimationCallback)
                .setDuration(animationDuration);
        if (rotationDelta != Surface.ROTATION_0
                && mFixedRotationState == FIXED_ROTATION_TRANSITION) {
            // For fixed rotation, the animation destination bounds is in old rotation coordinates.
            // Set the destination bounds to new coordinates after the animation is finished.
            // ComputeRotatedBounds has changed the DisplayLayout without affecting the animation.
            animator.setDestinationBounds(mPipBoundsAlgorithm.getEntryDestinationBounds());
        }
        // Keep the last appearance when finishing the transition. The transform will be reset when
        // setting bounds.
        animator.setPipTransactionHandler(mTransactionConsumer).applySurfaceControlTransaction(
                leash, finishTransaction, PipAnimationController.FRACTION_END);
        // Start to animate enter PiP.
        animator.setPipTransactionHandler(mPipOrganizer.getPipTransactionHandler()).start();
    }

    /** Computes destination bounds in old rotation and updates source hint rect if available. */
    private void computeEnterPipRotatedBounds(int rotationDelta, int startRotation, int endRotation,
            TaskInfo taskInfo, Rect outDestinationBounds, @Nullable Rect outSourceHintRect) {
        mPipDisplayLayoutState.rotateTo(endRotation);
        mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());

        final Rect displayBounds = mPipDisplayLayoutState.getDisplayBounds();
        outDestinationBounds.set(mPipBoundsAlgorithm.getEntryDestinationBounds());
        if (mFixedRotationState == FIXED_ROTATION_TRANSITION) {
            // Transform the destination bounds to current display coordinates.
            // With fixed rotation, the bounds of new rotation shows in old rotation.
            rotateBounds(outDestinationBounds, displayBounds, endRotation, startRotation);
        }
        // When entering PiP (from button navigation mode), adjust the source rect hint by
        // display cutout if applicable.
        if (outSourceHintRect != null && taskInfo.displayCutoutInsets != null) {
            if (rotationDelta == Surface.ROTATION_270) {
                outSourceHintRect.offset(taskInfo.displayCutoutInsets.left,
                        taskInfo.displayCutoutInsets.top);
            }
        }
    }

    private void handleSwipePipToHomeTransition(
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull SurfaceControl leash, @Nullable Rect sourceHintRect,
            @NonNull Rect destinationBounds,
            @NonNull ActivityManager.RunningTaskInfo pipTaskInfo) {
        if (mFixedRotationState == FIXED_ROTATION_TRANSITION) {
            // If rotation changes when returning to home, the transition should contain both the
            // entering PiP and the display change (PipController#startSwipePipToHome has updated
            // the display layout to new rotation). So it is not expected to see fixed rotation.
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "%s: SwipePipToHome should not use fixed rotation %d", TAG, mEndFixedRotation);
        }
        final SurfaceControl swipePipToHomeOverlay = mPipOrganizer.mPipOverlay;
        if (swipePipToHomeOverlay != null) {
            // Launcher fade in the overlay on top of the fullscreen Task. It is possible we
            // reparent the PIP activity to a new PIP task (in case there are other activities
            // in the original Task, in other words multi-activity apps), so we should also reparent
            // the overlay to the final PIP task.
            startTransaction.reparent(swipePipToHomeOverlay, leash)
                    .setLayer(swipePipToHomeOverlay, Integer.MAX_VALUE);
        }

        final Rect sourceBounds = pipTaskInfo.configuration.windowConfiguration.getBounds();
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getAnimator(pipTaskInfo, leash, sourceBounds, sourceBounds,
                        destinationBounds, sourceHintRect, TRANSITION_DIRECTION_TO_PIP,
                        0 /* startingAngle */, 0 /* rotationDelta */)
                        .setPipTransactionHandler(mTransactionConsumer)
                        .setTransitionDirection(TRANSITION_DIRECTION_TO_PIP);
        // The start state is the end state for swipe-auto-pip.
        startTransaction.merge(finishTransaction);
        animator.applySurfaceControlTransaction(leash, startTransaction,
                PipAnimationController.FRACTION_END);
        startTransaction.apply();

        mPipBoundsState.setBounds(destinationBounds);
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        onFinishResize(pipTaskInfo, destinationBounds, TRANSITION_DIRECTION_TO_PIP, tx);
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);
        if (swipePipToHomeOverlay != null) {
            mPipOrganizer.fadeOutAndRemoveOverlay(swipePipToHomeOverlay,
                    null /* callback */, false /* withStartDelay */);
        }
        mPipTransitionState.setInSwipePipToHomeTransition(false);
    }

    private void startExitToSplitAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull TaskInfo taskInfo) {
        for (int i = info.getChanges().size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final int mode = change.getMode();

            if (mode == TRANSIT_CHANGE && change.getParent() != null) {
                // TODO: perform resize/expand animation for reparented child task.
                continue;
            }

            if (TransitionUtil.isOpeningType(mode) && change.getParent() == null) {
                final SurfaceControl leash = change.getLeash();
                final Rect endBounds = change.getEndAbsBounds();
                startTransaction
                        .show(leash)
                        .setAlpha(leash, 1f)
                        .setPosition(leash, endBounds.left, endBounds.top)
                        .setWindowCrop(leash, endBounds.width(), endBounds.height());
            }
        }
        mSplitScreenOptional.get().finishEnterSplitScreen(finishTransaction);
        startTransaction.apply();

        mPipOrganizer.onExitPipFinished(taskInfo);
        finishCallback.onTransitionFinished(null);
    }

    private void resetPrevPip(@NonNull TransitionInfo.Change prevPipTaskChange,
            @NonNull SurfaceControl.Transaction startTransaction) {
        final SurfaceControl leash = prevPipTaskChange.getLeash();
        final Rect bounds = prevPipTaskChange.getEndAbsBounds();
        final Point offset = prevPipTaskChange.getEndRelOffset();
        bounds.offset(-offset.x, -offset.y);

        startTransaction.setWindowCrop(leash, null);
        startTransaction.setMatrix(leash, 1, 0, 0, 1);
        startTransaction.setCornerRadius(leash, 0);
        startTransaction.setPosition(leash, bounds.left, bounds.top);

        if (mHasFadeOut && prevPipTaskChange.getTaskInfo().isVisible()) {
            if (mPipAnimationController.getCurrentAnimator() != null) {
                mPipAnimationController.getCurrentAnimator().cancel();
            }
            startTransaction.setAlpha(leash, 1);
        }

        mHasFadeOut = false;
        mCurrentPipTaskToken = null;

        // clean-up the state in PipTaskOrganizer if the PipTaskOrganizer#onTaskAppeared() hasn't
        // been called yet with its leash reference now pointing to a new SurfaceControl not
        // matching the leash of the pip we are removing.
        if (mPipOrganizer.getSurfaceControl() == leash) {
            mPipOrganizer.onExitPipFinished(prevPipTaskChange.getTaskInfo());
        }
    }

    @Override
    public boolean syncPipSurfaceState(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        final TransitionInfo.Change pipChange = findCurrentPipTaskChange(info);
        if (pipChange == null) return false;
        updatePipForUnhandledTransition(pipChange, startTransaction, finishTransaction);
        return true;
    }

    private void updatePipForUnhandledTransition(@NonNull TransitionInfo.Change pipChange,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        // When the PIP window is visible and being a part of the transition, such as display
        // rotation, we need to update its bounds and rounded corner.
        final SurfaceControl leash = pipChange.getLeash();
        final Rect destBounds = mPipOrganizer.getCurrentOrAnimatingBounds();
        final boolean isInPip = mPipTransitionState.isInPip();
        mSurfaceTransactionHelper
                .crop(startTransaction, leash, destBounds)
                .round(startTransaction, leash, isInPip)
                .shadow(startTransaction, leash, isInPip);
        mSurfaceTransactionHelper
                .crop(finishTransaction, leash, destBounds)
                .round(finishTransaction, leash, isInPip)
                .shadow(finishTransaction, leash, isInPip);
        // Make sure the PiP keeps invisible if it was faded out. If it needs to fade in, that will
        // be handled by onFixedRotationFinished().
        if (isInPip && mHasFadeOut) {
            startTransaction.setAlpha(leash, 0f);
            finishTransaction.setAlpha(leash, 0f);
        }
    }

    /** Hides and shows the existing PIP during fixed rotation transition of other activities. */
    private void fadeExistingPip(boolean show) {
        final SurfaceControl leash = mPipOrganizer.getSurfaceControl();
        final TaskInfo taskInfo = mPipOrganizer.getTaskInfo();
        if (leash == null || !leash.isValid() || taskInfo == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Invalid leash on fadeExistingPip: %s", TAG, leash);
            return;
        }
        final float alphaStart = show ? 0 : 1;
        final float alphaEnd = show ? 1 : 0;
        final PipAnimationController.PipTransactionHandler transactionHandler =
                new PipAnimationController.PipTransactionHandler() {
            @Override
            public boolean handlePipTransaction(SurfaceControl leash,
                    SurfaceControl.Transaction tx, Rect destinationBounds, float alpha) {
                if (alpha == 0) {
                    if (show) {
                        tx.setPosition(leash, destinationBounds.left, destinationBounds.top);
                    } else {
                        // Put PiP out of the display so it won't block touch when it is hidden.
                        final Rect displayBounds = mPipDisplayLayoutState.getDisplayBounds();
                        final int max = Math.max(displayBounds.width(), displayBounds.height());
                        tx.setPosition(leash, max, max);
                    }
                }
                return false;
            }
        };
        mPipAnimationController
                .getAnimator(taskInfo, leash, mPipBoundsState.getBounds(), alphaStart, alphaEnd)
                .setTransitionDirection(TRANSITION_DIRECTION_SAME)
                .setPipTransactionHandler(transactionHandler)
                .setDuration(mEnterExitAnimationDuration)
                .start();
        mHasFadeOut = !show;
    }

    private void finishResizeForMenu(Rect destinationBounds) {
        mPipMenuController.movePipMenu(null, null, destinationBounds,
                PipMenuController.ALPHA_NO_CHANGE);
        mPipMenuController.updateMenuBounds(destinationBounds);
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mCurrentPipTaskToken=" + mCurrentPipTaskToken);
        pw.println(innerPrefix + "mFinishCallback=" + mFinishCallback);
    }
}
