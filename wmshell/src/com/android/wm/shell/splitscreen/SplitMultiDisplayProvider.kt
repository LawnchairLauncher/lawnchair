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

package com.android.wm.shell.splitscreen

import android.window.WindowContainerToken
import android.window.WindowContainerTransaction

interface SplitMultiDisplayProvider {
    /**
     * Returns the WindowContainerToken for the root of the given display ID.
     *
     * @param displayId The ID of the display.
     * @return The {@link WindowContainerToken} associated with the display's root task.
     */
    fun getDisplayRootForDisplayId(displayId: Int): WindowContainerToken?

    /**
     * Prepares to reparent the split-screen root to another display if the target task
     * resides on a different display. This is used to move the entire split-screen container
     * to the display where the user interaction is occurring. It only adds the reparent
     * operation to the given {@code wct} without executing it.
     *
     * @param wct The transaction to add the reparent operation to.
     * @param displayId The ID of the target display.
     */
    fun prepareMovingSplitScreenRoot(wct: WindowContainerTransaction?, displayId: Int)
}
