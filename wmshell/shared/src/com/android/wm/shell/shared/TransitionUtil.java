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

package com.android.wm.shell.shared;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.RemoteAnimationTarget.MODE_CHANGING;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_FIRST_CUSTOM;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.SparseBooleanArray;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;

import java.util.function.Predicate;

/** Various utility functions for transitions. */
public class TransitionUtil {
    /** Flag applied to a transition change to identify it as a divider bar for animation. */
    public static final int FLAG_IS_DIVIDER_BAR = FLAG_FIRST_CUSTOM;

    /** @return true if the transition was triggered by opening something vs closing something */
    public static boolean isOpeningType(@WindowManager.TransitionType int type) {
        return type == TRANSIT_OPEN
                || type == TRANSIT_TO_FRONT
                || type == TRANSIT_KEYGUARD_GOING_AWAY;
    }

    /** @return true if the transition was triggered by closing something vs opening something */
    public static boolean isClosingType(@WindowManager.TransitionType int type) {
        return type == TRANSIT_CLOSE || type == TRANSIT_TO_BACK;
    }

    /** Returns {@code true} if the transition is opening or closing mode. */
    public static boolean isOpenOrCloseMode(@TransitionInfo.TransitionMode int mode) {
        return isOpeningMode(mode) || isClosingMode(mode);
    }

    /** Returns {@code true} if the transition is opening mode. */
    public static boolean isOpeningMode(@TransitionInfo.TransitionMode int mode) {
        return mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT;
    }

    /** Returns {@code true} if the transition is closing mode. */
    public static boolean isClosingMode(@TransitionInfo.TransitionMode int mode) {
        return mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK;
    }

