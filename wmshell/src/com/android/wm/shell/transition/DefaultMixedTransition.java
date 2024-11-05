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

import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.transition.DefaultMixedHandler.subCopy;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateEnterPipFromSplit;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateKeyguard;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;

class DefaultMixedTransition extends DefaultMixedHandler.MixedTransition {
    private final UnfoldTransitionHandler mUnfoldHandler;
    private final ActivityEmbeddingController mActivityEmbeddingController;

    DefaultMixedTransition(int type, IBinder transition, Transitions player,
            MixedTransitionHandler mixedHandler, PipTransitionController pipHandler,
            StageCoordinator splitHandler, KeyguardTransitionHandler keyguardHandler,
            UnfoldTransitionHandler unfoldHandler,
            ActivityEmbeddingController activityEmbeddingController) {
        super(type, transition, player, mixedHandler, pipHandler, splitHandler, keyguardHandler);
        mUnfoldHandler = unfoldHandler;
        mActivityEmbeddingController = activityEmbeddingController;

        switch (type) {
            case TYPE_UNFOLD:
                mLeftoversHandler = mUnfoldHandler;
                break;
            case TYPE_DISPLAY_AND_SPLIT_CHANGE:
            case TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING:
            case TYPE_ENTER_PIP_FROM_SPLIT:
            case TYPE_KEYGUARD:
            case TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE:
            default:
                break;
        }
    }

