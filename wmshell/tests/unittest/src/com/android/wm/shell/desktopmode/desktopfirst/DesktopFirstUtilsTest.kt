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

package com.android.wm.shell.desktopmode.desktopfirst

import android.testing.AndroidTestingRunner
import android.window.DisplayAreaInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopFirstUtils]
 *
 * Usage: atest WMShellUnitTests:DesktopFirstUtilsTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopFirstUtilsTest : ShellTestCase() {
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val desktopFirstTDA = DisplayAreaInfo(MockToken().token(), DESKTOP_FIRST_DISPLAY_ID, 0)
    private val touchFirstTDA = DisplayAreaInfo(MockToken().token(), TOUCH_FIRST_DISPLAY_ID, 0)

    @Before
    fun setUp() {
        desktopFirstTDA.configuration.windowConfiguration.windowingMode =
            DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        touchFirstTDA.configuration.windowConfiguration.windowingMode =
            TOUCH_FIRST_DISPLAY_WINDOWING_MODE
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DESKTOP_FIRST_DISPLAY_ID))
            .thenReturn(desktopFirstTDA)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(TOUCH_FIRST_DISPLAY_ID))
            .thenReturn(touchFirstTDA)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(UNKNOWN_DISPLAY_ID))
            .thenReturn(null)
    }

    @Test
    fun isDisplayDesktopFirst() {
        assertTrue(rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(DESKTOP_FIRST_DISPLAY_ID))
        assertFalse(rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(TOUCH_FIRST_DISPLAY_ID))
        assertFalse(rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(UNKNOWN_DISPLAY_ID))
    }

    companion object {
        const val DESKTOP_FIRST_DISPLAY_ID = 100
        const val TOUCH_FIRST_DISPLAY_ID = 200
        const val UNKNOWN_DISPLAY_ID = 999
    }
}
