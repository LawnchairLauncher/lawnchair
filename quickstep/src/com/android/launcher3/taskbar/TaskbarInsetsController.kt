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
import android.view.InsetsState
import android.view.InsetsState.ITYPE_BOTTOM_MANDATORY_GESTURES
import android.view.InsetsState.ITYPE_BOTTOM_TAPPABLE_ELEMENT
import android.view.InsetsState.ITYPE_EXTRA_NAVIGATION_BAR
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD
import android.view.WindowManager.LayoutParams.TYPE_VOICE_INTERACTION
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import java.io.PrintWriter

/**
 * Handles the insets that Taskbar provides to underlying apps and the IME.
 */
class TaskbarInsetsController(val context: TaskbarActivityContext): LoggableTaskbarController {

    /** The bottom insets taskbar provides to the IME when IME is visible. */
    val taskbarHeightForIme: Int = context.resources.getDimensionPixelSize(R.dimen.taskbar_ime_size)
    private val touchableRegion: Region = Region()
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
        val touchableHeight = controllers.taskbarStashController.touchableHeight
        touchableRegion.set(0, windowLayoutParams.height - touchableHeight,
            context.deviceProfile.widthPx, windowLayoutParams.height)
        val contentHeight = controllers.taskbarStashController.contentHeightToReportToApps
        val tappableHeight = controllers.taskbarStashController.tappableHeightToReportToApps
        for (provider in windowLayoutParams.providedInsets) {
            if (provider.type == ITYPE_EXTRA_NAVIGATION_BAR
                    || provider.type == ITYPE_BOTTOM_MANDATORY_GESTURES) {
                provider.insetsSize = getInsetsByNavMode(contentHeight)
            } else if (provider.type == ITYPE_BOTTOM_TAPPABLE_ELEMENT) {
                provider.insetsSize = getInsetsByNavMode(tappableHeight)
            }
        }

        val imeInsetsSize = getInsetsByNavMode(taskbarHeightForIme)
        val insetsSizeOverride = arrayOf(
            InsetsFrameProvider.InsetsSizeOverride(
                TYPE_INPUT_METHOD,
                imeInsetsSize
            ),
        )
        // Use 0 tappableElement insets for the VoiceInteractionWindow when gesture nav is enabled.
        val visInsetsSizeForGestureNavTappableElement = getInsetsByNavMode(0)
        val insetsSizeOverrideForGestureNavTappableElement = arrayOf(
            InsetsFrameProvider.InsetsSizeOverride(
                TYPE_INPUT_METHOD,
                imeInsetsSize
            ),
            InsetsFrameProvider.InsetsSizeOverride(
                TYPE_VOICE_INTERACTION,
                visInsetsSizeForGestureNavTappableElement
            ),
        )
        for (provider in windowLayoutParams.providedInsets) {
            if (context.isGestureNav && provider.type == ITYPE_BOTTOM_TAPPABLE_ELEMENT) {
                provider.insetsSizeOverrides = insetsSizeOverrideForGestureNavTappableElement
            } else {
                provider.insetsSizeOverrides = insetsSizeOverride
            }
        }
    }

    /**
     * @return [Insets] where the [bottomInset] is either used as a bottom inset or
     *         right/left inset if using 3 button nav
     */
    private fun getInsetsByNavMode(bottomInset: Int) : Insets {
        val devicePortrait = !context.deviceProfile.isLandscape
        if (!TaskbarManager.isPhoneButtonNavMode(context) || devicePortrait) {
            // Taskbar or portrait phone mode
            return Insets.of(0, 0, 0, bottomInset)
        }

        // TODO(b/230394142): seascape
        return Insets.of(0, 0, bottomInset, 0)
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
        } else if (controllers.navbarButtonsViewController.isImeVisible
                && controllers.taskbarStashController.isStashed()) {
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (!controllers.uiController.isTaskbarTouchable) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (controllers.taskbarDragController.isSystemDragInProgress) {
            // Let touches pass through us.
            insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        } else if (AbstractFloatingView.hasOpenView(context, TYPE_TASKBAR_OVERLAY_PROXY)) {
            // Let touches pass through us if icons are hidden.
            if (controllers.taskbarViewController.areIconsVisible()) {
                insetsInfo.touchableRegion.set(touchableRegion)
            }
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
                    insetsInfo.touchableRegion.set(touchableRegion)
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
