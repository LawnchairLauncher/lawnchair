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

import android.app.ActivityManager
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.enableMultiDisplaySplit
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.split.SplitLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [SplitMultiDisplayHelper].
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class SplitMultiDisplayHelperTests : ShellTestCase() {

    private lateinit var splitMultiDisplayHelper: SplitMultiDisplayHelper

    @Mock
    private lateinit var mockDisplayManager: DisplayManager
    @Mock
    private lateinit var mockSplitLayout: SplitLayout
    @Mock
    private lateinit var mockDisplay1: Display
    @Mock
    private lateinit var mockDisplay2: Display

    @Before
    fun setUp() {
        assumeTrue(enableMultiDisplaySplit())
        MockitoAnnotations.initMocks(this)
        mockDisplay1 = mockDisplayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: mock(Display::class.java)
        mockDisplay2 = mockDisplayManager.getDisplay(Display.DEFAULT_DISPLAY + 1) ?: mock(Display::class.java)

        `when`(mockDisplay1.displayId).thenReturn(Display.DEFAULT_DISPLAY)
        `when`(mockDisplay2.displayId).thenReturn(Display.DEFAULT_DISPLAY + 1)

        splitMultiDisplayHelper = SplitMultiDisplayHelper(mockDisplayManager)
    }

    @Test
    fun getDisplayIds_noDisplays_returnsEmptyList() {
        `when`(mockDisplayManager.displays).thenReturn(emptyArray())

        val displayIds = splitMultiDisplayHelper.getCachedOrSystemDisplayIds()

        assertThat(displayIds).isEmpty()
    }

    @Test
    fun getDisplayIds_singleDisplay_returnsCorrectId() {
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDisplay1))

        val displayIds = splitMultiDisplayHelper.getCachedOrSystemDisplayIds()

        assertThat(displayIds).containsExactly(Display.DEFAULT_DISPLAY)
    }

    @Test
    fun getDisplayIds_multiDisplays_returnsCorrectIds() {
        `when`(mockDisplayManager.displays).thenReturn(arrayOf(mockDisplay1, mockDisplay2))

        val displayIds = splitMultiDisplayHelper.getCachedOrSystemDisplayIds()

        assertThat(displayIds).containsExactly(Display.DEFAULT_DISPLAY, Display.DEFAULT_DISPLAY + 1)
    }

    @Test
    fun swapDisplayTaskHierarchy_validDisplays_swapsHierarchies() {
        val rootTaskInfo1 = ActivityManager.RunningTaskInfo().apply { taskId = 1 }
        val rootTaskInfo2 = ActivityManager.RunningTaskInfo().apply { taskId = 2 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY, rootTaskInfo1)
        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY + 1, rootTaskInfo2)

        splitMultiDisplayHelper.swapDisplayTaskHierarchy(Display.DEFAULT_DISPLAY, Display.DEFAULT_DISPLAY + 1)

        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY)).isEqualTo(rootTaskInfo2)
        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY + 1)).isEqualTo(rootTaskInfo1)
    }

    @Test
    fun swapDisplayTaskHierarchy_invalidFirstDisplayId_doesNothing() {
        val rootTaskInfo2 = ActivityManager.RunningTaskInfo().apply { taskId = 2 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY + 1, rootTaskInfo2)

        splitMultiDisplayHelper.swapDisplayTaskHierarchy(Display.INVALID_DISPLAY, Display.DEFAULT_DISPLAY + 1)

        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.INVALID_DISPLAY)).isNull()
        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY + 1)).isEqualTo(rootTaskInfo2)
    }

    @Test
    fun swapDisplayTaskHierarchy_invalidSecondDisplayId_doesNothing() {
        val rootTaskInfo1 = ActivityManager.RunningTaskInfo().apply { taskId = 1 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY, rootTaskInfo1)

        splitMultiDisplayHelper.swapDisplayTaskHierarchy(Display.DEFAULT_DISPLAY, Display.INVALID_DISPLAY)

        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY)).isEqualTo(rootTaskInfo1)
        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.INVALID_DISPLAY)).isNull()
    }

    @Test
    fun swapDisplayTaskHierarchy_sameDisplayId_doesNothing() {
        val rootTaskInfo1 = ActivityManager.RunningTaskInfo().apply { taskId = 1 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY, rootTaskInfo1)

        splitMultiDisplayHelper.swapDisplayTaskHierarchy(Display.DEFAULT_DISPLAY, Display.DEFAULT_DISPLAY)

        assertThat(splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY)).isEqualTo(rootTaskInfo1)
    }

    @Test
    fun getDisplayRootTaskInfo_validDisplayId_returnsRootTaskInfo() {
        val rootTaskInfo = ActivityManager.RunningTaskInfo().apply { taskId = 123 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY, rootTaskInfo)

        val retrievedRootTaskInfo = splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY)

        assertThat(retrievedRootTaskInfo).isEqualTo(rootTaskInfo)
    }

    @Test
    fun getDisplayRootTaskInfo_invalidDisplayId_returnsNull() {
        val retrievedRootTaskInfo = splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.INVALID_DISPLAY)

        assertThat(retrievedRootTaskInfo).isNull()
    }

    @Test
    fun setDisplayRootTaskInfo_setsRootTaskInfo() {
        val rootTaskInfo = ActivityManager.RunningTaskInfo().apply { taskId = 456 }

        splitMultiDisplayHelper.setDisplayRootTaskInfo(Display.DEFAULT_DISPLAY, rootTaskInfo)
        val retrievedRootTaskInfo = splitMultiDisplayHelper.getDisplayRootTaskInfo(Display.DEFAULT_DISPLAY)

        assertThat(retrievedRootTaskInfo).isEqualTo(rootTaskInfo)
    }
}