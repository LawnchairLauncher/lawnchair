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

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;

import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

/**
 * Handles Content-PiP entry CUJ, where an app starts a new PiP-ing activity with launch-into-pip
 * activity option.
 *
 * <p>The PiP activity is expected to immediately draw in PiP-mode before the animation starts,
 * which requires a different handling and animation to other enter PiP transitions.
 */
public class ContentPipHandler implements Transitions.TransitionHandler {
    private final Context mContext;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final PipTransitionState mPipTransitionState;

    @Nullable
    private Transitions.TransitionFinishCallback mFinishCallback;
    private PipResizeAnimatorSupplier mPipResizeAnimatorSupplier;

    private final int mEnterAnimationDuration;

    public ContentPipHandler(Context context,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipTransitionState pipTransitionState) {
        mContext = context;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipTransitionState = pipTransitionState;
        mPipResizeAnimatorSupplier = PipResizeAnimator::new;
        mEnterAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipEnterAnimationDuration);
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // Content-PiP enter handleRequest filter is invariant from
        // that of auto-enter in button-navigation mode.
        return null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (isContentPipEnter(info)) {
            return startEnterContentPipAnimation(transition, info, startTransaction,
                    finishTransaction, finishCallback);
        }
        return false;
    }

    private boolean isContentPipEnter(@android.annotation.NonNull TransitionInfo info) {
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null || pipChange.getTaskInfo() == null) {
            return false;
        }
        return PipUtils.isContentPip(pipChange.getTaskInfo())
                && TransitionUtil.isOpeningMode(pipChange.getMode());
    }

    private boolean startEnterContentPipAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null || pipChange.getTaskInfo() == null) return false;
        mFinishCallback = finishCallback;

        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect endBounds = pipChange.getEndAbsBounds();

        final Rect srcRectHint = PipBoundsAlgorithm.getValidSourceHintRect(
                pipChange.getTaskInfo().getPictureInPictureParams(),
                pipChange.getStartAbsBounds(), pipChange.getEndAbsBounds());
        if (srcRectHint != null) {
            startBounds.set(srcRectHint);
            startTransaction.setWindowCrop(pipChange.getLeash(),
                    endBounds.width(), endBounds.height());
            finishTransaction.setWindowCrop(pipChange.getLeash(),
                    endBounds.width(), endBounds.height());
        }

        final PipResizeAnimator animator = mPipResizeAnimatorSupplier.get(mContext,
                mPipSurfaceTransactionHelper, pipChange.getLeash(), startTransaction,
                finishTransaction, endBounds, startBounds, endBounds,
                mEnterAnimationDuration, 0f /* delta */);
        animator.setAnimationEndCallback(this::finishTransition);
        animator.start();
        return true;
    }

    private void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        if (currentState != PipTransitionState.ENTERING_PIP) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "Unexpected state %s as we are finishing an enter content-pip transition",
                    mPipTransitionState);
        }
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);

        if (mFinishCallback != null) {
            // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
            // handler if there is a pending PiP animation.
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }

    @VisibleForTesting
    interface PipResizeAnimatorSupplier {
        PipResizeAnimator get(@NonNull Context context,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                @NonNull SurfaceControl leash,
                @Nullable SurfaceControl.Transaction startTransaction,
                @Nullable SurfaceControl.Transaction finishTransaction,
                @NonNull Rect baseBounds,
                @NonNull Rect startBounds,
                @NonNull Rect endBounds,
                int duration,
                float delta);
    }

    @VisibleForTesting
    void setPipResizeAnimatorSupplier(PipResizeAnimatorSupplier supplier) {
        mPipResizeAnimatorSupplier = supplier;
    }
}
