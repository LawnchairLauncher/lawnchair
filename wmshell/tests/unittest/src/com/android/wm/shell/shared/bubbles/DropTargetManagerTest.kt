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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.CircleZone
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.RectZone
import com.android.wm.shell.shared.bubbles.DragZone.DropTargetRect
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFails
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [DropTargetManager]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DropTargetManagerTest {

    @get:Rule
    val animatorTestRule = AnimatorTestRule()

    private val context = getApplicationContext<Context>()
    private lateinit var dropTargetManager: DropTargetManager
    private lateinit var dragZoneChangedListener: FakeDragZoneChangedListener
    private lateinit var container: FrameLayout

    // create 3 drop zones that are horizontally next to each other
    // -------------------------------------------------
    // |               |               |               |
    // |    bubble     |               |    bubble     |
    // |               |    dismiss    |               |
    // |     left      |               |     right     |
    // |               |               |               |
    // -------------------------------------------------
    private val bubbleLeftDragZone =
        DragZone.Bubble.Left(
            bounds = RectZone(Rect(0, 0, 100, 100)),
            dropTarget = DropTargetRect(Rect(0, 0, 50, 200), cornerRadius = 30f)
        )
    private val bubbleLeftDragZoneOnlySecondDropTarget =
        DragZone.Bubble.Left(
            bounds = RectZone(Rect(0, 0, 100, 100)),
            dropTarget = null,
            secondDropTarget = DropTargetRect(Rect(0, 250, 50, 300), cornerRadius = 25f)
        )
    private val bubbleLeftDragZoneWithSecondDropTarget =
        DragZone.Bubble.Left(
            bounds = RectZone(Rect(0, 0, 100, 100)),
            dropTarget = DropTargetRect(Rect(0, 0, 50, 200), cornerRadius = 30f),
            secondDropTarget = DropTargetRect(Rect(0, 250, 50, 300), cornerRadius = 25f)
        )
    private val dismissDragZone =
        DragZone.Dismiss(bounds = CircleZone(x = 150, y = 50, radius = 50))
    private val bubbleRightDragZone =
        DragZone.Bubble.Right(
            bounds = RectZone(Rect(200, 0, 300, 100)),
            dropTarget = DropTargetRect(Rect(200, 0, 280, 150), cornerRadius = 30f)
        )
    private val bubbleRightDragZoneOnlySecondDropTarget =
        DragZone.Bubble.Right(
            bounds = RectZone(Rect(200, 0, 300, 100)),
            dropTarget = null,
            secondDropTarget = DropTargetRect(Rect(200, 200, 80, 280), cornerRadius = 25f)
        )
    private val bubbleRightDragZoneWithSecondDropTarget =
        DragZone.Bubble.Right(
            bounds = RectZone(Rect(200, 0, 300, 100)),
            dropTarget = DropTargetRect(Rect(200, 0, 280, 150), cornerRadius = 30f),
            secondDropTarget = DropTargetRect(Rect(200, 200, 80, 280), cornerRadius = 25f)
        )

    private val dropTargetView: DropTargetView
        get() = dropTargetManager.dropTargetView

    private val secondDropTargetView: DropTargetView?
        get() = dropTargetManager.secondDropTargetView

    @Before
    fun setUp() {
        container = FrameLayout(context)
        dragZoneChangedListener = FakeDragZoneChangedListener()
        dropTargetManager =
            DropTargetManager(context, container, dragZoneChangedListener)
    }

    @Test
    fun onDragStarted_notifiesInitialDragZone() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(dragZoneChangedListener.initialDragZone).isEqualTo(bubbleLeftDragZone)
    }

    @Test
    fun onDragStarted_missingExpectedDragZone_fails() {
        assertFails {
            dropTargetManager.onDragStarted(
                DraggedObject.Bubble(BubbleBarLocation.RIGHT),
                listOf(bubbleLeftDragZone)
            )
        }
    }

    @Test
    fun onDragUpdated_notifiesDragZoneChanged() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone, dismissDragZone)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZone.bounds.rect.centerX(),
                bubbleRightDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleLeftDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(bubbleRightDragZone)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                dismissDragZone.bounds.x,
                dismissDragZone.bounds.y
            )
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleRightDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(dismissDragZone)
    }

    @Test
    fun onDragUpdated_withinSameZone_doesNotNotify() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone, dismissDragZone)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZone.bounds.rect.centerX(),
                bubbleLeftDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isNull()
        assertThat(dragZoneChangedListener.toDragZone).isNull()
    }

    @Test
    fun onDragUpdated_outsideAllZones_notifiesDragZoneChanged() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        val pointX = 200
        val pointY = 200
        assertThat(bubbleLeftDragZone.contains(pointX, pointY)).isFalse()
        assertThat(bubbleRightDragZone.contains(pointX, pointY)).isFalse()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(pointX, pointY)
            assertThat(dragZone).isNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isNotNull()
        assertThat(dragZoneChangedListener.toDragZone).isNull()
    }

    @Test
    fun onDragUpdated_hasOverlappingZones_notifiesFirstDragZoneChanged() {
        // create a drag zone that spans across the width of all 3 drag zones, but extends below
        // them
        val splitDragZone = DragZone.Split.Left(bounds = RectZone(Rect(0, 0, 300, 200)))
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone, dismissDragZone, splitDragZone)
        )

        // drag to a point that is within both the bubble right zone and split zone
        val pointX = bubbleRightDragZone.bounds.rect.centerX()
        val pointY = bubbleRightDragZone.bounds.rect.centerY()
        assertThat(splitDragZone.contains(pointX, pointY)).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(pointX, pointY)
            assertThat(dragZone).isNotNull()
        }
        // verify we dragged to the bubble right zone because that has higher priority than split
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleLeftDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(bubbleRightDragZone)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZone.bounds.rect.centerX(),
                150 // below the bubble and dismiss drag zones but within split
            )
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleRightDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(splitDragZone)

        val dismissPointX = dismissDragZone.bounds.x
        val dismissPointY = dismissDragZone.bounds.y
        assertThat(splitDragZone.contains(dismissPointX, dismissPointY)).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(dismissPointX, dismissPointY)
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(splitDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(dismissDragZone)
    }

    @Test
    fun onDragUpdated_afterDragEnded_doesNotNotify() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone, dismissDragZone)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
        }
        val dragZone = dropTargetManager.onDragUpdated(
            bubbleRightDragZone.bounds.rect.centerX(),
            bubbleRightDragZone.bounds.rect.centerY()
        )
        assertThat(dragZone).isNull()
        assertThat(dragZoneChangedListener.fromDragZone).isNull()
        assertThat(dragZoneChangedListener.toDragZone).isNull()
    }

    @Test
    fun onDragStarted_dropTargetAddedToContainer() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
        assertThat(dropTargetView.alpha).isEqualTo(0)
    }

    @Test
    fun onDragEnded_dropTargetRemovedFromContainer() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun onDragEnded_dropTargetNotifies() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone, dismissDragZone)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZone.bounds.rect.centerX(),
                bubbleRightDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            dropTargetManager.onDragEnded()
        }
        assertThat(dragZoneChangedListener.endedDragZone).isEqualTo(bubbleRightDragZone)
    }

    @Test
    fun startNewDrag_beforeDropTargetRemoved() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
            // advance the timer by 50ms so the animation doesn't complete
            // needs to be < DropTargetManager.DROP_TARGET_ALPHA_OUT_DURATION
            animatorTestRule.advanceTimeBy(50)
        }
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragStarted(
                DraggedObject.Bubble(BubbleBarLocation.LEFT),
                listOf(bubbleLeftDragZone, bubbleRightDragZone)
            )
        }
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
    }

    @Test
    fun updateDragZone_withDropTarget_dropTargetUpdated() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(dismissDragZone, bubbleLeftDragZone, bubbleRightDragZone)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZone.bounds.rect.centerX(),
                bubbleRightDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1)
        verifyDropTargetPosition(bubbleRightDragZone.dropTarget!!.rect)
    }

    @Test
    fun updateDragZone_withoutDropTarget_dropTargetHidden() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(dismissDragZone, bubbleLeftDragZone, bubbleRightDragZone)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone =
                dropTargetManager.onDragUpdated(dismissDragZone.bounds.x, dismissDragZone.bounds.y)
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(0)
    }

    @Test
    fun updateDragZone_betweenZonesWithDropTarget_dropTargetUpdated() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(dismissDragZone, bubbleLeftDragZone, bubbleRightDragZone)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZone.bounds.rect.centerX(),
                bubbleRightDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1)
        verifyDropTargetPosition(bubbleRightDragZone.dropTarget!!.rect)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZone.bounds.rect.centerX(),
                bubbleLeftDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1)
        verifyDropTargetPosition(bubbleLeftDragZone.dropTarget!!.rect)
    }

    @Test
    fun onDragStarted_noInitialDragZone_notifiesInitialDragZoneNull() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(dragZoneChangedListener.initialDragZone).isNull()
    }

    @Test
    fun onDragStartedMultipleTimes_secondDropViewRemoved() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
    }

    @Test
    fun onDragUpdated_noZoneToZoneWithDropTargetView_listenerNotified() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZone.bounds.rect.centerX(),
                bubbleLeftDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
        }
        assertThat(dragZoneChangedListener.fromDragZone).isNull()
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(bubbleLeftDragZone)
    }

    @Test
    fun onDragUpdated_noZoneToZoneWithDropTargetView_dropTargetShown() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZone.bounds.rect.centerX(),
                bubbleLeftDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
        assertThat(dropTargetView.alpha).isEqualTo(1)
        assertThat(secondDropTargetView).isNull()
        verifyDropTargetPosition(bubbleLeftDragZone.dropTarget!!.rect)
    }

    @Test
    fun onDragUpdated_noZoneToZoneWithTwoDropTargetViews_dropTargetsShown() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerX(),
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        assertThat(dropTargetView.alpha).isEqualTo(1)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1)
        verifyDropTargetPosition(bubbleRightDragZoneWithSecondDropTarget.dropTarget!!.rect)
        verifyDropTargetPosition(
            secondDropTargetView!!,
            bubbleRightDragZoneWithSecondDropTarget.secondDropTarget!!.rect
        )
    }

    @Test
    fun onDragUpdated_noZoneToZoneWithOnlySecondDropTargetView_secondDropTargetShown() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneOnlySecondDropTarget, bubbleRightDragZoneOnlySecondDropTarget)
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleRightDragZoneOnlySecondDropTarget.bounds.rect.centerX(),
                bubbleRightDragZoneOnlySecondDropTarget.bounds.rect.centerY()
            )
            assertThat(dragZone).isNotNull()
            animatorTestRule.advanceTimeBy(250)
        }

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        assertThat(dropTargetView.alpha).isEqualTo(0)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1)
        verifyDropTargetPosition(
            secondDropTargetView!!,
            bubbleRightDragZoneOnlySecondDropTarget.secondDropTarget!!.rect
        )
    }

    @Test
    fun runOnDropTargetsRemoved_dropTargetViewsAdded_notExecutedUntilAllViewsRemoved() {
        var runnableExecuted = false
        val action = Runnable { runnableExecuted = true }
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        dropTargetManager.onDropTargetRemoved(action)
        assertThat(runnableExecuted).isFalse()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
        }
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        assertThat(runnableExecuted).isFalse()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(200)
        }

        assertThat(container.childCount).isEqualTo(0)
        assertThat(runnableExecuted).isTrue()
    }

    @Test
    fun onDropTargetsRemoved_dropTargetViewsAbsent_actionExecuted() {
        var runnableExecuted = false
        val action = Runnable { runnableExecuted = true }
        assertThat(container.childCount).isEqualTo(0)
        dropTargetManager.onDropTargetRemoved(action)
        assertThat(runnableExecuted).isTrue()
    }

    @Test
    fun onDropTargetsRemoved_NonDropTargetViewPresent_actionExecuted() {
        var runnableExecuted = false
        val action = Runnable { runnableExecuted = true }
        container.addView(View(context))
        assertThat(container.childCount).isEqualTo(1)
        dropTargetManager.onDropTargetRemoved(action)
        assertThat(runnableExecuted).isTrue()
    }

    @Test
    fun onDragUpdated_reEnterZoneWithMultipleDropTargetViews_dropTargetsShown() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )
        val pointX = 200
        val pointY = 200
        assertThat(bubbleLeftDragZone.contains(pointX, pointY)).isFalse()
        assertThat(bubbleRightDragZone.contains(pointX, pointY)).isFalse()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragUpdated(
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerX(),
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerY()
            )
            animatorTestRule.advanceTimeBy(250)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragUpdated(pointX, pointY)
            animatorTestRule.advanceTimeBy(250)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragUpdated(
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerX(),
                bubbleRightDragZoneWithSecondDropTarget.bounds.rect.centerY()
            )
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        assertThat(dropTargetView.alpha).isEqualTo(1)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1)
        verifyDropTargetPosition(bubbleRightDragZoneWithSecondDropTarget.dropTarget!!.rect)
        verifyDropTargetPosition(
            secondDropTargetView!!,
            bubbleRightDragZoneWithSecondDropTarget.secondDropTarget!!.rect
        )
    }

    @Test
    fun onDropTargetsRemoved_multipleManagers_actionExecutedOnlyWhenItsViewsAreRemoved() {
        var firstRunnableExecuted = false
        val firstAction = Runnable { firstRunnableExecuted = true }

        var secondRunnableExecuted = false
        val secondAction = Runnable { secondRunnableExecuted = true }

        // Create a second DropTargetManager sharing the same container
        val secondDropTargetManager =
            DropTargetManager(context, container, FakeDragZoneChangedListener())

        val randomViewsCount = 10

        // Add some random views to the shared container
        repeat(randomViewsCount) {
            container.addView(View(context))
        }

        // First manager starts a drag, adds its views
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget)
        )
        assertThat(container.childCount)
            .isEqualTo(randomViewsCount + DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
        dropTargetManager.onDropTargetRemoved(firstAction)
        assertThat(firstRunnableExecuted).isFalse() // Manager 1 views are still there

        // Second manager starts a drag, adds its views
        secondDropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleRightDragZoneWithSecondDropTarget)
        )
        // Now container has views from both managers
        assertThat(container.childCount)
            .isEqualTo(randomViewsCount + DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS * 2)
        secondDropTargetManager.onDropTargetRemoved(secondAction)
        assertThat(secondRunnableExecuted).isFalse() // Second manager views are still there

        // First manager ends its drag
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
            animatorTestRule.advanceTimeBy(250) // Ensure fade out completes
        }

        // First manager's views should be removed, its runnable executed.
        // Second manager's views are still present.
        assertThat(firstRunnableExecuted).isTrue()
        assertThat(secondRunnableExecuted).isFalse()
        // Count should be only manager 2's views
        assertThat(container.childCount)
            .isEqualTo(randomViewsCount + DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)

        // Second manager ends its drag
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            secondDropTargetManager.onDragEnded()
            animatorTestRule.advanceTimeBy(250) // Ensure fade out completes
        }

        // Second manager's views should be removed, its runnable executed.
        assertThat(secondRunnableExecuted).isTrue()
        assertThat(container.childCount).isEqualTo(randomViewsCount) // All managers views removed
    }

    @Test
    fun hideDropTargets_whenInAZone_notifiesAndHidesDropTarget() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = false),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        // Initially, drag into the left zone
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZone.bounds.rect.centerX(),
                bubbleLeftDragZone.bounds.rect.centerY()
            )
            assertThat(dragZone).isEqualTo(bubbleLeftDragZone)
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(bubbleLeftDragZone)

        // Call hideDropTargets
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.hideDropTargets()
            animatorTestRule.advanceTimeBy(250)
        }

        // Verify listener was notified of leaving the zone
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleLeftDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isNull()
        // Verify drop target is hidden and container still has the view
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
    }

    @Test
    fun hideDropTargets_whenInAZoneWithSecondDropTarget_notifiesAndHidesBothDropTargets() {
        dropTargetManager.onDragStarted(
            DraggedObject.LauncherIcon(showBubbleBarPillowDropTarget = true),
            listOf(bubbleLeftDragZoneWithSecondDropTarget, bubbleRightDragZoneWithSecondDropTarget)
        )
        // Initially, drag into the left zone
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                bubbleLeftDragZoneWithSecondDropTarget.bounds.rect.centerX(),
                bubbleLeftDragZoneWithSecondDropTarget.bounds.rect.centerY()
            )
            assertThat(dragZone).isEqualTo(bubbleLeftDragZoneWithSecondDropTarget)
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1f)
        assertThat(dragZoneChangedListener.toDragZone).isEqualTo(
            bubbleLeftDragZoneWithSecondDropTarget
        )

        // Call hideDropTargets
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.hideDropTargets()
            animatorTestRule.advanceTimeBy(250)
        }

        // Verify listener was notified of leaving the zone
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(
            bubbleLeftDragZoneWithSecondDropTarget
        )
        assertThat(dragZoneChangedListener.toDragZone).isNull()
        // Verify drop targets are hidden and container still has the views
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(0f)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS)
    }

    @Test
    fun hideDropTargets_whenAlreadyOutsideZones_doesNothingAndDoesNotNotify() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        // Initially, drag outside all zones
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dragZone = dropTargetManager.onDragUpdated(
                500, // outside any defined zone
                500
            )
            assertThat(dragZone).isNull()
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(dragZoneChangedListener.fromDragZone).isEqualTo(bubbleLeftDragZone)
        assertThat(dragZoneChangedListener.toDragZone).isNull()

        // Reset listener state
        dragZoneChangedListener.fromDragZone = null
        dragZoneChangedListener.toDragZone = null

        // Call hideDropTargets
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // No animation should occur as it's already hidden
            dropTargetManager.hideDropTargets()
        }

        // Verify listener was NOT notified again (already outside)
        assertThat(dragZoneChangedListener.fromDragZone).isNull()
        assertThat(dragZoneChangedListener.toDragZone).isNull()
        // Verify drop target remains hidden
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
    }

    @Test
    fun hideDropTargets_dragNotStarted_doesNothing() {
        // Call hideDropTargets
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.hideDropTargets()
        }

        // Verify no listener calls
        assertThat(dragZoneChangedListener.initialDragZone).isNull()
        assertThat(dragZoneChangedListener.fromDragZone).isNull()
        assertThat(dragZoneChangedListener.toDragZone).isNull()
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun hideDropTargets_afterDragEnded_doesNothing() {
        dropTargetManager.onDragStarted(
            DraggedObject.Bubble(BubbleBarLocation.LEFT),
            listOf(bubbleLeftDragZone, bubbleRightDragZone)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.onDragEnded()
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(container.childCount).isEqualTo(0) // Views removed on drag end

        // Call hideDropTargets
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dropTargetManager.hideDropTargets()
        }

        // Drop target alpha is irrelevant as views are removed.
        // Listener should not be affected further.
        assertThat(dragZoneChangedListener.endedDragZone).isEqualTo(bubbleLeftDragZone) // From onDragEnded
    }

    private fun verifyDropTargetPosition(rect: Rect) {
        verifyDropTargetPosition(dropTargetView, rect)
    }

    private fun verifyDropTargetPosition(dropTargetView: DropTargetView, rect: Rect) {
        assertThat(dropTargetView.getRect().left).isEqualTo(rect.left)
        assertThat(dropTargetView.getRect().top).isEqualTo(rect.top)
        assertThat(dropTargetView.getRect().right).isEqualTo(rect.right)
        assertThat(dropTargetView.getRect().bottom).isEqualTo(rect.bottom)
    }

    private class FakeDragZoneChangedListener : DropTargetManager.DragZoneChangedListener {
        var initialDragZone: DragZone? = null
        var fromDragZone: DragZone? = null
        var toDragZone: DragZone? = null
        var endedDragZone: DragZone? = null
        var draggedObject: DraggedObject? = null

        override fun onInitialDragZoneSet(dragZone: DragZone?) {
            initialDragZone = dragZone
        }

        override fun onDragZoneChanged(
            draggedObject: DraggedObject,
            from: DragZone?,
            to: DragZone?
        ) {
            this.draggedObject = draggedObject
            fromDragZone = from
            toDragZone = to
        }

        override fun onDragEnded(zone: DragZone?) {
            endedDragZone = zone
        }
    }

    companion object {
        const val DROP_VIEWS_COUNT = 1
        const val DROP_VIEWS_COUNT_FOR_TWO_DROP_TARGETS = 2
    }
}