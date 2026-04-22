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
import android.window.WindowContainerTransaction;

/**
 * Listener interface that Launcher attaches to SystemUI to get split-select callbacks.
 */
interface ISplitSelectListener {
    /**
     * Called when a task requests to enter split select.
     *
     * @param taskInfo the task requesting to enter split select
     * @param splitPosition the position in which to place it
     * @param taskBounds the bounds of the task prior to entering split select
     * @param startRecents whether Launcher should start recents prior to entering split select
     * @param withRecentsWct the wct to include with the recents start, if appplicable
     */
    boolean onRequestSplitSelect(in RunningTaskInfo taskInfo,
        int splitPosition, in Rect taskBounds, boolean startRecents,
        in @nullable WindowContainerTransaction withRecentsWct);
}