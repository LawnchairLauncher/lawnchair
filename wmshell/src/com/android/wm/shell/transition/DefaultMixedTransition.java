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

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.shared.TransitionUtil.isOpeningType;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_FULLSCREEN_REQUEST;
import static com.android.wm.shell.transition.DefaultMixedHandler.subCopy;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateEnterPipFromSplit;
import static com.android.wm.shell.transition.MixedTransitionHelper.animateKeyguard;

import android.annotation.NonNull;
import android.annotation.Nullable;
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
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;

import java.util.List;
import java.util.function.Consumer;

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
                            finishTransaction, finishCallback, mSplitHandler);
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
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull StageCoordinator splitHandler) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Mixed transition for opening an intent"
                + " with a remote transition and PIP or Desktop #%d", info.getDebugId());
        boolean handledToPipOrDesktop = tryAnimateOpenIntentWithRemoteAndPipOrDesktop(
                info, startTransaction, finishTransaction, finishCallback, splitHandler);
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
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull StageCoordinator splitHandler) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "tryAnimateOpenIntentWithRemoteAndPipOrDesktop");

        // This is specifically for handling trampolines w/ previously split tasks. In the case that
        // an activity (which will trampoline into a previously split task) is launched into
        // fullscreen, the StageCoordinator does not have enough info at transition-request time to
        // decide whether to handle the transition and never gets a chance to clean up the split
        // state. So we check here to see if that happened, and clean up if so.
        if (splitHandler.transitionImpliesSplitToFullscreen(info)) {
            splitHandler.dismissSplitInBackground(EXIT_REASON_FULLSCREEN_REQUEST);
        }

        final TransitionInfo pipInfo = removePipChangesFrom(info);
        final boolean hasPipChange = !pipInfo.getChanges().isEmpty();
        final TransitionInfo.Change enterPipChange = pipInfo.getChanges().stream().filter(change ->
                mPipHandler.isEnteringPip(change, info.getType())).findFirst().orElse(null);

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
        if ((!hasPipChange && desktopChange == null)
                || (hasPipChange && desktopChange != null)) {
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
        } else if (hasPipChange && desktopChange == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Splitting PIP into a separate"
                            + " animation because remote-animation likely doesn't support it #%d",
                    info.getDebugId());
            // Split the transition into 2 parts: the pip part and the rest.
            mInFlightSubAnimations = 2;
            // make a new startTransaction because pip's startEnterAnimation "consumes" it so
            // we need a separate one to send over to launcher.
            SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();
            if (PipFlags.isPip2ExperimentEnabled()) {
                mPipHandler.startAnimation(mTransition, pipInfo, startTransaction,
                        finishTransaction, finishCB);
            } else if (enterPipChange != null) {
                mPipHandler.startEnterAnimation(enterPipChange, otherStartT, finishTransaction,
                        finishCB);
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
        } else if (!hasPipChange && desktopChange != null) {
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

    @NonNull
    private TransitionInfo removePipChangesFrom(@NonNull TransitionInfo outInfo) {
        final TransitionInfo pipInfo = subCopy(outInfo,
                // In PiP2, sub-flight PiP transition doesn't have to be entering PiP.
                PipFlags.isPip2ExperimentEnabled() ? outInfo.getType() : TRANSIT_PIP,
                false /* withChanges */);
        // Cache enter PiP change separately to find config-at-end activity change if present.
        TransitionInfo.Change enterPipChange = null;

        for (int i = outInfo.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = outInfo.getChanges().get(i);

            if (mPipHandler.isEnteringPip(change, outInfo.getType())) {
                if (enterPipChange != null) {
                    throw new IllegalStateException("More than 1 enter-pip changes in one"
                            + " transition? " + outInfo);
                }
                enterPipChange = change;
                outInfo.getChanges().remove(i);
                pipInfo.getChanges().addFirst(enterPipChange);
            } else if (PipFlags.isPip2ExperimentEnabled() && change.getTaskInfo() != null
                    && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
                // Sometimes a PiP change that isn't an entering change could be collected into
                // a different transition.
                outInfo.getChanges().remove(i);
                pipInfo.addChange(change);
            } else if (change.getTaskInfo() == null && enterPipChange != null
                    && change.getParent() != null
                    && change.getParent().equals(enterPipChange.getContainer())) {
                // Cache the PiP activity if it's a target and cached pip task change is its parent;
                // note that we are bottom-to-top, so if such activity has a task
                // that is also a target, then it must have been cached already as pipChange.
                TransitionInfo.Change pipActivityChange = outInfo.getChanges().remove(i);
                pipInfo.getChanges().addFirst(pipActivityChange);
            }
        }
        return pipInfo;
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

        final TransitionInfo.Change bubblingTask = bubbleTransitions.getEnterBubbleTask(info);
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

    /**
     * This is called when a task is being launched from a bubble, or when a task is launching to an
     * existing bubble. It may be one of the following cases, and each should be animated
     * differently:
     *  - Case 1: a Task was in an expanded Bubble, and a new Activity was launched on top of it
     *            from the task itself, or from a non-activity window, such as notification.
     *    - Pattern:
     *      - There is no open type Task in TransitionInfo (because it's an Activity transition).
     *      - A Task is Bubbled and expanded before and after the transition.
     *    - Expected Behavior:
     *      - Skip here.
     *      - Play Activity launch animation.
     *  - Case 2: a Task was in an expanded Bubble, and a new Activity was launched on top of it
     *            from a source activity of different windowing mode, such as Launcher.
     *    - Pattern:
     *      - There is a change type Task in TransitionInfo, which is no longer Bubbled.
     *    - Expected Behavior:
     *      - Skip here.
     *      - The Task should be dismissed from Bubble, and get opened in new windowing mode.
     *        Note: this shouldn't happen from normal user flow, and it now skipped here, but if it
     *              happens, there may not be a good animation.
     *  - Case 3: a Task was in an unfocused Bubble, a new Activity was launched to it from the
     *            focused expanded Bubble, or from a non-activity window, such as notification.
     *    - Pattern:
     *      - There is a move-to-front type Task in TransitionInfo, which is Bubbled.
     *      - That Task is Bubbled before and after the transition.
     *      - (Optional) There is a move-to-back type Task in TransitionInfo, which is Bubbled.
     *    - Expected Behavior:
     *      - Play expand Bubble animation.
     *      - (Optional) Hide the previous expanded Bubble.
     *  - Case 4: a Task was in an unfocused Bubble, a new Activity was launched to it from a source
     *            activity of different windowing mode, such as Launcher.
     *    - Pattern:
     *      - There is a move-to-front type Task in TransitionInfo, but is not Bubbled.
     *      - That Task was Bubbled before the transition.
     *    - Expected Behavior:
     *      - Skip here.
     *      - The Task should be dismissed from Bubble, and get opened in source's windowing mode.
     *  - Case 5: the source Task was in an expanded Bubble, it launched an Activity in new Task,
     *            and finished itself, such as Task trampoline.
     *    - Pattern:
     *      - There is an open type Task in TransitionInfo, which is Bubbled.
     *      - There is a close type Task in TransitionInfo, which is Bubbled.
     *    - Expected Behavior:
     *      - Jump cut, so the user should not see an extra animation for Task trampoline.
     *  - Case 6: the source Task was in an expanded Bubble, it launched an Activity in new Task,
     *            but didn't finish itself.
     *    - Pattern:
     *      - There is an opening Task in TransitionInfo, which is Bubbled.
     *      - (Optional) That Bubbled Task can be change/move-to-front type if it was in a different
     *        windowing mode before the transition.
     *      - A different Task was expanded Bubbled, but it may not be in TransitionInfo, as it may
     *        be closed later.
     *    - Expected Behavior:
     *      - Play Bubble switch animation.
     */
    static boolean animateEnterBubblesFromBubble(
            @NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull BubbleTransitions bubbleTransitions) {
        // Identify the task being launched into a bubble
        final TransitionInfo.Change enterBubbleTask = bubbleTransitions.getEnterBubbleTask(info);
        if (enterBubbleTask == null) {
            // The trigger Task is no longer in Bubble (Case 1/2/4)
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " No bubbling task found");

            if (bubbleTransitions.containsExpandedBubbledTaskNoLongerBubbled(info)) {
                // The expanded Bubbled Task is no longer Bubbled (Case 2)
                ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        " An activity launch converted the expanded Bubbled Task to non-Bubbled");
            }

            return false;
        }
        final TransitionInfo.Change closingBubble = bubbleTransitions.getClosingBubbleTask(info);

        final Consumer<Transitions.TransitionHandler> onInflatedCallback = handler -> {
            final Transitions.TransitionHandler h = bubbleTransitions
                    .getRunningEnterTransition(transition);
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation played by %s",
                    h);
            h.startAnimation(
                    transition, info, startTransaction, finishTransaction, finishCallback);
        };

        if (com.android.window.flags2.Flags.fixBubbleTrampolineAnimation()
                && closingBubble != null && isOpeningType(enterBubbleTask.getMode())) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                    + "opening bubble from another closing bubbled task");
            // Task Trampoline (Case 5)
            bubbleTransitions.startTaskTrampolineBubbleLaunch(
                    transition, enterBubbleTask.getTaskInfo(),
                    closingBubble.getTaskInfo(), onInflatedCallback);
        } else {
            // Opening a Bubble Task (Case 3/6)
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                    + "entering bubble from another bubbled task or for an existing bubble");
            bubbleTransitions.startBubbleToBubbleLaunchOrExistingBubbleConvert(
                    transition, enterBubbleTask.getTaskInfo(), onInflatedCallback);
        }

        return true;
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
            mBubbleTransitions.notifyUnfoldTransitionFinished(transition);
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
                mBubbleTransitions.notifyUnfoldTransitionFinished(transition);
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
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "Bubble transition consumed: aborted=%b", aborted);
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
