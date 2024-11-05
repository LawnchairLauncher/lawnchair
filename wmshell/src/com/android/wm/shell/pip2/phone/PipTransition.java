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

package com.android.wm.shell.pip2.phone;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_RESIZE_PIP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.util.Preconditions;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipContentOverlay;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

/**
 * Implementation of transitions for PiP on phone.
 */
public class PipTransition extends PipTransitionController implements
        PipTransitionState.PipTransitionStateChangedListener {
    private static final String TAG = PipTransition.class.getSimpleName();

    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_TOKEN = "pip_task_token";
    private static final String PIP_TASK_LEASH = "pip_task_leash";

    // Used for PiP CHANGING_BOUNDS state update.
    static final String PIP_START_TX = "pip_start_tx";
    static final String PIP_FINISH_TX = "pip_finish_tx";
    static final String PIP_DESTINATION_BOUNDS = "pip_dest_bounds";

    /**
     * The fixed start delay in ms when fading out the content overlay from bounds animation.
     * The fadeout animation is guaranteed to start after the client has drawn under the new config.
     */
    private static final int CONTENT_OVERLAY_FADE_OUT_DELAY_MS = 400;

    //
    // Dependencies
    //

    private final Context mContext;
    private final PipScheduler mPipScheduler;
    private final PipTransitionState mPipTransitionState;

    //
    // Transition tokens
    //

    @Nullable
    private IBinder mEnterTransition;
    @Nullable
    private IBinder mExitViaExpandTransition;
    @Nullable
    private IBinder mResizeTransition;

    //
    // Internal state and relevant cached info
    //

    @Nullable
    private WindowContainerToken mPipTaskToken;
    @Nullable
    private SurfaceControl mPipLeash;
    @Nullable
    private Transitions.TransitionFinishCallback mFinishCallback;

    public PipTransition(
            Context context,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            PipBoundsState pipBoundsState,
            PipMenuController pipMenuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipScheduler pipScheduler,
            PipTransitionState pipTransitionState) {
        super(shellInit, shellTaskOrganizer, transitions, pipBoundsState, pipMenuController,
                pipBoundsAlgorithm);

        mContext = context;
        mPipScheduler = pipScheduler;
        mPipScheduler.setPipTransitionController(this);
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
    }

    @Override
    protected void onInit() {
        if (PipUtils.isPip2ExperimentEnabled()) {
            mTransitions.addHandler(this);
        }
    }

    //
    // Transition collection stage lifecycle hooks
    //

    @Override
    public void startExitTransition(int type, WindowContainerTransaction out,
            @Nullable Rect destinationBounds) {
        if (out == null) {
            return;
        }
        IBinder transition = mTransitions.startTransition(type, out, this);
        if (type == TRANSIT_EXIT_PIP) {
            mExitViaExpandTransition = transition;
        }
    }

    @Override
    public void startResizeTransition(WindowContainerTransaction wct) {
        if (wct == null) {
            return;
        }
        mResizeTransition = mTransitions.startTransition(TRANSIT_RESIZE_PIP, wct, this);
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            mEnterTransition = transition;
            return getEnterPipTransaction(transition, request);
        }
        return null;
    }

    @Override
    public void augmentRequest(@NonNull IBinder transition, @NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWct) {
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            outWct.merge(getEnterPipTransaction(transition, request), true /* transfer */);
            mEnterTransition = transition;
        }
    }

    //
    // Transition playing stage lifecycle hooks
    //

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {}

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {}

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (transition == mEnterTransition || info.getType() == TRANSIT_PIP) {
            mEnterTransition = null;
            // If we are in swipe PiP to Home transition we are ENTERING_PIP as a jumpcut transition
            // is being carried out.
            TransitionInfo.Change pipChange = getPipChange(info);

            // If there is no PiP change, exit this transition handler and potentially try others.
            if (pipChange == null) return false;

            Bundle extra = new Bundle();
            extra.putParcelable(PIP_TASK_TOKEN, pipChange.getContainer());
            extra.putParcelable(PIP_TASK_LEASH, pipChange.getLeash());
            mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);

            if (mPipTransitionState.isInSwipePipToHomeTransition()) {
                // If this is the second transition as a part of swipe PiP to home cuj,
                // handle this transition as a special case with no-op animation.
                return handleSwipePipToHomeTransition(info, startTransaction, finishTransaction,
                        finishCallback);
            }
            if (isLegacyEnter(info)) {
                // If this is a legacy-enter-pip (auto-enter is off and PiP activity went to pause),
                // then we should run an ALPHA type (cross-fade) animation.
                return startAlphaTypeEnterAnimation(info, startTransaction, finishTransaction,
                        finishCallback);
            }
            return startBoundsTypeEnterAnimation(info, startTransaction, finishTransaction,
                    finishCallback);
        } else if (transition == mExitViaExpandTransition) {
            mExitViaExpandTransition = null;
            mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
            return startExpandAnimation(info, startTransaction, finishTransaction, finishCallback);
        } else if (transition == mResizeTransition) {
            mResizeTransition = null;
            return startResizeAnimation(info, startTransaction, finishTransaction, finishCallback);
        }

        if (isRemovePipTransition(info)) {
            return removePipImmediately(info, startTransaction, finishTransaction, finishCallback);
        }
        return false;
    }

    //
    // Animation schedulers and entry points
    //

    private boolean startResizeAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        SurfaceControl pipLeash = pipChange.getLeash();

        // Even though the final bounds and crop are applied with finishTransaction since
        // this is a visible change, we still need to handle the app draw coming in. Snapshot
        // covering app draw during collection will be removed by startTransaction. So we make
        // the crop equal to the final bounds and then let the current
        // animator scale the leash back to starting bounds.
        // Note: animator is responsible for applying the startTx but NOT finishTx.
        startTransaction.setWindowCrop(pipLeash, pipChange.getEndAbsBounds().width(),
                pipChange.getEndAbsBounds().height());

        // TODO: b/275910498 Couple this routine with a new implementation of the PiP animator.
        // Classes interested in continuing the animation would subscribe to this state update
        // getting info such as endBounds, startTx, and finishTx as an extra Bundle once
        // animators are in place. Once done state needs to be updated to CHANGED_PIP_BOUNDS.
        Bundle extra = new Bundle();
        extra.putParcelable(PIP_START_TX, startTransaction);
        extra.putParcelable(PIP_FINISH_TX, finishTransaction);
        extra.putParcelable(PIP_DESTINATION_BOUNDS, pipChange.getEndAbsBounds());

        mFinishCallback = finishCallback;
        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);
        return true;
    }

    private boolean handleSwipePipToHomeTransition(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        WindowContainerToken pipTaskToken = pipChange.getContainer();
        SurfaceControl pipLeash = pipChange.getLeash();

        if (pipTaskToken == null || pipLeash == null) {
            return false;
        }

        SurfaceControl overlayLeash = mPipTransitionState.getSwipePipToHomeOverlay();
        PictureInPictureParams params = pipChange.getTaskInfo().pictureInPictureParams;

        Rect appBounds = mPipTransitionState.getSwipePipToHomeAppBounds();
        Rect destinationBounds = pipChange.getEndAbsBounds();

        float aspectRatio = pipChange.getTaskInfo().pictureInPictureParams.getAspectRatioFloat();

        // We fake the source rect hint when the one prvided by the app is invalid for
        // the animation with an app icon overlay.
        Rect animationSrcRectHint = overlayLeash == null ? params.getSourceRectHint()
                : PipUtils.getEnterPipWithOverlaySrcRectHint(appBounds, aspectRatio);

        WindowContainerTransaction finishWct = new WindowContainerTransaction();
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();

        final float scale = (float) destinationBounds.width() / animationSrcRectHint.width();
        startTransaction.setWindowCrop(pipLeash, animationSrcRectHint);
        startTransaction.setPosition(pipLeash,
                destinationBounds.left - animationSrcRectHint.left * scale,
                destinationBounds.top - animationSrcRectHint.top * scale);
        startTransaction.setScale(pipLeash, scale, scale);

        if (overlayLeash != null) {
            final int overlaySize = PipContentOverlay.PipAppIconOverlay.getOverlaySize(
                    mPipTransitionState.getSwipePipToHomeAppBounds(), destinationBounds);

            // Overlay needs to be adjusted once a new draw comes in resetting surface transform.
            tx.setScale(overlayLeash, 1f, 1f);
            tx.setPosition(overlayLeash, (destinationBounds.width() - overlaySize) / 2f,
                    (destinationBounds.height() - overlaySize) / 2f);
        }
        startTransaction.apply();

        tx.addTransactionCommittedListener(mPipScheduler.getMainExecutor(),
                        this::onClientDrawAtTransitionEnd);
        finishWct.setBoundsChangeTransaction(pipTaskToken, tx);

        // Note that finishWct should be free of any actual WM state changes; we are using
        // it for syncing with the client draw after delayed configuration changes are dispatched.
        finishCallback.onTransitionFinished(finishWct.isEmpty() ? null : finishWct);
        return true;
    }

    private void startOverlayFadeoutAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
        animator.setDuration(CONTENT_OVERLAY_FADE_OUT_DELAY_MS);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                tx.remove(mPipTransitionState.getSwipePipToHomeOverlay());
                tx.apply();

                // We have fully completed enter-PiP animation after the overlay is gone.
                mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
            }
        });
        animator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            tx.setAlpha(mPipTransitionState.getSwipePipToHomeOverlay(), alpha).apply();
        });
        animator.start();
    }

    private boolean startBoundsTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        // cache the PiP task token and leash
        WindowContainerToken pipTaskToken = pipChange.getContainer();

        startTransaction.apply();
        // TODO: b/275910498 Use a new implementation of the PiP animator here.
        finishCallback.onTransitionFinished(null);
        return true;
    }

    private boolean startAlphaTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        Rect destinationBounds = pipChange.getEndAbsBounds();
        SurfaceControl pipLeash = mPipTransitionState.mPinnedTaskLeash;
        Preconditions.checkNotNull(pipLeash, "Leash is null for alpha transition.");

        // Start transition with 0 alpha at the entry bounds.
        startTransaction.setPosition(pipLeash, destinationBounds.left, destinationBounds.top)
                .setWindowCrop(pipLeash, destinationBounds.width(), destinationBounds.height())
                .setAlpha(pipLeash, 0f);

        PipAlphaAnimator animator = new PipAlphaAnimator(mContext, pipLeash, startTransaction,
                PipAlphaAnimator.FADE_IN);
        animator.setAnimationEndCallback(() -> {
            finishCallback.onTransitionFinished(null);
            // This should update the pip transition state accordingly after we stop playing.
            onClientDrawAtTransitionEnd();
        });

        animator.start();
        return true;
    }

    private boolean startExpandAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        startTransaction.apply();
        // TODO: b/275910498 Use a new implementation of the PiP animator here.
        finishCallback.onTransitionFinished(null);
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);
        return true;
    }

    private boolean removePipImmediately(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        startTransaction.apply();
        finishCallback.onTransitionFinished(null);
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);
        return true;
    }

    //
    // Various helpers to resolve transition requests and infos
    //

    @Nullable
    private TransitionInfo.Change getPipChange(TransitionInfo info) {
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
                return change;
            }
        }
        return null;
    }

    private WindowContainerTransaction getEnterPipTransaction(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // cache the original task token to check for multi-activity case later
        final ActivityManager.RunningTaskInfo pipTask = request.getPipTask();
        PictureInPictureParams pipParams = pipTask.pictureInPictureParams;
        mPipBoundsState.setBoundsStateForEntry(pipTask.topActivity, pipTask.topActivityInfo,
                pipParams, mPipBoundsAlgorithm);

        // calculate the entry bounds and notify core to move task to pinned with final bounds
        final Rect entryBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        mPipBoundsState.setBounds(entryBounds);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.movePipActivityToPinnedRootTask(pipTask.token, entryBounds);
        wct.deferConfigToTransitionEnd(pipTask.token);
        return wct;
    }

    private boolean isAutoEnterInButtonNavigation(@NonNull TransitionRequestInfo requestInfo) {
        final ActivityManager.RunningTaskInfo pipTask = requestInfo.getPipTask();
        if (pipTask == null) {
            return false;
        }
        if (pipTask.pictureInPictureParams == null) {
            return false;
        }

        // Assuming auto-enter is enabled and pipTask is non-null, the TRANSIT_OPEN request type
        // implies that we are entering PiP in button navigation mode. This is guaranteed by
        // TaskFragment#startPausing()` in Core which wouldn't get called in gesture nav.
        return requestInfo.getType() == TRANSIT_OPEN
                && pipTask.pictureInPictureParams.isAutoEnterEnabled();
    }

    private boolean isEnterPictureInPictureModeRequest(@NonNull TransitionRequestInfo requestInfo) {
        return requestInfo.getType() == TRANSIT_PIP;
    }

    private boolean isLegacyEnter(@NonNull TransitionInfo info) {
        TransitionInfo.Change pipChange = getPipChange(info);
        // If the only change in the changes list is a opening type PiP task,
        // then this is legacy-enter PiP.
        return pipChange != null && info.getChanges().size() == 1
                && (pipChange.getMode() == TRANSIT_TO_FRONT || pipChange.getMode() == TRANSIT_OPEN);
    }

    private boolean isRemovePipTransition(@NonNull TransitionInfo info) {
        if (mPipTransitionState.mPipTaskToken == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.mPipTaskToken);
        if (pipChange == null) {
            // Search for the PiP change by token since the windowing mode might be FULLSCREEN now.
            return false;
        }

        boolean isPipMovedToBack = info.getType() == TRANSIT_TO_BACK
                && pipChange.getMode() == TRANSIT_TO_BACK;
        boolean isPipClosed = info.getType() == TRANSIT_CLOSE
                && pipChange.getMode() == TRANSIT_CLOSE;
        // PiP is being removed if the pinned task is either moved to back or closed.
        return isPipMovedToBack || isPipClosed;
    }

    //
    // Miscellaneous callbacks and listeners
    //

    private void onClientDrawAtTransitionEnd() {
        if (mPipTransitionState.getSwipePipToHomeOverlay() != null) {
            startOverlayFadeoutAnimation();
        } else if (mPipTransitionState.getState() == PipTransitionState.ENTERING_PIP) {
            // If we were entering PiP (i.e. playing the animation) with a valid srcRectHint,
            // and then we get a signal on client finishing its draw after the transition
            // has ended, then we have fully entered PiP.
            mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        }
    }

    @Override
    public void finishTransition(@Nullable SurfaceControl.Transaction tx) {
        WindowContainerTransaction wct = null;
        if (tx != null && mPipTransitionState.mPipTaskToken != null) {
            // Outside callers can only provide a transaction to be applied with the final draw.
            // So no actual WM changes can be applied for this transition after this point.
            wct = new WindowContainerTransaction();
            wct.setBoundsChangeTransaction(mPipTransitionState.mPipTaskToken, tx);
        }
        if (mFinishCallback != null) {
            mFinishCallback.onTransitionFinished(wct);
        }
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.ENTERING_PIP:
                Preconditions.checkState(extra != null,
                        "No extra bundle for " + mPipTransitionState);

                mPipTransitionState.mPipTaskToken = extra.getParcelable(
                        PIP_TASK_TOKEN, WindowContainerToken.class);
                mPipTransitionState.mPinnedTaskLeash = extra.getParcelable(
                        PIP_TASK_LEASH, SurfaceControl.class);
                boolean hasValidTokenAndLeash = mPipTransitionState.mPipTaskToken != null
                        && mPipTransitionState.mPinnedTaskLeash != null;

                Preconditions.checkState(hasValidTokenAndLeash,
                        "Unexpected bundle for " + mPipTransitionState);
                break;
            case PipTransitionState.EXITED_PIP:
                mPipTransitionState.mPipTaskToken = null;
                mPipTransitionState.mPinnedTaskLeash = null;
                break;
        }
    }
}