    /** Returns {@code true} if the transition has a display change. */
    public static boolean hasDisplayChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getMode() == TRANSIT_CHANGE && change.hasFlags(FLAG_IS_DISPLAY)) {
                return true;
            }
        }
        return false;
    }

    /** Returns `true` if `change` is a wallpaper. */
    public static boolean isWallpaper(TransitionInfo.Change change) {
        return (change.getTaskInfo() == null)
                && change.hasFlags(FLAG_IS_WALLPAPER)
                && !change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY);
    }

    /** Returns `true` if `change` is not an app window or wallpaper. */
    public static boolean isNonApp(TransitionInfo.Change change) {
        return (change.getTaskInfo() == null)
                && !change.hasFlags(FLAG_IS_WALLPAPER)
                && !change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY);
    }

    /** Returns `true` if `change` is the divider. */
    public static boolean isDividerBar(TransitionInfo.Change change) {
        return isNonApp(change) && change.hasFlags(FLAG_IS_DIVIDER_BAR);
    }

    /** Returns `true` if `change` is only re-ordering. */
    public static boolean isOrderOnly(TransitionInfo.Change change) {
        return change.getMode() == TRANSIT_CHANGE
                && (change.getFlags() & FLAG_MOVED_TO_TOP) != 0
                && change.getStartAbsBounds().equals(change.getEndAbsBounds())
                && (change.getLastParent() == null
                        || change.getLastParent().equals(change.getParent()));
    }

    /**
     * Filter that selects leaf-tasks only. THIS IS ORDER-DEPENDENT! For it to work properly, you
     * MUST call `test` in the same order that the changes appear in the TransitionInfo.
     */
    public static class LeafTaskFilter implements Predicate<TransitionInfo.Change> {
        private final SparseBooleanArray mChildTaskTargets = new SparseBooleanArray();

        @Override
        public boolean test(TransitionInfo.Change change) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null) return false;
            // Children always come before parent since changes are in top-to-bottom z-order.
            boolean hasChildren = mChildTaskTargets.get(taskInfo.taskId);
            if (taskInfo.hasParentTask()) {
                mChildTaskTargets.put(taskInfo.parentTaskId, true);
            }
            // If it has children, it's not a leaf.
            return !hasChildren;
        }
    }


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
     * Very similar to Transitions#setupAnimHierarchy but specialized for leashes.
     */
    @SuppressLint("NewApi")
    private static void setupLeash(@NonNull SurfaceControl leash,
            @NonNull TransitionInfo.Change change, int layer,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t) {
        final boolean isOpening = TransitionUtil.isOpeningType(info.getType());
        // Put animating stuff above this line and put static stuff below it.
        int zSplitLine = info.getChanges().size();
        // changes should be ordered top-to-bottom in z
        final int mode = change.getMode();

        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        t.reparent(leash, info.getRoot(rootIdx).getLeash());
        final Rect absBounds =
                (mode == TRANSIT_OPEN) ? change.getEndAbsBounds() : change.getStartAbsBounds();
        t.setPosition(leash, absBounds.left - info.getRoot(rootIdx).getOffset().x,
                absBounds.top - info.getRoot(rootIdx).getOffset().y);

        if (isDividerBar(change)) {
            if (isOpeningType(mode)) {
                t.setAlpha(leash, 0.f);
            }
            // Set the transition leash position to 0 in case the divider leash position being
            // taking down.
            t.setPosition(leash, 0, 0);
            t.setLayer(leash, Integer.MAX_VALUE);
            return;
        }

        // Put all the OPEN/SHOW on top
        if ((change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
            // Wallpaper is always at the bottom, opening wallpaper on top of closing one.
            if (mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT) {
                t.setLayer(leash, -zSplitLine + info.getChanges().size() - layer);
            } else {
                t.setLayer(leash, -zSplitLine - layer);
            }
        } else if (TransitionUtil.isOpeningType(mode)) {
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
        } else if (TransitionUtil.isClosingType(mode)) {
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
        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        SurfaceControl leashSurface = new SurfaceControl.Builder()
                .setName(change.getLeash().toString() + "_transition-leash")
                .setContainerLayer()
                // Initial the surface visible to respect the visibility of the original surface.
                .setHidden(false)
                .setParent(info.getRoot(rootIdx).getLeash())
                .build();
        // Copied Transitions setup code (which expects bottom-to-top order, so we swap here)
        setupLeash(leashSurface, change, info.getChanges().size() - order, info, t);
        t.reparent(change.getLeash(), leashSurface);
        t.setAlpha(change.getLeash(), 1.0f);
        t.show(change.getLeash());
        if (!isDividerBar(change)) {
            // For divider, don't modify its inner leash position when creating the outer leash
            // for the transition. In case the position being wrong after the transition finished.
            t.setPosition(change.getLeash(), 0, 0);
        }
        t.setLayer(change.getLeash(), 0);
        return leashSurface;
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change info
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            TransitionInfo info, SurfaceControl.Transaction t,
            @Nullable ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        return newTarget(change, order, false /* forceTranslucent */, info, t, leashMap);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change info
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            boolean forceTranslucent, TransitionInfo info, SurfaceControl.Transaction t,
            @Nullable ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        final SurfaceControl leash = createLeash(info, change, order, t);
        if (leashMap != null) {
            leashMap.put(change.getLeash(), leash);
        }
        return newTarget(change, order, leash, forceTranslucent);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change and leash
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            SurfaceControl leash) {
        return newTarget(change, order, leash, false /* forceTranslucent */);
    }

    /**
     * Creates a new RemoteAnimationTarget from the provided change and leash
     */
    public static RemoteAnimationTarget newTarget(TransitionInfo.Change change, int order,
            SurfaceControl leash, boolean forceTranslucent) {
        if (isDividerBar(change)) {
            return getDividerTarget(change, leash);
        }

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
                leash,
                forceTranslucent || (change.getFlags() & TransitionInfo.FLAG_TRANSLUCENT) != 0,
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
                change.isAllowEnterPip(),
                INVALID_WINDOW_TYPE
        );
        target.setWillShowImeOnTarget(
                (change.getFlags() & TransitionInfo.FLAG_WILL_IME_SHOWN) != 0);
        target.setRotationChange(change.getEndRotation() - change.getStartRotation());
        target.backgroundColor = change.getBackgroundColor();
        return target;
    }

    private static RemoteAnimationTarget getDividerTarget(TransitionInfo.Change change,
            SurfaceControl leash) {
        return new RemoteAnimationTarget(-1 /* taskId */, newModeToLegacyMode(change.getMode()),
                leash, false /* isTranslucent */, null /* clipRect */,
                null /* contentInsets */, Integer.MAX_VALUE /* prefixOrderIndex */,
                new android.graphics.Point(0, 0) /* position */, change.getStartAbsBounds(),
                change.getStartAbsBounds(), new WindowConfiguration(), true, null /* startLeash */,
                null /* startBounds */, null /* taskInfo */, false /* allowEnterPip */,
                TYPE_DOCK_DIVIDER);
    }

    /**
     * Finds the "correct" root idx for a change. The change's end display is prioritized, then
     * the start display. If there is no display, it will fallback on the 0th root in the
     * transition. There MUST be at-least 1 root in the transition (ie. it's not a no-op).
     */
    public static int rootIndexFor(@NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info) {
        int rootIdx = info.findRootIndex(change.getEndDisplayId());
        if (rootIdx >= 0) return rootIdx;
        rootIdx = info.findRootIndex(change.getStartDisplayId());
        if (rootIdx >= 0) return rootIdx;
        return 0;
    }

    /**
     * Gets the {@link TransitionInfo.Root} for the given {@link TransitionInfo.Change}.
     * @see #rootIndexFor(TransitionInfo.Change, TransitionInfo)
     */
    @NonNull
    public static TransitionInfo.Root getRootFor(@NonNull TransitionInfo.Change change,
            @NonNull TransitionInfo info) {
        return info.getRoot(rootIndexFor(change, info));
    }
}