    @Override
    boolean startAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        return switch (mType) {
            case TYPE_DISPLAY_AND_SPLIT_CHANGE, TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE -> false;
            case TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING ->
                    animateEnterPipFromActivityEmbedding(
                            info, startTransaction, finishTransaction, finishCallback);
            case TYPE_ENTER_PIP_FROM_SPLIT ->
                    animateEnterPipFromSplit(this, info, startTransaction, finishTransaction,
                            finishCallback, mPlayer, mMixedHandler, mPipHandler, mSplitHandler,
                            /*replacingPip*/ false);
            case TYPE_ENTER_PIP_REPLACE_FROM_SPLIT ->
                    animateEnterPipFromSplit(this, info, startTransaction, finishTransaction,
                            finishCallback, mPlayer, mMixedHandler, mPipHandler, mSplitHandler,
                            /*replacingPip*/ true);
            case TYPE_KEYGUARD ->
                    animateKeyguard(this, info, startTransaction, finishTransaction, finishCallback,
                            mKeyguardHandler, mPipHandler);
            case TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE ->
                    animateOpenIntentWithRemoteAndPip(transition, info, startTransaction,
                            finishTransaction, finishCallback);
            case TYPE_UNFOLD ->
                    animateUnfold(info, startTransaction, finishTransaction, finishCallback);
            default -> throw new IllegalStateException(
                    "Starting default mixed animation with unknown or illegal type: " + mType);
        };
    }

    private boolean animateEnterPipFromActivityEmbedding(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for entering PIP from"
                + " an Activity Embedding window #%d", info.getDebugId());
        // Split into two transitions (wct)
        TransitionInfo.Change pipChange = null;
        final TransitionInfo everythingElse = subCopy(info, TRANSIT_TO_BACK, true /* changes */);
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (mPipHandler.isEnteringPip(change, info.getType())) {
                if (pipChange != null) {
                    throw new IllegalStateException("More than 1 pip-entering changes in one"
                            + " transition? " + info);
                }
                pipChange = change;
                // going backwards, so remove-by-index is fine.
                everythingElse.getChanges().remove(i);
            }
        }

        final Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mInFlightSubAnimations;
            joinFinishArgs(wct);
            if (mInFlightSubAnimations > 0) return;
            finishCallback.onTransitionFinished(mFinishWCT);
        };

        if (!mActivityEmbeddingController.shouldAnimate(everythingElse)) {
            // Fallback to dispatching to other handlers.
            return false;
        }

        // PIP window should always be on the highest Z order.
        if (pipChange != null) {
            mInFlightSubAnimations = 2;
            mPipHandler.startEnterAnimation(
                    pipChange, startTransaction.setLayer(pipChange.getLeash(), Integer.MAX_VALUE),
                    finishTransaction,
                    finishCB);
        } else {
            mInFlightSubAnimations = 1;
        }

        mActivityEmbeddingController.startAnimation(
                mTransition, everythingElse, startTransaction, finishTransaction, finishCB);
        return true;
    }

    private boolean animateOpenIntentWithRemoteAndPip(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for opening an intent"
                + " with a remote transition and PIP #%d", info.getDebugId());
        boolean handledToPip = tryAnimateOpenIntentWithRemoteAndPip(
                info, startTransaction, finishTransaction, finishCallback);
        // Consume the transition on remote handler if the leftover handler already handle this
        // transition. And if it cannot, the transition will be handled by remote handler, so don't
        // consume here.
        // Need to check leftOverHandler as it may change in #animateOpenIntentWithRemoteAndPip
        if (handledToPip && mHasRequestToRemote
                && mLeftoversHandler != mPlayer.getRemoteTransitionHandler()) {
            mPlayer.getRemoteTransitionHandler().onTransitionConsumed(transition, false, null);
        }
        return handledToPip;
    }

    private boolean tryAnimateOpenIntentWithRemoteAndPip(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = null;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (mPipHandler.isEnteringPip(change, info.getType())) {
                if (pipChange != null) {
                    throw new IllegalStateException("More than 1 pip-entering changes in one"
                            + " transition? " + info);
                }
                pipChange = change;
                info.getChanges().remove(i);
            }
        }
        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mInFlightSubAnimations;
            joinFinishArgs(wct);
            if (mInFlightSubAnimations > 0) return;
            finishCallback.onTransitionFinished(mFinishWCT);
        };
        if (pipChange == null) {
            if (mLeftoversHandler != null) {
                mInFlightSubAnimations = 1;
                if (mLeftoversHandler.startAnimation(
                        mTransition, info, startTransaction, finishTransaction, finishCB)) {
                    return true;
                }
            }
            return false;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Splitting PIP into a separate"
                + " animation because remote-animation likely doesn't support it #%d",
                info.getDebugId());
        // Split the transition into 2 parts: the pip part and the rest.
        mInFlightSubAnimations = 2;
        // make a new startTransaction because pip's startEnterAnimation "consumes" it so
        // we need a separate one to send over to launcher.
        SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();

        mPipHandler.startEnterAnimation(pipChange, otherStartT, finishTransaction, finishCB);

        // Dispatch the rest of the transition normally.
        if (mLeftoversHandler != null
                && mLeftoversHandler.startAnimation(mTransition, info,
                startTransaction, finishTransaction, finishCB)) {
            return true;
        }
        mLeftoversHandler = mPlayer.dispatchTransition(
                mTransition, info, startTransaction, finishTransaction, finishCB, mMixedHandler);
        return true;
    }

    private boolean animateUnfold(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for unfolding #%d",
                info.getDebugId());

        final Transitions.TransitionFinishCallback finishCB = (wct) -> {
            mInFlightSubAnimations--;
            if (mInFlightSubAnimations > 0) return;
            finishCallback.onTransitionFinished(wct);
        };
        mInFlightSubAnimations = 1;
        // Sync pip state.
        if (mPipHandler != null) {
            mPipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
        }
        if (mSplitHandler != null && mSplitHandler.isSplitActive()) {
            mSplitHandler.updateSurfaces(startTransaction);
        }
        return mUnfoldHandler.startAnimation(
                mTransition, info, startTransaction, finishTransaction, finishCB);
    }

    @Override
    void mergeAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        switch (mType) {
            case TYPE_DISPLAY_AND_SPLIT_CHANGE:
            case TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE:
                // queue since no actual animation.
                return;
            case TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING:
                mPipHandler.end();
                mActivityEmbeddingController.mergeAnimation(
                        transition, info, t, mergeTarget, finishCallback);
                return;
            case TYPE_ENTER_PIP_FROM_SPLIT:
                if (mAnimType == ANIM_TYPE_GOING_HOME) {
                    boolean ended = mSplitHandler.end();
                    // If split couldn't end (because it is remote), then don't end everything else
                    // since we have to play out the animation anyways.
                    if (!ended) return;
                    mPipHandler.end();
                    if (mLeftoversHandler != null) {
                        mLeftoversHandler.mergeAnimation(
                                transition, info, t, mergeTarget, finishCallback);
                    }
                } else {
                    mPipHandler.end();
                }
                return;
            case TYPE_KEYGUARD:
                mKeyguardHandler.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
                return;
            case TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE:
                mPipHandler.end();
                if (mLeftoversHandler != null) {
                    mLeftoversHandler.mergeAnimation(
                            transition, info, t, mergeTarget, finishCallback);
                }
                return;
            case TYPE_UNFOLD:
                mUnfoldHandler.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
                return;
            default:
                throw new IllegalStateException("Playing a default mixed transition with unknown or"
                        + " illegal type: " + mType);
        }
    }

    @Override
    void onTransitionConsumed(
            @NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        switch (mType) {
            case TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING:
                mPipHandler.onTransitionConsumed(transition, aborted, finishT);
                mActivityEmbeddingController.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_ENTER_PIP_FROM_SPLIT:
                mPipHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_KEYGUARD:
                mKeyguardHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_OPTIONS_REMOTE_AND_PIP_CHANGE:
                mLeftoversHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_UNFOLD:
                mUnfoldHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            default:
                break;
        }

        if (mHasRequestToRemote) {
            mPlayer.getRemoteTransitionHandler().onTransitionConsumed(transition, aborted, finishT);
        }
    }
}
