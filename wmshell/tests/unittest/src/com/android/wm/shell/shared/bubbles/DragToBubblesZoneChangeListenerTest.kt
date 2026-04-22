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
import android.graphics.Insets
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/** Unit tests for [DragToBubblesZoneChangeListener]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragToBubblesZoneChangeListenerTest {

    private var callback: DragToBubblesZoneChangeListener.Callback = mock()

    private lateinit var dragToBubbleZoneChangeListener: DragToBubblesZoneChangeListener

    private val draggedObject = DraggedObject.LauncherIcon(bubbleBarHasBubbles = false) {}
    private val context = getApplicationContext<Context>()
    private val dragZoneFactory: DragZoneFactory = createTestDragZoneFactory()
    // Define zones for testing
    private val dragZones = dragZoneFactory.createSortedDragZones(draggedObject)
    private val leftDragZone = dragZones.find { it is DragZone.Bubble.Left }
    private val rightDragZone = dragZones.find { it is DragZone.Bubble.Right }

    @Before
    fun setUp() {
        dragToBubbleZoneChangeListener = createDragToBubblesZoneChangeListener()
    }

    // --- Tests for scenarios WITHOUT existing bubbles ---

    @Test
    fun onDragStarted_noBubbles_noImmediateCallbackInvocation() {
        // given no bubbles and starting location is right
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)

        // when drag started
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // then callbacks are not notified
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
        verify(callback, never()).animateBubbleBarLocation(any())
        verify(callback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragMoved_noBubbles_enterLeftZone_pillowShownLeftAndDragEnteredLeft() {
        // given no bubbles and starting location is right
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // when enter left drag zone
        dragToBubbleZoneChangeListener.onDragZoneChanged(draggedObject, from = null, leftDragZone)

        // then callback is notified
        verify(callback).bubbleBarPillowShownAtLocation(BubbleBarLocation.LEFT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        verify(callback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragZoneChanged_noBubbles_enterRightZone_pillowShownRightAndDragEnteredRight() {
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.LEFT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // when enter right drag zone
        dragToBubbleZoneChangeListener.onDragZoneChanged(draggedObject, from = null, rightDragZone)

        // then callback is notified
        verify(callback).bubbleBarPillowShownAtLocation(BubbleBarLocation.RIGHT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
        verify(callback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragZoneChanged_noBubbles_moveToBetweenZones_afterEnteringLeft_pillowHiddenAndDragEnteredNull() {
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)
        // Enter left zone first
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        )
        reset(callback) // Focus on the exit behavior
        // Re-mock after reset
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)

        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = leftDragZone,
            to = null,
        )

        verify(callback).bubbleBarPillowShownAtLocation(null)
        verify(callback).onDragEnteredLocation(null)
        verify(callback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragZoneChanged_noBubbles_moveFromLeftToRightZone_callbacksOrderedCorrectly() {
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // Move into Left
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        )
        // When Move into Right
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = leftDragZone,
            to = rightDragZone,
        )

        // Then callback is notified as correctly, first entering Left
        verify(callback).bubbleBarPillowShownAtLocation(BubbleBarLocation.LEFT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        // Then entering Right
        verify(callback).bubbleBarPillowShownAtLocation(BubbleBarLocation.RIGHT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
        verify(callback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragEnded_noBubbles_whenInLeftZone_pillowHiddenAndDragEnteredNull() {
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        ) // In left zone
        reset(callback) // Focus on onDragEnded
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT) // Re-stub

        dragToBubbleZoneChangeListener.onDragEnded(leftDragZone)

        verify(callback).bubbleBarPillowShownAtLocation(null)
        verify(callback).onDragEnteredLocation(null)
        verify(callback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragZoneChanged_noBubbles_repeatedlyInSameZone_callbacksOnlyOncePerEntry() {
        stubCallback(hasBubbles = false, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        repeat(3) {
            dragToBubbleZoneChangeListener.onDragZoneChanged(
                draggedObject,
                from = null,
                to = leftDragZone,
            ) // In left zone
        }

        verify(callback, times(1)).bubbleBarPillowShownAtLocation(BubbleBarLocation.LEFT)
        verify(callback, times(1)).onDragEnteredLocation(BubbleBarLocation.LEFT)
    }

    // --- Tests for scenarios WITH existing bubbles ---

    @Test
    fun onDragStarted_hasBubbles_noImmediateCallbackInvocation() {
        // given no bubbles and starting location is right
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)

        // when drag started
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // then callbacks are not notified
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
        verify(callback, never()).animateBubbleBarLocation(any())
        verify(callback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragMoved_hasBubbles_enterLeftZone_animateLocationAndDragEnteredLeft() {
        // given no bubbles and starting location is right
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // when enter left drag zone
        dragToBubbleZoneChangeListener.onDragZoneChanged(draggedObject, from = null, leftDragZone)

        // then callback is notified
        verify(callback).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
    }

    @Test
    fun onDragZoneChanged_hasBubbles_enterRightZone_animateRightAndDragEnteredRight() {
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.LEFT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // when enter right drag zone
        dragToBubbleZoneChangeListener.onDragZoneChanged(draggedObject, from = null, rightDragZone)

        // then callback is notified
        verify(callback).animateBubbleBarLocation(BubbleBarLocation.RIGHT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
    }

    @Test
    fun onDragZoneChanged_hasBubbles_moveToBetweenZones_animateLocationBackAndDragEnteredNull() {
        val startingLocation = BubbleBarLocation.RIGHT
        stubCallback(hasBubbles = true, startingLocation = startingLocation)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)
        // Enter left zone first
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        )
        reset(callback) // Focus on the exit behavior
        // Re-mock after reset
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)

        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = leftDragZone,
            to = null,
        )

        verify(callback).animateBubbleBarLocation(startingLocation)
        verify(callback).onDragEnteredLocation(null)
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
    }

    @Test
    fun onDragZoneChanged_hasBubbles_moveFromLeftToRightZone_callbacksOrderedCorrectly() {
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        // Move into Left
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        )
        // When Move into Right
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = leftDragZone,
            to = rightDragZone,
        )

        // Then callback is notified as correctly, first entering Left
        verify(callback).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        // Then entering Right
        verify(callback).animateBubbleBarLocation(BubbleBarLocation.RIGHT)
        verify(callback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
    }

    @Test
    fun onDragEnded_hasBubbles_whenInLeftZone_animateLocationAndDragEnteredNull() {
        val startingLocation = BubbleBarLocation.RIGHT
        stubCallback(hasBubbles = true, startingLocation = startingLocation)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)
        dragToBubbleZoneChangeListener.onDragZoneChanged(
            draggedObject,
            from = null,
            to = leftDragZone,
        ) // In left zone
        reset(callback) // Focus on onDragEnded
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT) // Re-stub

        dragToBubbleZoneChangeListener.onDragEnded(leftDragZone)

        verify(callback).animateBubbleBarLocation(startingLocation)
        verify(callback).onDragEnteredLocation(null)
        verify(callback, never()).bubbleBarPillowShownAtLocation(any())
    }

    @Test
    fun onDragZoneChanged_hasBubbles_repeatedlyInSameZone_callbacksOnlyOncePerEntry() {
        stubCallback(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)
        dragToBubbleZoneChangeListener.onInitialDragZoneSet(dragZone = null)

        repeat(3) {
            dragToBubbleZoneChangeListener.onDragZoneChanged(
                draggedObject,
                from = null,
                to = leftDragZone,
            ) // In left zone
        }

        verify(callback, times(1)).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(callback, times(1)).onDragEnteredLocation(BubbleBarLocation.LEFT)
    }

    private fun createDragToBubblesZoneChangeListener(
        isRtl: Boolean = false
    ): DragToBubblesZoneChangeListener {
        return DragToBubblesZoneChangeListener(isRtl, callback)
    }

    private fun stubCallback(
        hasBubbles: Boolean = true,
        startingLocation: BubbleBarLocation = BubbleBarLocation.LEFT,
    ) {
        stub {
            on(callback.hasBubbles()).thenReturn(hasBubbles)
            on(callback.getStartingBubbleBarLocation()).thenReturn(startingLocation)
        }
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
            object : DragZoneFactory.BubbleBarPropertiesProvider {},
        )
    }

    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
    }
}
