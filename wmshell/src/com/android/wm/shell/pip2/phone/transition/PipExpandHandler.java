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

package com.android.wm.shell.pip2.phone.transition;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Surface.ROTATION_0;

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getFixedRotationDelta;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getLeash;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipParams;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppCompatTaskInfo;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipExpandAnimator;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

public class PipExpandHandler implements Transitions.TransitionHandler {
    private Context mContext;
    private final PipBoundsState mPipBoundsState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipTransitionState mPipTransitionState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipDesktopState mPipDesktopState;
    private final PipInteractionHandler mPipInteractionHandler;
    private final Optional<SplitScreenController> mSplitScreenControllerOptional;

    @Nullable
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Nullable
    private ValueAnimator mTransitionAnimator;

    private PipExpandAnimatorSupplier mPipExpandAnimatorSupplier;
    private final @NonNull PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    public PipExpandHandler(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipBoundsState pipBoundsState,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipTransitionState pipTransitionState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipDesktopState pipDesktopState,
            PipInteractionHandler pipInteractionHandler,
            Optional<SplitScreenController> splitScreenControllerOptional) {
        mContext = context;
        mPipBoundsState = pipBoundsState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipTransitionState = pipTransitionState;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDesktopState = pipDesktopState;
        mPipInteractionHandler = pipInteractionHandler;
        mSplitScreenControllerOptional = splitScreenControllerOptional;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;

        mPipExpandAnimatorSupplier = PipExpandAnimator::new;
    }

