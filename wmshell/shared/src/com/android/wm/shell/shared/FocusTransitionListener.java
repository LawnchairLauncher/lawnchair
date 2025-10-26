/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.shared;

import com.android.wm.shell.shared.annotations.ExternalThread;

/**
 * Listener to get focus-related transition callbacks.
 */
@ExternalThread
public interface FocusTransitionListener {
    /**
     * Called when a transition changes the top, focused display.
     */
    default void onFocusedDisplayChanged(int displayId) {}

    /**
     * Called when the per-app or system-wide focus state has changed for a task.
     */
    default void onFocusedTaskChanged(int taskId, boolean isFocusedOnDisplay,
            boolean isFocusedGlobally) {}
}
