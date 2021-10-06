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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Pair;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.launcher3.R;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.RemoteTransitionCompat;
import com.android.systemui.shared.system.RemoteTransitionRunner;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {

    private final SystemUiProxy mSystemUiProxy;
    private TaskView mInitialTaskView;
    private SplitPositionOption mInitialPosition;
    private Rect mInitialBounds;
    private final Handler mHandler;

    public SplitSelectStateController(Handler handler, SystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
        mHandler = handler;
    }

    /**
     * To be called after first task selected
     */
    public void setInitialTaskSelect(TaskView taskView, SplitPositionOption positionOption,
            Rect initialBounds) {
        mInitialTaskView = taskView;
        mInitialPosition = positionOption;
        mInitialBounds = initialBounds;
    }

    /**
     * To be called after second task selected
     */
    public void setSecondTaskId(TaskView taskView) {
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            // Assume initial task is for top/left part of screen
            final int[] taskIds = mInitialPosition.mStagePosition == STAGE_POSITION_TOP_OR_LEFT
                    ? new int[]{mInitialTaskView.getTask().key.id, taskView.getTask().key.id}
                    : new int[]{taskView.getTask().key.id, mInitialTaskView.getTask().key.id};

            RemoteSplitLaunchAnimationRunner animationRunner =
                    new RemoteSplitLaunchAnimationRunner(mInitialTaskView, taskView);
            mSystemUiProxy.startTasks(taskIds[0], null /* mainOptions */, taskIds[1],
                    null /* sideOptions */, STAGE_POSITION_BOTTOM_OR_RIGHT,
                    new RemoteTransitionCompat(animationRunner, MAIN_EXECUTOR));
            return;
        }
        // Assume initial mInitialTaskId is for top/left part of screen
        RemoteAnimationFactory initialSplitRunnerWrapped =  new SplitLaunchAnimationRunner(
                mInitialTaskView, 0);
        RemoteAnimationFactory secondarySplitRunnerWrapped =  new SplitLaunchAnimationRunner(
                taskView, 1);
        RemoteAnimationRunnerCompat initialSplitRunner = new LauncherAnimationRunner(
                new Handler(Looper.getMainLooper()), initialSplitRunnerWrapped,
                true /* startAtFrontOfQueue */);
        RemoteAnimationRunnerCompat secondarySplitRunner = new LauncherAnimationRunner(
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

    /**
     * @return {@link InsettableFrameLayout.LayoutParams} to correctly position the
     * split placeholder view
     */
    public InsettableFrameLayout.LayoutParams getLayoutParamsForActivePosition(Resources resources,
            DeviceProfile deviceProfile) {
        InsettableFrameLayout.LayoutParams params =
                new InsettableFrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        boolean topLeftPosition = mInitialPosition.mStagePosition == STAGE_POSITION_TOP_OR_LEFT;
        if (deviceProfile.isLandscape) {
            params.width = (int) resources.getDimension(R.dimen.split_placeholder_size);
            params.gravity = topLeftPosition ? Gravity.START : Gravity.END;
        } else {
            params.height = (int) resources.getDimension(R.dimen.split_placeholder_size);
            params.gravity = Gravity.TOP;
        }

        return params;
    }

    @Nullable
    public SplitPositionOption getActiveSplitPositionOption() {
        return mInitialPosition;
    }

    /**
     * Requires Shell Transitions
     */
    private class RemoteSplitLaunchAnimationRunner implements RemoteTransitionRunner {

        private final TaskView mInitialTaskView;
        private final TaskView mTaskView;

        RemoteSplitLaunchAnimationRunner(TaskView initialTaskView, TaskView taskView) {
            mInitialTaskView = initialTaskView;
            mTaskView = taskView;
        }

        @Override
        public void startAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t, Runnable finishCallback) {
            TaskViewUtils.composeRecentsSplitLaunchAnimator(mInitialTaskView, mTaskView,
                    info, t, finishCallback);
            // After successful launch, call resetState
            resetState();
        }
    }

    /**
     * LEGACY
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
     * LEGACY
     * Remote animation runner for animation to launch an app.
     */
    private class SplitLaunchAnimationRunner implements RemoteAnimationFactory {

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
            TaskViewUtils.composeRecentsSplitLaunchAnimatorLegacy(anim, mV,
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
        mInitialBounds = null;
    }

    public boolean isSplitSelectActive() {
        return mInitialTaskView != null;
    }

    public Rect getInitialBounds() {
        return mInitialBounds;
    }
}
