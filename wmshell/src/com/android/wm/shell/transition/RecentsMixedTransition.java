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

import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_UNOCCLUDING;

import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.transition.DefaultMixedHandler.handoverTransitionLeashes;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateEnterPipFromSplit;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateKeyguard;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerTransaction;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.splitscreen.StageCoordinator;

class RecentsMixedTransition extends DefaultMixedHandler.MixedTransition {
    private final RecentsTransitionHandler mRecentsHandler;
    private final DesktopTasksController mDesktopTasksController;

    RecentsMixedTransition(int type, IBinder transition, Transitions player,
            MixedTransitionHandler mixedHandler, PipTransitionController pipHandler,
            StageCoordinator splitHandler, KeyguardTransitionHandler keyguardHandler,
            RecentsTransitionHandler recentsHandler,
            DesktopTasksController desktopTasksController) {
        super(type, transition, player, mixedHandler, pipHandler, splitHandler, keyguardHandler);
        mRecentsHandler = recentsHandler;
        mDesktopTasksController = desktopTasksController;
        mLeftoversHandler = mRecentsHandler;
    }

    @Override
    boolean startAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        return switch (mType) {
            case TYPE_RECENTS_DURING_DESKTOP ->
                    animateRecentsDuringDesktop(
                            info, startTransaction, finishTransaction, finishCallback);
            case TYPE_RECENTS_DURING_KEYGUARD ->
                    animateRecentsDuringKeyguard(
                            info, startTransaction, finishTransaction, finishCallback);
            case TYPE_RECENTS_DURING_SPLIT ->
                    animateRecentsDuringSplit(
                            info, startTransaction, finishTransaction, finishCallback);
            default -> throw new IllegalStateException(
                    "Starting Recents mixed animation with unknown or illegal type: " + mType);
        };
    }

    private boolean animateRecentsDuringDesktop(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Transition for Recents during"
                + " Desktop #%d", info.getDebugId());

        if (mInfo == null) {
            mInfo = info;
            mFinishT = finishTransaction;
            mFinishCB = finishCallback;
        }
        Transitions.TransitionFinishCallback finishCB = wct -> {
            mInFlightSubAnimations--;
            if (mInFlightSubAnimations == 0) {
                finishCallback.onTransitionFinished(wct);
            }
        };

        mInFlightSubAnimations++;
        boolean consumed = mRecentsHandler.startAnimation(
                mTransition, info, startTransaction, finishTransaction, finishCB);
        if (!consumed) {
            mInFlightSubAnimations--;
            return false;
        }
        if (mDesktopTasksController != null) {
            mDesktopTasksController.syncSurfaceState(info, finishTransaction);
            return true;
        }

        return false;
    }

    private boolean animateRecentsDuringKeyguard(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for Recents during"
                + " Keyguard #%d", info.getDebugId());

        if (mInfo == null) {
            mInfo = info;
            mFinishT = finishTransaction;
            mFinishCB = finishCallback;
        }
        return startSubAnimation(mRecentsHandler, info, startTransaction, finishTransaction);
    }

    private boolean animateRecentsDuringSplit(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for Recents during"
                + " split screen #%d", info.getDebugId());

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            // Pip auto-entering info might be appended to recent transition like pressing
            // home-key in 3-button navigation. This offers split handler the opportunity to
            // handle split to pip animation.
            if (mPipHandler.isEnteringPip(change, info.getType())
                    && mSplitHandler.getSplitItemPosition(change.getLastParent())
                    != SPLIT_POSITION_UNDEFINED) {
                return animateEnterPipFromSplit(this, info, startTransaction, finishTransaction,
                        finishCallback, mPlayer, mMixedHandler, mPipHandler, mSplitHandler,
                        /*replacingPip*/ false);
            }
        }

        // Split-screen is only interested in the recents transition finishing (and merging), so
        // just wrap finish and start recents animation directly.
        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            mInFlightSubAnimations = 0;
            // If pair-to-pair switching, the post-recents clean-up isn't needed.
            wct = wct != null ? wct : new WindowContainerTransaction();
            if (mAnimType != ANIM_TYPE_PAIR_TO_PAIR) {
                mSplitHandler.onRecentsInSplitAnimationFinish(wct, finishTransaction);
            } else {
                // notify pair-to-pair recents animation finish
                mSplitHandler.onRecentsPairToPairAnimationFinish(wct);
            }
            mSplitHandler.onTransitionAnimationComplete();
            finishCallback.onTransitionFinished(wct);
        };
        mInFlightSubAnimations = 1;
        mSplitHandler.onRecentsInSplitAnimationStart(info);
        final boolean handled = mLeftoversHandler.startAnimation(
                mTransition, info, startTransaction, finishTransaction, finishCB);
        if (!handled) {
            mSplitHandler.onRecentsInSplitAnimationCanceled();
        }
        return handled;
    }

    @Override
    void mergeAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        switch (mType) {
            case TYPE_RECENTS_DURING_DESKTOP:
                mLeftoversHandler.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
                return;
            case TYPE_RECENTS_DURING_KEYGUARD:
                if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_UNOCCLUDING) != 0) {
                    handoverTransitionLeashes(mInfo, info, t, mFinishT);
                    if (animateKeyguard(
                            this, info, t, mFinishT, mFinishCB, mKeyguardHandler, mPipHandler)) {
                        finishCallback.onTransitionFinished(null);
                    }
                }
                mLeftoversHandler.mergeAnimation(transition, info, t, mergeTarget,
                        finishCallback);
                return;
            case TYPE_RECENTS_DURING_SPLIT:
                if (mSplitHandler.isPendingEnter(transition)) {
                    // Recents -> enter-split means that we are switching from one pair to
                    // another pair.
                    mAnimType = DefaultMixedHandler.MixedTransition.ANIM_TYPE_PAIR_TO_PAIR;
                }
                mLeftoversHandler.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
                return;
            default:
                throw new IllegalStateException("Playing a Recents mixed transition with unknown or"
                        + " illegal type: " + mType);
        }
    }

    @Override
    void onTransitionConsumed(
            @NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        switch (mType) {
            case TYPE_RECENTS_DURING_DESKTOP:
            case TYPE_RECENTS_DURING_SPLIT:
            case TYPE_RECENTS_DURING_KEYGUARD:
                mLeftoversHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            default:
                break;
        }

        if (mHasRequestToRemote) {
            mPlayer.getRemoteTransitionHandler().onTransitionConsumed(transition, aborted, finishT);
        }
    }
}
