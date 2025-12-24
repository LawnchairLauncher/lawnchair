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
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DropTargetView
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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
        dragToBubbleController = DragToBubbleController(context, bubbleController)
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
    fun dragStarted_isDropHandled_cleared() {
        dragToBubbleController.isDropHandled = true

        dragToBubbleController.onDragStarted()

        // Once drag is started again isDropHandled should be cleared
        assertThat(dragToBubbleController.isDropHandled).isFalse()
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
    fun droppedItemWithIntentAtTheLeftDropZone_noBubblesOnTheRight_bubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(bubbleBarLocation = bubbleBarOriginalLocation)
        val pendingIntent = PendingIntent(mock<IIntentSender>())
        val userHandle = UserHandle(0)

        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onItemDropped(pendingIntent, userHandle)
        }

        verify(bubbleController)
            .expandStackAndSelectBubble(
                pendingIntent,
                userHandle,
                EntryPoint.TASKBAR_ICON_DRAG,
                BubbleBarLocation.LEFT,
            )
        assertThat(dragToBubbleController.isDropHandled).isTrue()
    }

    @Test
    fun droppedItemWithShortcutInfoAtTheLeftDropZone_noBubblesOnTheRight_bubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(bubbleBarLocation = bubbleBarOriginalLocation)
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setLongLabel("Shortcut").build()

        dragToBubbleController.onDragStarted()

        runOnMainSync {
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onItemDropped(shortcutInfo)
        }

        verify(bubbleController)
            .expandStackAndSelectBubble(
                shortcutInfo,
                EntryPoint.TASKBAR_ICON_DRAG,
                BubbleBarLocation.LEFT,
            )
        assertThat(dragToBubbleController.isDropHandled).isTrue()
    }

    @Test
    fun droppedItem_afterNewDragStartedOnItemDropCleared() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleController(bubbleBarLocation = bubbleBarOriginalLocation)
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setLongLabel("Shortcut").build()
        runOnMainSync {
            dragToBubbleController.onDragStarted()
            dragToBubbleController.onDragUpdate(leftDropRect.centerX(), leftDropRect.centerY())
            dragToBubbleController.onItemDropped(shortcutInfo)
            assertThat(dragToBubbleController.isDropHandled).isTrue()
            dragToBubbleController.onDragEnded()
            animatorTestRule.advanceTimeBy(250)

            dragToBubbleController.onDragStarted()
        }
        assertThat(dragToBubbleController.isDropHandled).isFalse()
    }

    @Test
    fun itemDropped_withoutDragStarted_noBubbleCreationRequested() {
        val shortcutInfo = ShortcutInfo.Builder(context, "id").setLongLabel("Shortcut").build()
        runOnMainSync { dragToBubbleController.onItemDropped(shortcutInfo) }

        assertThat(dropTargetContainer.childCount).isEqualTo(0)
        verify(bubbleController, never())
            .expandStackAndSelectBubble(
                any<ShortcutInfo>(),
                eq(EntryPoint.TASKBAR_ICON_DRAG),
                any(),
            )
        verify(bubbleController, never())
            .expandStackAndSelectBubble(any<PendingIntent>(), any(), any(), any())
    }

    @Test
    fun hideDropTargets_dragEnteredDropZone_dropTargetsHidden() {
        // Given
        dragToBubbleController.onDragStarted()
        runOnMainSync {
            dragToBubbleController.onDragUpdate(rightDropRect.centerX(), rightDropRect.centerY())
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(dropTargetContainer.childCount).isEqualTo(1)
        assertThat(dropTargetView.alpha).isEqualTo(1f)

        // When
        runOnMainSync {
            dragToBubbleController.hideDropTargets()
            animatorTestRule.advanceTimeBy(250)
        }

        // Then
        assertThat(dropTargetView.alpha).isEqualTo(0f)
    }

    private fun runOnMainSync(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    }

    private fun prepareBubbleController(
        bubbleBarLocation: BubbleBarLocation = BubbleBarLocation.RIGHT,
    ) {
        bubbleController.stub {
            on { getBubbleBarLocation() } doReturn bubbleBarLocation
        }
    }
}
