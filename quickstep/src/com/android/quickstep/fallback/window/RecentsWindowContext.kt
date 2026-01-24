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

package com.android.quickstep.fallback.window

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import android.view.WindowManager.LayoutParams.PRIVATE_FLAG_CONSUME_IME_INSETS
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.BaseContext
import com.android.launcher3.util.Themes

/**
 * Window context for the Overview overlays.
 *
 * <p>
 * Overlays have their own window and need a window context.
 */
abstract class RecentsWindowContext(windowContext: Context, wallpaperColorHints: Int) :
    BaseContext(
        base = windowContext,
        themeResId = Themes.getActivityThemeRes(windowContext, wallpaperColorHints),
        destroyOnDetach = false,
    ) {

    private var deviceProfile: DeviceProfile? = null

    private val windowTitle: String = "RecentsWindow"

    protected var windowLayoutParams: WindowManager.LayoutParams? =
        createDefaultWindowLayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowTitle,
        )

    fun initDeviceProfile() {
        deviceProfile =
            if (displayId == Display.DEFAULT_DISPLAY)
                InvariantDeviceProfile.INSTANCE[this].getDeviceProfile(this)
            else InvariantDeviceProfile.INSTANCE[this].createDeviceProfileForSecondaryDisplay(this)
    }

    override fun getDeviceProfile(): DeviceProfile {
        if (deviceProfile == null) {
            initDeviceProfile()
        }
        return deviceProfile!!
    }

    /**
     * Creates LayoutParams for adding a view directly to WindowManager as a new window.
     *
     * @param type The window type to pass to the created WindowManager.LayoutParams.
     * @param title The window title to pass to the created WindowManager.LayoutParams.
     */
    private fun createDefaultWindowLayoutParams(
        type: Int,
        title: String,
    ): WindowManager.LayoutParams {
        var windowFlags =
            (WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        val windowLayoutParams =
            WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT,
            )

        windowLayoutParams.title = title
        windowLayoutParams.fitInsetsTypes = 0
        windowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        windowLayoutParams.isSystemApplicationOverlay = true
        windowLayoutParams.privateFlags = PRIVATE_FLAG_CONSUME_IME_INSETS

        return windowLayoutParams
    }
}
