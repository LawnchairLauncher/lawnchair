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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.transition.DefaultMixedHandler.subCopy;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateEnterPipFromSplit;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateKeyguard;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.bubbles.BubbleTransitions;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.phone.transition.PipTransitionUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;

import java.util.List;

class DefaultMixedTransition extends DefaultMixedHandler.MixedTransition {
    private final UnfoldTransitionHandler mUnfoldHandler;
    private final ActivityEmbeddingController mActivityEmbeddingController;
    @Nullable
    private final DesktopTasksController mDesktopTasksController;
    private final BubbleTransitions mBubbleTransitions;

    DefaultMixedTransition(int type, IBinder transition, Transitions player,
            MixedTransitionHandler mixedHandler, PipTransitionController pipHandler,
            StageCoordinator splitHandler, KeyguardTransitionHandler keyguardHandler,
            UnfoldTransitionHandler unfoldHandler,
            ActivityEmbeddingController activityEmbeddingController,
            @Nullable DesktopTasksController desktopTasksController,
            BubbleTransitions bubbleTransitions) {
        super(type, transition, player, mixedHandler, pipHandler, splitHandler, keyguardHandler);
        mUnfoldHandler = unfoldHandler;
        mActivityEmbeddingController = activityEmbeddingController;
        mDesktopTasksController = desktopTasksController;
        mBubbleTransitions = bubbleTransitions;

        switch (type) {
            case TYPE_UNFOLD:
                mLeftoversHandler = mUnfoldHandler;
                break;
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
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE ->
                    animateEnterBubbles(transition, info, startTransaction, finishTransaction,
                            finishCallback, mBubbleTransitions);
            case TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE ->
                    animateEnterBubblesFromSplit(this, transition, info, startTransaction,
                            finishTransaction, finishCallback, mSplitHandler, mBubbleTransitions);
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE ->
                    animateEnterBubblesFromBubble(transition, info, startTransaction,
                            finishTransaction, finishCallback, mBubbleTransitions);
            case TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE ->
                    animateEnterBubblesFromPip(this, transition, info, startTransaction,
                            finishTransaction, finishCallback, mPipHandler, mBubbleTransitions);
            case TYPE_KEYGUARD ->
                    animateKeyguard(this, info, startTransaction, finishTransaction, finishCallback,
                            mKeyguardHandler, mPipHandler);
            case TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE ->
                    animateOpenIntentWithRemoteAndPipOrDesktop(transition, info, startTransaction,
                            finishTransaction, finishCallback);
            case TYPE_UNFOLD ->
                    animateUnfold(transition, info, startTransaction, finishTransaction,
                            finishCallback);
            case TYPE_OPEN_IN_DESKTOP ->
                    animateOpenInDesktop(
                            transition, info, startTransaction, finishTransaction, finishCallback);
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

        TransitionInfo.Change pipActivityChange = null;
        if (pipChange != null) {
            pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                    info, pipChange.getContainer());
            everythingElse.getChanges().remove(pipActivityChange);
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

        if (pipChange != null && pipActivityChange == null) {
            // We are operating on a single PiP task for the enter animation here.
            mInFlightSubAnimations = 2;
            // PIP window should always be on the highest Z order.
            mPipHandler.startEnterAnimation(
                    pipChange, startTransaction.setLayer(pipChange.getLeash(), Integer.MAX_VALUE),
                    finishTransaction,
                    finishCB);
        } else if (pipActivityChange != null) {
            // If there is both a PiP task and a PiP config-at-end activity change,
            // put them into a separate TransitionInfo, and send to be animated as TRANSIT_PIP.
            mInFlightSubAnimations = 2;
            TransitionInfo pipInfo = subCopy(info, TRANSIT_PIP, false /* withChanges */);
            pipInfo.getChanges().addAll(List.of(pipChange, pipActivityChange));
            mPipHandler.startAnimation(mTransition, pipInfo,
                    startTransaction.setLayer(pipChange.getLeash(), Integer.MAX_VALUE),
                    finishTransaction, finishCB);
        } else {
            mInFlightSubAnimations = 1;
        }

        mActivityEmbeddingController.startAnimation(
                mTransition, everythingElse, startTransaction, finishTransaction, finishCB);
        return true;
    }

    private boolean animateOpenIntentWithRemoteAndPipOrDesktop(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for opening an intent"
                + " with a remote transition and PIP or Desktop #%d", info.getDebugId());
        boolean handledToPipOrDesktop = tryAnimateOpenIntentWithRemoteAndPipOrDesktop(
                info, startTransaction, finishTransaction, finishCallback);
        // Consume the transition on remote handler if the leftover handler already handle this
        // transition. And if it cannot, the transition will be handled by remote handler, so don't
        // consume here.
        // Need to check leftOverHandler as it may change in
        // #animateOpenIntentWithRemoteAndPipOrDesktop
        if (handledToPipOrDesktop && mHasRequestToRemote
                && mLeftoversHandler != mPlayer.getRemoteTransitionHandler()) {
            mPlayer.getRemoteTransitionHandler().onTransitionConsumed(transition, false, null);
        }
        return handledToPipOrDesktop;
    }

