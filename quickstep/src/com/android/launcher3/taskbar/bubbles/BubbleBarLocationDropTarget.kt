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
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DropTargetManager

/**
 * Implementation of the {@link DropTarget} that handles drag and drop events over the bubble bar
 * locations.
 */
class BubbleBarLocationDropTarget(
    private val bubbleBarDropTargetController: BubbleBarDropTargetController,
    dragZoneFactory: DragZoneFactory,
    private val dropTargetManager: DropTargetManager,
    private val isLeftDropTarget: Boolean,
) : DropTarget {

    interface BubbleBarDropTargetController {

        /** Return whether the item info can be dropped on the bubble bar drop target. */
        fun acceptDrop(itemInfo: ItemInfo): Boolean

        /** Called after dragged item info drop on the bubble bar drop target. */
        fun onDrop(itemInfo: ItemInfo, isLeftDropTarget: Boolean)
    }

    private val dropRect = dragZoneFactory.getBubbleBarDropRect(isLeftDropTarget)

    override fun isDropEnabled(): Boolean = true

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        bubbleBarDropTargetController.onDrop(dragObject.dragInfo, isLeftDropTarget)
    }

    override fun onDragEnter(dragObject: DropTarget.DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean =
        bubbleBarDropTargetController.acceptDrop(dragObject.dragInfo)

    override fun prepareAccessibilityDrop() {}

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        outRect.set(dropRect)
    }

    override fun getDropView(): View? = null
}
