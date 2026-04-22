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
package com.android.launcher3.taskbar;

import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;

import android.animation.Animator;

import androidx.annotation.Nullable;

import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.quickstep.FallbackActivityInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;

import java.io.PrintWriter;
import java.util.stream.Stream;

/**
 * A data source which integrates with the fallback RecentsActivity instance (for 3P launchers).
 * @param <T> The type of the RecentsViewContainer that will handle Recents state changes.
 */
public class FallbackTaskbarUIController
        <T extends RecentsViewContainer & StatefulContainer<RecentsState>>
        extends TaskbarUIController {

    private final T mRecentsContainer;

    private final StateManager.StateListener<RecentsState> mStateListener =
            new StateManager.StateListener<RecentsState>() {
                @Override
                public void onStateTransitionStart(RecentsState toState) {
                    animateToRecentsState(toState);

                    RecentsView recentsView = getRecentsView();
                    if (recentsView == null) {
                        return;
                    }
                    // Handle tapping on live tile.
                    recentsView.setTaskLaunchListener(toState == RecentsState.DEFAULT
                            ? (() -> animateToRecentsState(RecentsState.BACKGROUND_APP)) : null);
                }

                @Override
                public void onStateTransitionComplete(RecentsState finalState) {
                    boolean finalStateDefault = finalState == RecentsState.DEFAULT;
                    // TODO(b/268120202) Taskbar shows up on 3P home, currently we don't go to
                    //  overview from 3P home. Either implement that or it'll change w/ contextual?
                    boolean disallowLongClick = finalState == RecentsState.OVERVIEW_SPLIT_SELECT;
                    Utilities.setOverviewDragState(mControllers,
                            finalStateDefault /*disallowGlobalDrag*/, disallowLongClick,
                            finalStateDefault /*allowInitialSplitSelection*/);
                }
            };

    public FallbackTaskbarUIController(T recentsContainer) {
        mRecentsContainer = recentsContainer;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        super.init(taskbarControllers);
        mRecentsContainer.setTaskbarUIController(this);
        mRecentsContainer.getStateManager().addStateListener(mStateListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecentsContainer.setTaskbarUIController(null);
        mRecentsContainer.getStateManager().removeStateListener(mStateListener);
    }

    @Nullable
    @Override
    public Animator getParallelAnimationToGestureEndTarget(GestureState.GestureEndTarget endTarget,
            long duration, RecentsAnimationCallbacks callbacks) {
        return createAnimToRecentsState(
                FallbackActivityInterface.INSTANCE.stateFromGestureEndTarget(endTarget), duration);
    }

    /**
     * Creates an animation to animate the taskbar for the given state (but does not start it).
     * Currently this animation just force stashes the taskbar in Overview.
     */
    private Animator createAnimToRecentsState(RecentsState toState, long duration) {
        // Force stash taskbar (disallow unstashing) when:
        // - in a 3P launcher or overview task.
        // - not running in a test harness (unstash is needed for tests)
        boolean forceStash = isIn3pHomeOrRecents() && !isRunningInTestHarness();
        TaskbarStashController stashController = mControllers.taskbarStashController;
        // Set both FLAG_IN_STASHED_LAUNCHER_STATE and FLAG_IN_APP to ensure the state is respected.
        // For all other states, just use the current stashed-in-app setting (e.g. if long clicked).
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, forceStash);
        stashController.updateStateForFlag(FLAG_IN_APP, !forceStash);
        return stashController.createApplyStateAnimator(duration);
    }

    private void animateToRecentsState(RecentsState toState) {
        Animator anim = createAnimToRecentsState(toState,
                mControllers.taskbarStashController.getStashDuration());
        if (anim != null) {
            anim.start();
        }
    }

    @Override
    public @Nullable RecentsView getRecentsView() {
        return mRecentsContainer.getOverviewPanel();
    }

    @Override
    Stream<SystemShortcut.Factory<BaseTaskbarContext>> getSplitMenuOptions() {
        if (isIn3pHomeOrRecents()) {
            // Split from Taskbar is not supported in fallback launcher, so return empty stream
            return Stream.empty();
        } else {
            return super.getSplitMenuOptions();
        }
    }

    private boolean isIn3pHomeOrRecents() {
        TopTaskTracker.CachedTaskInfo topTask = TopTaskTracker.INSTANCE
                .get(mControllers.taskbarActivityContext).getCachedTopTask(true,
                        mRecentsContainer.asContext().getDisplayId());
        return topTask.isHomeTask() || topTask.isRecentsTask();
    }

    @Override
    protected String getTaskbarUIControllerName() {
        return "FallbackTaskbarUIController<" + mRecentsContainer.getClass().getSimpleName() + ">";
    }

    @Override
    protected void dumpLogs(String prefix, PrintWriter pw) {
        super.dumpLogs(prefix, pw);

        pw.println(String.format("%s\tRecentsState=%s", prefix,
                mRecentsContainer.getStateManager().getState()));
    }
}
