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

package com.android.wm.shell.desktopmode.desktopfirst

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.app.WindowConfiguration.windowingModeToString
import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.SystemProperties
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.util.IndentingPrintWriter
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.InputDevice
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.io.PrintWriter

/** Controls the display windowing mode in desktop mode */
class DesktopDisplayModeController(
    private val context: Context,
    shellInit: ShellInit,
    shellCommandHandler: ShellCommandHandler,
    private val transitions: Transitions,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManager: IWindowManager,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val inputManager: InputManager,
    private val displayController: DisplayController,
    @ShellMainThread private val mainHandler: Handler,
    private val desktopState: DesktopState,
) {

    /**
     * Debug flag to indicate whether to force default display to be in desktop-first mode
     * regardless of required factors.
     */
    private val FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY =
        SystemProperties.getBoolean(
            "persist.wm.debug.force_desktop_first_on_default_display_for_testing",
            false,
        )

    private val inputDeviceListener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }
        }

    init {
        shellInit.addInitCallback({ shellCommandHandler.addDumpCallback(this::dump, this) }, this)
        if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
            inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler)
        }
    }

    fun updateExternalDisplayWindowingMode(displayId: Int) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) return

        val desktopModeSupported = desktopState.isDesktopModeSupportedOnDisplay(displayId)
        if (!desktopModeSupported) return

        // An external display should always be a freeform display when desktop mode is enabled.
        updateDisplayWindowingMode(displayId, DESKTOP_FIRST_DISPLAY_WINDOWING_MODE)
    }

    fun updateDefaultDisplayWindowingMode() {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue) return

        updateDisplayWindowingMode(DEFAULT_DISPLAY, getTargetWindowingModeForDefaultDisplay())
    }

    private fun updateDisplayWindowingMode(displayId: Int, targetDisplayWindowingMode: Int) {
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        // A non-organized display (e.g., non-trusted virtual displays used in CTS) doesn't have
        // TDA.
        if (tdaInfo == null) {
            logW(
                "updateDisplayWindowingMode cannot find DisplayAreaInfo for displayId=%d. This " +
                    " could happen when the display is a non-trusted virtual display.",
                displayId,
            )
            return
        }
        val currentDisplayWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
        if (currentDisplayWindowingMode == targetDisplayWindowingMode) {
            // Already in the target mode.
            return
        }

        logV(
            "Changing display#%d's windowing mode from %s to %s",
            displayId,
            windowingModeToString(currentDisplayWindowingMode),
            windowingModeToString(targetDisplayWindowingMode),
        )

        val wct = WindowContainerTransaction()
        wct.setWindowingMode(tdaInfo.token, targetDisplayWindowingMode)
        shellTaskOrganizer
            .getRunningTasks(displayId)
            .filter { it.activityType == ACTIVITY_TYPE_STANDARD }
            .forEach {
                if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                    // With multi-desks, display windowing mode doesn't affect the windowing
                    // mode of freeform tasks but fullscreen tasks which are the direct children
                    // of TDA.
                    if (it.windowingMode == WINDOWING_MODE_FULLSCREEN) {
                        if (targetDisplayWindowingMode == DESKTOP_FIRST_DISPLAY_WINDOWING_MODE) {
                            wct.setWindowingMode(it.token, WINDOWING_MODE_FULLSCREEN)
                        } else {
                            wct.setWindowingMode(it.token, WINDOWING_MODE_UNDEFINED)
                        }
                    }
                } else {
                    when (it.windowingMode) {
                        currentDisplayWindowingMode -> {
                            wct.setWindowingMode(it.token, currentDisplayWindowingMode)
                        }
                        targetDisplayWindowingMode -> {
                            wct.setWindowingMode(it.token, WINDOWING_MODE_UNDEFINED)
                        }
                    }
                }
            }
        // The override windowing mode of DesktopWallpaper can be UNDEFINED on fullscreen-display
        // right after the first launch while its resolved windowing mode is FULLSCREEN. We here
        // it has the FULLSCREEN override windowing mode.
        desktopWallpaperActivityTokenProvider.getToken(displayId)?.let { token ->
            wct.setWindowingMode(token, WINDOWING_MODE_FULLSCREEN)
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
    }

    // Do not directly use this method to check the state of desktop-first mode. Use
    // [isDisplayDesktopFirst] instead.
    private fun canDesktopFirstModeBeEnabledOnDefaultDisplay(): Boolean {
        if (FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY) {
            logW(
                "FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY is enabled. Forcing desktop-first for " +
                    " testing purposes."
            )
            return true
        }

        val isDefaultDisplayDesktopEligible = isDefaultDisplayDesktopEligible()
        logV(
            "canDesktopFirstModeBeEnabledOnDefaultDisplay: isDefaultDisplayDesktopEligible=%s",
            isDefaultDisplayDesktopEligible,
        )
        if (isDefaultDisplayDesktopEligible) {
            val isExtendedDisplayEnabled = isExtendedDisplayEnabled()
            val hasExternalDisplay = hasExternalDisplay()
            logV(
                "canDesktopFirstModeBeEnabledOnDefaultDisplay: isExtendedDisplayEnabled=%s" +
                    " hasExternalDisplay=%s",
                isExtendedDisplayEnabled,
                hasExternalDisplay,
            )
            if (isExtendedDisplayEnabled && hasExternalDisplay) {
                return true
            }
            if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
                val hasAnyTouchpadDevice = hasAnyTouchpadDevice()
                val hasAnyPhysicalKeyboardDevice = hasAnyPhysicalKeyboardDevice()
                logV(
                    "canDesktopFirstModeBeEnabledOnDefaultDisplay: hasAnyTouchpadDevice=%s" +
                        " hasAnyPhysicalKeyboardDevice=%s",
                    hasAnyTouchpadDevice,
                    hasAnyPhysicalKeyboardDevice,
                )
                if (hasAnyTouchpadDevice && hasAnyPhysicalKeyboardDevice) {
                    return true
                }
            }
        }
        return false
    }

    // Do not directly use this method to check the state of desktop-first mode. Use
    // [isDisplayDesktopFirst] instead.
    @VisibleForTesting
    fun getTargetWindowingModeForDefaultDisplay(): Int {
        if (canDesktopFirstModeBeEnabledOnDefaultDisplay()) {
            return DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        }

        return if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
            TOUCH_FIRST_DISPLAY_WINDOWING_MODE
        } else {
            // If form factor-based desktop first switch is disabled, use the default display
            // windowing mode here to keep the freeform mode for some form factors (e.g.,
            // FEATURE_PC).
            windowManager.getWindowingMode(DEFAULT_DISPLAY)
        }
    }

    private fun isExtendedDisplayEnabled(): Boolean {
        if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) {
            return rootTaskDisplayAreaOrganizer
                .getDisplayIds()
                .filter { it != DEFAULT_DISPLAY }
                .any { displayId -> desktopState.isDesktopModeSupportedOnDisplay(displayId) }
        }

        return 0 !=
            Settings.Global.getInt(
                context.contentResolver,
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                0,
            )
    }

    private fun hasExternalDisplay() =
        rootTaskDisplayAreaOrganizer.getDisplayIds().any { it != DEFAULT_DISPLAY }

    private fun hasAnyTouchpadDevice() =
        inputManager.inputDeviceIds.any { deviceId ->
            inputManager.getInputDevice(deviceId)?.let { device ->
                device.supportsSource(InputDevice.SOURCE_TOUCHPAD) && device.isEnabled()
            } ?: false
        }

    private fun hasAnyPhysicalKeyboardDevice() =
        inputManager.inputDeviceIds.any { deviceId ->
            inputManager.getInputDevice(deviceId)?.let { device ->
                !device.isVirtual() && device.isFullKeyboard() && device.isEnabled()
            } ?: false
        }

    private fun isDefaultDisplayDesktopEligible(): Boolean {
        return desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun dump(originalWriter: PrintWriter, prefix: String) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue) return

        val pw = IndentingPrintWriter(originalWriter, "  ", prefix)

        pw.println(TAG)
        pw.increaseIndent()
        pw.println(
            "targetWindowingModeForDefaultDisplay=" + getTargetWindowingModeForDefaultDisplay()
        )
        pw.println(
            "canDesktopFirstModeBeEnabledOnDefaultDisplay=" +
                canDesktopFirstModeBeEnabledOnDefaultDisplay()
        )
        pw.println("isDefaultDisplayDesktopEligible=" + isDefaultDisplayDesktopEligible())
        pw.println("isExtendedDisplayEnabled=" + isExtendedDisplayEnabled())
        pw.println("hasExternalDisplay=" + hasExternalDisplay())
        pw.println(
            "FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY=" + FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY
        )
        if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
            pw.println("hasAnyTouchpadDevice=" + hasAnyTouchpadDevice())
            pw.println("hasAnyPhysicalKeyboardDevice=" + hasAnyPhysicalKeyboardDevice())
        }

        pw.println("Current Desktop Display Modes:")
        pw.increaseIndent()
        rootTaskDisplayAreaOrganizer.displayIds.forEach { displayId ->
            val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
            pw.println("Display#$displayId isDesktopFirst=$isDesktopFirst")
        }
    }

    companion object {
        private const val TAG = "DesktopDisplayModeController"
    }
}
