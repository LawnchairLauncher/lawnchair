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
package com.android.wm.shell.pip2.phone

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.pip.PipBoundsState
import com.android.wm.shell.common.pip.PipDisplayLayoutState
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.pip2.phone.PipTransitionState.SCHEDULED_BOUNDS_CHANGE
import com.android.wm.shell.pip2.phone.PipTransitionState.UNDEFINED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.testing.TestableResources
import android.util.ArrayMap
import android.view.Display
import android.view.SurfaceControl
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.ORIGIN_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.TARGET_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipTransition.PIP_DESTINATION_BOUNDS
import com.android.wm.shell.pip2.phone.PipTransition.PIP_START_TX
import com.android.wm.shell.pip2.phone.PipTransitionState.CHANGING_PIP_BOUNDS
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import com.google.common.truth.Truth.assertThat
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import com.android.wm.shell.common.pip.PipBoundsAlgorithm
import com.android.wm.shell.pip2.animation.PipResizeAnimator
import com.android.wm.shell.pip2.phone.PipTransitionState.EXITED_PIP
import com.android.wm.shell.pip2.phone.PipTransitionState.EXITING_PIP
import org.junit.Rule
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never

/**
 * Unit test against [PipDisplayTransferHandler].
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS)
class PipDisplayTransferHandlerTest : ShellTestCase() {
    private val mockPipDisplayLayoutState = mock<PipDisplayLayoutState>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockPipTransitionState = mock<PipTransitionState>()
    private val mockPipScheduler = mock<PipScheduler>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockPipBoundsState = mock<PipBoundsState>()
    private val mockPipBoundsAlgorithm = mock<PipBoundsAlgorithm>()
    private val mockTaskInfo = mock<ActivityManager.RunningTaskInfo>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockLeash = mock<SurfaceControl>()
    private val mockFactory = mock<PipSurfaceTransactionHelper.SurfaceControlTransactionFactory>()
    private val mockSurfaceTransactionHelper = mock<PipSurfaceTransactionHelper>()
    private val mockPipResizeAnimator = mock<PipResizeAnimator>()

    private lateinit var testableResources: TestableResources
    private lateinit var resources: Resources
    private lateinit var defaultTda: DisplayAreaInfo
    private lateinit var pipDisplayTransferHandler: PipDisplayTransferHandler

    private val displayIds = intArrayOf(ORIGIN_DISPLAY_ID, TARGET_DISPLAY_ID, SECONDARY_DISPLAY_ID)
    private val displayLayouts = ArrayMap<Int, DisplayLayout>()
    private val mockBounds = Rect()

    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(SurfaceControl::class.java)
            .build()!!

    @Before
    fun setUp() {
        testableResources = mContext.getOrCreateTestableResources()
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        testableResources.overrideConfiguration(resourceConfiguration)
        resources = testableResources.resources

        whenever(resources.getDimensionPixelSize(R.dimen.pip_corner_radius)).thenReturn(
            TEST_CORNER_RADIUS
        )
        whenever(resources.getDimensionPixelSize(R.dimen.pip_shadow_radius)).thenReturn(
            TEST_SHADOW_RADIUS
        )
        whenever(SurfaceControl.mirrorSurface(any())).thenReturn(mockLeash)
        whenever(mockPipTransitionState.pinnedTaskLeash).thenReturn(mockLeash)
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockTaskInfo.getDisplayId()).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(mockPipDisplayLayoutState.displayId).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(mockTransaction.remove(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.show(any())).thenReturn(mockTransaction)
        whenever(mockFactory.transaction).thenReturn(mockTransaction)
        whenever(mockPipBoundsState.bounds).thenReturn(mockBounds)
        whenever(
            mockSurfaceTransactionHelper.setPipTransformations(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(mockSurfaceTransactionHelper)
        whenever(
            mockSurfaceTransactionHelper.round(
                any(),
                any(),
                any()
            )
        ).thenReturn(mockSurfaceTransactionHelper)
        defaultTda =
            DisplayAreaInfo(mock<WindowContainerToken>(), ORIGIN_DISPLAY_ID, /* featureId = */ 0)
        whenever(mockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(ORIGIN_DISPLAY_ID)).thenReturn(
            defaultTda
        )
        whenever(mockRootTaskDisplayAreaOrganizer.displayIds).thenReturn(displayIds)

        for (id in displayIds) {
            val display = mock<Display>()
            whenever(mockDisplayController.getDisplay(id)).thenReturn(display)
            val displayLayout =
                TestDisplay.entries.find { it.id == id }?.getSpyDisplayLayout(resources)
            displayLayouts.put(id, displayLayout)
            whenever(mockDisplayController.getDisplayLayout(id)).thenReturn(displayLayout)
        }

        pipDisplayTransferHandler =
            PipDisplayTransferHandler(
                mContext, mockPipTransitionState, mockPipScheduler,
                mockRootTaskDisplayAreaOrganizer, mockPipBoundsState, mockDisplayController,
                mockPipDisplayLayoutState, mockPipBoundsAlgorithm, mockSurfaceTransactionHelper
            )
        pipDisplayTransferHandler.setSurfaceControlTransactionFactory(mockFactory)
        pipDisplayTransferHandler.setSurfaceTransactionHelper(mockSurfaceTransactionHelper)
        pipDisplayTransferHandler.setPipResizeAnimatorSupplier {
                context, pipSurfaceTransactionHelper, leash, startTx, finishTx, baseBounds,
                startBounds, endBounds, duration, delta -> mockPipResizeAnimator
        }
    }

    @Test
    fun scheduleMovePipToDisplay_setsTransitionState() {
        pipDisplayTransferHandler.scheduleMovePipToDisplay(
            ORIGIN_DISPLAY_ID,
            TARGET_DISPLAY_ID,
            DESTINATION_BOUNDS
        )

        verify(mockPipTransitionState).setState(eq(SCHEDULED_BOUNDS_CHANGE), any())
    }

    @Test
    fun onPipTransitionStateChanged_schedulingBoundsChange_triggersPipScheduler() {
        val extra = Bundle()
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, ORIGIN_DISPLAY_ID)
        extra.putInt(TARGET_DISPLAY_ID_KEY, TARGET_DISPLAY_ID)
        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            SCHEDULED_BOUNDS_CHANGE,
            extra
        )

        verify(mockPipScheduler).scheduleMoveToDisplay(eq(TARGET_DISPLAY_ID), anyOrNull())
    }

    @Test
    fun onPipTransitionStateChanged_schedulingBoundsChange_withinSameDisplay_doesNotScheduleMove() {
        val extra = Bundle()
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, ORIGIN_DISPLAY_ID)
        extra.putInt(TARGET_DISPLAY_ID_KEY, ORIGIN_DISPLAY_ID)
        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            SCHEDULED_BOUNDS_CHANGE,
            extra
        )

        verify(mockPipScheduler, never()).scheduleMoveToDisplay(any(), anyOrNull())
    }

    @Test
    fun onPipTransitionStateChanged_changingPipBounds_changesPipTransitionStates() {
        val extra = Bundle()
        val destinationBounds = Rect(0, 0, 100, 100)
        extra.putParcelable(PIP_START_TX, SurfaceControl.Transaction())
        extra.putParcelable(PIP_DESTINATION_BOUNDS, destinationBounds)
        pipDisplayTransferHandler.mWaitingForDisplayTransfer = true
        pipDisplayTransferHandler.mTargetDisplayId = TARGET_DISPLAY_ID

        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            CHANGING_PIP_BOUNDS,
            extra
        )

        verify(mockPipBoundsAlgorithm).snapToMovementBoundsEdge(
            eq(destinationBounds),
            eq(displayLayouts.get(TARGET_DISPLAY_ID))
        )
        verify(mockPipTransitionState).state = eq(EXITING_PIP)
        verify(mockPipTransitionState).state = eq(EXITED_PIP)
        verify(mockPipResizeAnimator).start()
    }

    @Test
    fun onPipTransitionStateChanged_exitedPip_removesMirrors() {
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId = ArrayMap()
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.apply {
            put(0, mockLeash)
            put(1, mockLeash)
            put(2, mockLeash)
        }

        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            EXITED_PIP,
            null
        )

        verify(mockTransaction, times(3)).remove(any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_hasNotLeftOriginDisplay_shouldNotCreateMirrors() {
        val globalDpBounds = MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
            displayLayouts.get(ORIGIN_DISPLAY_ID)!!, START_DRAG_COORDINATES,
            PIP_BOUNDS, displayLayouts.get(ORIGIN_DISPLAY_ID)!!, 150f, 150f)
        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
             globalDpBounds, ORIGIN_DISPLAY_ID
        )

        verify(mockRootTaskDisplayAreaOrganizer, never()).reparentToDisplayArea(
            any(), any(), any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
        verify(mockSurfaceTransactionHelper, never()).setPipTransformations(
            any(), any(), any(), any(), any()
        )
        verify(mockSurfaceTransactionHelper, never()).setMirrorTransformations(any(), any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.isMirrorShown()).isFalse()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_completelyMovedToAnotherDisplay_noMirrorCreated() {
        val globalDpBounds = MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
            displayLayouts.get(ORIGIN_DISPLAY_ID)!!, START_DRAG_COORDINATES,
            PIP_BOUNDS, displayLayouts.get(TARGET_DISPLAY_ID)!!,
            TestDisplay.DISPLAY_1.bounds.centerX(), TestDisplay.DISPLAY_1.bounds.centerY())

        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
             globalDpBounds, TARGET_DISPLAY_ID
        )

        verify(mockRootTaskDisplayAreaOrganizer, never()).reparentToDisplayArea(
            any(), any(), any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
        verify(mockSurfaceTransactionHelper, never()).setPipTransformations(
            any(), any(), any(), any(), any()
        )
        verify(mockSurfaceTransactionHelper, never()).setMirrorTransformations(any(), any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.isMirrorShown()).isFalse()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_partiallyMovedToAnotherDisplay_createsOneMirror() {
        val globalDpBounds = MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
            displayLayouts.get(ORIGIN_DISPLAY_ID)!!, START_DRAG_COORDINATES,
            PIP_BOUNDS, displayLayouts.get(TARGET_DISPLAY_ID)!!,
            TestDisplay.DISPLAY_1.bounds.centerX(), TestDisplay.DISPLAY_1.bounds.centerY()
        )

        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
            globalDpBounds, ORIGIN_DISPLAY_ID
        )

        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(TARGET_DISPLAY_ID),
            any(),
            any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.size).isEqualTo(1)
        assertThat(
            pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(
                TARGET_DISPLAY_ID
            )
        ).isTrue()
        verify(mockSurfaceTransactionHelper, times(1)).setPipTransformations(
            any(),
            any(),
            any(),
            any(),
            any()
        )
        verify(mockSurfaceTransactionHelper, times(1)).setMirrorTransformations(any(), any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.isMirrorShown()).isTrue()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_inBetweenThreeDisplays_createsTwoMirrors() {
        val globalDpBounds = MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
            displayLayouts.get(ORIGIN_DISPLAY_ID)!!, START_DRAG_COORDINATES,
            PIP_BOUNDS, displayLayouts.get(TARGET_DISPLAY_ID)!!,
            1000f, -100f)

        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
            globalDpBounds, ORIGIN_DISPLAY_ID
        )

        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(SECONDARY_DISPLAY_ID),
            any(),
            any()
        )
        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(TARGET_DISPLAY_ID),
            any(),
            any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.size).isEqualTo(2)
        assertThat(
            pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(
                SECONDARY_DISPLAY_ID
            )
        ).isTrue()
        assertThat(
            pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(
                TARGET_DISPLAY_ID
            )
        ).isTrue()
        verify(mockSurfaceTransactionHelper, times(2)).setPipTransformations(
            any(),
            any(),
            any(),
            any(),
            any()
        )
        verify(mockSurfaceTransactionHelper, times(2)).setMirrorTransformations(any(), any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.isMirrorShown()).isTrue()
    }

    @Test
    fun removeMirrors_removesAllMirrorsAndAppliesTransactionOnce() {
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId = ArrayMap()
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.apply {
            put(0, mockLeash)
            put(1, mockLeash)
            put(2, mockLeash)
        }

        pipDisplayTransferHandler.removeMirrors()

        verify(mockTransaction, times(3)).remove(any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
    }

    companion object {
        const val ORIGIN_DISPLAY_ID = 0
        const val TARGET_DISPLAY_ID = 1
        const val SECONDARY_DISPLAY_ID = 2
        const val TEST_CORNER_RADIUS = 5
        const val TEST_SHADOW_RADIUS = 5
        val START_DRAG_COORDINATES = PointF(100f, 100f)
        val PIP_BOUNDS = Rect(0, 0, 700, 700)
        val DESTINATION_BOUNDS = Rect(100, 100, 800, 800)
    }
}