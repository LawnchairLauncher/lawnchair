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
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragOptions
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DropTargetManager

/**
 * Implementation of the {@link DropTarget} that handles drag and drop events over the bubble bar
 * locations.
 */
class BubbleBarLocationDropTarget(
    private val bubbleBarDropTargetController: BubbleBarDropTargetController,
    dragZoneFactory: DragZoneFactory,
    private var dropTargetManager: DropTargetManager,
    private val isLeftDropTarget: Boolean,
) : DropTarget {

    /** Whether the [DragObject] can be dropped on the bubble bar drop target. */
    var isDropCanBeAccepted: Boolean = false

    interface BubbleBarDropTargetController {

        /** Called after [DragObject] dropped on the bubble bar drop target. */
        fun onDrop(dragObject: DragObject, isLeftDropTarget: Boolean)
    }

    /** Sets the drop target manager that drop target will use. */
    fun setDropTargetManager(dropTargetManager: DropTargetManager) {
        this.dropTargetManager = dropTargetManager
    }

    private val dropRect = dragZoneFactory.getBubbleBarDropRect(isLeftDropTarget)

    override fun isDropEnabled(): Boolean = isDropCanBeAccepted

    override fun onDrop(dragObject: DragObject, options: DragOptions) {
        bubbleBarDropTargetController.onDrop(dragObject, isLeftDropTarget)
    }

    override fun onDragEnter(dragObject: DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun onDragOver(dragObject: DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun onDragExit(dragObject: DragObject) {
        dropTargetManager.onDragUpdated(dragObject.x, dragObject.y)
    }

    override fun acceptDrop(dragObject: DragObject): Boolean = isDropCanBeAccepted

    override fun prepareAccessibilityDrop() {}

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        outRect.set(dropRect)
    }

    override fun getDropView(): View? = null
}
