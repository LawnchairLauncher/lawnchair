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

package com.android.wm.shell.bubbles.bar

import android.app.PendingIntent
import android.content.Context
import android.content.IIntentSender
import android.content.pm.ShortcutInfo
import android.graphics.Insets
import android.graphics.Rect
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.view.ViewGroup
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DropTargetView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@EnableFlags(FLAG_ENABLE_BUBBLE_ANYTHING)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragToBubbleControllerTest {

    @get:Rule val animatorTestRule = AnimatorTestRule()
    private val context = getApplicationContext<Context>()
    private val bubblePositioner: BubblePositioner = mock()
    private val bubbleController: BubbleController = mock()

    private lateinit var dragToBubbleController: DragToBubbleController
    private lateinit var dropTargetContainer: ViewGroup

    private val dropTargetView: DropTargetView
        get() = dragToBubbleController.dropTargetManager.dropTargetView

    private val dragZoneFactory: DragZoneFactory
        get() = dragToBubbleController.dragZoneFactory

    private val leftDropRect: Rect
        get() = dragZoneFactory.getBubbleBarDropRect(isLeftSide = true)

    private val rightDropRect: Rect
        get() = dragZoneFactory.getBubbleBarDropRect(isLeftSide = false)

    @Before
    fun setUp() {
        bubblePositioner.stub { on { currentConfig } doReturn createDeviceConfig() }
        dragToBubbleController = DragToBubbleController(context, bubblePositioner, bubbleController)
        dropTargetContainer = dragToBubbleController.getDropTargetContainer()
    }

    @Test
    fun dragStarted_dropZoneAdded() {
        dragToBubbleController.onDragStarted()

        // Once drag is started drop view should be added
        assertThat(dropTargetContainer.childCount).isEqualTo(1)
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(dropTargetView.parent).isEqualTo(dropTargetContainer)
    }

    @Test
    fun dragStarted_multipleTimes_dropZoneAddedOnlyOnce() {
        repeat(10) { dragToBubbleController.onDragStarted() }

        // Only one drop target view is added
        assertThat(dropTargetContainer.childCount).isEqualTo(1)
    }

    @Test
    fun dragEnded_withoutDragStarted_noCrashAndNoViewRemoved() {
        dragToBubbleController.onDragEnded()
    }

    @Test
    fun dragEnded_dropViewRemovedAfterAnimationIsCompleted() {
        dragToBubbleController.onDragStarted()
        runOnMainSync { dragToBubbleController.onDragEnded() }
        // should not remove view immediately
        assertThat(dropTargetContainer.childCount).isEqualTo(1)
        // wait till animation is completed
        runOnMainSync { animatorTestRule.advanceTimeBy(250) }
        // after animation is completed there should be no views in container
        assertThat(dropTargetContainer.childCount).isEqualTo(0)
    }

    @Test
    fun draggedToTheRightDropZone_noBubbles_dropTargetViewShown_bubbleBarDropTargetShowRequested() {
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(rightDropRect.centerX(), rightDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        verify(bubbleController).showBubbleBarPinAtLocation(BubbleBarLocation.RIGHT)
        verify(bubbleController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun draggedToTheRightDropZone_bubbleOnTheRight_dropTargetShown_locationUpdatedNotRequested() {
        prepareBubbleController(hasBubbles = true, bubbleBarLocation = BubbleBarLocation.RIGHT)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(rightDropRect.centerX(), rightDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        verify(bubbleController, never()).showBubbleBarPinAtLocation(any())
        verify(bubbleController, never()).showBubbleBarPinAtLocation(any())
    }

    @Test
    fun draggedToTheLeftDropZone_hasBubblesOnTheRight_bubbleBarLocationChangeRequested() {
        prepareBubbleController(hasBubbles = true, bubbleBarLocation = BubbleBarLocation.RIGHT)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        verify(bubbleController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun draggedToTheLeftDropZone_dragEnded_noBubblesOnTheRight_pinViewHideRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = false, bubbleBarLocation = bubbleBarOriginalLocation)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onDragEnded()
        }

        verify(bubbleController).showBubbleBarPinAtLocation(BubbleBarLocation.LEFT)
        verify(bubbleController).showBubbleBarPinAtLocation(null)
        assertThat(dropTargetContainer.childCount).isEqualTo(1)

        runOnMainSync { animatorTestRule.advanceTimeBy(250) }
        assertThat(dropTargetContainer.childCount).isEqualTo(0)
    }

    @Test
    fun draggedToTheLeftDropZone_dragEnded_hasBubblesOnTheRight_locationRestored() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = true, bubbleBarLocation = bubbleBarOriginalLocation)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onDragEnded()
        }

        verify(bubbleController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(bubbleController).animateBubbleBarLocation(bubbleBarOriginalLocation)
        assertThat(dropTargetContainer.childCount).isEqualTo(1)

        runOnMainSync { animatorTestRule.advanceTimeBy(250) }
        assertThat(dropTargetContainer.childCount).isEqualTo(0)
    }

    @Test
    fun dragBetweenLeftAndRightDropZones_hasBubblesOnRight_bubbleBarAnimatesCorrectly() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = true, bubbleBarLocation = bubbleBarOriginalLocation)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        verify(bubbleController).animateBubbleBarLocation(BubbleBarLocation.LEFT)

        runOnMainSync {
            // drag to no zone
            dragToBubbleController.onDragUpdate(0, 0)
            animatorTestRule.advanceTimeBy(250)
        }
        // should return to original position
        verify(bubbleController).animateBubbleBarLocation(bubbleBarOriginalLocation)
        clearInvocations(bubbleController)

        runOnMainSync {
            // drag to the same zone as bubble bar
            dragToBubbleController.onDragUpdate(rightDropRect.centerX(), rightDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        // should not trigger any call to animate bubble bar
        verify(bubbleController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun dragBetweenLeftAndRightDropZones_noBubblesOnRight_bubbleDropTargetShowRequestedCorrectly() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = false, bubbleBarLocation = bubbleBarOriginalLocation)
        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        // should request displaying pin on left
        verify(bubbleController).showBubbleBarPinAtLocation(BubbleBarLocation.LEFT)

        runOnMainSync {
            // drag to no zone
            dragToBubbleController.onDragUpdate(0, 0)
            animatorTestRule.advanceTimeBy(250)
        }
        // should hide pin view
        verify(bubbleController).showBubbleBarPinAtLocation(null)
        clearInvocations(bubbleController)

        runOnMainSync {
            // drag to the same zone as bubble bar
            dragToBubbleController.onDragUpdate(rightDropRect.centerX(), rightDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        // should request displaying pin at right
        verify(bubbleController).showBubbleBarPinAtLocation(BubbleBarLocation.RIGHT)
    }

    @Test
    fun droppedItemWithIntentAtTheLeftDropZone_noBubblesOnTheRight_bubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = false, bubbleBarLocation = bubbleBarOriginalLocation)
        val pendingIntent = PendingIntent(mock<IIntentSender>())
        val userHandle = UserHandle(0)

        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onItemDropped(pendingIntent, userHandle)
        }

        verify(bubbleController)
            .expandStackAndSelectBubble(pendingIntent, userHandle, BubbleBarLocation.LEFT)
    }

    @Test
    fun droppedItemWithShortcutInfoAtTheLeftDropZone_noBubblesOnTheRight_bubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(hasBubbles = false, bubbleBarLocation = bubbleBarOriginalLocation)
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setLongLabel("Shortcut").build()

        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onItemDropped(shortcutInfo)
        }

        verify(bubbleController).expandStackAndSelectBubble(shortcutInfo, BubbleBarLocation.LEFT)
    }

    @Test
    fun itemDropped_withoutDragStarted_noBubbleCreationRequested() {
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setLongLabel("Shortcut").build()
        runOnMainSync { dragToBubbleController.onItemDropped(shortcutInfo) }

        assertThat(dropTargetContainer.childCount).isEqualTo(0)
        verify(bubbleController, never()).expandStackAndSelectBubble(any<ShortcutInfo>(), any())
        verify(bubbleController, never())
            .expandStackAndSelectBubble(any<PendingIntent>(), any(), any())
    }

    private fun runOnMainSync(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    }

    private fun prepareBubbleController(
        hasBubbles: Boolean = false,
        bubbleBarLocation: BubbleBarLocation = BubbleBarLocation.RIGHT,
    ) {
        bubbleController.stub {
            on { hasBubbles() } doReturn hasBubbles
            on { getBubbleBarLocation() } doReturn bubbleBarLocation
        }
    }

    private fun createDeviceConfig(
        isLargeScreen: Boolean = true,
        isSmallTablet: Boolean = false,
        isLandscape: Boolean = true,
        isRtl: Boolean = false,
        windowBounds: Rect = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
        insets: Insets = Insets.NONE,
    ) = DeviceConfig(isLargeScreen, isSmallTablet, isLandscape, isRtl, windowBounds, insets)

    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
    }
}
