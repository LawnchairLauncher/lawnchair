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

import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayTopology
import android.hardware.display.DisplayTopologyGraph
import android.hardware.display.DisplayTopologyGraph.AdjacentDisplay
import android.hardware.display.DisplayTopologyGraph.DisplayNode
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import com.android.wm.shell.sysui.ShellInit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.times
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Test class for [VisualIndicatorUpdateScheduler]
 *
 * Usage: atest WMShellUnitTests:VisualIndicatorUpdateSchedulerTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
class VisualIndicatorUpdateSchedulerTest : ShellTestCase() {
    private var mockDisplayController = mock<DisplayController>()
    private var mockIndicator = mock<DesktopModeVisualIndicator>()
    private var mockDisplayTopology = mock<DisplayTopology>()
    private var mockDisplayTopologyGraph = mock<DisplayTopologyGraph>()

    @Captor
    private lateinit var displayListenerCaptor:
        ArgumentCaptor<DisplayController.OnDisplaysChangedListener>

    private lateinit var scheduler: VisualIndicatorUpdateScheduler
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)
    private lateinit var shellInit: ShellInit

    private val delayMillis = 800L // Match the constant in the class
    private val displayId0 = 0
    private val displayId1 = 1
    private val displayId3 = 3

    private val taskBounds = Rect(100, 100, 500, 500)
    private val differentTaskBounds = Rect(110, 110, 510, 510)
    private val similarTaskBounds = Rect(102, 102, 502, 502)

    // With the test data from MultiDisplayTestUtil, we are using three displays here:
    //   +---+
    //   | 1 |
    // +-+---+-+---+-+
    // |   0   | 3 |
    // +-------+   |
    //         +---+

    private val adjacentDisplay1To0 =
        AdjacentDisplay(displayId1, DisplayTopology.TreeNode.POSITION_TOP, 100f)
    private val adjacentDisplay3To0 =
        AdjacentDisplay(displayId3, DisplayTopology.TreeNode.POSITION_RIGHT, 0f)

    private val adjacentDisplay0To1 =
        AdjacentDisplay(displayId0, DisplayTopology.TreeNode.POSITION_BOTTOM, -200f)

    private val adjacentDisplay0To3 =
        AdjacentDisplay(displayId0, DisplayTopology.TreeNode.POSITION_LEFT, 0f)

    private val displayNodes =
        listOf(
            DisplayNode(
                displayId0,
                TestDisplay.DISPLAY_0.dpi,
                TestDisplay.DISPLAY_0.bounds,
                arrayOf(adjacentDisplay1To0, adjacentDisplay3To0),
            ),
            DisplayNode(
                displayId1,
                TestDisplay.DISPLAY_1.dpi,
                TestDisplay.DISPLAY_1.bounds,
                arrayOf(adjacentDisplay0To1),
            ),
            DisplayNode(
                displayId3,
                TestDisplay.DISPLAY_3.dpi,
                TestDisplay.DISPLAY_3.bounds,
                arrayOf(adjacentDisplay0To3),
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shellInit = ShellInit(TestShellExecutor())

        scheduler =
            VisualIndicatorUpdateScheduler(
                shellInit = shellInit,
                mainDispatcher = testDispatcher,
                bgScope = testScope,
                displayController = mockDisplayController,
            )

        val resources = mContext.getOrCreateTestableResources()
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        resources.overrideConfiguration(resourceConfiguration)
        val spyDisplayLayout0 = TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        val spyDisplayLayout1 = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)
        val spyDisplayLayout3 = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)

        shellInit.init()
        verify(mockDisplayController).addDisplayWindowListener(displayListenerCaptor.capture())

        whenever(mockDisplayController.getDisplayLayout(displayId0)).thenReturn(spyDisplayLayout0)
        whenever(mockDisplayController.getDisplayLayout(displayId1)).thenReturn(spyDisplayLayout1)
        whenever(mockDisplayController.getDisplayLayout(displayId3)).thenReturn(spyDisplayLayout3)
        whenever(mockDisplayTopology.getGraph()).thenReturn(mockDisplayTopologyGraph)
        whenever(mockDisplayTopologyGraph.displayNodes).thenReturn(displayNodes)
        displayListenerCaptor.value.onTopologyChanged(mockDisplayTopology)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun init_registersDisplayListener() {
        assert(displayListenerCaptor.value != null)
    }

    @Test
    fun onTopologyChanged_validTopology_updatesGraph() {
        val newTopology = mock<DisplayTopology>()
        val newGraph = mock<DisplayTopologyGraph>()
        whenever(newTopology.getGraph()).thenReturn(newGraph)

        displayListenerCaptor.value.onTopologyChanged(newTopology)

        verify(newTopology).getGraph()
    }

    @Test
    fun scheduleVisualIndicator_indicatorUpdateTypeNotCrossDisplay_updatesImmediately() =
        runTest(testDispatcher) {
            val type = DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR
            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                inputX = 10f,
                inputY = 10f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator,
            )

            runCurrent()

            verify(mockIndicator).updateIndicatorWithType(type)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }

    @Test
    fun scheduleVisualIndicatorUpdate_notNearAdjacentBorder_updatesImmediately() =
        runTest(testDispatcher) {
            val type = DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR
            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 50f,
                inputY = 10f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator,
            )

            runCurrent()

            verify(mockIndicator).updateIndicatorWithType(type)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }

    @Test
    fun scheduleVisualIndicatorUpdateOnExternalDisplay_notNearAdjacentBorder_updatesImmediately() =
        runTest(testDispatcher) {
            val type = DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR

            scheduler.schedule(
                displayId = displayId3,
                indicatorType = type,
                inputX = 200f,
                // Display#3's left edge aligns with Display#0's right edge along Display#3's
                // Y-coordinates [0 , 200]px.
                inputY = 250f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator,
            )

            runCurrent()

            verify(mockIndicator).updateIndicatorWithType(type)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }

    @Test
    fun scheduleUpdateVisualIndicator_potentialCrossDisplayDrag_boundsNotChanged_continuePreviousJob() =
        runTest(testDispatcher) {
            val mockIndicator1 = mock<DesktopModeVisualIndicator>()
            val mockIndicator2 = mock<DesktopModeVisualIndicator>()
            val type = DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR

            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 150f,
                inputY = 10f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator1,
            )

            advanceTimeBy(delayMillis / 2)
            runCurrent()

            assert(testScope.coroutineContext[Job]?.children?.count() == 1)
            verifyNoInteractions(mockIndicator1)

            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 150f,
                inputY = 10f,
                taskBounds = similarTaskBounds,
                visualIndicator = mockIndicator2,
            )

            advanceTimeBy(delayMillis * 2)
            runCurrent()

            verify(mockIndicator1, times(1)).updateIndicatorWithType(type)
            verify(mockIndicator2, never()).updateIndicatorWithType(type)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }

    @Test
    fun scheduleUpdateVisualIndicator_potentialCrossDisplayDrag_boundsChanged_cancelsPreviousJob() =
        runTest(testDispatcher) {
            val mockIndicator1 = mock<DesktopModeVisualIndicator>()
            val mockIndicator2 = mock<DesktopModeVisualIndicator>()
            val type = DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR

            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 150f,
                inputY = 10f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator1,
            )

            advanceTimeBy(delayMillis / 2)
            runCurrent()

            assert(testScope.coroutineContext[Job]?.children?.count() == 1)
            val firstJob = testScope.coroutineContext[Job]?.children?.first()
            verifyNoInteractions(mockIndicator1)

            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 150f,
                inputY = 10f,
                taskBounds = differentTaskBounds, // Significant change
                visualIndicator = mockIndicator2,
            )

            assert(firstJob?.isCancelled == true)
            assert(testScope.coroutineContext[Job]?.children?.count() == 1)

            advanceTimeBy(delayMillis)
            runCurrent()

            verify(mockIndicator1, never()).updateIndicatorWithType(type)
            verify(mockIndicator2, times(1)).updateIndicatorWithType(type)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }

    @Test
    fun scheduleUpdateVisualIndicator_potentialCrossDisplayDrag_indicatorTypeChange_cancelsPreviousJob() =
        runTest(testDispatcher) {
            val mockIndicator1 = mock<DesktopModeVisualIndicator>()
            val mockIndicator2 = mock<DesktopModeVisualIndicator>()
            val type1 = DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR
            val type2 = DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR

            scheduler.schedule(
                displayId = displayId0,
                indicatorType = type1,
                // Display#1's bottom edge aligns with Display#0's top edge along Display#0's
                // X-coordinates [100, 1100]px.
                inputX = 150f,
                inputY = 10f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator1,
            )
            assert(testScope.coroutineContext[Job]?.children?.count() == 1)
            val firstJob = testScope.coroutineContext[Job]?.children?.first()

            scheduler.schedule(
                displayId = displayId3,
                indicatorType = type2,
                inputX = 200f,
                // Display#3's left edge aligns with Display#0's right edge along Display#3's
                // Y-coordinates [0, 200]px.
                inputY = 150f,
                taskBounds = taskBounds,
                visualIndicator = mockIndicator2,
            )

            assert(firstJob?.isCancelled == true)
            assert(testScope.coroutineContext[Job]?.children?.count() == 1)
            advanceTimeBy(delayMillis)
            runCurrent()

            verify(mockIndicator1, never()).updateIndicatorWithType(type1)
            verify(mockIndicator2, times(1)).updateIndicatorWithType(type2)
            assert(testScope.coroutineContext[Job]?.children?.count() == 0)
        }
}
