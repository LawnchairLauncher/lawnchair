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

import android.animation.AnimatorTestRule
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.PointF
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.internal.policy.SystemBarUtils
import com.android.window.flags.Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.shared.R as sharedR
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopModeVisualIndicator]
 *
 * Usage: atest WMShellUnitTests:DesktopModeVisualIndicatorTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
@Ignore("Non-hermetic test - b/419771302")
class DesktopModeVisualIndicatorTest : ShellTestCase() {

    @JvmField @Rule val animatorTestRule = AnimatorTestRule(this)

    private lateinit var taskInfo: RunningTaskInfo
    @Mock private lateinit var syncQueue: SyncTransactionQueue
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var taskSurface: SurfaceControl
    @Mock private lateinit var taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var displayLayout: DisplayLayout
    @Mock private lateinit var bubbleBoundsProvider: BubbleDropTargetBoundsProvider
    @Mock private lateinit var snapEventHandler: SnapEventHandler

    private lateinit var visualIndicator: DesktopModeVisualIndicator
    private val desktopExecutor = TestShellExecutor()
    private val mainExecutor = TestShellExecutor()

    @Before
    fun setUp() {
        setUpDisplayBoundsTablet()
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayController.getDisplay(anyInt())).thenReturn(mContext.display)
        whenever(bubbleBoundsProvider.getBubbleBarExpandedViewDropTargetBounds(any()))
            .thenReturn(Rect())
        taskInfo = DesktopTestHelpers.createFullscreenTask()

        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_isDesktopModeSupported,
            true,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_canInternalDisplayHostDesktops,
            true,
        )
    }

    private fun setUpDisplayBoundsTablet() {
        whenever(displayLayout.width()).thenReturn(TABLET_DISPLAY_BOUNDS.width())
        whenever(displayLayout.height()).thenReturn(TABLET_DISPLAY_BOUNDS.height())
        whenever(displayLayout.stableInsets()).thenReturn(TABLET_STABLE_INSETS)
    }

    private fun setUpDisplayBoundsFoldable() {
        whenever(displayLayout.width()).thenReturn(FOLDABLE_DISPLAY_BOUNDS.width())
        whenever(displayLayout.height()).thenReturn(FOLDABLE_DISPLAY_BOUNDS.height())
        whenever(displayLayout.stableInsets()).thenReturn(FOLDABLE_STABLE_INSETS)
    }

    private fun disableDesktop() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_canInternalDisplayHostDesktops,
            false,
        )
    }

    private fun setUpFoldable() {
        setUpDisplayBoundsFoldable()
        disableDesktop()
    }

    @Test
    fun testFullscreenRegionCalculation() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var testRegion = visualIndicator.calculateFullscreenRegion(displayLayout, CAPTION_HEIGHT)
        assertThat(testRegion.bounds)
            .isEqualTo(Rect(0, Short.MIN_VALUE.toInt(), 2400, 2 * TABLET_STABLE_INSETS.top))

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)
        testRegion = visualIndicator.calculateFullscreenRegion(displayLayout, CAPTION_HEIGHT)
        val transitionHeight = SystemBarUtils.getStatusBarHeight(context)
        val toFullscreenScale =
            mContext.resources.getFloat(R.dimen.desktop_mode_fullscreen_region_scale)
        val toFullscreenWidth = displayLayout.width() * toFullscreenScale
        assertThat(testRegion.bounds)
            .isEqualTo(
                Rect(
                    (TABLET_DISPLAY_BOUNDS.width() / 2f - toFullscreenWidth / 2f).toInt(),
                    Short.MIN_VALUE.toInt(),
                    (TABLET_DISPLAY_BOUNDS.width() / 2f + toFullscreenWidth / 2f).toInt(),
                    transitionHeight,
                )
            )

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        testRegion = visualIndicator.calculateFullscreenRegion(displayLayout, CAPTION_HEIGHT)
        assertThat(testRegion.bounds)
            .isEqualTo(Rect(0, Short.MIN_VALUE.toInt(), 2400, 2 * TABLET_STABLE_INSETS.top))

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.DRAGGED_INTENT)
        testRegion = visualIndicator.calculateFullscreenRegion(displayLayout, CAPTION_HEIGHT)
        assertThat(testRegion.bounds)
            .isEqualTo(Rect(0, Short.MIN_VALUE.toInt(), 2400, transitionHeight))
    }

    @Test
    fun testSplitLeftRegionCalculation() {
        val transitionHeight =
            context.resources.getDimensionPixelSize(R.dimen.desktop_mode_split_from_desktop_height)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var testRegion =
            visualIndicator.calculateSplitLeftRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(0, -50, 32, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)
        testRegion =
            visualIndicator.calculateSplitLeftRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(0, transitionHeight, 32, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        testRegion =
            visualIndicator.calculateSplitLeftRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(0, -50, 32, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.DRAGGED_INTENT)
        testRegion =
            visualIndicator.calculateSplitLeftRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(0, -50, 32, 1600))
    }

    @Test
    fun testSplitRightRegionCalculation() {
        val transitionHeight =
            context.resources.getDimensionPixelSize(R.dimen.desktop_mode_split_from_desktop_height)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var testRegion =
            visualIndicator.calculateSplitRightRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(2368, -50, 2400, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)
        testRegion =
            visualIndicator.calculateSplitRightRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(2368, transitionHeight, 2400, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        testRegion =
            visualIndicator.calculateSplitRightRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(2368, -50, 2400, 1600))
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.DRAGGED_INTENT)
        testRegion =
            visualIndicator.calculateSplitRightRegion(
                displayLayout,
                TRANSITION_AREA_WIDTH,
                CAPTION_HEIGHT,
            )
        assertThat(testRegion).isEqualTo(Rect(2368, -50, 2400, 1600))
    }

    @Test
    fun testBubbleLeftRegionCalculation() {
        val bubbleRegionSize =
            context.resources.getDimensionPixelSize(sharedR.dimen.drag_zone_bubble_tablet)
        val expectedRect = Rect(0, 1600 - bubbleRegionSize, bubbleRegionSize, 1600)

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var testRegion = visualIndicator.calculateBubbleLeftRegion(displayLayout)
        assertThat(testRegion).isEqualTo(expectedRect)

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        testRegion = visualIndicator.calculateBubbleLeftRegion(displayLayout)
        assertThat(testRegion).isEqualTo(expectedRect)
    }

    @Test
    fun testBubbleRightRegionCalculation() {
        val bubbleRegionSize =
            context.resources.getDimensionPixelSize(sharedR.dimen.drag_zone_bubble_tablet)
        val expectedRect = Rect(2400 - bubbleRegionSize, 1600 - bubbleRegionSize, 2400, 1600)

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var testRegion = visualIndicator.calculateBubbleRightRegion(displayLayout)
        assertThat(testRegion).isEqualTo(expectedRect)

        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        testRegion = visualIndicator.calculateBubbleRightRegion(displayLayout)
        assertThat(testRegion).isEqualTo(expectedRect)
    }

    @Test
    fun testDefaultIndicators() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(-10000f, 500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(10000f, 500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.DRAGGED_INTENT)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(500f, 10000f))
        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(500f, 10000f))
        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testDefaultIndicators_enableBubbleToFullscreen() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(10f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_LEFT_INDICATOR)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(2390f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR)

        // Check that bubble zones are not available from split
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(10f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(2390f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR)

        // Check that bubble zones are not available from desktop
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(10f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(2390f, 1500f))
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testDefaultIndicators_foldable_enableBubbleToFullscreen_dragFromFullscreen() {
        setUpFoldable()

        createVisualIndicator(
            DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN,
            isSmallTablet = true,
            isLeftRightSplit = true,
        )
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldCenter())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftEdge())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightEdge())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_LEFT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING)
    fun testDefaultIndicators_foldable_enableBubbleToFullscreen_dragFromSplit() {
        setUpFoldable()

        createVisualIndicator(
            DesktopModeVisualIndicator.DragStartState.FROM_SPLIT,
            isSmallTablet = true,
            isLeftRightSplit = true,
        )
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldCenter())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        // Check that bubbles are not available from split
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR)
    }

    @Test
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING)
    fun testDefaultIndicators_foldable_enableBubbleAnything_dragFromSplit() {
        setUpFoldable()

        createVisualIndicator(
            DesktopModeVisualIndicator.DragStartState.FROM_SPLIT,
            isSmallTablet = true,
            isLeftRightSplit = true,
        )
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldCenter())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_LEFT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testDefaultIndicators_foldable_topBottomSplit() {
        setUpFoldable()

        createVisualIndicator(
            DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN,
            isSmallTablet = true,
            isLeftRightSplit = false,
        )
        var result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldCenter())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftEdge())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_LEFT_INDICATOR)

        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightBottom())
        assertThat(result)
            .isEqualTo(DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR)

        createVisualIndicator(
            DesktopModeVisualIndicator.DragStartState.FROM_SPLIT,
            isSmallTablet = true,
            isLeftRightSplit = false,
        )
        // No indicator as top/bottom split apps should not be dragged
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldCenter())
        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldLeftBottom())
        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        result = visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, foldRightBottom())
        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG)
    fun testDefaultIndicators_crossDisplayDrag_noIndicator() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)

        // Simulate dragging to a point on a different display.
        // Even though this point (-10000f, 500f) would trigger TO_SPLIT_LEFT_INDICATOR
        // on the original display, dragging across displays should show no indicator.
        val result = visualIndicator.updateIndicatorType(/* displayId= */ 10, PointF(-10000f, 500f))

        assertThat(result).isEqualTo(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testBubbleLeftVisualIndicatorSize() {
        val dropTargetBounds = Rect(100, 100, 500, 1500)
        whenever(bubbleBoundsProvider.getBubbleBarExpandedViewDropTargetBounds(/* onLeft= */ true))
            .thenReturn(dropTargetBounds)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        desktopExecutor.flushAll()
        mainExecutor.flushAll()
        visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(100f, 1500f))
        desktopExecutor.flushAll()
        mainExecutor.flushAll()

        animatorTestRule.advanceTimeBy(200)

        assertThat(visualIndicator.indicatorBounds).isEqualTo(dropTargetBounds)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testBubbleRightVisualIndicatorSize() {
        val dropTargetBounds = Rect(1900, 100, 2300, 1500)
        whenever(bubbleBoundsProvider.getBubbleBarExpandedViewDropTargetBounds(/* onLeft= */ false))
            .thenReturn(dropTargetBounds)
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)
        desktopExecutor.flushAll()
        mainExecutor.flushAll()
        visualIndicator.updateIndicatorType(DEFAULT_DISPLAY, PointF(2300f, 1500f))
        desktopExecutor.flushAll()
        mainExecutor.flushAll()

        animatorTestRule.advanceTimeBy(200)

        assertThat(visualIndicator.indicatorBounds).isEqualTo(dropTargetBounds)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun createIndicator_inTransitionFlagDisabled_isAttachedToDisplayArea() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)

        verify(taskDisplayAreaOrganizer).attachToDisplayArea(anyInt(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun createIndicator_fromFreeform_inTransitionFlagEnabled_isAttachedToDisplayArea() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM)

        verify(taskDisplayAreaOrganizer).attachToDisplayArea(anyInt(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun createIndicator_fromFullscreen_inTransitionFlagEnabled_notAttachedToDisplayArea() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_FULLSCREEN)

        verify(taskDisplayAreaOrganizer, never()).attachToDisplayArea(anyInt(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun createIndicator_fromSplit_inTransitionFlagEnabled_notAttachedToDisplayArea() {
        createVisualIndicator(DesktopModeVisualIndicator.DragStartState.FROM_SPLIT)

        verify(taskDisplayAreaOrganizer, never()).attachToDisplayArea(anyInt(), any())
    }

    private fun createVisualIndicator(
        dragStartState: DesktopModeVisualIndicator.DragStartState,
        isSmallTablet: Boolean = false,
        isLeftRightSplit: Boolean = true,
    ) {
        visualIndicator =
            DesktopModeVisualIndicator(
                desktopExecutor,
                mainExecutor,
                syncQueue,
                taskInfo,
                displayController,
                context,
                taskSurface,
                taskDisplayAreaOrganizer,
                dragStartState,
                bubbleBoundsProvider,
                snapEventHandler,
                isSmallTablet,
                isLeftRightSplit,
            )
    }

    private fun foldCenter(): PointF {
        return PointF(
            FOLDABLE_DISPLAY_BOUNDS.centerX().toFloat(),
            FOLDABLE_DISPLAY_BOUNDS.centerY().toFloat(),
        )
    }

    private fun foldLeftEdge(): PointF {
        return PointF(0f, 50f)
    }

    private fun foldRightEdge(): PointF {
        return PointF(750f, 50f)
    }

    private fun foldLeftBottom(): PointF {
        return PointF(0f, 650f)
    }

    private fun foldRightBottom(): PointF {
        return PointF(750f, 650f)
    }

    companion object {
        private const val TRANSITION_AREA_WIDTH = 32
        private const val CAPTION_HEIGHT = 50
        private const val NAVBAR_HEIGHT = 50
        private val TABLET_DISPLAY_BOUNDS = Rect(0, 0, 2400, 1600)
        private val TABLET_STABLE_INSETS =
            Rect(
                TABLET_DISPLAY_BOUNDS.left,
                TABLET_DISPLAY_BOUNDS.top + CAPTION_HEIGHT,
                TABLET_DISPLAY_BOUNDS.right,
                TABLET_DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT,
            )
        private val FOLDABLE_DISPLAY_BOUNDS = Rect(0, 0, 800, 700)
        private val FOLDABLE_STABLE_INSETS =
            Rect(
                FOLDABLE_DISPLAY_BOUNDS.left,
                FOLDABLE_DISPLAY_BOUNDS.top + CAPTION_HEIGHT,
                FOLDABLE_DISPLAY_BOUNDS.right,
                FOLDABLE_DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT,
            )
    }
}
