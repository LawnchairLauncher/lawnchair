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
package com.android.wm.shell.bubbles.bar

import android.content.Context
import android.graphics.Insets
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.protolog.common.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.DeviceConfig
import com.android.wm.shell.common.bubbles.BaseBubblePinController
import com.android.wm.shell.common.bubbles.BaseBubblePinController.Companion.DROP_TARGET_ALPHA_IN_DURATION
import com.android.wm.shell.common.bubbles.BaseBubblePinController.Companion.DROP_TARGET_ALPHA_OUT_DURATION
import com.android.wm.shell.common.bubbles.BubbleBarLocation
import com.android.wm.shell.common.bubbles.BubbleBarLocation.LEFT
import com.android.wm.shell.common.bubbles.BubbleBarLocation.RIGHT
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [BubbleExpandedViewPinController] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleExpandedViewPinControllerTest {

    companion object {
        @JvmField @ClassRule val animatorTestRule: AnimatorTestRule = AnimatorTestRule()

        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000

        const val BUBBLE_BAR_HEIGHT = 50
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var positioner: BubblePositioner
    private lateinit var container: FrameLayout

    private lateinit var controller: BubbleExpandedViewPinController
    private lateinit var testListener: TestLocationChangeListener

    private val dropTargetView: View?
        get() = container.findViewById(R.id.bubble_bar_drop_target)

    private val pointOnLeft = PointF(100f, 100f)
    private val pointOnRight = PointF(1900f, 500f)

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        container = FrameLayout(context)
        val windowManager = context.getSystemService(WindowManager::class.java)
        positioner = BubblePositioner(context, windowManager)
        positioner.setShowingInBubbleBar(true)
        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40)
            )
        positioner.update(deviceConfig)
        positioner.bubbleBarTopOnScreen =
            SCREEN_HEIGHT - deviceConfig.insets.bottom - BUBBLE_BAR_HEIGHT
        controller = BubbleExpandedViewPinController(context, container, positioner)
        testListener = TestLocationChangeListener()
        controller.setListener(testListener)
    }

    @After
    fun tearDown() {
        getInstrumentation().runOnMainSync { controller.onDragEnd() }
        waitForAnimateOut()
    }

    /** Dragging on same side should not show drop target or trigger location changes */
    @Test
    fun drag_stayOnRightSide() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onDragEnd()
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).containsExactly(RIGHT)
    }

    /** Dragging on same side should not show drop target or trigger location changes */
    @Test
    fun drag_stayOnLeftSide() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onDragEnd()
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).containsExactly(LEFT)
    }

    /** Drag crosses to the other side. Show drop target and trigger a location change. */
    @Test
    fun drag_rightToLeft() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()

        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnLeft())
        assertThat(testListener.locationChanges).containsExactly(LEFT)
        assertThat(testListener.locationReleases).isEmpty()
    }

    /** Drag crosses to the other side. Show drop target and trigger a location change. */
    @Test
    fun drag_leftToRight() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()

        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnRight())
        assertThat(testListener.locationChanges).containsExactly(RIGHT)
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drop target does not initially show on the side that the drag starts. Check that it shows up
     * after the dragging the view to other side and back to the initial side.
     */
    @Test
    fun drag_rightToLeftToRight() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()

        getInstrumentation().runOnMainSync { controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y) }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()

        getInstrumentation().runOnMainSync {
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateOut()
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnRight())
        assertThat(testListener.locationChanges).containsExactly(LEFT, RIGHT).inOrder()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drop target does not initially show on the side that the drag starts. Check that it shows up
     * after the dragging the view to other side and back to the initial side.
     */
    @Test
    fun drag_leftToRightToLeft() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()

        getInstrumentation().runOnMainSync {
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()

        getInstrumentation().runOnMainSync { controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y) }
        waitForAnimateOut()
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnLeft())
        assertThat(testListener.locationChanges).containsExactly(RIGHT, LEFT).inOrder()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag from right to left, but stay in exclusion rect around the dismiss view. Drop target
     * should not show and location change should not trigger.
     */
    @Test
    fun drag_rightToLeft_inExclusionRect() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            // Exclusion rect is around the bottom center area of the screen
            controller.onDragUpdate(SCREEN_WIDTH / 2f - 50, SCREEN_HEIGHT - 100f)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag from left to right, but stay in exclusion rect around the dismiss view. Drop target
     * should not show and location change should not trigger.
     */
    @Test
    fun drag_leftToRight_inExclusionRect() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            // Exclusion rect is around the bottom center area of the screen
            controller.onDragUpdate(SCREEN_WIDTH / 2f + 50, SCREEN_HEIGHT - 100f)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag to dismiss target and back to the same side should not cause the drop target to show.
     */
    @Test
    fun drag_rightToDismissToRight() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onStuckToDismissTarget()
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag to dismiss target and back to the same side should not cause the drop target to show.
     */
    @Test
    fun drag_leftToDismissToLeft() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onStuckToDismissTarget()
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).isEmpty()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /** Drag to dismiss target and other side should show drop target on the other side. */
    @Test
    fun drag_rightToDismissToLeft() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onStuckToDismissTarget()
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnLeft())

        assertThat(testListener.locationChanges).containsExactly(LEFT)
        assertThat(testListener.locationReleases).isEmpty()
    }

    /** Drag to dismiss target and other side should show drop target on the other side. */
    @Test
    fun drag_leftToDismissToRight() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onStuckToDismissTarget()
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dropTargetView!!.bounds()).isEqualTo(getExpectedDropTargetBoundsOnRight())

        assertThat(testListener.locationChanges).containsExactly(RIGHT)
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag to dismiss should trigger a location change to the initial location, if the current
     * location is different. And hide the drop target.
     */
    @Test
    fun drag_rightToLeftToDismiss() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)

        getInstrumentation().runOnMainSync { controller.onStuckToDismissTarget() }
        waitForAnimateOut()
        assertThat(dropTargetView!!.alpha).isEqualTo(0f)

        assertThat(testListener.locationChanges).containsExactly(LEFT, RIGHT).inOrder()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /**
     * Drag to dismiss should trigger a location change to the initial location, if the current
     * location is different. And hide the drop target.
     */
    @Test
    fun drag_leftToRightToDismiss() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()
        assertThat(dropTargetView!!.alpha).isEqualTo(1f)
        getInstrumentation().runOnMainSync { controller.onStuckToDismissTarget() }
        waitForAnimateOut()
        assertThat(dropTargetView!!.alpha).isEqualTo(0f)
        assertThat(testListener.locationChanges).containsExactly(RIGHT, LEFT).inOrder()
        assertThat(testListener.locationReleases).isEmpty()
    }

    /** Finishing drag should remove drop target and send location update. */
    @Test
    fun drag_rightToLeftRelease() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = false)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()

        getInstrumentation().runOnMainSync { controller.onDragEnd() }
        waitForAnimateOut()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).containsExactly(LEFT)
        assertThat(testListener.locationReleases).containsExactly(LEFT)
    }

    /** Finishing drag should remove drop target and send location update. */
    @Test
    fun drag_leftToRightRelease() {
        getInstrumentation().runOnMainSync {
            controller.onDragStart(initialLocationOnLeft = true)
            controller.onDragUpdate(pointOnLeft.x, pointOnLeft.y)
            controller.onDragUpdate(pointOnRight.x, pointOnRight.y)
        }
        waitForAnimateIn()
        assertThat(dropTargetView).isNotNull()

        getInstrumentation().runOnMainSync { controller.onDragEnd() }
        waitForAnimateOut()
        assertThat(dropTargetView).isNull()
        assertThat(testListener.locationChanges).containsExactly(RIGHT)
        assertThat(testListener.locationReleases).containsExactly(RIGHT)
    }

    private fun getExpectedDropTargetBoundsOnLeft(): Rect =
        Rect().also {
            positioner.getBubbleBarExpandedViewBounds(
                true /* onLeft */,
                false /* isOverflowExpanded */,
                it
            )
        }

    private fun getExpectedDropTargetBoundsOnRight(): Rect =
        Rect().also {
            positioner.getBubbleBarExpandedViewBounds(
                false /* onLeft */,
                false /* isOverflowExpanded */,
                it
            )
        }

    private fun waitForAnimateIn() {
        // Advance animator for on-device test
        getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(DROP_TARGET_ALPHA_IN_DURATION)
        }
    }

    private fun waitForAnimateOut() {
        // Advance animator for on-device test
        getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(DROP_TARGET_ALPHA_OUT_DURATION)
        }
    }

    private fun View.bounds(): Rect {
        return Rect(0, 0, layoutParams.width, layoutParams.height).also { rect ->
            rect.offsetTo(x.toInt(), y.toInt())
        }
    }

    internal class TestLocationChangeListener : BaseBubblePinController.LocationChangeListener {
        val locationChanges = mutableListOf<BubbleBarLocation>()
        val locationReleases = mutableListOf<BubbleBarLocation>()
        override fun onChange(location: BubbleBarLocation) {
            locationChanges.add(location)
        }

        override fun onRelease(location: BubbleBarLocation) {
            locationReleases.add(location)
        }
    }
}
