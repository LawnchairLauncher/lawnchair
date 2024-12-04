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

package com.android.wm.shell.startingsurface;

/**
 * Listener interface that Launcher attaches to SystemUI to get
 * callbacks when need a new starting window.
 */
interface IStartingWindowListener {
    /**
     * Notifies when Shell going to create a new starting window.
     * @param taskId The task Id
     * @param supportedType The starting window type
     * @param splashScreenBackgroundColor The splash screen's background color
     */
    oneway void onTaskLaunching(int taskId, int supportedType, int splashScreenBackgroundColor);
}
