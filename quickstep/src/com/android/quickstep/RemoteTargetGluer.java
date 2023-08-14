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

import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.DesktopTaskView;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Glues together the necessary components to animate a remote target using a
 * {@link TaskViewSimulator}
 */
public class RemoteTargetGluer {
    private static final String TAG = "RemoteTargetGluer";

    private static final int DEFAULT_NUM_HANDLES = 2;

    private RemoteTargetHandle[] mRemoteTargetHandles;
    private SplitBounds mSplitBounds;

    /**
     * Use this constructor if remote targets are split-screen independent
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy,
            RemoteAnimationTargets targets, boolean forDesktop) {
        init(context, sizingStrategy, targets.apps.length, forDesktop);
    }

    /**
     * Use this constructor if you want the number of handles created to match the number of active
     * running tasks
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy) {
        if (DesktopTaskView.DESKTOP_MODE_SUPPORTED) {
            // TODO(279931899): binder call, only for prototyping. Creating the gluer should be
            //  postponed so we can create it when we have the remote animation targets ready.
            int desktopTasks = SystemUiProxy.INSTANCE.get(context).getVisibleDesktopTaskCount(
                    context.getDisplayId());
            if (desktopTasks > 0) {
                init(context, sizingStrategy, desktopTasks, true /* forDesktop */);
                return;
            }
        }

        // Assume 2 handles needed for split, scale down as needed later on when we actually
        // get remote targets
        init(context, sizingStrategy, DEFAULT_NUM_HANDLES, false /* forDesktop */);
    }

    private void init(Context context, BaseActivityInterface sizingStrategy, int numHandles,
            boolean forDesktop) {
        mRemoteTargetHandles = createHandles(context, sizingStrategy, numHandles, forDesktop);
    }

    private RemoteTargetHandle[] createHandles(Context context,
            BaseActivityInterface sizingStrategy, int numHandles, boolean forDesktop) {
        RemoteTargetHandle[] handles = new RemoteTargetHandle[numHandles];
        for (int i = 0; i < numHandles; i++) {
            TaskViewSimulator tvs = new TaskViewSimulator(context, sizingStrategy);
            tvs.setIsDesktopTask(forDesktop);
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
     * {@link #assignTargetsForSplitScreen(RemoteAnimationTargets)}
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
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this assigns the
     * apps in {@code targets.apps} to the {@link #mRemoteTargetHandles} with index 0 will being
     * the left/top task, index 1 right/bottom.
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets) {
        // Resize the mRemoteTargetHandles array since we started assuming split screen, but
        // targets.apps is the ultimate source of truth here
        long appCount = Arrays.stream(targets.apps)
                .filter(app -> app.mode == targets.targetMode)
                .count();
        Log.d(TAG, "appCount: " + appCount + " handleLength: " + mRemoteTargetHandles.length);
        if (appCount < mRemoteTargetHandles.length) {
            Log.d(TAG, "resizing handles");
            RemoteTargetHandle[] newHandles = new RemoteTargetHandle[(int) appCount];
            System.arraycopy(mRemoteTargetHandles, 0/*src*/, newHandles, 0/*dst*/, (int) appCount);
            mRemoteTargetHandles = newHandles;
        }

        boolean containsSplitTargets = Arrays.stream(targets.apps)
                .anyMatch(remoteAnimationTarget ->
                        remoteAnimationTarget.windowConfiguration.getWindowingMode()
                                == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW);
        Log.d(TAG, "containsSplitTargets? " + containsSplitTargets + " handleLength: " +
                mRemoteTargetHandles.length + " appsLength: " + targets.apps.length);

        if (mRemoteTargetHandles.length == 1) {
            // Single fullscreen app

            // If we're not in split screen, the splitIds count doesn't really matter since we
            // should always hit this case.
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(targets);
            if (targets.apps.length > 0) {
                // Unclear why/when target.apps length == 0, but it sure does happen :(
                mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(targets.apps[0], null);
            }
        } else if (!containsSplitTargets) {
            // Single App + Assistant
            for (int i = 0; i < mRemoteTargetHandles.length; i++) {
                mRemoteTargetHandles[i].mTransformParams.setTargetSet(targets);
                mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(targets.apps[i], null);
            }
        } else {
            // Split apps (+ maybe assistant)
            RemoteAnimationTarget topLeftTarget = Arrays.stream(targets.apps)
                    .filter(remoteAnimationTarget ->
                            remoteAnimationTarget.windowConfiguration.getWindowingMode()
                                    == WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
                    .findFirst().get();

            // Fetch the adjacent target for split screen.
            RemoteAnimationTarget bottomRightTarget = null;
            for (int i = 0; i < targets.apps.length; i++) {
                final RemoteAnimationTarget target = targets.apps[i];
                if (target.windowConfiguration.getWindowingMode() !=
                        WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW ||
                        target == topLeftTarget) {
                    continue;
                }
                Rect topLeftBounds = getStartBounds(topLeftTarget);
                Rect bounds = getStartBounds(target);
                if (topLeftBounds.left > bounds.right || topLeftBounds.top > bounds.bottom) {
                    bottomRightTarget = topLeftTarget;
                    topLeftTarget = target;
                    break;
                } else if (topLeftBounds.right < bounds.left || topLeftBounds.bottom < bounds.top) {
                    bottomRightTarget = target;
                    break;
                }
            }

            // remoteTargetHandle[0] denotes topLeft task, so we pass in the bottomRight to exclude,
            // vice versa
            mSplitBounds = new SplitBounds(
                    getStartBounds(topLeftTarget),
                    getStartBounds(bottomRightTarget),
                    topLeftTarget.taskId,
                    bottomRightTarget.taskId);
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

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this creates distinct
     * transform params per app in {@code targets.apps} list.
     */
    public RemoteTargetHandle[] assignTargetsForDesktop(RemoteAnimationTargets targets) {
        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTaskId(targets, primaryTaskTarget.taskId));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
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

    /**
     * Ensures that we only animate one specific app target. Includes ancillary targets such as
     * home/recents
     *
     * @param targets remote animation targets to filter
     * @param taskId  id for a task that we want this remote animation to apply to
     * @return {@link RemoteAnimationTargets} where app target only includes the app that has the
     * {@code taskId} that was passed in
     */
    private RemoteAnimationTargets createRemoteAnimationTargetsForTaskId(
            RemoteAnimationTargets targets, int taskId) {
        RemoteAnimationTarget[] targetApp = null;
        for (RemoteAnimationTarget targetCompat : targets.unfilteredApps) {
            if (targetCompat.taskId == taskId) {
                targetApp = new RemoteAnimationTarget[]{targetCompat};
                break;
            }
        }

        if (targetApp == null) {
            targetApp = new RemoteAnimationTarget[0];
        }

        return new RemoteAnimationTargets(targetApp, targets.wallpapers, targets.nonApps,
                targets.targetMode);
    }

    /**
     * The object returned by this is may be modified in
     * {@link #assignTargetsForSplitScreen(RemoteAnimationTargets)}, specifically the length of the
     * array may be shortened based on the number of RemoteAnimationTargets present.
     * <p>
     * This can be accessed at any time, however the count will be more accurate if accessed after
     * calling one of the respective assignTargets*() methods
     */
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
