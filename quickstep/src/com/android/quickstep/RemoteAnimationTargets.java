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
package com.android.quickstep;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;

import android.os.Bundle;
import android.view.RemoteAnimationTarget;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds a collection of RemoteAnimationTargets, filtered by different properties.
 */
public class RemoteAnimationTargets {

    private final CopyOnWriteArrayList<ReleaseCheck> mReleaseChecks = new CopyOnWriteArrayList<>();

    public final RemoteAnimationTarget[] unfilteredApps;
    public final RemoteAnimationTarget[] apps;
    public final RemoteAnimationTarget[] wallpapers;
    public final RemoteAnimationTarget[] nonApps;
    public final Bundle extras;
    public final int targetMode;
    public final boolean hasRecents;

    private boolean mReleased = false;

    public RemoteAnimationTargets(RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
            int targetMode, Bundle extras) {
        ArrayList<RemoteAnimationTarget> filteredApps = new ArrayList<>();
        boolean hasRecents = false;
        if (apps != null) {
            for (RemoteAnimationTarget target : apps) {
                if (target.mode == targetMode) {
                    filteredApps.add(target);
                }

                hasRecents |= target.windowConfiguration.getActivityType() == ACTIVITY_TYPE_RECENTS;
            }
        }

        this.unfilteredApps = apps;
        this.apps = filteredApps.toArray(new RemoteAnimationTarget[filteredApps.size()]);
        this.wallpapers = wallpapers;
        this.targetMode = targetMode;
        this.hasRecents = hasRecents;
        this.nonApps = nonApps;
        this.extras = extras;
    }

    public RemoteAnimationTargets(RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
            int targetMode) {
        this(apps, wallpapers, nonApps, targetMode, new Bundle());
    }

    public RemoteAnimationTarget findTask(int taskId) {
        for (RemoteAnimationTarget target : apps) {
            if (target.taskId == taskId) {
                return target;
            }
        }
        return null;
    }

    /**
     * Gets the navigation bar remote animation target if exists.
     */
    public RemoteAnimationTarget getNavBarRemoteAnimationTarget() {
        return getNonAppTargetOfType(TYPE_NAVIGATION_BAR);
    }

    public RemoteAnimationTarget getNonAppTargetOfType(int type) {
        for (RemoteAnimationTarget target : nonApps) {
            if (target.windowType == type) {
                return target;
            }
        }
        return null;
    }

    /** Returns the first opening app target. */
    public RemoteAnimationTarget getFirstAppTarget() {
        return apps.length > 0 ? apps[0] : null;
    }

    /** Returns the task id of the first opening app target, or -1 if none is found. */
    public int getFirstAppTargetTaskId() {
        RemoteAnimationTarget target = getFirstAppTarget();
        return target == null ? -1 : target.taskId;
    }

    public boolean isAnimatingHome() {
        for (RemoteAnimationTarget target : unfilteredApps) {
            if (target.windowConfiguration.getActivityType() == ACTIVITY_TYPE_HOME) {
                return true;
            }
        }
        return false;
    }

    public void addReleaseCheck(ReleaseCheck check) {
        mReleaseChecks.add(check);
    }

    public void release() {
        if (mReleased) {
            return;
        }
        for (ReleaseCheck check : mReleaseChecks) {
            if (!check.mCanRelease) {
                check.addOnSafeToReleaseCallback(this::release);
                return;
            }
        }
        mReleaseChecks.clear();
        mReleased = true;
        release(unfilteredApps);
        release(wallpapers);
        release(nonApps);
    }

    private static void release(RemoteAnimationTarget[] targets) {
        for (RemoteAnimationTarget target : targets) {
            if (target.leash != null) {
                target.leash.release();
            }
            if (target.startLeash != null) {
                target.startLeash.release();
            }
        }
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "RemoteAnimationTargets:");

        pw.println(prefix + "\ttargetMode=" + targetMode);
        pw.println(prefix + "\thasRecents=" + hasRecents);
        pw.println(prefix + "\tmReleased=" + mReleased);
    }

    /**
     * Interface for intercepting surface release method
     */
    public static class ReleaseCheck {

        boolean mCanRelease = false;
        private Runnable mAfterApplyCallback;

        protected void setCanRelease(boolean canRelease) {
            mCanRelease = canRelease;
            if (mCanRelease && mAfterApplyCallback != null) {
                Runnable r = mAfterApplyCallback;
                mAfterApplyCallback = null;
                r.run();
            }
        }

        /**
         * Adds a callback to notify when the surface can safely be released
         */
        void addOnSafeToReleaseCallback(Runnable callback) {
            if (mCanRelease) {
                callback.run();
            } else {
                if (mAfterApplyCallback == null) {
                    mAfterApplyCallback = callback;
                } else {
                    final Runnable oldCallback = mAfterApplyCallback;
                    mAfterApplyCallback = () -> {
                        callback.run();
                        oldCallback.run();
                    };
                }
            }
        }
    }
}
