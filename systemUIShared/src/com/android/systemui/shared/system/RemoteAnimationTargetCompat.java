/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.shared.system;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.RemoteAnimationTarget.MODE_CHANGING;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;

import static com.android.wm.shell.common.split.SplitScreenConstants.FLAG_IS_DIVIDER_BAR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.SparseBooleanArray;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionInfo.Change;

import java.util.ArrayList;
import java.util.function.BiPredicate;

/**
 * Some utility methods for creating {@link RemoteAnimationTarget} instances.
 */
public class RemoteAnimationTargetCompat {

    private static int newModeToLegacyMode(int newMode) {
        switch (newMode) {
            case WindowManager.TRANSIT_OPEN:
            case WindowManager.TRANSIT_TO_FRONT:
                return MODE_OPENING;
            case WindowManager.TRANSIT_CLOSE:
            case WindowManager.TRANSIT_TO_BACK:
                return MODE_CLOSING;
            default:
                return MODE_CHANGING;
        }
    }

    /**
     * Almost a copy of Transitions#setupStartState.
     * TODO: remove when there is proper cross-process transaction sync.
     */
    @SuppressLint("NewApi")
    private static void setupLeash(@NonNull SurfaceControl leash,
            @NonNull TransitionInfo.Change change, int layer,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t) {
        boolean isOpening = info.getType() == TRANSIT_OPEN || info.getType() == TRANSIT_TO_FRONT;
        // Put animating stuff above this line and put static stuff below it.
        int zSplitLine = info.getChanges().size();
        // changes should be ordered top-to-bottom in z
        final int mode = change.getMode();

        t.reparent(leash, info.getRootLeash());
        final Rect absBounds =
                (mode == TRANSIT_OPEN) ? change.getEndAbsBounds() : change.getStartAbsBounds();
        t.setPosition(leash, absBounds.left - info.getRootOffset().x,
                absBounds.top - info.getRootOffset().y);

        // Put all the OPEN/SHOW on top
        if (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) {
            if (isOpening) {
                t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
                if ((change.getFlags() & FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT) == 0) {
                    // if transferred, it should be left visible.
                    t.setAlpha(leash, 0.f);
                }
            } else {
                // put on bottom and leave it visible
                t.setLayer(leash, zSplitLine - layer);
            }
        } else if (mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK) {
            if (isOpening) {
                // put on bottom and leave visible
                t.setLayer(leash, zSplitLine - layer);
            } else {
                // put on top
                t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
            }
        } else { // CHANGE
            t.setLayer(leash, zSplitLine + info.getChanges().size() - layer);
        }
    }

    @SuppressLint("NewApi")
    private static SurfaceControl createLeash(TransitionInfo info, TransitionInfo.Change change,
            int order, SurfaceControl.Transaction t) {
        // TODO: once we can properly sync transactions across process, then get rid of this leash.
        if (change.getParent() != null && (change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
            // Special case for wallpaper atm. Normally these are left alone; but, a quirk of
            // making leashes means we have to handle them specially.
            return change.getLeash();
        }
        SurfaceControl leashSurface = new SurfaceControl.Builder()
                .setName(change.getLeash().toString() + "_transition-leash")
                .setContainerLayer()
                // Initial the surface visible to respect the visibility of the original surface.
                .setHidden(false)
                .setParent(info.getRootLeash())
                .build();
        // Copied Transitions setup code (which expects bottom-to-top order, so we swap here)
        setupLeash(leashSurface, change, info.getChanges().size() - order, info, t);
        t.reparent(change.getLeash(), leashSurface);
        t.setAlpha(change.getLeash(), 1.0f);
        t.show(change.getLeash());
        t.setPosition(change.getLeash(), 0, 0);
        t.setLayer(change.getLeash(), 0);
        return leashSurface;
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change info
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            TransitionInfo info, SurfaceControl.Transaction t,
            @Nullable ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        int taskId;
        boolean isNotInRecents;
        ActivityManager.RunningTaskInfo taskInfo;
        WindowConfiguration windowConfiguration;

        taskInfo = change.getTaskInfo();
        if (taskInfo != null) {
            taskId = taskInfo.taskId;
            isNotInRecents = !taskInfo.isRunning;
            windowConfiguration = taskInfo.configuration.windowConfiguration;
        } else {
            taskId = INVALID_TASK_ID;
            isNotInRecents = true;
            windowConfiguration = new WindowConfiguration();
        }

        Rect localBounds = new Rect(change.getEndAbsBounds());
        localBounds.offsetTo(change.getEndRelOffset().x, change.getEndRelOffset().y);

        RemoteAnimationTarget target = new RemoteAnimationTarget(
                taskId,
                newModeToLegacyMode(change.getMode()),
                // TODO: once we can properly sync transactions across process,
                // then get rid of this leash.
                createLeash(info, change, order, t),
                (change.getFlags() & TransitionInfo.FLAG_TRANSLUCENT) != 0,
                null,
                // TODO(shell-transitions): we need to send content insets? evaluate how its used.
                new Rect(0, 0, 0, 0),
                order,
                null,
                localBounds,
                new Rect(change.getEndAbsBounds()),
                windowConfiguration,
                isNotInRecents,
                null,
                new Rect(change.getStartAbsBounds()),
                taskInfo,
                change.getAllowEnterPip(),
                (change.getFlags() & FLAG_IS_DIVIDER_BAR) != 0
                        ? TYPE_DOCK_DIVIDER : INVALID_WINDOW_TYPE
        );
//        target.setWillShowImeOnTarget(
//                (change.getFlags() & TransitionInfo.FLAG_WILL_IME_SHOWN) != 0);
//        target.setRotationChange(change.getEndRotation() - change.getStartRotation());
        if (leashMap != null) {
            leashMap.put(change.getLeash(), target.leash);
        }
        return target;
    }

    /**
     * Represents a TransitionInfo object as an array of old-style app targets
     *
     * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should be
     *                 populated by this function. If null, it is ignored.
     */
    public static RemoteAnimationTarget[] wrapApps(TransitionInfo info,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        SparseBooleanArray childTaskTargets = new SparseBooleanArray();
        return wrap(info, t, leashMap, (change, taskInfo) -> {
            // Children always come before parent since changes are in top-to-bottom z-order.
            if ((taskInfo == null) || childTaskTargets.get(taskInfo.taskId)) {
                // has children, so not a leaf. Skip.
                return false;
            }
            if (taskInfo.hasParentTask()) {
                childTaskTargets.put(taskInfo.parentTaskId, true);
            }
            return true;
        });
    }

    /**
     * Represents a TransitionInfo object as an array of old-style non-app targets
     *
     * @param wallpapers If true, this will return wallpaper targets; otherwise it returns
     *                   non-wallpaper targets.
     * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should be
     *                 populated by this function. If null, it is ignored.
     */
    public static RemoteAnimationTarget[] wrapNonApps(TransitionInfo info, boolean wallpapers,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        return wrap(info, t, leashMap, (change, taskInfo) -> (taskInfo == null));
    }

    private static RemoteAnimationTarget[] wrap(TransitionInfo info,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap,
            BiPredicate<Change, TaskInfo> filter) {
        final ArrayList<RemoteAnimationTarget> out = new ArrayList<>();
        for (int i = 0; i < info.getChanges().size(); i++) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (filter.test(change, change.getTaskInfo())) {
                out.add(newTarget(change, info.getChanges().size() - i, info, t, leashMap));
            }
        }
        return out.toArray(new RemoteAnimationTarget[out.size()]);
    }
}
