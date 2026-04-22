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

package com.android.launcher3.util.launcheremulator

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManagerGlobal
import android.net.Uri.Builder
import android.view.Display
import android.view.DisplayInfo
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.launcheremulator.models.EmulationParams
import com.android.launcher3.util.launcheremulator.models.LauncherOrientation
import com.android.launcher3.util.window.WindowManagerProxy
import kotlin.math.roundToInt
import org.junit.Assert
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow.extract
import org.robolectric.shadows.ShadowDisplayManagerGlobal
import org.robolectric.util.ReflectionHelpers

/** Alternate implementation of LauncherCustomizer for deviceless tests */
object LauncherCustomizer {

    private const val RESULT_SUCCESS = 1
    private const val COL_ICON_THEMED_VALUE = "boolean_value"

    /** Apply all non null customizations starting from device, then grid, font scale and theme */
    @Throws(Exception::class)
    @JvmStatic
    fun applyAll(context: Context, params: EmulationParams) {
        val device = params.device
        val proxy = WindowManagerProxy.INSTANCE.get(context)
        if (proxy is TestWindowManagerProxy) proxy.setDevice(device)

        val density = DensityPicker.getDisplayEntries(device)[params.density]!!
        val isLandscape = params.orientation != LauncherOrientation.PORTRAIT

        val scaledWidth = (device.width * 160f / density).roundToInt()
        val scaledHeight = (device.height * 160f / density).roundToInt()
        val darkMode = if (params.isDarkTheme) "night" else "notnight"
        val landscape = if (isLandscape) "land" else "port"
        // Use test pseudolocales *_XA (RTL) and *_XB (LTR)
        val locale = if (params.isRtl) "en-rXB" else "en"
        val qualifier =
            "${locale}-w${scaledWidth}dp-h${scaledHeight}dp-${landscape}-${darkMode}-${density}dpi"
        RuntimeEnvironment.setQualifiers(qualifier)
        RuntimeEnvironment.setFontScale(params.fontScale.value)

        // Hack to ensure that the device size exactly matches real devices. Robolectric auto
        // sets the appWidth to widthDp * density, which can cause rounding errors. Instead we
        // set those values using reflection
        val minSize = Math.min(device.width, device.height)
        val maxSize = Math.max(device.width, device.height)
        val di = DisplayManagerGlobal.getInstance().getDisplayInfo(Display.DEFAULT_DISPLAY)
        di.appWidth = if (isLandscape) maxSize else minSize
        di.appHeight = if (isLandscape) minSize else maxSize
        di.logicalHeight = di.appHeight
        di.logicalWidth = di.appWidth
        di.smallestNominalAppHeight = minSize
        di.smallestNominalAppWidth = minSize
        di.largestNominalAppHeight = maxSize
        di.largestNominalAppWidth = maxSize
        val dmGlobal: ShadowDisplayManagerGlobal = extract(DisplayManagerGlobal.getInstance())
        ReflectionHelpers.callInstanceMethod<Void>(
            dmGlobal,
            "changeDisplay",
            ReflectionHelpers.ClassParameter.from(Int::class.java, Display.DEFAULT_DISPLAY),
            ReflectionHelpers.ClassParameter.from(DisplayInfo::class.java, di),
        )

        DisplayController.INSTANCE[context].onConfigurationChanged(
            Configuration(context.resources.configuration)
        )

        get(context).put(LauncherPrefs.ALLOW_ROTATION, !params.isFixedLandscape)
        get(context).put(LauncherPrefs.FIXED_LANDSCAPE_MODE, params.isFixedLandscape)

        applyGridOption(context, "default_grid", "name", params.grid)
        applyGridOption(context, "icon_themed", COL_ICON_THEMED_VALUE, params.isUsingThemeIcons)
    }

    private fun applyGridOption(context: Context, method: String, arg: String, paramArgValue: Any) {
        var argValue: Any = paramArgValue
        if (Flags.oneGridSpecs()) {
            if (argValue is String && argValue.compareTo("normal") == 0) {
                argValue = "medium"
            }
        } else {
            if (argValue is String && argValue.compareTo("medium") == 0) {
                argValue = "normal"
            }
        }
        val gridUri =
            Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(context.packageName + ".grid_control")
                .appendPath(method)
                .build()
        Assert.assertEquals(
            RESULT_SUCCESS,
            context.appComponent.gridCustomizationsProxy.update(
                gridUri,
                ContentValues().apply { putObject(arg, argValue) },
                null,
                null,
            ),
        )
    }

    @Throws(java.lang.Exception::class) fun stopEmulation() {}
}
