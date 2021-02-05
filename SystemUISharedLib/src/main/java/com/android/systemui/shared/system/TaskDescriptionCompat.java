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

import android.app.ActivityManager;
import android.graphics.Bitmap;

public class TaskDescriptionCompat {

    private ActivityManager.TaskDescription mTaskDescription;

    public TaskDescriptionCompat(ActivityManager.TaskDescription td) {
        mTaskDescription = td;
    }

    public int getPrimaryColor() {
        return mTaskDescription != null
                ? mTaskDescription.getPrimaryColor()
                : 0;
    }

    public int getBackgroundColor() {
        return mTaskDescription != null
                ? mTaskDescription.getBackgroundColor()
                : 0;
    }

    public static Bitmap getIcon(ActivityManager.TaskDescription desc, int userId) {
        if (desc.getInMemoryIcon() != null) {
            return desc.getInMemoryIcon();
        }
        return ActivityManager.TaskDescription.loadTaskDescriptionIcon(
                desc.getIconFilename(), userId);
    }
}