    private boolean tryAnimateOpenIntentWithRemoteAndPipOrDesktop(
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "tryAnimateOpenIntentWithRemoteAndPipOrDesktop");
        TransitionInfo.Change pipChange = null;
        TransitionInfo.Change pipActivityChange = null;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (mPipHandler.isEnteringPip(change, info.getType())) {
                if (pipChange != null) {
                    throw new IllegalStateException("More than 1 pip-entering changes in one"
                            + " transition? " + info);
                }
                pipChange = change;
                info.getChanges().remove(i);
            } else if (change.getTaskInfo() == null && change.getParent() != null
                    && pipChange != null && change.getParent().equals(pipChange.getContainer())) {
                // Cache the PiP activity if it's a target and cached pip task change is its parent;
                // note that we are bottom-to-top, so if such activity has a task
                // that is also a target, then it must have been cached already as pipChange.
                pipActivityChange = change;
            }
        }
        TransitionInfo.Change desktopChange = null;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (mDesktopTasksController != null
                    && mDesktopTasksController.isDesktopChange(mTransition, change)) {
                if (desktopChange != null) {
                    throw new IllegalStateException("More than 1 desktop changes in one"
                            + " transition? " + info);
                }
                desktopChange = change;
                info.getChanges().remove(i);
            }
        }
        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mInFlightSubAnimations;
            joinFinishArgs(wct);
            if (mInFlightSubAnimations > 0) return;
            finishCallback.onTransitionFinished(mFinishWCT);
        };
        if ((pipChange == null && desktopChange == null)
                || (pipChange != null && desktopChange != null)) {
            // Don't split the transition. Let the leftovers handler handle it all.
            // TODO: b/? - split the transition into three pieces when there's both a PIP and a
            //  desktop change are present. For example, during remote intent open over a desktop
            //  with both a PIP capable task and an immersive task.
            if (mLeftoversHandler != null) {
                mInFlightSubAnimations = 1;
                if (mLeftoversHandler.startAnimation(
                        mTransition, info, startTransaction, finishTransaction, finishCB)) {
                    return true;
                }
            }
            return false;
        } else if (pipChange != null && desktopChange == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Splitting PIP into a separate"
                            + " animation because remote-animation likely doesn't support it #%d",
                    info.getDebugId());
            // Split the transition into 2 parts: the pip part and the rest.
            mInFlightSubAnimations = 2;
            // make a new startTransaction because pip's startEnterAnimation "consumes" it so
            // we need a separate one to send over to launcher.
            SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();
            if (pipActivityChange == null) {
                mPipHandler.startEnterAnimation(pipChange, otherStartT, finishTransaction,
                        finishCB);
            } else {
                info.getChanges().remove(pipActivityChange);
                TransitionInfo pipInfo = subCopy(info, TRANSIT_PIP, false /* withChanges */);
                pipInfo.getChanges().addAll(List.of(pipChange, pipActivityChange));
                mPipHandler.startAnimation(mTransition, pipInfo, startTransaction,
                        finishTransaction, finishCB);
            }

            // Dispatch the rest of the transition normally.
            if (mLeftoversHandler != null
                    && mLeftoversHandler.startAnimation(mTransition, info,
                    startTransaction, finishTransaction, finishCB)) {
                return true;
            }
            mLeftoversHandler = mPlayer.dispatchTransition(
                    mTransition, info, startTransaction, finishTransaction, finishCB,
                    mMixedHandler);
            return true;
        } else if (pipChange == null && desktopChange != null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Splitting desktop change into a"
                            + "separate animation because remote-animation likely doesn't support"
                            + "it #%d", info.getDebugId());
            mInFlightSubAnimations = 2;
            SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();

            mDesktopTasksController.animateDesktopChange(
                            mTransition, desktopChange, otherStartT, finishTransaction, finishCB);

            // Dispatch the rest of the transition normally.
            if (mLeftoversHandler != null
                    && mLeftoversHandler.startAnimation(mTransition, info,
                    startTransaction, finishTransaction, finishCB)) {
                return true;
            }
            mLeftoversHandler = mPlayer.dispatchTransition(
                    mTransition, info, startTransaction, finishTransaction, finishCB,
                    mMixedHandler);
            return true;
        } else {
            throw new IllegalStateException(
                    "All PIP and Immersive combinations should've been handled");
        }
    }

    static boolean animateEnterBubbles(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull BubbleTransitions bubbleTransitions) {
        final Transitions.TransitionHandler handler = bubbleTransitions.getRunningEnterTransition(
                transition);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering Bubbles while an app is in the foreground by %s", handler);
        // TODO(b/408328557): Migrate to checking transition token
        handler.startAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback);
        return true;
    }

    static boolean animateEnterBubblesFromSplit(
            @NonNull DefaultMixedHandler.MixedTransition mixed,
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull StageCoordinator splitHandler,
            @NonNull BubbleTransitions bubbleTransitions) {
        final Transitions.TransitionHandler handler = bubbleTransitions.getRunningEnterTransition(
                transition);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering Bubbles while Split-Screen is foreground by %s", handler);

        TransitionInfo.Change bubblingTask = getChangeForBubblingTask(info, bubbleTransitions);
        // find previous split location for other task
        @SplitScreen.StageType int topSplitStageToKeep = SplitScreen.STAGE_TYPE_UNDEFINED;
        for (int i = info.getChanges().size() - 1; i >= 0; i--) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (change == bubblingTask) continue;
            int prevStage = splitHandler.getSplitItemStage(change.getLastParent());
            if (prevStage != SplitScreen.STAGE_TYPE_UNDEFINED) {
                topSplitStageToKeep = prevStage;
                break;
            }
        }
        splitHandler.prepareDismissAnimation(topSplitStageToKeep,
                SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_BUBBLE, info, startTransaction,
                finishTransaction);

        // TODO(b/408328557): Migrate to checking transition token
        handler.startAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback);
        return true;
    }

    static boolean animateEnterBubblesFromPip(
            @NonNull DefaultMixedHandler.MixedTransition mixed,
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull PipTransitionController pipHandler,
            @NonNull BubbleTransitions bubbleTransitions) {
        final Transitions.TransitionHandler handler = bubbleTransitions.getRunningEnterTransition(
                transition);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering Bubbles while PIP is foreground by %s", handler);
        pipHandler.cleanUpState();
        handler.startAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback);
        return true;
    }

    static boolean animateEnterBubblesFromBubble(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull BubbleTransitions bubbleTransitions) {
        // Identify the task being launched into a bubble
        final TransitionInfo.Change change = getChangeForBubblingTask(info, bubbleTransitions);
        if (change == null) {
            // Fallback to remote transition scenarios, ex:
            // 1. Move bubble'd app to fullscreen for launcher icon clicked
            // 2. Launch activity in expanded and selected bubble for notification clicked
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " No bubbling task found");
            return false;
        }

        // Task transition scenarios, ex:
        // 1. Start a new task from a bubbled task
        // 2. Expand the collapsed bubble for notification launch
        // 3. Switch the expanded bubble for notification launch
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering bubble from another bubbled task or for an existing bubble");
        final boolean started = bubbleTransitions.startBubbleToBubbleLaunchOrExistingBubbleConvert(
                transition, change.getTaskInfo(), handler -> {
                    final Transitions.TransitionHandler h = bubbleTransitions
                            .getRunningEnterTransition(transition);
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation played by %s",
                            h);
                    h.startAnimation(
                            transition, info, startTransaction, finishTransaction, finishCallback);
                });
        if (!started) {
            // If nothing started, we are still consuming it since nothing else should handle it
            finishCallback.onTransitionFinished(null);
        }
        return true;
    }

    private static @Nullable TransitionInfo.Change getChangeForBubblingTask(
            @NonNull TransitionInfo info, BubbleTransitions bubbleTransitions) {
        for (int i = 0; i < info.getChanges().size(); i++) {
            final TransitionInfo.Change chg = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = chg.getTaskInfo();
            // Exclude activity transition scenarios.
            if (taskInfo == null || taskInfo.getActivityType() != ACTIVITY_TYPE_STANDARD) {
                continue;
            }
            // Only process opening or change transitions.
            if (!TransitionUtil.isOpeningMode(chg.getMode()) && chg.getMode() != TRANSIT_CHANGE) {
                continue;
            }
            // Skip non-app-bubble tasks (e.g., a reused task in a bubble-to-fullscreen scenario).
            if (!bubbleTransitions.shouldBeAppBubble(taskInfo)) {
                continue;
            }
            return chg;
        }
        return null;
    }

    private boolean animateUnfold(
            @NonNull IBinder transition,
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
            mBubbleTransitions.notifyUnfoldTransitionFinished(transition);
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

    private boolean animateOpenInDesktop(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "animateOpenInDesktop");
        TransitionInfo.Change desktopChange = null;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (mDesktopTasksController.isDesktopChange(mTransition, change)) {
                if (desktopChange != null) {
                    throw new IllegalStateException("More than 1 desktop changes in one"
                            + " transition? " + info);
                }
                desktopChange = change;
                info.getChanges().remove(i);
            }
        }
        final Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mInFlightSubAnimations;
            joinFinishArgs(wct);
            if (mInFlightSubAnimations > 0) return;
            finishCallback.onTransitionFinished(mFinishWCT);
        };
        if (desktopChange == null) {
            if (mLeftoversHandler != null) {
                mInFlightSubAnimations = 1;
                if (mLeftoversHandler.startAnimation(
                        mTransition, info, startTransaction, finishTransaction, finishCB)) {
                    return true;
                }
            }
            return false;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Splitting desktop change into a"
                + "separate animation #%d", info.getDebugId());
        mInFlightSubAnimations = 2;
        mDesktopTasksController.animateDesktopChange(
                transition, desktopChange, startTransaction, finishTransaction, finishCB);
        mLeftoversHandler = mPlayer.dispatchTransition(
                mTransition, info, startTransaction, finishTransaction, finishCB, mMixedHandler);
        return true;
    }

    @Override
    void mergeAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT, @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        switch (mType) {
            case TYPE_DISPLAY_AND_SPLIT_CHANGE:
            case TYPE_ENTER_PIP_WITH_DISPLAY_CHANGE:
                // queue since no actual animation.
                return;
            case TYPE_ENTER_PIP_FROM_ACTIVITY_EMBEDDING:
                mPipHandler.end();
                mActivityEmbeddingController.mergeAnimation(
                        transition, info, startT, finishT, mergeTarget, finishCallback);
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
                                transition, info, startT, finishT, mergeTarget, finishCallback);
                    }
                }
                return;
            case TYPE_KEYGUARD:
                mKeyguardHandler.mergeAnimation(transition, info, startT, finishT, mergeTarget,
                        finishCallback);
                return;
            case TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE:
                mPipHandler.end();
                if (mLeftoversHandler != null) {
                    mLeftoversHandler.mergeAnimation(
                            transition, info, startT, finishT, mergeTarget, finishCallback);
                }
                return;
            case TYPE_UNFOLD:
                mUnfoldHandler.mergeAnimation(transition, info, startT, finishT, mergeTarget,
                        finishCallback);
                return;
            case TYPE_OPEN_IN_DESKTOP:
                mDesktopTasksController.mergeAnimation(
                        transition, info, startT, finishT, mergeTarget, finishCallback);
                return;
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE:
                final Transitions.TransitionHandler handler =
                        mBubbleTransitions.getRunningEnterTransition(transition);
                if (handler != null) {
                    handler.mergeAnimation(transition, info, startT, finishT, mergeTarget,
                            finishCallback);
                }
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
            case TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE:
                mLeftoversHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_UNFOLD:
                mUnfoldHandler.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_OPEN_IN_DESKTOP:
                mDesktopTasksController.onTransitionConsumed(transition, aborted, finishT);
                break;
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_SPLIT_TASK_TO_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE_FROM_EXISTING_BUBBLE:
            case TYPE_LAUNCH_OR_CONVERT_PIP_TASK_TO_BUBBLE:
                final Transitions.TransitionHandler handler =
                        mBubbleTransitions.getRunningEnterTransition(transition);
                if (handler != null) {
                    handler.onTransitionConsumed(transition, aborted, finishT);
                }
                break;
            default:
                break;
        }

        if (mHasRequestToRemote) {
            mPlayer.getRemoteTransitionHandler().onTransitionConsumed(transition, aborted, finishT);
        }
    }
}
