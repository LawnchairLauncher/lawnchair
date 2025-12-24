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

package com.android.wm.shell.windowdecor

/** Interface to create, check status of, and update hover status of maximize menu. */
interface MaximizeMenuController {
    /** Returns whether the maximize menu is currently open. */
    val isMaximizeMenuActive: Boolean

    /** Updates whether the maximize button is being hovered over. */
    fun onMaximizeHoverStateChanged()

    /** Notifies that the maximize button has received a [ACTION_HOVER_ENTER] event. */
    fun onMaximizeButtonHoverExit()

    /** Notifies that the maximize button has received a [ACTION_HOVER_ENTER] event. */
    fun onMaximizeButtonHoverEnter()

    /** Creates the maximize menu if supported by caption. */
    fun createMaximizeMenu()

    /** Updates on whether the maximize button is being hovered over. */
    fun setAppHeaderMaximizeButtonHovered(hovered: Boolean)
}
