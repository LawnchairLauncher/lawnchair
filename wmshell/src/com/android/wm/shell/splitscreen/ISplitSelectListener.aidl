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

package com.android.wm.shell.splitscreen;

import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Rect;
/**
 * Listener interface that Launcher attaches to SystemUI to get split-select callbacks.
 */
interface ISplitSelectListener {
    /**
     * Called when a task requests to enter split select
     */
    boolean onRequestSplitSelect(in RunningTaskInfo taskInfo, int splitPosition, in Rect taskBounds);
}