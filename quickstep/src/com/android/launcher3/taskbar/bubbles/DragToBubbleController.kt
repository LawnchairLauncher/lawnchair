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
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
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
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.google.common.annotations.VisibleForTesting
import kotlin.math.min

class DragToBubbleController(
    private val context: Context,
    private val bubbleBarContainer: FrameLayout,
) : DragController.DragListener {

    // Two DropTargetManagers are needed because the drag call chain has
    // conflicting states that require showing different targets:
    // - Launcher#onDragStart() -> Shows 2 drop targets.
    // - Shell#onShellDragStateChanged(true) -> Shows only the secondary target.
    // It is not possible to alter drop targets (drag zones) in runtime, because they are data
    // classes so the only way is to restart the drag.
    @VisibleForTesting var launcherDropTargetManager = createDropTargetManager(bubbleBarContainer)
    @VisibleForTesting var shellDropTargetManager = createDropTargetManager(bubbleBarContainer)

    @VisibleForTesting lateinit var bubbleBarLeftDropTarget: BubbleBarLocationDropTarget
    @VisibleForTesting lateinit var bubbleBarRightDropTarget: BubbleBarLocationDropTarget
    @VisibleForTesting lateinit var dragZoneFactory: DragZoneFactory
    // If item drop is handled the next sysui update will set the bubble bar location
    @VisibleForTesting var isItemDropHandled = false
    private lateinit var bubbleBarLocationListener: BubbleBarLocationListener
    private lateinit var bubbleActivityStarter: BubbleActivityStarter
    private lateinit var bubbleBarViewController: BubbleBarViewController
    private val bubbleDropController: BubbleBarDropTargetController = createDropController()
    private var isShellDragInProgress = false
    private var isLauncherDragInProgress = false

    /** The field value is true if the drag is in progress. */
    val isDragInProgress: Boolean
        get() = isLauncherDragInProgress || isShellDragInProgress

    fun init(
        bubbleBarViewController: BubbleBarViewController,
        bubbleBarPropertiesProvider: BubbleBarPropertiesProvider,
        bubbleBarLocationListener: BubbleBarLocationListener,
        bubbleActivityStarter: BubbleActivityStarter,
    ) {
        this.bubbleBarViewController = bubbleBarViewController
        this.bubbleActivityStarter = bubbleActivityStarter
        this.bubbleBarLocationListener = bubbleBarLocationListener
        dragZoneFactory = createDragZoneFactory(bubbleBarPropertiesProvider)
        bubbleBarLeftDropTarget = createDropTarget(bubbleDropController, isLeftDropTarget = true)
        bubbleBarRightDropTarget = createDropTarget(bubbleDropController, isLeftDropTarget = false)
    }

    /** Adds bubble bar locations drop zones to the drag controller. */
    fun addBubbleBarDropTargets(dragController: DragController<*>) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            return
        }
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
        launcherDropTargetManager.onDropTargetRemoved(afterHiddenAction)
    }

    fun setOverlayContainerView(containerView: FrameLayout?) {
        val container = containerView ?: bubbleBarContainer
        // onDragEnded() call will remove added drop target views
        launcherDropTargetManager.onDragEnded()
        shellDropTargetManager.onDragEnded()
        // create new drop target managers
        launcherDropTargetManager = createDropTargetManager(container)
        shellDropTargetManager = createDropTargetManager(container)
        // update drop target managers in bubble bar drop targets
        bubbleBarLeftDropTarget.setDropTargetManager(launcherDropTargetManager)
        bubbleBarRightDropTarget.setDropTargetManager(launcherDropTargetManager)
    }

    fun onShellDragStateChanged(started: Boolean) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            return
        }
        isShellDragInProgress = started
        if (started) {
            onDragStarted(showDropTarget = false, shellDropTargetManager)
        } else {
            shellDropTargetManager.onDragEnded()
        }
    }

    fun showShellBubbleBarDropTargetAt(location: BubbleBarLocation?) {
        bubbleBarViewController.isShowingDropTarget = location != null
        if (location == null) {
            val leftDropRect = dragZoneFactory.getBubbleBarDropRect(isLeftSide = true)
            val rightDropRect = dragZoneFactory.getBubbleBarDropRect(isLeftSide = false)
            // drag to no zones, so bubble bar drop target view is hidden
            val x = (leftDropRect.right + rightDropRect.left) / 2
            val y = min(leftDropRect.top, rightDropRect.top) - 1
            shellDropTargetManager.onDragUpdated(x, y)
            return
        }
        val dropRect = dragZoneFactory.getBubbleBarDropRect(location.isOnLeft(context.isRtl))
        // drag to the zone center, so bubble bar drop target view is shown
        shellDropTargetManager.onDragUpdated(dropRect.centerX(), dropRect.centerY())
    }

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        isLauncherDragInProgress = true
        isItemDropHandled = false
        val isDropCanBeAccepted = canAcceptDrop(dragObject)
        bubbleBarLeftDropTarget.isDropCanBeAccepted = isDropCanBeAccepted
        bubbleBarRightDropTarget.isDropCanBeAccepted = isDropCanBeAccepted
        if (isDropCanBeAccepted) {
            onDragStarted(showDropTarget = true, launcherDropTargetManager)
        }
    }

    override fun onDragEnd() {
        isLauncherDragInProgress = false
        launcherDropTargetManager.onDragEnded()
    }

    private fun onDragStarted(showDropTarget: Boolean, dropTargetManager: DropTargetManager) {
        val launcherIcon: DraggedObject =
            LauncherIcon(
                showExpandedViewDropTarget = showDropTarget,
                showBubbleBarPillowDropTarget = !bubbleBarViewController.hasBubbles(),
            )
        val dragZones: List<DragZone> = dragZoneFactory.createSortedDragZones(launcherIcon)
        dropTargetManager.onDragStarted(launcherIcon, dragZones)
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

    private fun canAcceptDrop(dragObject: DragObject): Boolean {
        val itemInfo = dragObject.dragInfo
        return itemInfo != null && (hasShortcutInfo(itemInfo) || itemInfo.intent?.component != null)
    }

    private fun hasShortcutInfo(itemInfo: ItemInfo): Boolean {
        return itemInfo is WorkspaceItemInfo && itemInfo.deepShortcutInfo != null
    }

    private fun createDropController(): BubbleBarDropTargetController {
        return object : BubbleBarDropTargetController {

            override fun onDrop(dragObject: DragObject, isLeftDropTarget: Boolean) {
                val itemInfo = dragObject.dragInfo ?: return
                isItemDropHandled = handleDrop(itemInfo, isLeftDropTarget)
            }

            private fun handleDrop(itemInfo: ItemInfo, isLeftDropTarget: Boolean): Boolean {
                val location =
                    if (isLeftDropTarget) {
                        BubbleBarLocation.LEFT
                    } else {
                        BubbleBarLocation.RIGHT
                    }
                val entryPoint =
                    if (itemInfo.isInAllApps) {
                        EntryPoint.ALL_APPS_ICON_DRAG
                    } else {
                        EntryPoint.TASKBAR_ICON_DRAG
                    }
                if (hasShortcutInfo(itemInfo)) {
                    val si = (itemInfo as WorkspaceItemInfo).deepShortcutInfo
                    bubbleActivityStarter.showShortcutBubble(si, entryPoint, location)
                    return true
                }
                if (itemInfo.intent == null) {
                    return false
                }
                val itemIntent = Intent(itemInfo.intent)
                val packageName = itemIntent.component?.packageName ?: return false
                itemIntent.setPackage(packageName)
                bubbleActivityStarter.showAppBubble(itemIntent, itemInfo.user, entryPoint, location)
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
            launcherDropTargetManager,
            isLeftDropTarget,
        )
}
