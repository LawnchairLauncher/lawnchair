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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;

import java.util.ArrayList;

/**
 * @see RemoteAnimationTarget
 */
public class RemoteAnimationTargetCompat {

    public static final int MODE_OPENING = RemoteAnimationTarget.MODE_OPENING;
    public static final int MODE_CLOSING = RemoteAnimationTarget.MODE_CLOSING;
    public static final int MODE_CHANGING = RemoteAnimationTarget.MODE_CHANGING;
    public final int mode;

    public static final int ACTIVITY_TYPE_UNDEFINED = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
    public static final int ACTIVITY_TYPE_STANDARD = WindowConfiguration.ACTIVITY_TYPE_STANDARD;
    public static final int ACTIVITY_TYPE_HOME = WindowConfiguration.ACTIVITY_TYPE_HOME;
    public static final int ACTIVITY_TYPE_RECENTS = WindowConfiguration.ACTIVITY_TYPE_RECENTS;
    public static final int ACTIVITY_TYPE_ASSISTANT = WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
    public final int activityType;

    public final int taskId;
    public final SurfaceControlCompat leash;
    public final boolean isTranslucent;
    public final Rect clipRect;
    public final int prefixOrderIndex;
    public final Point position;
    public final Rect localBounds;
    public final Rect sourceContainerBounds;
    public final Rect screenSpaceBounds;
    public final boolean isNotInRecents;
    public final Rect contentInsets;
    public final ActivityManager.RunningTaskInfo taskInfo;
    public final int rotationChange;
    public final int windowType;

    private final SurfaceControl mStartLeash;

    public RemoteAnimationTargetCompat(RemoteAnimationTarget app) {
        taskId = app.taskId;
        mode = app.mode;
        leash = new SurfaceControlCompat(app.leash);
        isTranslucent = app.isTranslucent;
        clipRect = app.clipRect;
        position = app.position;
        localBounds = app.localBounds;
        sourceContainerBounds = app.sourceContainerBounds;
        screenSpaceBounds = app.screenSpaceBounds;
        prefixOrderIndex = app.prefixOrderIndex;
        isNotInRecents = app.isNotInRecents;
        contentInsets = app.contentInsets;
        activityType = app.windowConfiguration.getActivityType();
        taskInfo = app.taskInfo;
        rotationChange = 0;

        mStartLeash = app.startLeash;
        windowType = app.windowType;
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
                return 2; // MODE_CHANGING
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

        // Don't move anything that isn't independent within its parents
        if (!TransitionInfo.isIndependent(change, info)) {
            if (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT || mode == TRANSIT_CHANGE) {
                t.show(leash);
                t.setPosition(leash, change.getEndRelOffset().x, change.getEndRelOffset().y);
            }
            return;
        }

        boolean hasParent = change.getParent() != null;

        if (!hasParent) {
            t.reparent(leash, info.getRootLeash());
            t.setPosition(leash, change.getStartAbsBounds().left - info.getRootOffset().x,
                    change.getStartAbsBounds().top - info.getRootOffset().y);
        }
        t.show(leash);
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
                .setContainerLayer().setParent(change.getParent() == null ? info.getRootLeash()
                        : info.getChange(change.getParent()).getLeash()).build();
        // Copied Transitions setup code (which expects bottom-to-top order, so we swap here)
        setupLeash(leashSurface, change, info.getChanges().size() - order, info, t);
        t.reparent(change.getLeash(), leashSurface);
        t.setAlpha(change.getLeash(), 1.0f);
        t.show(change.getLeash());
        t.setPosition(change.getLeash(), 0, 0);
        t.setLayer(change.getLeash(), 0);
        return leashSurface;
    }

    public RemoteAnimationTargetCompat(TransitionInfo.Change change, int order,
            TransitionInfo info, SurfaceControl.Transaction t) {
        taskId = change.getTaskInfo() != null ? change.getTaskInfo().taskId : -1;
        mode = newModeToLegacyMode(change.getMode());

        // TODO: once we can properly sync transactions across process, then get rid of this leash.
        leash = new SurfaceControlCompat(createLeash(info, change, order, t));

        isTranslucent = (change.getFlags() & TransitionInfo.FLAG_TRANSLUCENT) != 0
                || (change.getFlags() & TransitionInfo.FLAG_SHOW_WALLPAPER) != 0;
        clipRect = null;
        position = null;
        localBounds = new Rect(change.getEndAbsBounds());
        localBounds.offsetTo(change.getEndRelOffset().x, change.getEndRelOffset().y);
        sourceContainerBounds = null;
        screenSpaceBounds = new Rect(change.getEndAbsBounds());
        prefixOrderIndex = order;
        // TODO(shell-transitions): I guess we need to send content insets? evaluate how its used.
        contentInsets = new Rect(0, 0, 0, 0);
        if (change.getTaskInfo() != null) {
            isNotInRecents = !change.getTaskInfo().isRunning;
            activityType = change.getTaskInfo().getActivityType();
        } else {
            isNotInRecents = true;
            activityType = ACTIVITY_TYPE_UNDEFINED;
        }
        taskInfo = change.getTaskInfo();
        mStartLeash = null;
        rotationChange = change.getEndRotation() - change.getStartRotation();
        windowType = INVALID_WINDOW_TYPE;
    }

    public static RemoteAnimationTargetCompat[] wrap(RemoteAnimationTarget[] apps) {
        final int length = apps != null ? apps.length : 0;
        final RemoteAnimationTargetCompat[] appsCompat = new RemoteAnimationTargetCompat[length];
        for (int i = 0; i < length; i++) {
            appsCompat[i] = new RemoteAnimationTargetCompat(apps[i]);
        }
        return appsCompat;
    }

    /**
     * Represents a TransitionInfo object as an array of old-style targets
     *
     * @param wallpapers If true, this will return wallpaper targets; otherwise it returns
     *                   non-wallpaper targets.
     * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should be
     *                 populated by this function. If null, it is ignored.
     */
    public static RemoteAnimationTargetCompat[] wrap(TransitionInfo info, boolean wallpapers,
            SurfaceControl.Transaction t, ArrayMap<SurfaceControl, SurfaceControl> leashMap) {
        final ArrayList<RemoteAnimationTargetCompat> out = new ArrayList<>();
        for (int i = 0; i < info.getChanges().size(); i++) {
            boolean changeIsWallpaper =
                    (info.getChanges().get(i).getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0;
            if (wallpapers != changeIsWallpaper) continue;
            out.add(new RemoteAnimationTargetCompat(info.getChanges().get(i),
                    info.getChanges().size() - i, info, t));
            if (leashMap == null) continue;
            leashMap.put(info.getChanges().get(i).getLeash(),
                    out.get(out.size() - 1).leash.mSurfaceControl);
        }
        return out.toArray(new RemoteAnimationTargetCompat[out.size()]);
    }

    /**
     * @see SurfaceControl#release()
     */
    public void release() {
        leash.mSurfaceControl.release();
        if (mStartLeash != null) {
            mStartLeash.release();
        }
    }
}
