/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.quickstep.util;

import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.WrappedAnimationRunnerImpl;
import com.android.launcher3.WrappedLauncherAnimationRunner;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {

    private final SystemUiProxy mSystemUiProxy;
    private TaskView mInitialTaskView;
    private SplitPositionOption mInitialPosition;

    public SplitSelectStateController(SystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
    }

    /**
     * To be called after first task selected
     */
    public void setInitialTaskSelect(TaskView taskView, SplitPositionOption positionOption) {
        mInitialTaskView = taskView;
        mInitialPosition = positionOption;
    }

    /**
     * To be called after second task selected
     */
    public void setSecondTaskId(TaskView taskView) {
        // Assume initial mInitialTaskId is for top/left part of screen
        WrappedAnimationRunnerImpl initialSplitRunnerWrapped =  new SplitLaunchAnimationRunner(
                mInitialTaskView, 0);
        WrappedAnimationRunnerImpl secondarySplitRunnerWrapped =  new SplitLaunchAnimationRunner(
                taskView, 1);
        RemoteAnimationRunnerCompat initialSplitRunner = new WrappedLauncherAnimationRunner(
                new Handler(Looper.getMainLooper()), initialSplitRunnerWrapped,
                true /* startAtFrontOfQueue */);
        RemoteAnimationRunnerCompat secondarySplitRunner = new WrappedLauncherAnimationRunner(
                new Handler(Looper.getMainLooper()), secondarySplitRunnerWrapped,
                true /* startAtFrontOfQueue */);
        ActivityOptions initialOptions = ActivityOptionsCompat.makeRemoteAnimation(
                new RemoteAnimationAdapterCompat(initialSplitRunner, 300, 150));
        ActivityOptions secondaryOptions = ActivityOptionsCompat.makeRemoteAnimation(
                new RemoteAnimationAdapterCompat(secondarySplitRunner, 300, 150));
        mSystemUiProxy.startTask(mInitialTaskView.getTask().key.id, mInitialPosition.mStageType,
                mInitialPosition.mStagePosition,
                /*null*/ initialOptions.toBundle());
        Pair<Integer, Integer> compliment = getComplimentaryStageAndPosition(mInitialPosition);
        mSystemUiProxy.startTask(taskView.getTask().key.id, compliment.first,
                compliment.second,
                /*null*/ secondaryOptions.toBundle());
        // After successful launch, call resetState
        resetState();
    }

    @Nullable
    public SplitPositionOption getActiveSplitPositionOption() {
        return mInitialPosition;
    }

    /**
     * @return the opposite stage and position from the {@param position} provided as first and
     *         second object, respectively
     * Ex. If position is has stage = Main and position = Top/Left, this will return
     * Pair(stage=Side, position=Bottom/Left)
     */
    private Pair<Integer, Integer> getComplimentaryStageAndPosition(SplitPositionOption position) {
        // Right now this is as simple as flipping between 0 and 1
        int complimentStageType = position.mStageType ^ 1;
        int complimentStagePosition = position.mStagePosition ^ 1;
        return new Pair<>(complimentStageType, complimentStagePosition);
    }

    /**
     * Remote animation runner for animation to launch an app.
     */
    private class SplitLaunchAnimationRunner implements WrappedAnimationRunnerImpl {

        private final TaskView mV;
        private final int mTargetState;

        SplitLaunchAnimationRunner(TaskView v, int targetState) {
            mV = v;
            mTargetState = targetState;
        }

        @Override
        public void onCreateAnimation(int transit,
                RemoteAnimationTargetCompat[] appTargets,
                RemoteAnimationTargetCompat[] wallpaperTargets,
                RemoteAnimationTargetCompat[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            AnimatorSet anim = new AnimatorSet();
            BaseQuickstepLauncher activity = BaseActivity.fromContext(mV.getContext());
            TaskViewUtils.composeRecentsSplitLaunchAnimator(anim, mV,
                    appTargets, wallpaperTargets, nonAppTargets, true, activity.getStateManager(),
                    activity.getDepthController(), mTargetState);
            result.setAnimation(anim, activity);
        }
    }


    /**
     * To be called if split select was cancelled
     */
    public void resetState() {
        mInitialTaskView = null;
        mInitialPosition = null;
    }

    public boolean isSplitSelectActive() {
        return mInitialTaskView != null;
    }
}
