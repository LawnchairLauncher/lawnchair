/*
 * Copyright (C) 2026 Abdelrahman Hussein (عبدالرحمن حسين)
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
package app.lawnchair.animation

import android.graphics.Path
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Runtime animation profiles for Omni Launcher-owned transitions.
 *
 * The profile is cached in memory so animation frames never read preferences or allocate
 * interpolators. These presets are inspired by familiar motion languages, not OEM code.
 */
object OmniMotionProfile {
    const val STYLE_FUSION = "fusion"
    const val STYLE_PIXEL = "pixel"
    const val STYLE_IOS = "ios"
    const val STYLE_SAMSUNG = "samsung"

    @Volatile
    private var activeStyle = STYLE_FUSION

    private val fusionHome = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)
    private val pixelHome = PathInterpolator(0.18f, 0.0f, 0.0f, 1.0f)
    private val iosHome = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)
    private val samsungHome = PathInterpolator(0.34f, 0.0f, 0.20f, 1.0f)

    private val fusionAppLaunch = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)
    private val pixelAppLaunch = PathInterpolator(0.16f, 0.0f, 0.20f, 1.0f)
    private val iosAppLaunch = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)
    private val samsungAppLaunch = PathInterpolator(0.30f, 0.0f, 0.15f, 1.0f)

    private val fusionDrawerSettle = PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f)
    private val pixelDrawerSettle = PathInterpolator(0.22f, 0.0f, 0.0f, 1.0f)
    private val iosDrawerSettle = PathInterpolator(0.32f, 0.72f, 0.0f, 1.0f)
    private val samsungDrawerSettle = PathInterpolator(0.34f, 0.0f, 0.20f, 1.0f)

    private val fusionFolderClose = PathInterpolator(0.40f, 0.0f, 0.20f, 1.0f)
    private val pixelFolderClose = PathInterpolator(0.40f, 0.0f, 0.60f, 1.0f)
    private val iosFolderClose = PathInterpolator(0.40f, 0.0f, 0.20f, 1.0f)
    private val samsungFolderClose = PathInterpolator(0.50f, 0.0f, 0.10f, 1.0f)

    private val fusionRadial = PathInterpolator(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.045f, 0.0356f, 0.0975f, 0.2055f, 0.15f, 0.3952f)
            cubicTo(0.235f, 0.6855f, 0.235f, 1f, 1f, 1f)
        },
    )
    private val pixelRadial = PathInterpolator(0.20f, 0.0f, 0.10f, 1.0f)
    private val iosRadial = PathInterpolator(0.16f, 1.0f, 0.30f, 1.0f)
    private val samsungRadial = PathInterpolator(0.34f, 0.0f, 0.20f, 1.0f)

    @JvmStatic
    fun setStyle(style: String?) {
        activeStyle = when (style) {
            STYLE_PIXEL -> STYLE_PIXEL
            STYLE_IOS -> STYLE_IOS
            STYLE_SAMSUNG -> STYLE_SAMSUNG
            else -> STYLE_FUSION
        }
    }

    @JvmStatic
    fun getStyle(): String = activeStyle

    @JvmStatic
    fun getHomeRevealInterpolator(): Interpolator = when (activeStyle) {
        STYLE_PIXEL -> pixelHome
        STYLE_IOS -> iosHome
        STYLE_SAMSUNG -> samsungHome
        else -> fusionHome
    }

    /**
     * Curve for app-window launches. Kept separate from workspace reveals so each surface
     * can have its own motion character without adding work to the frame loop.
     */
    @JvmStatic
    fun getAppLaunchInterpolator(): Interpolator = when (activeStyle) {
        STYLE_PIXEL -> pixelAppLaunch
        STYLE_IOS -> iosAppLaunch
        STYLE_SAMSUNG -> samsungAppLaunch
        else -> fusionAppLaunch
    }

    /** Curve used only when the app drawer settles after a user releases their gesture. */
    @JvmStatic
    fun getAppDrawerSettleInterpolator(): Interpolator = when (activeStyle) {
        STYLE_PIXEL -> pixelDrawerSettle
        STYLE_IOS -> iosDrawerSettle
        STYLE_SAMSUNG -> samsungDrawerSettle
        else -> fusionDrawerSettle
    }

    /** Closing motion is tuned independently from opening motion. */
    @JvmStatic
    fun getFolderCloseInterpolator(): Interpolator = when (activeStyle) {
        STYLE_PIXEL -> pixelFolderClose
        STYLE_IOS -> iosFolderClose
        STYLE_SAMSUNG -> samsungFolderClose
        else -> fusionFolderClose
    }

    /**
     * Give each profile a subtle duration character while keeping Fusion's existing timing.
     * Clamp short transitions to avoid making label/fade animations effectively disappear.
     */
    @JvmStatic
    fun scaleFolderDuration(baseDurationMs: Int): Int {
        val factor = when (activeStyle) {
            STYLE_PIXEL -> 0.96f
            STYLE_IOS -> 0.92f
            STYLE_SAMSUNG -> 1.04f
            else -> 1.0f
        }
        return (baseDurationMs * factor).toInt().coerceAtLeast(120)
    }

    @JvmStatic
    fun getRadialScaleInterpolator(): Interpolator = when (activeStyle) {
        STYLE_PIXEL -> pixelRadial
        STYLE_IOS -> iosRadial
        STYLE_SAMSUNG -> samsungRadial
        else -> fusionRadial
    }
}