    /** Called by [PipTransition#onDisplayIdChanged] when the display id changes. */
    public void onDisplayIdChanged(Context context) {
        mContext = context;
    }

    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // All Exit-via-Expand from PiP transitions are Shell initiated.
        return null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        switch (info.getType()) {
            case TRANSIT_EXIT_PIP:
                return startExpandAnimation(info, startTransaction, finishTransaction,
                        finishCallback);
            case TRANSIT_EXIT_PIP_TO_SPLIT:
                return startExpandToSplitAnimation(info, startTransaction, finishTransaction,
                        finishCallback);
        }
        return false;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        end();
    }

    /**
     * Ends the animation if such is running in the context of expanding out of PiP.
     */
    public void end() {
        if (mTransitionAnimator != null && mTransitionAnimator.isRunning()) {
            mTransitionAnimator.end();
            mTransitionAnimator = null;
        }
    }

    private boolean startExpandAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        WindowContainerToken pipToken = mPipTransitionState.getPipTaskToken();

        TransitionInfo.Change pipChange = getChangeByToken(info, pipToken);
        if (pipChange == null) {
            // pipChange is null, check to see if we've reparented the PIP activity for
            // the multi activity case. If so we should use the activity leash instead
            for (TransitionInfo.Change change : info.getChanges()) {
                if (change.getTaskInfo() == null
                        && change.getLastParent() != null
                        && change.getLastParent().equals(pipToken)) {
                    pipChange = change;
                    break;
                }
            }

            // failsafe
            if (pipChange == null) {
                return false;
            }
        }
        mFinishCallback = finishCallback;

        // The parent change if we were in a multi-activity PiP; null if single activity PiP.
        final TransitionInfo.Change parentBeforePip = pipChange.getTaskInfo() == null
                ? getChangeByToken(info, pipChange.getParent()) : null;
        if (parentBeforePip != null) {
            // For multi activity, we need to manually set the leash layer
            startTransaction.setLayer(parentBeforePip.getLeash(), Integer.MAX_VALUE - 1);
        }

        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect endBounds = pipChange.getEndAbsBounds();
        // Resolve the AppCompat info for multi-activity case
        if (parentBeforePip != null
                && parentBeforePip.getTaskInfo() != null) {
            final AppCompatTaskInfo appCompatTaskInfo =
                    parentBeforePip.getTaskInfo().appCompatTaskInfo;
            if (appCompatTaskInfo.topActivityLetterboxBounds != null) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Offset endBounds from %s to %s due to letterbox on expand",
                        endBounds, appCompatTaskInfo.topActivityLetterboxBounds);
                endBounds.set(appCompatTaskInfo.topActivityLetterboxBounds);
            }
        }
        final SurfaceControl pipLeash = getLeash(pipChange);

        PictureInPictureParams params = null;
        if (pipChange.getTaskInfo() != null) {
            // single activity
            params = getPipParams(pipChange);
        } else if (parentBeforePip != null && parentBeforePip.getTaskInfo() != null) {
            // multi activity
            params = getPipParams(parentBeforePip);
        }
        final Rect sourceRectHint = PipBoundsAlgorithm.getValidSourceHintRect(params, endBounds,
                startBounds);

        // We define delta = startRotation - endRotation, so we need to flip the sign.
        final int delta = -getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            // Update PiP target change in place to prepare for fixed rotation;
            handleExpandFixedRotation(pipChange, delta);
        }

        PipExpandAnimator animator = mPipExpandAnimatorSupplier.get(mContext,
                mSurfaceTransactionHelper, pipLeash,
                startTransaction, finishTransaction, endBounds, startBounds, endBounds,
                sourceRectHint, delta, mPipDesktopState.isPipInDesktopMode());
        animator.setAnimationStartCallback(() -> {
            mPipInteractionHandler.begin(pipLeash, PipInteractionHandler.INTERACTION_EXIT_PIP);

            if (parentBeforePip != null) {
                setupMultiActivityExpandAnimation(info, startTransaction, pipLeash,
                        parentBeforePip);
            }
        });

        final TransitionInfo.Change finalPipChange = pipChange;
        animator.setAnimationEndCallback(() -> {
            if (parentBeforePip != null) {
                // TODO b/377362511: Animate local leash instead to also handle letterbox case.
                // For multi-activity, set the crop to be null
                finishTransaction.setCrop(pipLeash, null);
                setupMultiActivityAnimationFinalState(finishTransaction, finalPipChange, pipLeash,
                        parentBeforePip);
            }
            finishTransition();
            mPipInteractionHandler.end();
        });
        cacheAndStartTransitionAnimator(animator);
        saveReentryState();
        return true;
    }

    private void setupMultiActivityExpandAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction, @NonNull SurfaceControl pipLeash,
            @NonNull TransitionInfo.Change parentBeforePip) {
        if (!mPipDesktopState.isDesktopWindowingPipEnabled()) {
            return;
        }

        final int rootIndex = info.findRootIndex(mPipDisplayLayoutState.getDisplayId());
        final int parentWindowingMode = parentBeforePip.getTaskInfo().getWindowingMode();
        if (rootIndex != -1 && parentWindowingMode == WINDOWING_MODE_FREEFORM) {
            // Reparent PiP activity to the root leash if it's animating to freeform so that it is
            // not cropped by the parent task.
            SurfaceControl rootLeash = info.getRoot(rootIndex).getLeash();
            startTransaction.reparent(pipLeash, rootLeash);
            startTransaction.setAlpha(parentBeforePip.getLeash(), 0);
        } else if (parentWindowingMode == WINDOWING_MODE_FULLSCREEN) {
            // Don't animate the parent task; show it immediately when the PiP animation finishes
            parentBeforePip.setStartAbsBounds(parentBeforePip.getEndAbsBounds());
            startTransaction.setPosition(parentBeforePip.getLeash(),
                    parentBeforePip.getStartAbsBounds().left,
                    parentBeforePip.getStartAbsBounds().top);
            startTransaction.setCrop(parentBeforePip.getLeash(), parentBeforePip.getEndAbsBounds());
        }
    }

    private void setupMultiActivityAnimationFinalState(
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionInfo.Change pipChange, @NonNull SurfaceControl pipLeash,
            @NonNull TransitionInfo.Change parentBeforePip) {
        if (!mPipDesktopState.isDesktopWindowingPipEnabled()
                || parentBeforePip.getTaskInfo().getWindowingMode() != WINDOWING_MODE_FREEFORM) {
            return;
        }

        // Reparent the PiP activity to the parent task and reset its position
        finishTransaction.reparent(pipLeash, parentBeforePip.getLeash());
        finishTransaction.setPosition(pipLeash, pipChange.getEndRelOffset().x,
                pipChange.getEndRelOffset().y);
        finishTransaction.setAlpha(parentBeforePip.getLeash(), 1);
    }

    private boolean startExpandToSplitAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        WindowContainerToken pipToken = mPipTransitionState.getPipTaskToken();

        // Expanding PiP to Split-screen makes sense only if we are dealing with multi-activity PiP
        // and the lastParentBeforePip is still in one of the split-stages.
        //
        // This means we should be animating the PiP activity leash, since we do the reparenting
        // of the PiP activity back to its original task in startWCT.
        TransitionInfo.Change pipChange = null;
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() == null
                    && change.getLastParent() != null
                    && change.getLastParent().equals(pipToken)) {
                pipChange = change;
                break;
            }
        }
        // failsafe
        if (pipChange == null || pipChange.getLeash() == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        // Get the original parent before PiP. If original task hosting the PiP activity was
        // already visible, then it's not participating in this transition; in that case,
        // parentBeforePip would be null.
        final TransitionInfo.Change parentBeforePip = getChangeByToken(info, pipChange.getParent());

        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect endBounds = pipChange.getEndAbsBounds();
        if (parentBeforePip != null) {
            // Since we have the parent task amongst the targets, all PiP activity
            // leash translations will be relative to the original task, NOT the root leash.
            startBounds.offset(-parentBeforePip.getStartAbsBounds().left,
                    -parentBeforePip.getStartAbsBounds().top);
            endBounds.offset(-parentBeforePip.getEndAbsBounds().left,
                    -parentBeforePip.getEndAbsBounds().top);
        }

        final SurfaceControl pipLeash = pipChange.getLeash();
        PipExpandAnimator animator = mPipExpandAnimatorSupplier.get(mContext,
                mSurfaceTransactionHelper, pipLeash,
                startTransaction, finishTransaction, endBounds, startBounds, endBounds,
                null /* srcRectHint */, ROTATION_0 /* delta */,
                mPipDesktopState.isPipInDesktopMode());


        mSplitScreenControllerOptional.ifPresent(splitController -> {
            splitController.finishEnterSplitScreen(finishTransaction);
        });

        animator.setAnimationStartCallback(() -> mPipInteractionHandler.begin(pipLeash,
                PipInteractionHandler.INTERACTION_EXIT_PIP_TO_SPLIT));
        animator.setAnimationEndCallback(() -> {
            if (parentBeforePip == null) {
                // After PipExpandAnimator is done modifying finishTransaction, we need to make
                // sure PiP activity leash is offset at origin relative to its task as we reparent
                // targets back from the transition root leash.
                finishTransaction.setPosition(pipLeash, 0, 0);
            }
            finishTransition();
            mPipInteractionHandler.end();
        });
        cacheAndStartTransitionAnimator(animator);
        saveReentryState();
        return true;
    }

    private void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        if (currentState != PipTransitionState.EXITING_PIP) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "Unexpected state %s as we are finishing an exit-via-expand transition",
                    mPipTransitionState);
        }
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);

        if (mFinishCallback != null) {
            // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
            // handler if there is a pending PiP animation.
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }

    private void handleExpandFixedRotation(TransitionInfo.Change outPipTaskChange, int delta) {
        final Rect endBounds = outPipTaskChange.getEndAbsBounds();
        final int width = endBounds.width();
        final int height = endBounds.height();
        final int left = endBounds.left;
        final int top = endBounds.top;
        int newTop, newLeft;

        if (delta == Surface.ROTATION_90) {
            newLeft = top;
            newTop = -(left + width);
        } else {
            newLeft = -(height + top);
            newTop = left;
        }
        // Modify the endBounds, rotating and placing them potentially off-screen, so that
        // as we translate and rotate around the origin, we place them right into the target.
        endBounds.set(newLeft, newTop, newLeft + height, newTop + width);
    }

    private void saveReentryState() {
        float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                mPipBoundsState.getBounds());
        mPipBoundsState.saveReentryState(snapFraction);
    }

    private void cacheAndStartTransitionAnimator(@NonNull ValueAnimator animator) {
        mTransitionAnimator = animator;
        mTransitionAnimator.start();
    }

    @VisibleForTesting
    interface PipExpandAnimatorSupplier {
        PipExpandAnimator get(Context context,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                @NonNull SurfaceControl leash,
                SurfaceControl.Transaction startTransaction,
                SurfaceControl.Transaction finishTransaction,
                @NonNull Rect baseBounds,
                @NonNull Rect startBounds,
                @NonNull Rect endBounds,
                @Nullable Rect sourceRectHint,
                @Surface.Rotation int rotation,
                Boolean isPipInDesktopMode);
    }

    @VisibleForTesting
    void setPipExpandAnimatorSupplier(@NonNull PipExpandAnimatorSupplier supplier) {
        mPipExpandAnimatorSupplier = supplier;
    }
}
