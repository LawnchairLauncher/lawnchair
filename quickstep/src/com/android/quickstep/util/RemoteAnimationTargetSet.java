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
package com.android.quickstep.util;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.ArrayList;

/**
 * Holds a collection of RemoteAnimationTargets, filtered by different properties.
 */
public class RemoteAnimationTargetSet {

    public final RemoteAnimationTargetCompat[] unfilteredApps;
    public final RemoteAnimationTargetCompat[] apps;
    public final int targetMode;

    public RemoteAnimationTargetSet(RemoteAnimationTargetCompat[] apps, int targetMode) {
        ArrayList<RemoteAnimationTargetCompat> filteredApps = new ArrayList<>();
        if (apps != null) {
            for (RemoteAnimationTargetCompat target : apps) {
                if (target.mode == targetMode) {
                    filteredApps.add(target);
                }
            }
        }

        this.unfilteredApps = apps;
        this.apps = filteredApps.toArray(new RemoteAnimationTargetCompat[filteredApps.size()]);
        this.targetMode = targetMode;
    }

    public RemoteAnimationTargetCompat findTask(int taskId) {
        for (RemoteAnimationTargetCompat target : apps) {
            if (target.taskId == taskId) {
                return target;
            }
        }
        return null;
    }

    public boolean isAnimatingHome() {
        for (RemoteAnimationTargetCompat target : apps) {
            if (target.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                return true;
            }
        }
        return false;
    }
}
