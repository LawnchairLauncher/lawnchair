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

import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds a collection of RemoteAnimationTargets, filtered by different properties.
 */
public class RemoteAnimationTargets {

    private final CopyOnWriteArrayList<ReleaseCheck> mReleaseChecks = new CopyOnWriteArrayList<>();

    public final RemoteAnimationTargetCompat[] unfilteredApps;
    public final RemoteAnimationTargetCompat[] apps;
    public final RemoteAnimationTargetCompat[] wallpapers;
    public final RemoteAnimationTargetCompat[] nonApps;
    public final int targetMode;
    public final boolean hasRecents;

    private boolean mReleased = false;

    public RemoteAnimationTargets(RemoteAnimationTargetCompat[] apps,
            RemoteAnimationTargetCompat[] wallpapers, RemoteAnimationTargetCompat[] nonApps,
            int targetMode) {
        ArrayList<RemoteAnimationTargetCompat> filteredApps = new ArrayList<>();
        boolean hasRecents = false;
        if (apps != null) {
            for (RemoteAnimationTargetCompat target : apps) {
                if (target.mode == targetMode) {
                    filteredApps.add(target);
                }

                hasRecents |= target.activityType ==
                        RemoteAnimationTargetCompat.ACTIVITY_TYPE_RECENTS;
            }
        }

        this.unfilteredApps = apps;
        this.apps = filteredApps.toArray(new RemoteAnimationTargetCompat[filteredApps.size()]);
        this.wallpapers = wallpapers;
        this.targetMode = targetMode;
        this.hasRecents = hasRecents;
        this.nonApps = nonApps;
    }

    public RemoteAnimationTargetCompat findTask(int taskId) {
        for (RemoteAnimationTargetCompat target : apps) {
            if (target.taskId == taskId) {
                return target;
            }
        }
        return null;
    }

    /**
     * Gets the navigation bar remote animation target if exists.
     */
    public RemoteAnimationTargetCompat getNavBarRemoteAnimationTarget() {
        return getNonAppTargetOfType(TYPE_NAVIGATION_BAR);
    }

    public RemoteAnimationTargetCompat getNonAppTargetOfType(int type) {
        for (RemoteAnimationTargetCompat target : nonApps) {
            if (target.windowType == type) {
                return target;
            }
        }
        return null;
    }

    /** Returns the first opening app target. */
    public RemoteAnimationTargetCompat getFirstAppTarget() {
        return apps.length > 0 ? apps[0] : null;
    }

    /** Returns the task id of the first opening app target, or -1 if none is found. */
    public int getFirstAppTargetTaskId() {
        RemoteAnimationTargetCompat target = getFirstAppTarget();
        return target == null ? -1 : target.taskId;
    }

    public boolean isAnimatingHome() {
        for (RemoteAnimationTargetCompat target : unfilteredApps) {
            if (target.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
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

        for (RemoteAnimationTargetCompat target : unfilteredApps) {
            target.release();
        }
        for (RemoteAnimationTargetCompat target : wallpapers) {
            target.release();
        }
        for (RemoteAnimationTargetCompat target : nonApps) {
            target.release();
        }
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
