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

package com.android.launcher3.popup

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.popup.ArrowPopup.TYPE_FOLDER
import com.android.launcher3.popup.ArrowPopup.closeOpenContainer
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider
import com.android.launcher3.touch.ItemLongClickListener

/** Handler to control drag-and-drop for popup items */
interface PopupItemDragHandler : OnLongClickListener, OnTouchListener

/** Drag and drop handler for popup items in Launcher activity */
class LauncherPopupItemDragHandler
internal constructor(
    private val mLauncher: Launcher,
    private val mContainer: PopupContainerWithArrow<*>,
) : PopupItemDragHandler {
    private val mIconLastTouchPos: Point = Point()

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        // Touched a shortcut, update where it was touched so we can drag from there on
        // long click.
        when (ev.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> mIconLastTouchPos[ev.x.toInt()] = ev.y.toInt()
        }
        return false
    }

    override fun onLongClick(v: View): Boolean {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false
        // Return early if not the correct view
        if (v.parent !is DeepShortcutView) return false

        // Long clicked on a shortcut.
        val sv = v.parent as DeepShortcutView
        sv.setWillDrawIcon(false)

        // Move the icon to align with the center-top of the touch point
        val iconShift = Point()
        iconShift.x = mIconLastTouchPos.x - sv.iconCenter.x
        iconShift.y = mIconLastTouchPos.y - mLauncher.deviceProfile.workspaceIconProfile.iconSizePx

        val draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON)
        val itemInfo = sv.finalInfo
        itemInfo.container = LauncherSettings.Favorites.CONTAINER_SHORTCUTS
        val dv =
            mLauncher.workspace.beginDragShared(
                sv.iconView,
                draggableView,
                mContainer,
                itemInfo,
                ShortcutDragPreviewProvider(sv.iconView, iconShift),
                DragOptions(),
            )
        dv.animateShift(-iconShift.x, -iconShift.y)

        // TODO: support dragging from within folder without having to close it
        closeOpenContainer(mLauncher, TYPE_FOLDER)
        return false
    }
}
