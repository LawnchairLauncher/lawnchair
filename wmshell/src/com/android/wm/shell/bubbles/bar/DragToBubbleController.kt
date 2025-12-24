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
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.draganddrop.DragAndDropController.DragAndDropListener
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.ContextUtils.isRtl
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragToBubblesZoneChangeListener
import com.android.wm.shell.shared.bubbles.DragZone
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DraggedObject.LauncherIcon
import com.android.wm.shell.shared.bubbles.DropTargetManager
import com.android.wm.shell.shared.bubbles.logging.EntryPoint

/** Handles scenarios when launcher icon is being dragged to the bubble bar drop zones. */
class DragToBubbleController(
    val context: Context,
    val bubbleController: BubbleController,
) : DragAndDropListener {

    private val containerView: FrameLayout =
        FrameLayout(context).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
        }

    @VisibleForTesting
    val dropTargetManager: DropTargetManager =
        DropTargetManager(
            context,
            containerView,
            createDragZoneChangedListener()
        )

    @VisibleForTesting
    var dragZoneFactory = createDragZoneFactory()
        private set
    @VisibleForTesting
    var isDropHandled = false
    private var lastDragZone: DragZone? = null

    /** Returns the container view in which drop targets are added. */
    fun getDropTargetContainer(): ViewGroup = containerView

    override fun onConfigurationChanged() {
        dragZoneFactory = createDragZoneFactory()
    }

    /** Called when the drag is tarted. */
    override fun onDragStarted() {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            return
        }
        isDropHandled = false
        val draggedObject = LauncherIcon(showBubbleBarPillowDropTarget = false)
        val dragZones = dragZoneFactory.createSortedDragZones(draggedObject)
        dropTargetManager.onDragStarted(draggedObject, dragZones)
    }

    /**
     * Called when drag position is updated.
     *
     * @return true if drag is over any bubble bar drop zones
     */
    fun onDragUpdate(x: Int, y: Int): Boolean {
        lastDragZone = dropTargetManager.onDragUpdated(x, y)
        return lastDragZone != null
    }

    /** Called when drop targets should be hidden. */
    fun hideDropTargets() {
        dropTargetManager.hideDropTargets()
    }

    /** Called when the item with the [ShortcutInfo] is dropped over the bubble bar drop target. */
    fun onItemDropped(shortcutInfo: ShortcutInfo) {
        val dropLocation = lastDragZone?.getBubbleBarLocation() ?: return
        isDropHandled = true
        bubbleController.expandStackAndSelectBubble(
            shortcutInfo,
            EntryPoint.TASKBAR_ICON_DRAG,
            dropLocation,
        )
    }

    /**
     * Called when the item with the [PendingIntent] and the [UserHandle] is dropped over the
     * bubble bar drop target.
     */
    fun onItemDropped(pendingIntent: PendingIntent, userHandle: UserHandle) {
        val dropLocation = lastDragZone?.getBubbleBarLocation() ?: return
        isDropHandled = true
        bubbleController.expandStackAndSelectBubble(
            pendingIntent,
            userHandle,
            EntryPoint.TASKBAR_ICON_DRAG,
            dropLocation,
        )
    }

    /** Called when the drag is ended. */
    override fun onDragEnded() {
        dropTargetManager.onDragEnded()
    }

    private fun createDragZoneFactory(): DragZoneFactory {
        val deviceConfig =
            DeviceConfig.create(context, context.getSystemService(WindowManager::class.java)!!)
        return DragZoneFactory(
            context,
            deviceConfig,
            { SplitScreenMode.UNSUPPORTED },
            { false },
            object : BubbleBarPropertiesProvider {},
        )
    }

    private fun DragZone.getBubbleBarLocation(): BubbleBarLocation? =
        when (this) {
            is DragZone.Bubble.Left -> BubbleBarLocation.LEFT
            is DragZone.Bubble.Right -> BubbleBarLocation.RIGHT
            else -> null
        }

    private fun createDragZoneChangedListener() = DragToBubblesZoneChangeListener(
        context.isRtl,
        object : DragToBubblesZoneChangeListener.Callback {

            override fun getStartingBubbleBarLocation(): BubbleBarLocation {
                return bubbleController.bubbleBarLocation ?: BubbleBarLocation.DEFAULT
            }

            override fun hasBubbles(): Boolean = bubbleController.hasBubbles()

            override fun onDragEnteredLocation(bubbleBarLocation: BubbleBarLocation?) {
                // if drop was handled, do not need to send signal to launcher
                if (isDropHandled) return
                bubbleController.showBubbleBarDropTargetAt(bubbleBarLocation)
            }
        })
}
