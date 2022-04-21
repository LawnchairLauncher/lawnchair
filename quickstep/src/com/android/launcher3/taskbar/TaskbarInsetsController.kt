/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar

import android.graphics.Insets
import android.view.WindowManager
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.quickstep.KtR
import com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo
import com.android.systemui.shared.system.WindowManagerWrapper
import com.android.systemui.shared.system.WindowManagerWrapper.*
import java.io.PrintWriter

/**
 * Handles the insets that Taskbar provides to underlying apps and the IME.
 */
class TaskbarInsetsController(val context: TaskbarActivityContext): LoggableTaskbarController {

    /** The bottom insets taskbar provides to the IME when IME is visible. */
    val taskbarHeightForIme: Int = context.resources.getDimensionPixelSize(
        KtR.dimen.taskbar_ime_size)

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
        windowLayoutParams = context.windowLayoutParams

        val wmWrapper: WindowManagerWrapper = getInstance()
        wmWrapper.setProvidesInsetsTypes(
            windowLayoutParams,
            intArrayOf(
                ITYPE_EXTRA_NAVIGATION_BAR,
                ITYPE_BOTTOM_TAPPABLE_ELEMENT
            )
        )

        windowLayoutParams.providedInternalImeInsets = arrayOfNulls<Insets>(ITYPE_SIZE)

        onTaskbarWindowHeightChanged()

        windowLayoutParams.insetsRoundedCornerFrame = true
    }

    fun onTaskbarWindowHeightChanged() {
        val reducingSize = Insets.of(0, windowLayoutParams.height - taskbarHeightForIme, 0, 0)
        windowLayoutParams.providedInternalImeInsets[ITYPE_EXTRA_NAVIGATION_BAR] = reducingSize
        windowLayoutParams.providedInternalImeInsets[ITYPE_BOTTOM_TAPPABLE_ELEMENT] = reducingSize
    }

    /**
     * Called to update the touchable insets.
     * @see InsetsInfo.setTouchableInsets
     */
    fun updateInsetsTouchability(insetsInfo: InsetsInfo) {
        insetsInfo.touchableRegion.setEmpty()
        // Always have nav buttons be touchable
        controllers.navbarButtonsViewController.addVisibleButtonsRegion(
            context.dragLayer, insetsInfo.touchableRegion
        )
        var insetsIsTouchableRegion = true
        if (context.dragLayer.alpha < AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (controllers.navbarButtonsViewController.isImeVisible) {
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (!controllers.uiController.isTaskbarTouchable) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarDragController.isSystemDragInProgress) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (AbstractFloatingView.getOpenView<AbstractFloatingView?>(
                context,
                AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
            ) != null
        ) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarViewController.areIconsVisible()
            || AbstractFloatingView.getOpenView<AbstractFloatingView?>(
                context,
                AbstractFloatingView.TYPE_ALL
            ) != null
            || context.isNavBarKidsModeActive
        ) {
            // Taskbar has some touchable elements, take over the full taskbar area
            insetsInfo.setTouchableInsets(
                if (context.isTaskbarWindowFullscreen) {
                    InsetsInfo.TOUCHABLE_INSETS_FRAME
                } else {
                    InsetsInfo.TOUCHABLE_INSETS_CONTENT
                }
            )
            insetsIsTouchableRegion = false
        } else {
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        }
        context.excludeFromMagnificationRegion(insetsIsTouchableRegion)
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarInsetsController:")
        pw.println("$prefix\twindowHeight=${windowLayoutParams.height}")
        pw.println("$prefix\tprovidedInternalImeInsets[ITYPE_EXTRA_NAVIGATION_BAR]=" +
                "${windowLayoutParams.providedInternalImeInsets[ITYPE_EXTRA_NAVIGATION_BAR]}")
        pw.println("$prefix\tprovidedInternalImeInsets[ITYPE_BOTTOM_TAPPABLE_ELEMENT]=" +
                "${windowLayoutParams.providedInternalImeInsets[ITYPE_BOTTOM_TAPPABLE_ELEMENT]}")
    }
}