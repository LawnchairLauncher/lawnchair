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

import static com.android.quickstep.util.SplitScreenUtils.convertShellSplitBoundsToLauncher;
import static com.android.wm.shell.util.SplitBounds.KEY_EXTRA_SPLIT_BOUNDS;

import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.wm.shell.util.SplitBounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Glues together the necessary components to animate a remote target using a
 * {@link TaskViewSimulator}
 */
public class RemoteTargetGluer {
    private static final String TAG = "RemoteTargetGluer";

    // This is the default number of handles to create when we don't know how many tasks are running
    // (e.g. if we're in split screen). Allocate extra for potential tasks overlaid, like volume.
    private static final int DEFAULT_NUM_HANDLES = 4;

    private RemoteTargetHandle[] mRemoteTargetHandles;
    private SplitConfigurationOptions.SplitBounds mSplitBounds;

    /**
     * Use this constructor if remote targets are split-screen independent
     */
    public RemoteTargetGluer(Context context, BaseContainerInterface sizingStrategy,
            RemoteAnimationTargets targets, boolean forDesktop) {
        init(context, sizingStrategy, targets.apps.length, forDesktop);
    }

    /**
     * Use this constructor if you want the number of handles created to match the number of active
     * running tasks
     */
    public RemoteTargetGluer(Context context, BaseContainerInterface sizingStrategy) {
        DesktopVisibilityController desktopVisibilityController =
                LauncherActivityInterface.INSTANCE.getDesktopVisibilityController();
        if (desktopVisibilityController != null) {
            int visibleTasksCount = desktopVisibilityController.getVisibleDesktopTasksCount();
            if (visibleTasksCount > 0) {
                // Allocate +1 to account for a new task added to the desktop mode
                int numHandles = visibleTasksCount + 1;
                init(context, sizingStrategy, numHandles, true /* forDesktop */);
                return;
            }
        }

        // Assume 2 handles needed for split, scale down as needed later on when we actually
        // get remote targets
        init(context, sizingStrategy, DEFAULT_NUM_HANDLES, false /* forDesktop */);
    }

    private void init(Context context, BaseContainerInterface sizingStrategy, int numHandles,
            boolean forDesktop) {
        mRemoteTargetHandles = createHandles(context, sizingStrategy, numHandles, forDesktop);
    }

