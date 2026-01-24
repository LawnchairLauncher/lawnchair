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
package com.android.wm.shell.desktopmode.multidesks

/** Handle requests to preserve a desktop display. */
fun interface PreserveDisplayRequestHandler {
    /**
     * Called when a request is received to preserve a display and the desks on it before the
     * display itself is removed.
     *
     * @param displayId the display to preserve
     */
    fun requestPreserveDisplay(displayId: Int)
}
