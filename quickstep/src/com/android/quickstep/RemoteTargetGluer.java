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

package com.android.quickstep;

import android.content.Context;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;

import java.util.ArrayList;

/**
 * Glues together the necessary components to animate a remote target using a
 * {@link TaskViewSimulator}
 */
public class RemoteTargetGluer {
    private RemoteTargetHandle[] mRemoteTargetHandles;
    private SplitBounds mSplitBounds;

    /**
     * Use this constructor if remote targets are split-screen independent
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy,
            RemoteAnimationTargets targets) {
        mRemoteTargetHandles = createHandles(context, sizingStrategy, targets.apps.length);
    }

    /**
     * Use this constructor if you want the number of handles created to match the number of active
     * running tasks
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy) {
        int[] splitIds = TopTaskTracker.INSTANCE.get(context).getRunningSplitTaskIds();
        mRemoteTargetHandles = createHandles(context, sizingStrategy, splitIds.length == 2 ? 2 : 1);
    }

    private RemoteTargetHandle[] createHandles(Context context,
            BaseActivityInterface sizingStrategy, int numHandles) {
        RemoteTargetHandle[] handles = new RemoteTargetHandle[numHandles];
        for (int i = 0; i < numHandles; i++) {
            TaskViewSimulator tvs = new TaskViewSimulator(context, sizingStrategy);
            TransformParams transformParams = new TransformParams();
            handles[i] = new RemoteTargetHandle(tvs, transformParams);
        }
        return handles;
    }

    /**
     * Pairs together {@link TaskViewSimulator}s and {@link TransformParams} into a
     * {@link RemoteTargetHandle}
     * Assigns only the apps associated with {@param targets} into their own TaskViewSimulators.
     * Length of targets.apps should match that of {@link #mRemoteTargetHandles}.
     *
     * If split screen may be active when this is called, you might want to use
     * {@link #assignTargetsForSplitScreen(Context, RemoteAnimationTargets)}
     */
    public RemoteTargetHandle[] assignTargets(RemoteAnimationTargets targets) {
        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, null));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
        }
        return mRemoteTargetHandles;
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this matches the
     * apps in targets.apps to that of the _active_ split screened tasks.
     * See {@link #assignTargetsForSplitScreen(RemoteAnimationTargets, int[])}
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(
            Context context, RemoteAnimationTargets targets) {
        int[] splitIds = TopTaskTracker.INSTANCE.get(context).getRunningSplitTaskIds();
        return assignTargetsForSplitScreen(targets, splitIds);
    }

    /**
     * Assigns the provided splitIDs to the {@link #mRemoteTargetHandles}, with index 0 will being
     * the left/top task, index 1 right/bottom
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets,
            int[] splitIds) {
        RemoteAnimationTarget topLeftTarget; // only one set if single/fullscreen task
        RemoteAnimationTarget bottomRightTarget;
        if (mRemoteTargetHandles.length == 1) {
            // If we're not in split screen, the splitIds count doesn't really matter since we
            // should always hit this case.
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(targets);
            if (targets.apps.length > 0) {
                // Unclear why/when target.apps length == 0, but it sure does happen :(
                topLeftTarget = targets.apps[0];
                mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(topLeftTarget, null);
            }
        } else {
            // split screen
            topLeftTarget = targets.findTask(splitIds[0]);
            bottomRightTarget = targets.findTask(splitIds[1]);

            // remoteTargetHandle[0] denotes topLeft task, so we pass in the bottomRight to exclude,
            // vice versa
            mSplitBounds = new SplitBounds(
                    getStartBounds(topLeftTarget),
                    getStartBounds(bottomRightTarget), splitIds[0], splitIds[1]);
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, bottomRightTarget));
            mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(topLeftTarget,
                    mSplitBounds);

            mRemoteTargetHandles[1].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, topLeftTarget));
            mRemoteTargetHandles[1].mTaskViewSimulator.setPreview(bottomRightTarget,
                    mSplitBounds);
        }
        return mRemoteTargetHandles;
    }

    private Rect getStartBounds(RemoteAnimationTarget target) {
        return target.startBounds == null ? target.screenSpaceBounds : target.startBounds;
    }

    /**
     * Ensures that we aren't excluding ancillary targets such as home/recents
     *
     * @param targetToExclude Will be excluded from the resulting return value.
     *                        Pass in {@code null} to not exclude anything
     * @return RemoteAnimationTargets where all the app targets from the passed in
     *         {@param targets} are included except {@param targetToExclude}
     */
    private RemoteAnimationTargets createRemoteAnimationTargetsForTarget(
            RemoteAnimationTargets targets,
            RemoteAnimationTarget targetToExclude) {
        ArrayList<RemoteAnimationTarget> targetsWithoutExcluded = new ArrayList<>();

        for (RemoteAnimationTarget targetCompat : targets.unfilteredApps) {
            if (targetCompat == targetToExclude) {
                continue;
            }
            if (targetToExclude != null
                    && targetToExclude.taskInfo != null
                    && targetCompat.taskInfo != null
                    && targetToExclude.taskInfo.parentTaskId == targetCompat.taskInfo.taskId) {
                // Also exclude corresponding parent task
                continue;
            }

            targetsWithoutExcluded.add(targetCompat);
        }
        final RemoteAnimationTarget[] filteredApps = targetsWithoutExcluded.toArray(
                new RemoteAnimationTarget[targetsWithoutExcluded.size()]);
        return new RemoteAnimationTargets(
                filteredApps, targets.wallpapers, targets.nonApps, targets.targetMode);
    }

    public RemoteTargetHandle[] getRemoteTargetHandles() {
        return mRemoteTargetHandles;
    }

    public SplitBounds getSplitBounds() {
        return mSplitBounds;
    }

    /**
     * Container to keep together all the associated objects whose properties need to be updated to
     * animate a single remote app target
     */
    public static class RemoteTargetHandle {
        private final TaskViewSimulator mTaskViewSimulator;
        private final TransformParams mTransformParams;
        @Nullable
        private AnimatorControllerWithResistance mPlaybackController;

        public RemoteTargetHandle(TaskViewSimulator taskViewSimulator,
                TransformParams transformParams) {
            mTransformParams = transformParams;
            mTaskViewSimulator = taskViewSimulator;
        }

        public TaskViewSimulator getTaskViewSimulator() {
            return mTaskViewSimulator;
        }

        public TransformParams getTransformParams() {
            return mTransformParams;
        }

        @Nullable
        public AnimatorControllerWithResistance getPlaybackController() {
            return mPlaybackController;
        }

        public void setPlaybackController(
                @Nullable AnimatorControllerWithResistance playbackController) {
            mPlaybackController = playbackController;
        }
    }
}
