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
import android.view.InsetsFrameProvider
import android.view.InsetsState.ITYPE_BOTTOM_MANDATORY_GESTURES
import android.view.InsetsState
import android.view.InsetsState.ITYPE_BOTTOM_TAPPABLE_ELEMENT
import android.view.InsetsState.ITYPE_EXTRA_NAVIGATION_BAR
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD
import android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
import com.android.launcher3.DeviceProfile
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.quickstep.KtR
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

        setProvidesInsetsTypes(
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

        val imeInsetsSize = Insets.of(0, 0, 0, taskbarHeightForIme)
        // Use 0 insets for the VoiceInteractionWindow (assistant) when gesture nav is enabled.
        val visInsetsSize = Insets.of(0, 0, 0, if (context.isGestureNav) 0 else tappableHeight)
        val insetsSizeOverride = arrayOf(
            InsetsFrameProvider.InsetsSizeOverride(
                TYPE_INPUT_METHOD,
                imeInsetsSize
            ),
            InsetsFrameProvider.InsetsSizeOverride(
                TYPE_VOICE_INTERACTION,
                visInsetsSize
            )
        )
        for (provider in windowLayoutParams.providedInsets) {
            provider.insetsSizeOverrides = insetsSizeOverride
        }
    }

    /**
     * Sets {@param providesInsetsTypes} as the inset types provided by {@param params}.
     * @param params The window layout params.
     * @param providesInsetsTypes The inset types we would like this layout params to provide.
     */
    fun setProvidesInsetsTypes(params: WindowManager.LayoutParams, providesInsetsTypes: IntArray) {
        params.providedInsets = arrayOfNulls<InsetsFrameProvider>(providesInsetsTypes.size);
        for (i in providesInsetsTypes.indices) {
            params.providedInsets[i] = InsetsFrameProvider(providesInsetsTypes[i]);
        }
    }

    /**
     * Called to update the touchable insets.
     * @see InternalInsetsInfo.setTouchableInsets
     */
    fun updateInsetsTouchability(insetsInfo: ViewTreeObserver.InternalInsetsInfo) {
        insetsInfo.touchableRegion.setEmpty()
        // Always have nav buttons be touchable
        controllers.navbarButtonsViewController.addVisibleButtonsRegion(
            context.dragLayer, insetsInfo.touchableRegion
        )
        var insetsIsTouchableRegion = true
        if (context.dragLayer.alpha < AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (controllers.navbarButtonsViewController.isImeVisible) {
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (!controllers.uiController.isTaskbarTouchable) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarDragController.isSystemDragInProgress) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (AbstractFloatingView.hasOpenView(context, TYPE_TASKBAR_ALL_APPS)) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarViewController.areIconsVisible()
            || AbstractFloatingView.hasOpenView(context, AbstractFloatingView.TYPE_ALL)
            || context.isNavBarKidsModeActive
        ) {
            // Taskbar has some touchable elements, take over the full taskbar area
            insetsInfo.setTouchableInsets(
                if (context.isTaskbarWindowFullscreen) {
                    TOUCHABLE_INSETS_FRAME
                } else {
                    insetsInfo.touchableRegion.set(contentRegion)
                    TOUCHABLE_INSETS_REGION
                }
            )
            insetsIsTouchableRegion = false
        } else {
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        }
        context.excludeFromMagnificationRegion(insetsIsTouchableRegion)
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarInsetsController:")
        pw.println("$prefix\twindowHeight=${windowLayoutParams.height}")
        for (provider in windowLayoutParams.providedInsets) {
            pw.print("$prefix\tprovidedInsets: (type=" + InsetsState.typeToString(provider.type)
                    + " insetsSize=" + provider.insetsSize)
            if (provider.insetsSizeOverrides != null) {
                pw.print(" insetsSizeOverrides={")
                for ((i, overrideSize) in provider.insetsSizeOverrides.withIndex()) {
                    if (i > 0) pw.print(", ")
                    pw.print(overrideSize)
                }
                pw.print("})")
            }
            pw.println()
        }
    }
}
