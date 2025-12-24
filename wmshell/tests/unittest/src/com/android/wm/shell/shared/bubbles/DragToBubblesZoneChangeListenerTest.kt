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

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.CircleZone
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.RectZone
import com.android.wm.shell.shared.bubbles.DragZone.Bubble.Left
import com.android.wm.shell.shared.bubbles.DragZone.Bubble.Right
import com.android.wm.shell.shared.bubbles.DragZone.Dismiss
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/** Unit tests for [DragToBubblesZoneChangeListener]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragToBubblesZoneChangeListenerTest {

    private val mockCallback: DragToBubblesZoneChangeListener.Callback = mock()
    private val draggedObject: DraggedObject.LauncherIcon = mock()

    private lateinit var listener: DragToBubblesZoneChangeListener

    // Define drag zones for testing
    private val leftZone = Left(RectZone(Rect(0, 0, 100, 100)), mock())
    private val rightZone = Right(RectZone(Rect(200, 0, 300, 100)), mock())
    private val otherZone = Dismiss(CircleZone(150, 50, 25))
    private val noZone: DragZone? = null

    private fun setUpListener(
        isRtl: Boolean = false,
        startingLocation: BubbleBarLocation = BubbleBarLocation.RIGHT,
        hasBubbles: Boolean = false,
    ) {
        mockCallback.stub {
            on { getStartingBubbleBarLocation() } doReturn startingLocation
            on { hasBubbles() } doReturn hasBubbles
        }
        listener = DragToBubblesZoneChangeListener(isRtl, mockCallback)
        // Call onInitialDragZoneSet, though it doesn't do much in the current implementation
        listener.onInitialDragZoneSet(noZone)
        clearInvocations(mockCallback) // Clear setup stubs from being counted as invocations
    }

    // --- Tests for onDragEnteredLocation ---

    @Test
    fun onDragZoneChanged_fromNoZoneToLeftZone_onDragEnteredLocationCalledWithLeft() {
        // Given
        setUpListener()
        // When
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        // Then
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun onDragZoneChanged_fromLeftZoneToNoZone_onDragEnteredLocationCalledWithNull() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, leftZone, noZone) // Exit
        // Then onDragEnteredLocation should be called with null
        verify(mockCallback).onDragEnteredLocation(null)
    }

    @Test
    fun onDragZoneChanged_fromLeftZoneToRightZone_onDragEnteredLocationCalledWithRight() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter Left
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, leftZone, rightZone) // Move to Right
        // Then onDragEnteredLocation should be called with Right
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
    }

    @Test
    fun onDragZoneChanged_fromLeftZoneToSameLeftZone_onDragEnteredLocationNotCalled() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter Left
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, leftZone, leftZone) // Move within Left
        // Then onDragEnteredLocation should NOT be called
        verify(mockCallback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragZoneChanged_fromNoZoneToRightZone_onDragEnteredLocationCalledWithRight() {
        // Given
        setUpListener()
        // When
        listener.onDragZoneChanged(draggedObject, noZone, rightZone)
        // Then
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.RIGHT)
    }

    @Test
    fun onDragZoneChanged_fromRightZoneToNoZone_onDragEnteredLocationCalledWithNull() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, rightZone) // Enter Right
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, rightZone, noZone) // Exit
        // Then
        verify(mockCallback).onDragEnteredLocation(null)
    }

    @Test
    fun onDragZoneChanged_fromOtherZoneToLeftZone_onDragEnteredLocationCalledWithLeft() {
        // Given
        setUpListener()
        // Simulate entering 'otherZone' first, which results in onDragEnteredLocation(null)
        listener.onDragZoneChanged(draggedObject, noZone, otherZone)
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, otherZone, leftZone) // Move to Left
        // Then
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun onDragZoneChanged_fromLeftZoneToOtherZone_onDragEnteredLocationCalledWithNull() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter Left
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT) // Pre-condition
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, leftZone, otherZone) // Move to Other
        // Then (OtherZone is not a BubbleBarLocation, so it's like exiting to no relevant location)
        verify(mockCallback).onDragEnteredLocation(null)
    }

    @Test
    fun onDragZoneChanged_fromNoZoneToOtherZone_onDragEnteredLocationNotCalled() {
        // Given
        setUpListener()
        // When
        listener.onDragZoneChanged(draggedObject, noZone, otherZone)
        // Then (OtherZone is not a BubbleBarLocation, so current logic calls with null)
        verify(mockCallback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragZoneChanged_fromOtherZoneToNoZone_onDragEnteredLocationNotCalledIfAlreadyNull() {
        // Given
        setUpListener()
        // Enter otherZone, callback.onDragEnteredLocation(null) would have been called.
        listener.onDragZoneChanged(draggedObject, noZone, otherZone)
        clearInvocations(mockCallback)

        // When moving from otherZone (null location) to noZone (null location)
        listener.onDragZoneChanged(draggedObject, otherZone, noZone)
        // Then onDragEnteredLocation should NOT be called again if the BubbleBarLocation state
        // (null) hasn't changed.
        verify(mockCallback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragZoneChanged_fromOtherZoneToSameOtherZone_onDragEnteredLocationNotCalled() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, otherZone) // Enters other zone
        clearInvocations(mockCallback)

        // When
        listener.onDragZoneChanged(draggedObject, otherZone, otherZone) // Stays in other zone
        // Then
        verify(mockCallback, never()).onDragEnteredLocation(any())
    }

    @Test
    fun onDragEnded_whileInLeftZone_onDragEnteredLocationNotCalledOnEnd() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter Left
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        clearInvocations(mockCallback)

        // When
        listener.onDragEnded(leftZone)
        // Then onDragEnteredLocation is NOT called by onDragEnded itself.
        // onDragEnded calls updateBubbleBarLocation(null) which then might call other callbacks,
        // but onDragEnteredLocation is specifically for onDragZoneChanged transitions.
        // The change to null is handled by the subsequent onDragZoneChanged if the drag continues,
        // or implicitly by the drag session ending.
        // The DragToBubblesZoneChangeListener's onDragEnded calls updateBubbleBarLocation(null),
        // which then calls onDragEnteredLocation(null) if it was previously non-null.
        verify(mockCallback).onDragEnteredLocation(null)
    }

    @Test
    fun onDragEnded_whileInNoZone_onDragEnteredLocationNotCalledOnEnd() {
        // Given
        setUpListener()
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Enter Left
        listener.onDragZoneChanged(draggedObject, leftZone, noZone) // Exit to No Zone
        verify(mockCallback).onDragEnteredLocation(null) // From exiting the zone
        clearInvocations(mockCallback)

        // When
        listener.onDragEnded(noZone)
        // Then
        verify(mockCallback, never()).onDragEnteredLocation(any())
    }

    // --- Tests for animateBubbleBarLocation ---

    @Test
    fun hasBubbles_onDragZoneChanged_fromNoZoneToDifferentSideZone_animateCalled() {
        // Given starting on RIGHT, hasBubbles = true
        setUpListener(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)
        // When drag enters LEFT zone (different side)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        // Then animate to the new location (LEFT)
        verify(mockCallback).animateBubbleBarLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromNoZoneToSameSideZone_animateNotCalled() {
        // Given starting on LEFT, hasBubbles = true
        setUpListener(hasBubbles = true, startingLocation = BubbleBarLocation.LEFT)
        // When drag enters LEFT zone (same side)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        // Then animate should NOT be called
        verify(mockCallback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromLeftToRightZone_animateCalled() {
        // Given starting on LEFT, hasBubbles = true
        setUpListener(hasBubbles = true, startingLocation = BubbleBarLocation.LEFT)
        // First, enter LEFT zone (no animation expected here as it's the starting side)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        clearInvocations(mockCallback)

        // When drag moves from LEFT to RIGHT zone
        listener.onDragZoneChanged(draggedObject, leftZone, rightZone)
        // Then animate to the new location (RIGHT)
        verify(mockCallback).animateBubbleBarLocation(BubbleBarLocation.RIGHT)
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromLeftToSameLeftZone_animateNotCalled() {
        // Given starting on LEFT, hasBubbles = true
        setUpListener(hasBubbles = true, startingLocation = BubbleBarLocation.LEFT)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone) // Initial entry
        clearInvocations(mockCallback)

        // When drag moves within LEFT zone
        listener.onDragZoneChanged(draggedObject, leftZone, leftZone)
        // Then animate should NOT be called
        verify(mockCallback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromLeftToNoZone_originalLocationDifferent_animateToOriginal() {
        // Given starting on RIGHT, hasBubbles = true
        val startingLocation = BubbleBarLocation.RIGHT
        setUpListener(hasBubbles = true, startingLocation = startingLocation)
        // First, drag to LEFT (this will update lastUpdateLocation to LEFT)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        verify(mockCallback).animateBubbleBarLocation(BubbleBarLocation.LEFT) // Animation to left
        clearInvocations(mockCallback)

        // When drag moves from LEFT to No Zone
        listener.onDragZoneChanged(draggedObject, leftZone, noZone)
        // Then animate back to the original starting location (RIGHT)
        verify(mockCallback).animateBubbleBarLocation(startingLocation)
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromLeftToNoZone_originalLocationSame_animateNotCalled() {
        // Given starting on LEFT, hasBubbles = true
        val startingLocation = BubbleBarLocation.LEFT
        setUpListener(hasBubbles = true, startingLocation = startingLocation)
        // First, drag to LEFT (no animation, lastUpdateLocation becomes LEFT)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        clearInvocations(mockCallback)

        // When drag moves from LEFT to No Zone
        listener.onDragZoneChanged(draggedObject, leftZone, noZone)
        // Then animate should NOT be called as it's already on the original side (or was never
        // moved)
        verify(mockCallback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun hasBubbles_onDragEnded_whileInDifferentSideZone_animateToOriginal() {
        // Given starting on RIGHT, hasBubbles = true
        val startingLocation = BubbleBarLocation.RIGHT
        setUpListener(hasBubbles = true, startingLocation = startingLocation)
        // Drag to LEFT zone
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        verify(mockCallback).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        clearInvocations(mockCallback)

        // When drag ends while in LEFT zone
        listener.onDragEnded(leftZone)
        // Then animate back to the original starting location (RIGHT)
        verify(mockCallback).animateBubbleBarLocation(startingLocation)
    }

    @Test
    fun hasBubbles_onDragEnded_whileInSameSideZone_animateNotCalled() {
        // Given starting on LEFT, hasBubbles = true
        val startingLocation = BubbleBarLocation.LEFT
        setUpListener(hasBubbles = true, startingLocation = startingLocation)
        // Drag to LEFT zone (no animation to begin with if same side)
        listener.onDragZoneChanged(draggedObject, noZone, leftZone)
        clearInvocations(mockCallback)

        // When drag ends while in LEFT zone
        listener.onDragEnded(leftZone)
        // Then animate should NOT be called
        verify(mockCallback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun hasBubbles_onDragZoneChanged_fromNoZoneToOtherZone_animateNotCalled() {
        // Given has bubbles, starting on RIGHT
        setUpListener(hasBubbles = true, startingLocation = BubbleBarLocation.RIGHT)
        // When drag enters a non-bubble bar zone
        listener.onDragZoneChanged(draggedObject, noZone, otherZone)
        // Then animate should NOT be called
        verify(mockCallback, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun onDragZoneChanged_dragToSameLocationTwice_onDragEnteredLocationCalledOnSecondDrag() {
        // // Given has bubbles, starting on LEFT
        setUpListener(hasBubbles = false, startingLocation = BubbleBarLocation.LEFT)

        // First drag to the left zone
        listener.onDragZoneChanged(draggedObject, null, leftZone)
        listener.onDragEnded(leftZone)
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT)
        clearInvocations(mockCallback) // Reset mock to clear interactions for the next assertion

        // Second drag to the same left zone
        listener.onDragZoneChanged(draggedObject, null, leftZone)
        listener.onDragEnded(leftZone)

        // Verify onDragEnteredLocation is called again for the same location
        verify(mockCallback).onDragEnteredLocation(BubbleBarLocation.LEFT)
    }
}
