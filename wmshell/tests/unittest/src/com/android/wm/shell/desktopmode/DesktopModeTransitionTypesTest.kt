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

package com.android.wm.shell.desktopmode

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.APP_FROM_OVERVIEW
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.KEYBOARD_SHORTCUT
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.TASK_DRAG
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.UNKNOWN
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.getEnterTransitionType
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.getExitTransitionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [DesktopModeTransitionTypes]
 *
 * Usage: atest WMShellUnitTests:DesktopModeTransitionTypesTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopModeTransitionTypesTest {

    @Test
    fun testGetEnterTransitionType() {
        assertThat(UNKNOWN.getEnterTransitionType()).isEqualTo(TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN)
        assertThat(APP_HANDLE_MENU_BUTTON.getEnterTransitionType())
            .isEqualTo(TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON)
        assertThat(APP_FROM_OVERVIEW.getEnterTransitionType())
            .isEqualTo(TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW)
        assertThat(TASK_DRAG.getEnterTransitionType())
            .isEqualTo(TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN)
        assertThat(KEYBOARD_SHORTCUT.getEnterTransitionType())
            .isEqualTo(TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT)
    }

    @Test
    fun testGetExitTransitionType() {
        assertThat(UNKNOWN.getExitTransitionType()).isEqualTo(TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN)
        assertThat(APP_HANDLE_MENU_BUTTON.getExitTransitionType())
            .isEqualTo(TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON)
        assertThat(APP_FROM_OVERVIEW.getExitTransitionType())
            .isEqualTo(TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN)
        assertThat(TASK_DRAG.getExitTransitionType()).isEqualTo(TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG)
        assertThat(KEYBOARD_SHORTCUT.getExitTransitionType())
            .isEqualTo(TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT)
    }
}
