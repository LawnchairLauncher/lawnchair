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

import android.graphics.Rect
import android.view.View
import com.android.launcher3.DropTarget
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.wm.shell.shared.bubbles.BubbleBarLocation

/**
 * Implementation of the {@link DropTarget} that handles drag and drop events over the bubble bar
 * locations.
 */
class BubbleBarLocationDropTarget(
    private val bubbleBarLocation: BubbleBarLocation,
    private val bubbleBarDragListener: BubbleBarDragListener,
) : DropTarget {

    /** Controller that takes care of the bubble bar drag events inside launcher process. */
    interface BubbleBarDragListener {

        /** Called when the drag event is over the bubble bar drop zone. */
        fun onLauncherItemDraggedOverBubbleBarDragZone(location: BubbleBarLocation)

        /** Called when the drag event leaves the bubble bar drop zone. */
        fun onLauncherItemDraggedOutsideBubbleBarDropZone()

        /** Called when the drop event happens over the bubble bar drop zone. */
        fun onLauncherItemDroppedOverBubbleBarDragZone(
            location: BubbleBarLocation,
            itemInfo: ItemInfo,
        )

        /** Gets the hit [rect][android.graphics.Rect] of the bubble bar location. */
        fun getBubbleBarLocationHitRect(bubbleBarLocation: BubbleBarLocation, outRect: Rect)

        /** Provides the view that will accept the drop. */
        fun getDropView(): View
    }

    private var isShowingDropTarget = false

    override fun isDropEnabled(): Boolean = true

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        val itemInfo = dragObject.dragInfo ?: return
        bubbleBarDragListener.onLauncherItemDroppedOverBubbleBarDragZone(
            bubbleBarLocation,
            itemInfo,
        )
    }

    override fun onDragEnter(dragObject: DropTarget.DragObject) {}

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        if (isShowingDropTarget) return
        isShowingDropTarget = true
        bubbleBarDragListener.onLauncherItemDraggedOverBubbleBarDragZone(bubbleBarLocation)
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        if (!isShowingDropTarget) return
        isShowingDropTarget = false
        bubbleBarDragListener.onLauncherItemDraggedOutsideBubbleBarDropZone()
    }

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = true

    override fun prepareAccessibilityDrop() {}

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        bubbleBarDragListener.getBubbleBarLocationHitRect(bubbleBarLocation, outRect)
    }

    override fun getDropView(): View = bubbleBarDragListener.getDropView()
}
