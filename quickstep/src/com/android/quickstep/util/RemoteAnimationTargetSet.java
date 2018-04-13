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

    public final boolean allTransparent;
    public final RemoteAnimationTargetCompat[] apps;

    public RemoteAnimationTargetSet(RemoteAnimationTargetCompat[] apps, int targetMode) {
        boolean allTransparent = true;

        ArrayList<RemoteAnimationTargetCompat> filteredApps = new ArrayList<>();
        if (apps != null) {
            for (RemoteAnimationTargetCompat target : apps) {
                if (target.mode == targetMode) {
                    allTransparent &= target.isTranslucent;
                    filteredApps.add(target);
                }
            }
        }

        this.allTransparent = allTransparent;
        this.apps = filteredApps.toArray(new RemoteAnimationTargetCompat[filteredApps.size()]);
    }

    public RemoteAnimationTargetCompat findTask(int taskId) {
        for (RemoteAnimationTargetCompat target : apps) {
            if (target.taskId == taskId) {
                return target;
            }
        }
        return null;
    }
}
