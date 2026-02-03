/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Point
import android.view.View
import android.view.WindowManager
import android.window.TaskSnapshot
import androidx.compose.ui.graphics.toArgb
import com.android.wm.shell.R
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.multiinstance.ManageWindowsViewContainer
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewContainer
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.calculateMenuPosition

/**
 * Implementation of [ManageWindowsViewContainer] meant to be used in desktop header and app
 * handle.
 */
class DesktopHandleManageWindowsMenu(
    private val callerTaskInfo: RunningTaskInfo,
    private val splitScreenController: SplitScreenController,
    private val captionX: Int,
    private val captionWidth: Int,
    private val windowManagerWrapper: WindowManagerWrapper,
    private val desktopState: DesktopState,
    context: Context,
    snapshotList: List<Pair<Int, TaskSnapshot?>>,
    onIconClickListener: ((Int) -> Unit),
    onOutsideClickListener: (() -> Unit)
) : ManageWindowsViewContainer(
    context,
    DecorThemeUtil(context).getColorScheme(callerTaskInfo).background.toArgb()
) {
    private var menuViewContainer: AdditionalViewContainer? = null

    init {
        createMenu(snapshotList, onIconClickListener, onOutsideClickListener)
        animateOpen()
    }

    private fun calculateMenuPosition(): Point {
        return calculateMenuPosition(
            splitScreenController,
            callerTaskInfo,
            marginStart = 0,
            marginTop = context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_handle_menu_margin_top
            ),
            captionX,
            0,
            captionWidth,
            menuView.menuWidth,
            context.isRtl()
        )
    }

    override fun addToContainer(menuView: ManageWindowsView) {
        val menuPosition = calculateMenuPosition()
        menuViewContainer = AdditionalSystemViewContainer(
            windowManagerWrapper = windowManagerWrapper,
            taskId = callerTaskInfo.taskId,
            x = menuPosition.x,
            y = menuPosition.y,
            width = menuView.menuWidth,
            height = menuView.menuHeight,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            view = menuView.rootView,
            ignoreCutouts = desktopState.canEnterDesktopModeOrShowAppHandle,
        )
    }

    override fun removeFromContainer() {
        menuViewContainer?.releaseView()
    }

    private fun Context.isRtl() =
        resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
}
