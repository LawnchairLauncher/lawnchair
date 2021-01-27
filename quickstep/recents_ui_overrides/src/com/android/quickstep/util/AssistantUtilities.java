/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT;

import android.annotation.TargetApi;
import android.app.TaskInfo;
import android.content.Intent;
import android.os.Build;

import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskInfoCompat;

/**
 * Utility class for interacting with the Assistant.
 */
@TargetApi(Build.VERSION_CODES.Q)
public final class AssistantUtilities {

    /** Returns true if an Assistant activity that is excluded from recents is running. */
    public static boolean isExcludedAssistantRunning() {
        return isExcludedAssistant(ActivityManagerWrapper.getInstance().getRunningTask());
    }

    /** Returns true if the given task holds an Assistant activity that is excluded from recents. */
    public static boolean isExcludedAssistant(TaskInfo info) {
        return info != null
            && TaskInfoCompat.getActivityType(info) == ACTIVITY_TYPE_ASSISTANT
            && (info.baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
    }

    private AssistantUtilities() {}
}
