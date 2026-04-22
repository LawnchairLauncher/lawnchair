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

import static com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops;
import static com.android.wm.shell.shared.split.SplitBounds.KEY_EXTRA_SPLIT_BOUNDS;

import static java.util.stream.Collectors.toList;

import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.split.SplitBounds;

import kotlin.collections.CollectionsKt;

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
    private static final int DEFAULT_NUM_HANDLES = 10;
    private RemoteTargetHandle[] mRemoteTargetHandles;
    private SplitBounds mSplitBounds;

    /**
     * Use this constructor if remote targets are split-screen independent
     */
    public RemoteTargetGluer(Context context, BaseContainerInterface sizingStrategy,
            RemoteAnimationTargets targets, boolean forDesktop) {
        mRemoteTargetHandles = createHandles(context, sizingStrategy, forDesktop,
                targets.apps.length);
    }

    /**
     * Use this constructor if you want the number of handles created to match the number of active
     * running tasks
     */
    public RemoteTargetGluer(Context context, BaseContainerInterface sizingStrategy,
            @Nullable GroupedTaskInfo groupedTaskInfo) {
        if (enableMultipleDesktops(context)) {
            if (groupedTaskInfo != null && groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
                // Allocate +1 to account for the DesktopWallpaperActivity added to the desk.
                int numHandles = groupedTaskInfo.getTaskInfoList().size() + 1;
                mRemoteTargetHandles = createHandles(context, sizingStrategy,
                        /* forDesktop =  */ true, numHandles);
                return;
            }
        } else {
            int visibleTasksCount = DesktopVisibilityController.INSTANCE.get(context)
                    .getVisibleDesktopTasksCountDeprecated();
            if (visibleTasksCount > 0) {
                // Allocate +1 to account for the DesktopWallpaperActivity added to the desk.
                int numHandles = visibleTasksCount + 1;
                mRemoteTargetHandles = createHandles(context, sizingStrategy,
                        /* forDesktop = */ true, numHandles);
                return;
            }
        }

        // Assume 2 handles needed for split, scale down as needed later on when we actually
        // get remote targets
        mRemoteTargetHandles = createHandles(context, sizingStrategy, /* forDesktop = */ false,
                DEFAULT_NUM_HANDLES);
    }

    private RemoteTargetHandle[] createHandles(Context context,
            BaseContainerInterface sizingStrategy, boolean forDesktop, int numHandles) {
        RemoteTargetHandle[] handles = new RemoteTargetHandle[numHandles];
        for (int i = 0; i < numHandles; i++) {
            handles[i] = createHandle(context, sizingStrategy, forDesktop, i);
        }
        return handles;
    }

    private RemoteTargetHandle createHandle(Context context,
            BaseContainerInterface sizingStrategy, boolean forDesktop, int taskIndex) {
        TaskViewSimulator tvs = new TaskViewSimulator(
                context, sizingStrategy, forDesktop , taskIndex);
        TransformParams transformParams = new TransformParams();
        return new RemoteTargetHandle(tvs, transformParams);
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
            SplitBounds splitBounds) {
        mSplitBounds = splitBounds;
        return assignTargetsForSplitScreen(targets);
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this assigns the
     * apps in {@code targets.apps} to the {@link #mRemoteTargetHandles} with index 0 will being
     * the left/top task, index 1 right/bottom.
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets) {
        // If we are in a true split screen case (2 apps running on screen), either:
        //     a) mSplitBounds was already set (from the clicked GroupedTaskView)
        //     b) A SplitBounds was passed up from shell (via AbsSwipeUpHandler)
        // If both of these are null, we are in a 1-app or 1-app-plus-assistant case.
        if (mSplitBounds == null && targets.extras != null
                && targets.extras.containsKey(KEY_EXTRA_SPLIT_BOUNDS)) {
            mSplitBounds = targets.extras.getParcelable(KEY_EXTRA_SPLIT_BOUNDS, SplitBounds.class);
        }

        boolean containsSplitTargets = mSplitBounds != null;
        Log.d(TAG, "containsSplitTargets? " + containsSplitTargets + " handleLength: " +
                mRemoteTargetHandles.length + " appsLength: " + targets.apps.length);

        if (mRemoteTargetHandles.length == 1) {
            resizeRemoteTargetHandles(targets);
            // Single fullscreen app

            // If we're not in split screen, the splitIds count doesn't really matter since we
            // should always hit this case.
            setRemoteTargetHandle(targets,
                    targets.apps.length > 0 ? targets.apps[0] : null,
                    /* targetsToExclude = */ null, /* transitionInfo = */ null,
                    /* splitBounds = */ null, /* taskIndex = */ 0);
        } else if (!containsSplitTargets) {
            resizeRemoteTargetHandles(targets);
            // Single App + Assistant
            for (int i = 0; i < mRemoteTargetHandles.length; i++) {
                setRemoteTargetHandle(targets, targets.apps[i], /* targetsToExclude = */ null,
                        /* transitionInfo = */ null, /* splitBounds = */ null, /* taskIndex = */ i);
            }
        } else if (mSplitBounds != null) {
            setSplitRemoteTargetHandles(targets);
        }
        return mRemoteTargetHandles;
    }

    private void setSplitRemoteTargetHandles(RemoteAnimationTargets targets) {
        // Split apps (+ maybe assistant)
        final List<Integer> leftTopTargetIds = mSplitBounds.leftTopTaskIds;
        final List<Integer> rightBottomTargetIds = mSplitBounds.rightBottomTaskIds;
        if (leftTopTargetIds.isEmpty() || rightBottomTargetIds.isEmpty()) {
            throw new IllegalStateException("The target ids is invalid: mSplitBounds = "
                    + mSplitBounds);
        }
        final List<RemoteAnimationTarget> leftTopTargets =
                CollectionsKt.mapNotNull(leftTopTargetIds, targets::findTask);
        final List<RemoteAnimationTarget> rightBottomTargets =
                CollectionsKt.mapNotNull(rightBottomTargetIds, targets::findTask);

        final List<RemoteAnimationTarget> overlayTargets = Arrays.stream(targets.apps).filter(
                target -> isOverlayTarget(target, leftTopTargets,
                        rightBottomTargets)).toList();
        final int handleCount = leftTopTargets.size() + rightBottomTargets.size()
                + overlayTargets.size();
        if (handleCount > targets.apps.length) {
            throw new IllegalStateException("Attempting to animate app count:" + handleCount
                    + "but the total app count: " + targets.apps.length);
        }
        if (handleCount > mRemoteTargetHandles.length) {
            throw new IllegalStateException("Attempting to animate app count:" + handleCount
                    + "but the max handle count: " + mRemoteTargetHandles.length);
        }
        if (handleCount < mRemoteTargetHandles.length) {
            reduceRemoteTargetHandles(handleCount);
        }

        int taskIndex = 0;
        for (final RemoteAnimationTarget target : leftTopTargets) {
            setRemoteTargetHandle(targets, target, rightBottomTargets, /* transitionInfo = */ null,
                    mSplitBounds, taskIndex++);
        }
        for (final RemoteAnimationTarget target : rightBottomTargets) {
            setRemoteTargetHandle(targets, target, leftTopTargets, /* transitionInfo = */ null,
                    mSplitBounds, taskIndex++);
        }
        // Set the remaining overlay tasks to be their own TaskViewSimulator as fullscreen tasks
        if (!overlayTargets.isEmpty()) {
            List<RemoteAnimationTarget> targetsToExclude = new ArrayList<>(leftTopTargets);
            targetsToExclude.addAll(rightBottomTargets);
            for (final RemoteAnimationTarget target : overlayTargets) {
                setRemoteTargetHandle(targets, target, targetsToExclude,
                        /* transitionInfo = */ null, /* splitBounds = */ null, taskIndex++);
            }
        }
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this creates distinct
     * transform params per app in {@code targets.apps} list.
     */
    public RemoteTargetHandle[] assignTargetsForDesktop(
            RemoteAnimationTargets targets, @Nullable TransitionInfo transitionInfo) {
        resizeRemoteTargetHandles(targets);

        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            List<RemoteAnimationTarget> excludeTargets = Arrays.stream(targets.apps)
                    .filter(target -> target.taskId != primaryTaskTarget.taskId).collect(toList());
            setRemoteTargetHandle(targets, primaryTaskTarget, excludeTargets, transitionInfo,
                    /* splitBounds = */ null, i);
        }
        return mRemoteTargetHandles;
    }

    private boolean isOverlayTarget(@NonNull RemoteAnimationTarget target,
            @NonNull List<RemoteAnimationTarget> leftTopTargets,
            @NonNull List<RemoteAnimationTarget> rightBottomTargets) {
        return target.windowConfiguration.getWindowingMode()
                != WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
                && !leftTopTargets.contains(target)
                && !rightBottomTargets.contains(target);
    }

    /**
     * Resize the `mRemoteTargetHandles` array since we assumed initial size, but
     * `targets.apps` is the ultimate source of truth here
     */
    private void resizeRemoteTargetHandles(RemoteAnimationTargets targets) {
        int handleCount = (int) Arrays.stream(targets.apps)
                .filter(app -> app.mode == targets.targetMode)
                .count();
        Log.d(TAG, "appCount: " + handleCount + " handleLength: " + mRemoteTargetHandles.length);
        if (handleCount < mRemoteTargetHandles.length) {
            reduceRemoteTargetHandles(handleCount);
        }
    }

    /**
     * Reduces the number of remote target handles to a specified count.
     * The caller is responsible for ensuring that the target {@code handleCount}
     * is always less than the current number of remote target handles
     * ({@link #mRemoteTargetHandles}'s current size).
     *
     * @param handleCount The desired number of remote target handles after reduction.
     * This value should be non-negative and less than the current size of the handle list.
     */
    private void reduceRemoteTargetHandles(int handleCount) {
        Log.d(TAG, "Reduce handles, count: " + handleCount);
        RemoteTargetHandle[] newHandles = new RemoteTargetHandle[(int) handleCount];
        System.arraycopy(mRemoteTargetHandles, 0/*src*/, newHandles, 0/*dst*/, (int) handleCount);
        mRemoteTargetHandles = newHandles;
    }

    private void setRemoteTargetHandle(@NonNull RemoteAnimationTargets targets,
            @Nullable RemoteAnimationTarget target,
            @Nullable List<RemoteAnimationTarget> targetsToExclude,
            @Nullable TransitionInfo transitionInfo,
            @Nullable SplitBounds splitBounds, int taskIndex) {
        if (targetsToExclude != null) {
            mRemoteTargetHandles[taskIndex].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, targetsToExclude));
        } else {
            mRemoteTargetHandles[taskIndex].mTransformParams.setTargetSet(targets);
        }
        if (transitionInfo != null) {
            mRemoteTargetHandles[taskIndex].mTransformParams.setTransitionInfo(transitionInfo);
        }
        if (target != null) {
            mRemoteTargetHandles[taskIndex].mTaskViewSimulator.setPreview(target, splitBounds);
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
