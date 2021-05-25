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
package com.android.launcher3.taskbar;

/**
 * Base class for providing different taskbar UI
 */
public class TaskbarUIController {

    public static final TaskbarUIController DEFAULT = new TaskbarUIController();

    /**
     * Pads the Hotseat to line up exactly with Taskbar's copy of the Hotseat.
     */
    public void alignRealHotseatWithTaskbar() { }

    protected void onCreate() { }

    protected void onDestroy() { }

    protected boolean isTaskbarTouchable() {
        return true;
    }

    protected void onImeVisible(TaskbarDragLayer container, boolean isVisible) {
        container.updateImeBarVisibilityAlpha(isVisible ? 1 : 0);
    }
}
