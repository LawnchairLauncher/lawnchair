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

import com.android.wm.shell.shared.bubbles.BubbleBarLocation.Companion.isDifferentSides
import com.android.wm.shell.shared.bubbles.DropTargetManager.DragZoneChangedListener

/**
 * Class that encapsulates common logic of reacting to the drag zone changes for dragging launcher
 * icons to the bubble bar.
 */
class DragToBubblesZoneChangeListener(
    private val isRtl: Boolean,
    private val callback: Callback,
) : DragZoneChangedListener {

    private var lastUpdateLocation: BubbleBarLocation? = null
    private val isLocationChangedFromOriginal: Boolean
        get() = lastUpdateLocation != null
                && isDifferentSides(
            lastUpdateLocation,
            callback.getStartingBubbleBarLocation(),
            isRtl
        )

    override fun onInitialDragZoneSet(dragZone: DragZone?) {}

    override fun onDragZoneChanged(
        draggedObject: DraggedObject,
        from: DragZone?,
        to: DragZone?,
    ) {
        processLocationUpdate(to.toBubbleBarLocation())
    }

    override fun onDragEnded(zone: DragZone?) {
        processLocationUpdate(updateLocation = null)
    }

    private fun processLocationUpdate(updateLocation: BubbleBarLocation?) {
        actOnLocationUpdate(updateLocation)
        lastUpdateLocation = updateLocation
    }

    private fun actOnLocationUpdate(updateLocation: BubbleBarLocation?) {
        val updatedBefore = lastUpdateLocation != null
        val originalLocation = callback.getStartingBubbleBarLocation()
        val isLocationUpdated = isDifferentSides(lastUpdateLocation, updateLocation, isRtl)
        if (shouldNotifyZoneChanged(updateLocation)) {
            callback.onDragEnteredLocation(updateLocation)
        }
        if (!callback.hasBubbles()) {
            // has no bubbles, so showing the pin view
            if (updateLocation == null || !updatedBefore || isLocationUpdated) {
                callback.bubbleBarPillowShownAtLocation(updateLocation)
            }
            return
        }
        if (updateLocation == null) {
            if (isLocationChangedFromOriginal) {
                callback.animateBubbleBarLocation(originalLocation)
            }
            return
        }
        if (updatedBefore && isLocationUpdated) {
            // updated before and location updated - update to new location
            callback.animateBubbleBarLocation(updateLocation)
            return
        }
        if (!updatedBefore && isDifferentSides(originalLocation, updateLocation, isRtl)) {
            // not updated before and location changed from original
            callback.animateBubbleBarLocation(updateLocation)
        }
    }

    private fun DragZone?.toBubbleBarLocation(): BubbleBarLocation? {
        return when (this) {
            is DragZone.Bubble.Left -> BubbleBarLocation.LEFT
            is DragZone.Bubble.Right -> BubbleBarLocation.RIGHT
            else -> null
        }
    }

    private fun shouldNotifyZoneChanged(updateLocation: BubbleBarLocation?): Boolean {
        // Notify if one is null and the other isn't (entering/exiting a general zone area)
        // OR if both are non-null and they represent different sides.
        return (lastUpdateLocation == null) != (updateLocation == null) ||
                isDifferentSides(lastUpdateLocation, updateLocation, isRtl)
    }

    /**
     * Callback interface for {@link DragToBubblesZoneChangeListener} to communicate drag-related
     * events and request actions on the bubble bar.
     * The primary purpose of this callback is to decouple the generic drag zone detection logic
     * within {@code DragToBubblesZoneChangeListener} from the specific UI implementation details
     * of the bubble bar.
     */
    interface Callback {
        /** The starting bubble bar location before the drag started. */
        fun getStartingBubbleBarLocation(): BubbleBarLocation

        /** Check if the bubble bar has any bubbles. */
        fun hasBubbles(): Boolean

        /** Called when need to animate the bubble bar location. */
        fun animateBubbleBarLocation(bubbleBarLocation: BubbleBarLocation) {}

        // TODO(b/411505605) remove all related code
        /** Called when the bubble bar pillow view is shown at position. */
        fun bubbleBarPillowShownAtLocation(bubbleBarLocation: BubbleBarLocation?) {}

        /**
         * Called when a drag operation enters or exits a bubble bar location.
         *
         * @param bubbleBarLocation The [BubbleBarLocation] that the drag operation has entered.
         *                          This will be non-null if the drag has entered a valid bubble bar
         *                          location. It will be `null` if the drag operation has exited
         *                          all bubble bar locations. Values are guaranteed to be distinct.
         */
        fun onDragEnteredLocation(bubbleBarLocation: BubbleBarLocation?) {}
    }
}
