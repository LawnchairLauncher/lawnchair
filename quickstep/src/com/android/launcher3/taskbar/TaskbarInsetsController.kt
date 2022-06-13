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
import android.graphics.Region
import android.view.InsetsState.ITYPE_BOTTOM_MANDATORY_GESTURES
import android.view.InsetsState
import android.view.WindowManager
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
import com.android.launcher3.DeviceProfile
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
    private val contentRegion: Region = Region()
    private val deviceProfileChangeListener = { _: DeviceProfile ->
        onTaskbarWindowHeightOrInsetsChanged()
    }

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
                ITYPE_BOTTOM_TAPPABLE_ELEMENT,
                ITYPE_BOTTOM_MANDATORY_GESTURES
            )
        )

        onTaskbarWindowHeightOrInsetsChanged()

        windowLayoutParams.insetsRoundedCornerFrame = true
        context.addOnDeviceProfileChangeListener(deviceProfileChangeListener)
    }

    fun onDestroy() {
        context.removeOnDeviceProfileChangeListener(deviceProfileChangeListener)
    }

    fun onTaskbarWindowHeightOrInsetsChanged() {
        var contentHeight = controllers.taskbarStashController.contentHeightToReportToApps
        contentRegion.set(0, windowLayoutParams.height - contentHeight,
            context.deviceProfile.widthPx, windowLayoutParams.height)
        var tappableHeight = controllers.taskbarStashController.tappableHeightToReportToApps
        for (provider in windowLayoutParams.providedInsets) {
            if (provider.type == ITYPE_EXTRA_NAVIGATION_BAR) {
                provider.insetsSize = Insets.of(0, 0, 0, contentHeight)
            } else if (provider.type == ITYPE_BOTTOM_TAPPABLE_ELEMENT
                      || provider.type == ITYPE_BOTTOM_MANDATORY_GESTURES) {
                provider.insetsSize = Insets.of(0, 0, 0, tappableHeight)
            }
        }

        var imeInsetsSize = Insets.of(0, 0, 0, taskbarHeightForIme)
        for (provider in windowLayoutParams.providedInsets) {
            provider.imeInsetsSize = imeInsetsSize
        }
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
        } else if (AbstractFloatingView.hasOpenView(context, TYPE_TASKBAR_ALL_APPS)) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(InsetsInfo.TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarViewController.areIconsVisible()
            || AbstractFloatingView.hasOpenView(context, AbstractFloatingView.TYPE_ALL)
            || context.isNavBarKidsModeActive
        ) {
            // Taskbar has some touchable elements, take over the full taskbar area
            insetsInfo.setTouchableInsets(
                if (context.isTaskbarWindowFullscreen) {
                    InsetsInfo.TOUCHABLE_INSETS_FRAME
                } else {
                    insetsInfo.touchableRegion.set(contentRegion)
                    InsetsInfo.TOUCHABLE_INSETS_REGION
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
        for (provider in windowLayoutParams.providedInsets) {
            pw.println("$prefix\tprovidedInsets: (type=" + InsetsState.typeToString(provider.type)
                    + " insetsSize=" + provider.insetsSize
                    + " imeInsetsSize=" + provider.imeInsetsSize + ")")
        }
    }
}
