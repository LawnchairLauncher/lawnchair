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
package com.android.wm.shell.desktopmode

import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.desktopmode.FakeDesktopState

/** Fake [ShellDesktopState] for testing. */
class FakeShellDesktopState(fakeDesktopState: FakeDesktopState) :
    ShellDesktopState, DesktopState by fakeDesktopState {
    /**
     * Change whether or not the displays are eligible as window drop target.
     *
     * This will be the default value for all displays. To change the value for a particular
     * display, update [overrideWindowDropTargetEligibility].
     */
    var canBeWindowDropTarget: Boolean = false

    /** Override [canBeWindowDropTarget] for a specific display. */
    val overrideWindowDropTargetEligibility = mutableMapOf<Int, Boolean>()

    /**
     * This implementation returns [canBeWindowDropTarget] unless overridden in
     * [overrideWindowDropTargetEligibility].
     */
    override fun isEligibleWindowDropTarget(displayId: Int): Boolean =
        overrideWindowDropTargetEligibility[displayId] ?: canBeWindowDropTarget
}
