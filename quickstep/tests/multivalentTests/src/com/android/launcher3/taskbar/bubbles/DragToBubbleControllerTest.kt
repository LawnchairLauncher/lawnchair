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

package com.android.launcher3.taskbar.bubbles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.taskbar.bubbles.BubbleBarController.BubbleBarLocationListener
import com.android.quickstep.SystemUiProxy
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DropTargetView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/** Unit tests for [DragToBubbleControllerTest]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragToBubbleControllerTest {

    @get:Rule val animatorTestRule = AnimatorTestRule()

    private val context = getApplicationContext<Context>()
    private val container = FrameLayout(context)
    private val bubbleBarViewController: BubbleBarViewController = mock()
    private val systemUiProxy: SystemUiProxy = mock()
    private val bubbleBarLocationListener: BubbleBarLocationListener = mock()
    private val bubbleBarPropertiesProvider = FakeBubbleBarPropertiesProvider()
    private val testDragZonesFactory = createTestDragZoneFactory()
    private val dragObject = DragObject(context)
    private lateinit var dragToBubbleController: DragToBubbleController

    private val dropTargetView: DropTargetView
        get() = dragToBubbleController.dropTargetManager.dropTargetView

    private val secondDropTargetView: DropTargetView?
        get() = dragToBubbleController.dropTargetManager.secondDropTargetView

    private val bubbleBarLeftDropTarget: DropTarget
        get() = dragToBubbleController.bubbleBarLeftDropTarget

    private val bubbleBarRightDropTarget: DropTarget
        get() = dragToBubbleController.bubbleBarRightDropTarget

    private val leftDropTargetRect: Rect
        get() = testDragZonesFactory.getBubbleBarDropRect(isLeftSide = true)

    private val rightDropTargetRect: Rect
        get() = testDragZonesFactory.getBubbleBarDropRect(isLeftSide = false)

    @Before
    fun setUp() {
        prepareBubbleBarViewController()
        dragToBubbleController = DragToBubbleController(context, container)
        dragToBubbleController.init(
            bubbleBarViewController,
            bubbleBarPropertiesProvider,
            bubbleBarLocationListener,
            systemUiProxy,
        )
        dragToBubbleController.dragZoneFactory = testDragZonesFactory
    }

    @Test
    fun dragStarted_noBubbleBar_dropZonesAdded() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_NO_BUBBLE_BAR)
        assertThat(dropTargetView.parent).isEqualTo(container)
        assertThat(secondDropTargetView!!.parent).isEqualTo(container)
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(0f)
        assertThat(dragToBubbleController.isItemDropHandled).isFalse()
    }

    @Test
    fun dragStarted_hasBubbleBar_dropZonesAdded() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
        assertThat(dropTargetView.parent).isEqualTo(container)
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(secondDropTargetView).isNull()
    }

    @Test
    fun dragEnded_allViewsRemoved() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onDragEnd()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun draggedToTheRightDropZone_noBubbles_dropTargetViewsShown() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1f)
        verify(bubbleBarViewController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun draggedToTheRightDropZone_hasBubblesOnTheRight_dropTargetViewShown() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(secondDropTargetView).isNull()
    }

    @Test
    fun draggedToTheRightDropZone_hasBubblesOnTheRight_bubbleBarLocationChangeNotRequested() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(250)
        }
        verify(bubbleBarViewController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun draggedToTheLeftDropZone_hasBubblesOnTheRight_bubbleBarLocationChangeRequested() {
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.RIGHT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }
        verify(bubbleBarViewController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun draggedToTheLeftDropZone_dragEnded_hasBubblesOnTheRight_locationRestored() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = bubbleBarOriginalLocation,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
            dragObject.updateXY(x = 0, y = 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        verify(bubbleBarViewController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(bubbleBarViewController).animateBubbleBarLocation(bubbleBarOriginalLocation)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun droppedAtTheLeftDropZone_noBubblesOnTheRight_appBubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleBarViewController(
            hasBubbles = false,
            bubbleBarLocation = bubbleBarOriginalLocation,
        )
        val packageName = "test.package"
        val itemIntent =
            Intent().apply {
                component = ComponentName(packageName, "TestClass")
                `package` = packageName
            }
        val appInfo = AppInfo().apply { intent = itemIntent }
        dragObject.dragInfo = appInfo

        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
            bubbleBarLeftDropTarget.onDrop(dragObject, DragOptions())
            assertThat(dragToBubbleController.isItemDropHandled).isTrue()
            bubbleBarLeftDropTarget.onDragExit(dragObject)
        }

        verify(systemUiProxy).showAppBubble(itemIntent, appInfo.user, BubbleBarLocation.LEFT)
    }

    @Test
    fun dragExitRightZone_noBubbles_listenerNotNotified() {
        // Scenario: No bubbles. Drag enters RIGHT, then exits to no particular zone.
        // This is distinct as it starts on the default side.
        prepareBubbleBarViewController(
            hasBubbles = false,
            bubbleBarLocation = BubbleBarLocation.RIGHT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject) // Location is the same
        }
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0) // Move out of all zones
            bubbleBarRightDropTarget.onDragExit(dragObject)
        }

        // Exiting the RIGHT zone (which is the default) should not re-notify of RIGHT
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    @Test
    fun onDragEnd_noBubbles_wasDraggingLeft_listenerNotifiedWithDefaultRightLocationAnimated() {
        val startingLocation = BubbleBarLocation.RIGHT
        // Scenario: No bubbles. Drag was over LEFT zone. Drag ends.
        prepareBubbleBarViewController(hasBubbles = false, bubbleBarLocation = startingLocation)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }
        // Notifies onBubbleBarLocationAnimated(LEFT)
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
        clearInvocations(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onDragEnd()
        }
        assertThat(dragToBubbleController.isItemDropHandled).isFalse()

        // After drag ends (and no bubbles), the listener should be notified of the default location
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(startingLocation)
    }

    @Test
    fun onDragEnd_noBubbles_wasDraggingRight_listenerNotifiedWithDefaultRightLocationAnimated() {
        // Scenario: No bubbles. Drag was over RIGHT zone (default side). Drag ends.
        prepareBubbleBarViewController(hasBubbles = false)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
        }
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
        clearInvocations(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        // After drag ends (and no bubbles), listener  should not be notified of the default
        // location.
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    @Test
    fun dragEnterLeftZone_bubblesOnLeft_listenerNotNotified() {
        // Scenario: Bubbles on LEFT. Drag enters LEFT zone.
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.LEFT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }

        // Bubbles are already on the LEFT, and drag enters LEFT.
        // No new animation to LEFT should be triggered by the zone entry itself.
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
    }

    @Test
    fun onDragEnd_bubblesOnLeft_defaultIsLeft_wasDraggingRight_listenerNotifiedLeftAnimated() {
        // Scenario: Bubbles on LEFT. Drag was over RIGHT zone. Drag ends.
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.LEFT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject) // Notifies Animated(RIGHT)
        }
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
        clearInvocations(bubbleBarLocationListener) // Clear the Animated(RIGHT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        // Bubble bar's final animated location should be LEFT.
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
    }

    @Test
    fun dragEnterLeftThenExitToNoZoneThenEnterRight_noBubbles_listenerSequenceCorrectAnimated() {
        // Scenario: No bubbles. Complex drag path: Left -> None -> Right
        prepareBubbleBarViewController(hasBubbles = false)
        dragToBubbleController.onDragStart(dragObject, DragOptions())
        clearInvocations(bubbleBarLocationListener)

        val inOrder = inOrder(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // 1. Enter Left
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)

            // 2. Exit Left to no zone
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)

            // 3. Enter Right
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
        }

        inOrder
            .verify(bubbleBarLocationListener)
            .onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
        // Revert to default, following enter of the same zone should not trigger updated
        inOrder
            .verify(bubbleBarLocationListener)
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    private fun prepareBubbleBarViewController(
        hasBubbles: Boolean = false,
        bubbleBarLocation: BubbleBarLocation = BubbleBarLocation.RIGHT,
    ) {
        bubbleBarViewController.stub {
            on { hasBubbles() } doReturn hasBubbles
            on { getBubbleBarLocation() } doReturn bubbleBarLocation
        }
    }

    private fun DragObject.updateXYToCenterOf(rect: Rect) {
        updateXY(rect.centerX(), rect.centerY())
    }

    private fun DragObject.updateXY(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    private fun createTestDragZoneFactory(): DragZoneFactory {
        val deviceConfig =
            DeviceConfig(
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                insets = Insets.NONE,
            )
        return DragZoneFactory(
            context,
            deviceConfig,
            { SplitScreenMode.NONE },
            { false },
            bubbleBarPropertiesProvider,
        )
    }

    private class FakeBubbleBarPropertiesProvider : BubbleBarPropertiesProvider {

        override fun getHeight(): Int = BUBBLE_BAR_HEIGHT

        override fun getWidth(): Int = BUBBLE_BAR_WIDTH

        override fun getBottomPadding(): Int = BUBBLE_BAR_BOTTOM_PADDING
    }

    companion object {
        const val BUBBLE_BAR_WIDTH = 100
        const val BUBBLE_BAR_HEIGHT = 110
        const val BUBBLE_BAR_BOTTOM_PADDING = 70
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
        const val DROP_VIEWS_COUNT = 1
        const val DROP_VIEWS_COUNT_NO_BUBBLE_BAR = 2
    }
}
