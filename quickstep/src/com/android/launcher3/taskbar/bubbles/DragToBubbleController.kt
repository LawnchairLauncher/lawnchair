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

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.FrameLayout
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.bubbles.BubbleBarController.BubbleBarLocationListener
import com.android.launcher3.taskbar.bubbles.BubbleBarLocationDropTarget.BubbleBarDropTargetController
import com.android.quickstep.SystemUiProxy
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.ContextUtils.isRtl
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragToBubblesZoneChangeListener
import com.android.wm.shell.shared.bubbles.DragZone
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.DesktopWindowModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DraggedObject
import com.android.wm.shell.shared.bubbles.DraggedObject.LauncherIcon
import com.android.wm.shell.shared.bubbles.DropTargetManager
import com.android.wm.shell.shared.bubbles.DropTargetManager.DragZoneChangedListener
import com.google.common.annotations.VisibleForTesting

class DragToBubbleController(private val context: Context, bubbleBarContainer: FrameLayout) :
    DragController.DragListener {

    @VisibleForTesting val dropTargetManager: DropTargetManager
    @VisibleForTesting lateinit var bubbleBarLeftDropTarget: BubbleBarLocationDropTarget
    @VisibleForTesting lateinit var bubbleBarRightDropTarget: BubbleBarLocationDropTarget
    @VisibleForTesting lateinit var dragZoneFactory: DragZoneFactory
    // If item drop is handled the next sysui update will set the bubble bar location
    @VisibleForTesting var isItemDropHandled = false
    private lateinit var bubbleBarLocationListener: BubbleBarLocationListener
    private lateinit var systemUiProxy: SystemUiProxy
    private lateinit var bubbleBarViewController: BubbleBarViewController

    init {
        dropTargetManager = createDropTargetManager(bubbleBarContainer)
    }

    fun init(
        bubbleBarViewController: BubbleBarViewController,
        bubbleBarPropertiesProvider: BubbleBarPropertiesProvider,
        bubbleBarLocationListener: BubbleBarLocationListener,
        systemUiProxy: SystemUiProxy,
    ) {
        this.bubbleBarViewController = bubbleBarViewController
        this.systemUiProxy = systemUiProxy
        this.bubbleBarLocationListener = bubbleBarLocationListener
        val dropController: BubbleBarDropTargetController = createDropController()
        dragZoneFactory = createDragZoneFactory(bubbleBarPropertiesProvider)
        bubbleBarLeftDropTarget = createDropTarget(dropController, isLeftDropTarget = true)
        bubbleBarRightDropTarget = createDropTarget(dropController, isLeftDropTarget = false)
    }

    /** Adds bubble bar locations drop zones to the drag controller. */
    fun addBubbleBarDropTargets(dragController: DragController<*>) {
        dragController.addDragListener(this)
        dragController.addDropTarget(bubbleBarLeftDropTarget)
        dragController.addDropTarget(bubbleBarRightDropTarget)
    }

    /** Removes bubble bar locations drop zones to the drag controller. */
    fun removeBubbleBarDropTargets(dragController: DragController<*>) {
        dragController.removeDragListener(this)
        dragController.removeDropTarget(bubbleBarLeftDropTarget)
        dragController.removeDropTarget(bubbleBarRightDropTarget)
    }

    /**
     * Runs the provided action once all drop target views are removed from the container. If there
     * are no drop target views currently present or being animated, the action will be executed
     * immediately.
     */
    fun runAfterDropTargetsHidden(afterHiddenAction: Runnable) {
        dropTargetManager.onDropTargetRemoved(afterHiddenAction)
    }

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        isItemDropHandled = false
        val launcherIcon: DraggedObject = LauncherIcon(bubbleBarViewController.hasBubbles()) {}
        val dragZones: List<DragZone> = dragZoneFactory.createSortedDragZones(launcherIcon)
        dropTargetManager.onDragStarted(launcherIcon, dragZones)
    }

    override fun onDragEnd() {
        dropTargetManager.onDragEnded()
    }

    private fun createDropTargetManager(bubbleBarContainer: FrameLayout): DropTargetManager {
        val listener: DragZoneChangedListener =
            DragToBubblesZoneChangeListener(
                context.isRtl,
                object : DragToBubblesZoneChangeListener.Callback {

                    private var currentBarLocation: BubbleBarLocation? = null

                    override fun onDragEnteredLocation(bubbleBarLocation: BubbleBarLocation?) {
                        bubbleBarViewController.isShowingDropTarget = bubbleBarLocation != null
                        if (isItemDropHandled) return
                        val updatedLocation = bubbleBarLocation ?: getStartingBubbleBarLocation()
                        currentBarLocation = currentBarLocation ?: getStartingBubbleBarLocation()
                        if (updatedLocation != currentBarLocation) {
                            currentBarLocation = updatedLocation
                            bubbleBarLocationListener.onBubbleBarLocationAnimated(updatedLocation)
                        }
                    }

                    override fun getStartingBubbleBarLocation(): BubbleBarLocation {
                        return bubbleBarViewController.bubbleBarLocation
                            ?: BubbleBarLocation.DEFAULT
                    }

                    override fun hasBubbles(): Boolean = bubbleBarViewController.hasBubbles()

                    override fun animateBubbleBarLocation(bubbleBarLocation: BubbleBarLocation) {
                        if (isItemDropHandled) return
                        bubbleBarViewController.animateBubbleBarLocation(bubbleBarLocation)
                    }
                },
            )
        return DropTargetManager(context, bubbleBarContainer, listener)
    }

    private fun createDragZoneFactory(
        bubbleBarPropertiesProvider: BubbleBarPropertiesProvider
    ): DragZoneFactory {
        val splitScreenModeChecker = SplitScreenModeChecker { SplitScreenMode.NONE }
        val desktopWindowModeChecker = DesktopWindowModeChecker { false }
        val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)!!
        val deviceConfig: DeviceConfig = DeviceConfig.create(context, windowManager)
        return DragZoneFactory(
            context,
            deviceConfig,
            splitScreenModeChecker,
            desktopWindowModeChecker,
            bubbleBarPropertiesProvider,
        )
    }

    private fun createDropController(): BubbleBarDropTargetController {
        return object : BubbleBarDropTargetController {
            override fun onDrop(itemInfo: ItemInfo, isLeftDropTarget: Boolean) {
                isItemDropHandled = handleDrop(itemInfo, isLeftDropTarget)
            }

            override fun acceptDrop(itemInfo: ItemInfo): Boolean {
                return hasShortcutInfo(itemInfo) || itemInfo.intent?.component != null
            }

            fun hasShortcutInfo(itemInfo: ItemInfo): Boolean {
                return itemInfo is WorkspaceItemInfo && itemInfo.deepShortcutInfo != null
            }

            private fun handleDrop(itemInfo: ItemInfo, isLeftDropTarget: Boolean): Boolean {
                val location =
                    if (isLeftDropTarget) {
                        BubbleBarLocation.LEFT
                    } else {
                        BubbleBarLocation.RIGHT
                    }
                if (hasShortcutInfo(itemInfo)) {
                    val si = (itemInfo as WorkspaceItemInfo).deepShortcutInfo
                    systemUiProxy.showShortcutBubble(si, location)
                    return true
                }
                val itemIntent: Intent = itemInfo.intent ?: return false
                val packageName = itemIntent.component?.packageName ?: return false
                itemIntent.setPackage(packageName)
                systemUiProxy.showAppBubble(itemIntent, itemInfo.user, location)
                return true
            }
        }
    }

    private fun createDropTarget(
        dropController: BubbleBarDropTargetController,
        isLeftDropTarget: Boolean,
    ) =
        BubbleBarLocationDropTarget(
            dropController,
            dragZoneFactory,
            dropTargetManager,
            isLeftDropTarget,
        )
}
