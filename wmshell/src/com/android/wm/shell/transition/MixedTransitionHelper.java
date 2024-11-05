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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;

import static com.android.wm.shell.common.split.SplitScreenConstants.FLAG_IS_DIVIDER_BAR;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.pip.PipAnimationController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.shared.TransitionUtil.isOpeningMode;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_PIP;
import static com.android.wm.shell.transition.DefaultMixedHandler.subCopy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.StageCoordinator;

public class MixedTransitionHelper {
    static boolean animateEnterPipFromSplit(
            @NonNull DefaultMixedHandler.MixedTransition mixed, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull Transitions player, @NonNull MixedTransitionHandler mixedHandler,
            @NonNull PipTransitionController pipHandler, @NonNull StageCoordinator splitHandler,
            boolean replacingPip) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animating a mixed transition for "
                + "entering PIP while Split-Screen is foreground.");
        TransitionInfo.Change pipChange = null;
        TransitionInfo.Change wallpaper = null;
        final TransitionInfo everythingElse =
                subCopy(info, TRANSIT_TO_BACK, true /* changes */);
        boolean homeIsOpening = false;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (pipHandler.isEnteringPip(change, info.getType())) {
                if (pipChange != null) {
                    throw new IllegalStateException("More than 1 pip-entering changes in one"
                            + " transition? " + info);
                }
                pipChange = change;
                // going backwards, so remove-by-index is fine.
                everythingElse.getChanges().remove(i);
            } else if (isHomeOpening(change)) {
                homeIsOpening = true;
            } else if (isWallpaper(change)) {
                wallpaper = change;
            }
        }
        if (pipChange == null) {
            // um, something probably went wrong.
            return false;
        }
        final boolean isGoingHome = homeIsOpening;
        Transitions.TransitionFinishCallback finishCB = (wct) -> {
            --mixed.mInFlightSubAnimations;
            mixed.joinFinishArgs(wct);
            if (mixed.mInFlightSubAnimations > 0) return;
            if (isGoingHome) {
                splitHandler.onTransitionAnimationComplete();
            }
            finishCallback.onTransitionFinished(mixed.mFinishWCT);
        };
        if (isGoingHome || splitHandler.getSplitItemPosition(pipChange.getLastParent())
                != SPLIT_POSITION_UNDEFINED) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Animation is actually mixed "
                    + "since entering-PiP caused us to leave split and return home.");
            // We need to split the transition into 2 parts: the pip part (animated by pip)
            // and the dismiss-part (animated by launcher).
            mixed.mInFlightSubAnimations = 2;
            // immediately make the wallpaper visible (so that we don't see it pop-in during
            // the time it takes to start recents animation (which is remote).
            if (wallpaper != null) {
                startTransaction.show(wallpaper.getLeash()).setAlpha(wallpaper.getLeash(), 1.f);
            }
            // make a new startTransaction because pip's startEnterAnimation "consumes" it so
            // we need a separate one to send over to launcher.
            SurfaceControl.Transaction otherStartT = new SurfaceControl.Transaction();
            @SplitScreen.StageType int topStageToKeep = STAGE_TYPE_UNDEFINED;
            if (splitHandler.isSplitScreenVisible() && !replacingPip) {
                // The non-going home case, we could be pip-ing one of the split stages and keep
                // showing the other
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    TransitionInfo.Change change = info.getChanges().get(i);
                    if (change == pipChange) {
                        // Ignore the change/task that's going into Pip
                        continue;
                    }
                    @SplitScreen.StageType int splitItemStage =
                            splitHandler.getSplitItemStage(change.getLastParent());
                    if (splitItemStage != STAGE_TYPE_UNDEFINED) {
                        topStageToKeep = splitItemStage;
                        break;
                    }
                }

                // Let split update internal state for dismiss.
                splitHandler.prepareDismissAnimation(topStageToKeep,
                        EXIT_REASON_CHILD_TASK_ENTER_PIP, everythingElse, otherStartT,
                        finishTransaction);
            }

            // We are trying to accommodate launcher's close animation which can't handle the
            // divider-bar, so if split-handler is closing the divider-bar, just hide it and
            // remove from transition info.
            for (int i = everythingElse.getChanges().size() - 1; i >= 0; --i) {
                if ((everythingElse.getChanges().get(i).getFlags() & FLAG_IS_DIVIDER_BAR)
                        != 0) {
                    everythingElse.getChanges().remove(i);
                    break;
                }
            }

            pipHandler.setEnterAnimationType(ANIM_TYPE_ALPHA);
            pipHandler.startEnterAnimation(pipChange, startTransaction, finishTransaction,
                    finishCB);
            // Dispatch the rest of the transition normally. This will most-likely be taken by
            // recents or default handler.
            mixed.mLeftoversHandler = player.dispatchTransition(mixed.mTransition, everythingElse,
                    otherStartT, finishTransaction, finishCB, mixedHandler);
        } else {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  Not leaving split, so just "
                    + "forward animation to Pip-Handler.");
            // This happens if the pip-ing activity is in a multi-activity task (and thus a
            // new pip task is spawned). In this case, we don't actually exit split so we can
            // just let pip transition handle the animation verbatim.
            mixed.mInFlightSubAnimations = 1;
            pipHandler.startAnimation(
                    mixed.mTransition, info, startTransaction, finishTransaction, finishCB);
        }
        return true;
    }

    /**
     * Check to see if we're only closing split to enter pip or if we're replacing pip with
     * another task. If we are replacing, this will return the change for the task we are replacing
     * pip with
     *
     * @param info Any number of changes
     * @param pipChange TransitionInfo.Change indicating the task that is being pipped
     * @param splitMainStageRootId MainStage's rootTaskInfo's id
     * @param splitSideStageRootId SideStage's rootTaskInfo's id
     * @param lastPipSplitStage The last stage that {@param pipChange} was in
     * @return The change from {@param info} that is replacing the {@param pipChange}, {@code null}
     *         otherwise
     */
    @Nullable
    public static TransitionInfo.Change getPipReplacingChange(TransitionInfo info,
            TransitionInfo.Change pipChange, int splitMainStageRootId, int splitSideStageRootId,
            @SplitScreen.StageType int lastPipSplitStage) {
        int lastPipParentTask = -1;
        if (lastPipSplitStage == STAGE_TYPE_MAIN) {
            lastPipParentTask = splitMainStageRootId;
        } else if (lastPipSplitStage == STAGE_TYPE_SIDE) {
            lastPipParentTask = splitSideStageRootId;
        }

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (change == pipChange || !isOpeningMode(change.getMode())) {
                // Ignore the change/task that's going into Pip or not opening
                continue;
            }

            if (change.getTaskInfo().parentTaskId == lastPipParentTask) {
                return change;
            }
        }
        return null;
    }

    private static boolean isHomeOpening(@NonNull TransitionInfo.Change change) {
        return change.getTaskInfo() != null
                && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME;
    }

    private static boolean isWallpaper(@NonNull TransitionInfo.Change change) {
        return (change.getFlags() & FLAG_IS_WALLPAPER) != 0;
    }

    static boolean animateKeyguard(
            @NonNull DefaultMixedHandler.MixedTransition mixed, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull KeyguardTransitionHandler keyguardHandler,
            PipTransitionController pipHandler) {
        if (mixed.mFinishT == null) {
            mixed.mFinishT = finishTransaction;
            mixed.mFinishCB = finishCallback;
        }
        // Sync pip state.
        if (pipHandler != null) {
            pipHandler.syncPipSurfaceState(info, startTransaction, finishTransaction);
        }
        return mixed.startSubAnimation(keyguardHandler, info, startTransaction, finishTransaction);
    }
}
