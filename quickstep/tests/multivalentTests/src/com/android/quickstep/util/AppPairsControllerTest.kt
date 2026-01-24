/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.quickstep.util

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.res.Resources
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.TopTaskTracker
import com.android.quickstep.TopTaskTracker.CachedTaskInfo
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_33_66
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_66_33
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppPairsControllerTest {
    @Mock lateinit var context: ActivityContext
    @Mock lateinit var resources: Resources
    @Mock lateinit var splitSelectStateController: SplitSelectStateController
    @Mock lateinit var statsLogManager: StatsLogManager

    private lateinit var appPairsController: AppPairsController

    private val left33: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_2_33_66)
    }
    private val left50: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50)
    }
    private val left66: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_2_66_33)
    }
    private val right33: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_2_33_66)
    }
    private val right50: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_2_50_50)
    }
    private val right66: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_2_66_33)
    }

    @Mock lateinit var mockAppPairIcon: AppPairIcon
    @Mock lateinit var mockDisplay: Display
    @Mock lateinit var mockTaskbarActivityContext: TaskbarActivityContext
    @Mock lateinit var mockTopTaskTracker: TopTaskTracker
    @Mock lateinit var mockCachedTaskInfo: CachedTaskInfo
    @Mock lateinit var mockItemInfo1: ItemInfo
    @Mock lateinit var mockItemInfo2: ItemInfo
    @Mock lateinit var mockTask1: Task
    @Mock lateinit var mockTask2: Task
    @Mock lateinit var mockTaskKey1: TaskKey
    @Mock lateinit var mockTaskKey2: TaskKey
    @Captor lateinit var callbackCaptor: ArgumentCaptor<Consumer<Array<Task>>>

    private lateinit var spyAppPairsController: AppPairsController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        appPairsController =
            AppPairsController(context, splitSelectStateController, statsLogManager)

        // Stub methods on appPairsController so that they return mocks
        spyAppPairsController = spy(appPairsController)
        whenever(mockAppPairIcon.context).thenReturn(mockTaskbarActivityContext)
        whenever(mockAppPairIcon.display).thenReturn(mockDisplay)
        whenever(mockDisplay.displayId).thenReturn(DEFAULT_DISPLAY)
        doReturn(mockTopTaskTracker).whenever(spyAppPairsController).topTaskTracker
        whenever(mockTopTaskTracker.getCachedTopTask(any(), any())).thenReturn(mockCachedTaskInfo)
        whenever(mockTask1.getKey()).thenReturn(mockTaskKey1)
        whenever(mockTask2.getKey()).thenReturn(mockTaskKey2)
        doNothing().whenever(spyAppPairsController).launchAppPair(any(), any())
        doNothing()
            .whenever(spyAppPairsController)
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        whenever(mockAppPairIcon.context.resources).thenReturn(resources)
        whenever(DesktopModeStatus.canEnterDesktopMode(mockAppPairIcon.context)).thenReturn(false)
    }

    @Test
    fun shouldEncodeRankCorrectly() {
        assertEquals("left + 33-66 should encode as 0 (0b0)", 0, left33)
        assertEquals("left + 50-50 should encode as 1 (0b1)", 1, left50)
        assertEquals("left + 66-33 should encode as 2 (0b10)", 2, left66)
        // See AppPairsController#BITMASK_SIZE and BITMASK_FOR_SNAP_POSITION for context
        assertEquals("right + 33-66 should encode as 1 followed by 16 0s", 1 shl 16, right33)
        assertEquals("right + 50-50 should encode as the above value + 1", (1 shl 16) + 1, right50)
        assertEquals("right + 66-33 should encode as the above value + 2", (1 shl 16) + 2, right66)
    }

    @Test
    fun shouldDecodeRankCorrectly() {
        assertEquals(
            "left + 33-66 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left33),
        )
        assertEquals(
            "left + 33-66 should decode to 33-66",
            SNAP_TO_2_33_66,
            AppPairsController.convertRankToSnapPosition(left33),
        )

        assertEquals(
            "left + 50-50 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left50),
        )
        assertEquals(
            "left + 50-50 should decode to 50-50",
            SNAP_TO_2_50_50,
            AppPairsController.convertRankToSnapPosition(left50),
        )

        assertEquals(
            "left + 66-33 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left66),
        )
        assertEquals(
            "left + 66-33 should decode to 66-33",
            SNAP_TO_2_66_33,
            AppPairsController.convertRankToSnapPosition(left66),
        )

        assertEquals(
            "right + 33-66 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right33),
        )
        assertEquals(
            "right + 33-66 should decode to 33-66",
            SNAP_TO_2_33_66,
            AppPairsController.convertRankToSnapPosition(right33),
        )

        assertEquals(
            "right + 50-50 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right50),
        )
        assertEquals(
            "right + 50-50 should decode to 50-50",
            SNAP_TO_2_50_50,
            AppPairsController.convertRankToSnapPosition(right50),
        )

        assertEquals(
            "right + 66-33 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right66),
        )
        assertEquals(
            "right + 66-33 should decode to 66-33",
            SNAP_TO_2_66_33,
            AppPairsController.convertRankToSnapPosition(right66),
        )
    }

    @Test
    fun handleAppPairLaunchInApp_shouldDoNothingWhenAppsAreAlreadyRunning() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 1 and 2 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(1, 2).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchAppPair and launchToSide were never called
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, never())
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp2ToRightWhenApp1IsOnLeft() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 1 and 3 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(1, 3).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_BOTTOM_OR_RIGHT))
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp2ToLeftWhenApp1IsOnRight() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 3 and 1 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(3, 1).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_TOP_OR_LEFT))
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp1ToRightWhenApp2IsOnLeft() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 2 and 3 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(2, 3).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_BOTTOM_OR_RIGHT))
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp1ToLeftWhenApp2IsOnRight() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 3 and 2 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(3, 2).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_TOP_OR_LEFT))
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchAppPairNormallyWhenUnrelatedPairIsOnScreen() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with apps 3 and 4 already on screen
        whenever(mockTopTaskTracker.runningSplitTaskIds).thenReturn(arrayListOf(3, 4).toIntArray())

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchAppPair was called
        verify(spyAppPairsController, times(1)).launchAppPair(any(), any())
        verify(spyAppPairsController, never())
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp2ToRightWhenApp1IsFullscreen() {
        /// Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with app 1 already on screen
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            whenever(mockCachedTaskInfo.topGroupedTaskContainsTask(eq(1))).thenReturn(true)
        } else {
            whenever(mockCachedTaskInfo.taskId).thenReturn(1)
        }

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_BOTTOM_OR_RIGHT))
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchApp1ToLeftWhenApp2IsFullscreen() {
        /// Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with app 2 already on screen
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            whenever(mockCachedTaskInfo.topGroupedTaskContainsTask(eq(2))).thenReturn(true)
        } else {
            whenever(mockCachedTaskInfo.taskId).thenReturn(2)
        }

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchToSide was called with the correct arguments
        verify(spyAppPairsController, never()).launchAppPair(any(), any())
        verify(spyAppPairsController, times(1))
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), eq(STAGE_POSITION_TOP_OR_LEFT))
    }

    @Test
    fun handleAppPairLaunchInApp_freeformTask1IsOnScreen_shouldLaunchAppPair() {
        whenever(DesktopModeStatus.canEnterDesktopMode(mockAppPairIcon.context)).thenReturn(true)
        /// Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // Task 1 is in freeform windowing mode
        mockTaskKey1.windowingMode = WINDOWING_MODE_FREEFORM
        // ... and app 1 is already on screen
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            whenever(mockCachedTaskInfo.topGroupedTaskContainsTask(eq(1))).thenReturn(true)
        } else {
            whenever(mockCachedTaskInfo.taskId).thenReturn(1)
        }

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchAppPair was called
        verify(spyAppPairsController, times(1)).launchAppPair(any(), any())
        verify(spyAppPairsController, never())
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun handleAppPairLaunchInApp_freeformTask2IsOnScreen_shouldLaunchAppPair() {
        whenever(DesktopModeStatus.canEnterDesktopMode(mockAppPairIcon.context)).thenReturn(true)
        /// Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // Task 2 is in freeform windowing mode
        mockTaskKey1.windowingMode = WINDOWING_MODE_FREEFORM
        // ... and app 2 is already on screen
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            whenever(mockCachedTaskInfo.topGroupedTaskContainsTask(eq(2))).thenReturn(true)
        } else {
            whenever(mockCachedTaskInfo.taskId).thenReturn(2)
        }

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchAppPair was called
        verify(spyAppPairsController, times(1)).launchAppPair(any(), any())
        verify(spyAppPairsController, never())
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun handleAppPairLaunchInApp_shouldLaunchAppPairNormallyWhenUnrelatedSingleAppIsFullscreen() {
        // Test launching apps 1 and 2 from app pair
        whenever(mockTaskKey1.getId()).thenReturn(1)
        whenever(mockTaskKey2.getId()).thenReturn(2)
        // ... with app 3 already on screen
        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
            whenever(mockCachedTaskInfo.topGroupedTaskContainsTask(eq(3))).thenReturn(true)
        } else {
            whenever(mockCachedTaskInfo.taskId).thenReturn(3)
        }

        // Trigger app pair launch, capture and run callback from findLastActiveTasksAndRunCallback
        spyAppPairsController.handleAppPairLaunchInApp(
            mockAppPairIcon,
            listOf(mockItemInfo1, mockItemInfo2),
        )
        verify(splitSelectStateController)
            .findLastActiveTasksAndRunCallback(any(), any(), callbackCaptor.capture())
        val callback: Consumer<Array<Task>> = callbackCaptor.value
        callback.accept(arrayOf(mockTask1, mockTask2))

        // Verify that launchAppPair was called
        verify(spyAppPairsController, times(1)).launchAppPair(any(), any())
        verify(spyAppPairsController, never())
            .launchToSide(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
}
