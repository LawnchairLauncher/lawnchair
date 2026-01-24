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

import android.animation.AnimatorTestRule
import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.widget.FrameLayout
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider
import com.android.wm.shell.windowdecor.WindowDecoration.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/**
 * Test class for [VisualIndicatorViewContainer] and [VisualIndicatorAnimator]
 *
 * Usage: atest WMShellUnitTests:VisualIndicatorViewContainerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
class VisualIndicatorViewContainerTest : ShellTestCase() {

    @JvmField @Rule val animatorTestRule = AnimatorTestRule(this)

    @Mock private lateinit var view: View
    @Mock private lateinit var displayLayout: DisplayLayout
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var taskSurface: SurfaceControl
    @Mock private lateinit var syncQueue: SyncTransactionQueue
    @Mock private lateinit var mockSurfaceControlViewHostFactory: SurfaceControlViewHostFactory
    @Mock private lateinit var mockBackground: LayerDrawable
    @Mock private lateinit var bubbleDropTargetBoundsProvider: BubbleDropTargetBoundsProvider
    @Mock private lateinit var snapEventHandler: SnapEventHandler
    private val taskInfo: RunningTaskInfo = createTaskInfo()
    private val mainExecutor = TestShellExecutor()
    private val desktopExecutor = TestShellExecutor()

    @Before
    fun setUp() {
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(DISPLAY_BOUNDS)
        }
        whenever(snapEventHandler.getRightSnapBoundsIfTiled(any())).thenReturn(Rect(1, 2, 3, 4))
        whenever(snapEventHandler.getLeftSnapBoundsIfTiled(any())).thenReturn(Rect(5, 6, 7, 8))
        whenever(mockSurfaceControlViewHostFactory.create(any(), any(), any()))
            .thenReturn(mock(SurfaceControlViewHost::class.java))
    }

    @Test
    fun testTransitionIndicator_sameTypeReturnsEarly() {
        val spyViewContainer = setupSpyViewContainer()
        // Test early return on startType == endType.
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .transitionIndicator(
                eq(taskInfo),
                eq(displayController),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
            )
        // Assert fadeIn, fadeOut, and animateIndicatorType were not called.
        verifyNoMoreInteractions(spyViewContainer)
    }

    @Test
    fun testTransitionIndicator_firstTypeNoIndicator_callsFadeIn() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer).fadeInIndicatorInternal(any(), any(), any(), any())
    }

    @Test
    fun testTransitionIndicator_secondTypeNoIndicator_callsFadeOut() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .fadeOutIndicator(
                any(),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                anyOrNull(),
                eq(taskInfo.displayId),
                eq(snapEventHandler),
            )
    }

    @Test
    fun testTransitionIndicator_differentTypes_callsTransitionIndicator() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .transitionIndicator(
                any(),
                any(),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR),
            )
    }

    @Test
    fun testFadeInBoundsCalculation() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsIn(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                    taskInfo.displayId,
                    snapEventHandler,
                )
            }
        assertThat(animator?.indicatorStartBounds).isEqualTo(Rect(15, 15, 985, 985))
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(0, 0, 1000, 1000))
    }

    @Test
    fun testFadeInBoundsCalculationForLeftSnap() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsIn(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                    taskInfo.displayId,
                    snapEventHandler,
                )
            }

        // Right bound is the same as whatever right bound snapEventHandler returned minus padding,
        // in this case, the right bound for the left app is 7.
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(0, 0, 7, 1000))
    }

    @Test
    fun testFadeInBoundsCalculationForRightSnap() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsIn(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                    taskInfo.displayId,
                    snapEventHandler,
                )
            }

        // Left bound is the same as whatever left bound snapEventHandler returned plus padding
        // in this case, the left bound of the right app is 1.
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(1, 0, 1000, 1000))
    }

    @Test
    fun testFadeOutBoundsCalculation() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsOut(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                    taskInfo.displayId,
                    snapEventHandler,
                )
            }
        assertThat(animator?.indicatorStartBounds).isEqualTo(Rect(0, 0, 1000, 1000))
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(15, 15, 985, 985))
    }

    @Test
    fun testChangeIndicatorTypeBoundsCalculation() {
        // Test fullscreen to split-left bounds.
        var animator =
            VisualIndicatorViewContainer.VisualIndicatorAnimator.animateIndicatorType(
                view,
                displayLayout,
                DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
                bubbleDropTargetBoundsProvider,
                taskInfo.displayId,
                snapEventHandler,
            )
        // Test desktop to split-right bounds.
        animator =
            VisualIndicatorViewContainer.VisualIndicatorAnimator.animateIndicatorType(
                view,
                displayLayout,
                DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR,
                DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR,
                bubbleDropTargetBoundsProvider,
                taskInfo.displayId,
                snapEventHandler,
            )
    }

    @Test
    fun fadeInIndicator_callsFadeIn() {
        val spyViewContainer = setupSpyViewContainer()

        spyViewContainer.fadeInIndicator(
            mock<DisplayLayout>(),
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DEFAULT_DISPLAY,
        )
        desktopExecutor.flushAll()

        verify(spyViewContainer).fadeInIndicatorInternal(any(), any(), any(), any())
    }

    @Test
    fun fadeInIndicator_alreadyReleased_doesntCallFadeIn() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.releaseVisualIndicator()

        spyViewContainer.fadeInIndicator(
            mock<DisplayLayout>(),
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DEFAULT_DISPLAY,
        )
        desktopExecutor.flushAll()

        verify(spyViewContainer, never()).fadeInIndicatorInternal(any(), any(), any(), any())
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testCreateView_bubblesEnabled_indicatorIsFrameLayout() {
        val spyViewContainer = setupSpyViewContainer()
        assertThat(spyViewContainer.indicatorView).isInstanceOf(FrameLayout::class.java)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testFadeInOutBubbleIndicator_addAndRemoveBarIndicator() {
        setUpBubbleBoundsProvider()
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.fadeInIndicator(
            displayLayout,
            DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR,
            DEFAULT_DISPLAY,
        )
        desktopExecutor.flushAll()
        animatorTestRule.advanceTimeBy(200)
        assertThat((spyViewContainer.indicatorView as FrameLayout).getChildAt(0)).isNotNull()

        spyViewContainer.fadeOutIndicator(
            displayLayout,
            DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR,
            finishCallback = null,
            DEFAULT_DISPLAY,
            snapEventHandler,
        )
        desktopExecutor.flushAll()
        animatorTestRule.advanceTimeBy(250)
        assertThat((spyViewContainer.indicatorView as FrameLayout).getChildAt(0)).isNull()
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testTransitionIndicator_fullscreenToBubble_addBarIndicator() {
        setUpBubbleBoundsProvider()
        val spyViewContainer = setupSpyViewContainer()

        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR,
        )
        desktopExecutor.flushAll()
        animatorTestRule.advanceTimeBy(200)

        assertThat((spyViewContainer.indicatorView as FrameLayout).getChildAt(0)).isNotNull()
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun testTransitionIndicator_bubbleToFullscreen_removeBarIndicator() {
        setUpBubbleBoundsProvider()
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.fadeInIndicator(
            displayLayout,
            DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR,
            DEFAULT_DISPLAY,
        )
        desktopExecutor.flushAll()
        animatorTestRule.advanceTimeBy(200)
        assertThat((spyViewContainer.indicatorView as FrameLayout).getChildAt(0)).isNotNull()

        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_BUBBLE_RIGHT_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
        )
        desktopExecutor.flushAll()
        animatorTestRule.advanceTimeBy(200)

        assertThat((spyViewContainer.indicatorView as FrameLayout).getChildAt(0)).isNull()
    }

    private fun setupSpyViewContainer(): VisualIndicatorViewContainer {
        val viewContainer =
            VisualIndicatorViewContainer(
                desktopExecutor,
                mainExecutor,
                SurfaceControl.Builder(),
                syncQueue,
                mockSurfaceControlViewHostFactory,
                bubbleDropTargetBoundsProvider,
                snapEventHandler,
            )
        viewContainer.createView(
            context,
            mock(Display::class.java),
            displayLayout,
            taskInfo,
            taskSurface,
        )
        desktopExecutor.flushAll()
        viewContainer.indicatorView?.background = mockBackground
        whenever(mockBackground.findDrawableByLayerId(anyInt()))
            .thenReturn(mock(Drawable::class.java))
        return spy(viewContainer)
    }

    private fun createTaskInfo(): RunningTaskInfo {
        val taskDescriptionBuilder = ActivityManager.TaskDescription.Builder()
        return TestRunningTaskInfoBuilder()
            .setDisplayId(Display.DEFAULT_DISPLAY)
            .setTaskDescriptionBuilder(taskDescriptionBuilder)
            .setVisible(true)
            .build()
    }

    private fun setUpBubbleBoundsProvider() {
        bubbleDropTargetBoundsProvider =
            object : BubbleDropTargetBoundsProvider {
                override fun getBubbleBarExpandedViewDropTargetBounds(onLeft: Boolean): Rect {
                    return BUBBLE_INDICATOR_BOUNDS
                }

                override fun getBarDropTargetBounds(onLeft: Boolean): Rect {
                    return BAR_INDICATOR_BOUNDS
                }
            }
    }

    companion object {
        private val DISPLAY_BOUNDS = Rect(0, 0, 1000, 1000)
        private val BUBBLE_INDICATOR_BOUNDS = Rect(800, 200, 900, 900)
        private val BAR_INDICATOR_BOUNDS = Rect(880, 950, 900, 960)
    }
}
