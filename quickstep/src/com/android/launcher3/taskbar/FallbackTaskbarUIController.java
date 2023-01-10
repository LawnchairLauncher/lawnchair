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

import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;
import static com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION;

import android.animation.Animator;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StateManager;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.views.RecentsView;

/**
 * A data source which integrates with the fallback RecentsActivity instance (for 3P launchers).
 */
public class FallbackTaskbarUIController extends TaskbarUIController {

    private final RecentsActivity mRecentsActivity;

    private final StateManager.StateListener<RecentsState> mStateListener =
            new StateManager.StateListener<RecentsState>() {
                @Override
                public void onStateTransitionStart(RecentsState toState) {
                    animateToRecentsState(toState);

                    // Handle tapping on live tile.
                    getRecentsView().setTaskLaunchListener(toState == RecentsState.DEFAULT
                            ? (() -> animateToRecentsState(RecentsState.BACKGROUND_APP)) : null);
                }
            };

    public FallbackTaskbarUIController(RecentsActivity recentsActivity) {
        mRecentsActivity = recentsActivity;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        super.init(taskbarControllers);

        mRecentsActivity.setTaskbarUIController(this);
        mRecentsActivity.getStateManager().addStateListener(mStateListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecentsActivity.setTaskbarUIController(null);
        mRecentsActivity.getStateManager().removeStateListener(mStateListener);
    }

    /**
     * Creates an animation to animate the taskbar for the given state (but does not start it).
     * Currently this animation just force stashes the taskbar in Overview.
     */
    public Animator createAnimToRecentsState(RecentsState toState, long duration) {
        boolean useStashedLauncherState = toState.hasOverviewActions();
        boolean stashedLauncherState =
                useStashedLauncherState && !FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get();
        TaskbarStashController controller = mControllers.taskbarStashController;
        // Set both FLAG_IN_STASHED_LAUNCHER_STATE and FLAG_IN_APP to ensure the state is respected.
        // For all other states, just use the current stashed-in-app setting (e.g. if long clicked).
        controller.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, stashedLauncherState);
        controller.updateStateForFlag(FLAG_IN_APP, !useStashedLauncherState);
        return controller.applyStateWithoutStart(duration);
    }

    private void animateToRecentsState(RecentsState toState) {
        Animator anim = createAnimToRecentsState(toState, TASKBAR_STASH_DURATION);
        if (anim != null) {
            anim.start();
        }
    }

    @Override
    public RecentsView getRecentsView() {
        return mRecentsActivity.getOverviewPanel();
    }
}