    private RemoteTargetHandle[] createHandles(Context context,
            BaseContainerInterface sizingStrategy, int numHandles, boolean forDesktop) {
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
                    createRemoteAnimationTargetsForTarget(targets, Collections.emptyList()));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
        }
        return mRemoteTargetHandles;
    }

    /**
     * Calls {@link #assignTargetsForSplitScreen(RemoteAnimationTargets)} with SplitBounds
     * information specified.
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets,
            SplitConfigurationOptions.SplitBounds splitBounds) {
        mSplitBounds = splitBounds;
        return assignTargetsForSplitScreen(targets);
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this assigns the
     * apps in {@code targets.apps} to the {@link #mRemoteTargetHandles} with index 0 will being
     * the left/top task, index 1 right/bottom.
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets) {
        resizeRemoteTargetHandles(targets);

        // If we are in a true split screen case (2 apps running on screen), either:
        //     a) mSplitBounds was already set (from the clicked GroupedTaskView)
        //     b) A SplitBounds was passed up from shell (via AbsSwipeUpHandler)
        // If both of these are null, we are in a 1-app or 1-app-plus-assistant case.
        if (mSplitBounds == null) {
            SplitBounds shellSplitBounds = targets.extras.getParcelable(KEY_EXTRA_SPLIT_BOUNDS,
                    SplitBounds.class);
            mSplitBounds = convertShellSplitBoundsToLauncher(shellSplitBounds);
        }

        boolean containsSplitTargets = mSplitBounds != null;
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
            RemoteAnimationTarget topLeftTarget = targets.findTask(mSplitBounds.leftTopTaskId);
            RemoteAnimationTarget bottomRightTarget = targets.findTask(
                    mSplitBounds.rightBottomTaskId);
            List<RemoteAnimationTarget> overlayTargets = Arrays.stream(targets.apps).filter(
                    target -> target.windowConfiguration.getWindowingMode()
                            != WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW).toList();

            // remoteTargetHandle[0] denotes topLeft task, so we pass in the bottomRight to exclude,
            // vice versa
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets,
                            Collections.singletonList(bottomRightTarget)));
            mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(topLeftTarget, mSplitBounds);

            mRemoteTargetHandles[1].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets,
                            Collections.singletonList(topLeftTarget)));
            mRemoteTargetHandles[1].mTaskViewSimulator.setPreview(bottomRightTarget, mSplitBounds);

            // Set the remaining overlay tasks to be their own TaskViewSimulator as fullscreen tasks
            if (!overlayTargets.isEmpty()) {
                ArrayList<RemoteAnimationTarget> targetsToExclude = new ArrayList<>();
                targetsToExclude.add(topLeftTarget);
                targetsToExclude.add(bottomRightTarget);
                // Start i at 2 to account for top/left and bottom/right split handles already made
                for (int i = 2; i < targets.apps.length; i++) {
                    if (i >= mRemoteTargetHandles.length) {
                        Log.e(TAG, String.format("Attempting to animate an untracked target"
                                + " (%d handles allocated, but %d want to animate)",
                                mRemoteTargetHandles.length, targets.apps.length));
                        break;
                    }
                    mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                            createRemoteAnimationTargetsForTarget(targets, targetsToExclude));
                    mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(
                            overlayTargets.get(i - 2));
                }

            }
        }
        return mRemoteTargetHandles;
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this creates distinct
     * transform params per app in {@code targets.apps} list.
     */
    public RemoteTargetHandle[] assignTargetsForDesktop(RemoteAnimationTargets targets) {
        resizeRemoteTargetHandles(targets);

        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            List<RemoteAnimationTarget> excludeTargets = Arrays.stream(targets.apps)
                    .filter(target -> target.taskId != primaryTaskTarget.taskId).toList();
            mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, excludeTargets));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
        }
        return mRemoteTargetHandles;
    }

    /**
     * Resize the `mRemoteTargetHandles` array since we assumed initial size, but
     * `targets.apps` is the ultimate source of truth here
     */
    private void resizeRemoteTargetHandles(RemoteAnimationTargets targets) {
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
    }

    private Rect getStartBounds(RemoteAnimationTarget target) {
        return target.startBounds == null ? target.screenSpaceBounds : target.startBounds;
    }

    /**
     * Ensures that we aren't excluding ancillary targets such as home/recents
     *
     * @param targetsToExclude Will be excluded from the resulting return value.
     *                        Pass in an empty list to not exclude anything
     * @return RemoteAnimationTargets where all the app targets from the passed in
     *         {@code targets} are included except {@code targetsToExclude}
     */
    private RemoteAnimationTargets createRemoteAnimationTargetsForTarget(
            @NonNull RemoteAnimationTargets targets,
            @NonNull List<RemoteAnimationTarget> targetsToExclude) {
        ArrayList<RemoteAnimationTarget> targetsToInclude = new ArrayList<>();

        for (RemoteAnimationTarget targetCompat : targets.unfilteredApps) {
            boolean skipTarget = false;
            for (RemoteAnimationTarget excludingTarget : targetsToExclude) {
                if (targetCompat == excludingTarget) {
                    skipTarget = true;
                    break;
                }
                if (excludingTarget != null
                        && excludingTarget.taskInfo != null
                        && targetCompat.taskInfo != null
                        && excludingTarget.taskInfo.parentTaskId == targetCompat.taskInfo.taskId) {
                    // Also exclude corresponding parent task
                    skipTarget = true;
                }
            }
            if (skipTarget) {
                continue;
            }
            targetsToInclude.add(targetCompat);
        }
        final RemoteAnimationTarget[] filteredApps = targetsToInclude.toArray(
                new RemoteAnimationTarget[0]);
        return new RemoteAnimationTargets(
                filteredApps, targets.wallpapers, targets.nonApps, targets.targetMode);
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

    public SplitConfigurationOptions.SplitBounds getSplitBounds() {
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
