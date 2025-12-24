/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.windowdecor.viewholder

/**
 * Listener for receiving updates about the current list of application handles. Defined as a nested
 * functional interface within [AppHandleManager]. Being a 'fun interface' allows SAM (Single
 * Abstract Method) conversion for lambda usage.
 */
fun interface AppHandlePositionCallback {

    /**
     * Called when the list of current application handles changes or is updated.
     *
     * Implementations should expect this callback to occur on the same thread of the Executor that
     * was passed in when adding this listener. See [AppHandles.addListener]
     *
     * @param handles The new map of [AppHandleIdentifier] objects key'd by taskId.
     *
     *     TODO(b/417194560): Make AppHandle key'd on both taskId and displayId. The map is never
     *       null, may be empty.
     */
    fun onAppHandlesUpdated(handles: Map<Int, AppHandleIdentifier>)
}
