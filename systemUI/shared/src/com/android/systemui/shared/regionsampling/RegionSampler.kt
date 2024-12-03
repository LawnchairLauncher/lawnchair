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
package com.android.systemui.shared.regionsampling

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import java.io.PrintWriter
import java.util.concurrent.Executor

/** Class for instance of RegionSamplingHelper */
open class RegionSampler
@JvmOverloads
constructor(
    val sampledView: View,
    val mainExecutor: Executor?,
    val bgExecutor: Executor?,
    val regionSamplingEnabled: Boolean,
    val isLockscreen: Boolean = false,
    val wallpaperManager: WallpaperManager? = WallpaperManager.getInstance(sampledView.context),
    val updateForegroundColor: UpdateColorCallback,
) : WallpaperManager.LocalWallpaperColorConsumer {
    private var regionDarkness = RegionDarkness.DEFAULT
    private var samplingBounds = Rect()
    private val tmpScreenLocation = IntArray(2)
    private var lightForegroundColor = Color.WHITE
    private var darkForegroundColor = Color.BLACK
    @VisibleForTesting val displaySize = Point()
    private var initialSampling: WallpaperColors? = null

    /**
     * Sets the colors to be used for Dark and Light Foreground.
     *
     * @param lightColor The color used for Light Foreground.
     * @param darkColor The color used for Dark Foreground.
     */
    fun setForegroundColors(lightColor: Int, darkColor: Int) {
        lightForegroundColor = lightColor
        darkForegroundColor = darkColor
    }

    private val layoutChangedListener =
        object : View.OnLayoutChangeListener {

            override fun onLayoutChange(
                view: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {

                // don't pass in negative bounds when region is in transition state
                if (sampledView.locationOnScreen[0] < 0 || sampledView.locationOnScreen[1] < 0) {
                    return
                }

                val currentViewRect = Rect(left, top, right, bottom)
                val oldViewRect = Rect(oldLeft, oldTop, oldRight, oldBottom)

                if (currentViewRect != oldViewRect) {
                    stopRegionSampler()
                    startRegionSampler()
                }
            }
        }

    /**
     * Determines which foreground color to use based on region darkness.
     *
     * @return the determined foreground color
     */
    fun currentForegroundColor(): Int {
        return if (regionDarkness.isDark) {
            lightForegroundColor
        } else {
            darkForegroundColor
        }
    }

    private fun getRegionDarkness(isRegionDark: Boolean): RegionDarkness {
        return if (isRegionDark) {
            RegionDarkness.DARK
        } else {
            RegionDarkness.LIGHT
        }
    }

    fun currentRegionDarkness(): RegionDarkness {
        return regionDarkness
    }

    /** Start region sampler */
    fun startRegionSampler() {

        if (!regionSamplingEnabled) {
            if (DEBUG) Log.d(TAG, "startRegionSampler() | RegionSampling flag not enabled")
            return
        }

        sampledView.addOnLayoutChangeListener(layoutChangedListener)

        val screenLocationBounds = calculateScreenLocation(sampledView)
        if (screenLocationBounds == null) {
            if (DEBUG) Log.d(TAG, "startRegionSampler() | passed in null region")
            return
        }
        if (screenLocationBounds.isEmpty) {
            if (DEBUG) Log.d(TAG, "startRegionSampler() | passed in empty region")
            return
        }

        val sampledRegionWithOffset = convertBounds(screenLocationBounds)
        if (
            sampledRegionWithOffset.left < 0.0 ||
                sampledRegionWithOffset.right > 1.0 ||
                sampledRegionWithOffset.top < 0.0 ||
                sampledRegionWithOffset.bottom > 1.0
        ) {
            if (DEBUG)
                Log.d(
                    TAG,
                    "startRegionSampler() | view out of bounds: $screenLocationBounds | " +
                        "screen width: ${displaySize.x}, screen height: ${displaySize.y}",
                    Exception()
                )
            return
        }

        val regions = ArrayList<RectF>()
        regions.add(sampledRegionWithOffset)

        wallpaperManager?.addOnColorsChangedListener(
            this,
            regions,
            if (isLockscreen) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        )

        bgExecutor?.execute(
            Runnable {
                initialSampling =
                    wallpaperManager?.getWallpaperColors(
                        if (isLockscreen) WallpaperManager.FLAG_LOCK
                        else WallpaperManager.FLAG_SYSTEM
                    )
                mainExecutor?.execute { onColorsChanged(sampledRegionWithOffset, initialSampling) }
            }
        )
    }

    /** Stop region sampler */
    fun stopRegionSampler() {
        wallpaperManager?.removeOnColorsChangedListener(this)
        sampledView.removeOnLayoutChangeListener(layoutChangedListener)
    }

    /** Dump region sampler */
    fun dump(pw: PrintWriter) {
        pw.println("[RegionSampler]")
        pw.println("regionSamplingEnabled: $regionSamplingEnabled")
        pw.println("regionDarkness: $regionDarkness")
        pw.println("lightForegroundColor: ${Integer.toHexString(lightForegroundColor)}")
        pw.println("darkForegroundColor: ${Integer.toHexString(darkForegroundColor)}")
        pw.println("passed-in sampledView: $sampledView")
        pw.println("calculated samplingBounds: $samplingBounds")
        pw.println(
            "sampledView width: ${sampledView.width}, sampledView height: ${sampledView.height}"
        )
        pw.println("screen width: ${displaySize.x}, screen height: ${displaySize.y}")
        pw.println(
            "sampledRegionWithOffset: ${convertBounds(
                    calculateScreenLocation(sampledView) ?: RectF())}"
        )
        pw.println(
            "initialSampling for ${if (isLockscreen) "lockscreen" else "homescreen" }" +
                ": $initialSampling"
        )
    }

    fun calculateScreenLocation(sampledView: View): RectF? {

        val screenLocation = tmpScreenLocation
        /**
         * The method getLocationOnScreen is used to obtain the view coordinates relative to its
         * left and top edges on the device screen. Directly accessing the X and Y coordinates of
         * the view returns the location relative to its parent view instead.
         */
        sampledView.getLocationOnScreen(screenLocation)
        val left = screenLocation[0]
        val top = screenLocation[1]

        samplingBounds.left = left
        samplingBounds.top = top
        samplingBounds.right = left + sampledView.width
        samplingBounds.bottom = top + sampledView.height

        // ensure never go out of bounds
        if (samplingBounds.right > displaySize.x) samplingBounds.right = displaySize.x
        if (samplingBounds.bottom > displaySize.y) samplingBounds.bottom = displaySize.y

        return RectF(samplingBounds)
    }

    /**
     * Convert the bounds of the region we want to sample from to fractional offsets because
     * WallpaperManager requires the bounds to be between [0,1]. The wallpaper is treated as one
     * continuous image, so if there are multiple screens, then each screen falls into a fractional
     * range. For instance, 4 screens have the ranges [0, 0.25], [0,25, 0.5], [0.5, 0.75], [0.75,
     * 1].
     */
    fun convertBounds(originalBounds: RectF): RectF {

        // TODO(b/265969235): GRAB # PAGES + CURRENT WALLPAPER PAGE # FROM LAUNCHER (--> HS
        // Smartspace always on 1st page)
        // TODO(b/265968912): remove hard-coded value once LS wallpaper supported
        val wallpaperPageNum = 0
        val numScreens = 1

        val screenWidth = displaySize.x
        // TODO: investigate small difference between this and the height reported in go/web-hv
        val screenHeight = displaySize.y

        val newBounds = RectF()
        // horizontal
        newBounds.left = ((originalBounds.left / screenWidth) + wallpaperPageNum) / numScreens
        newBounds.right = ((originalBounds.right / screenWidth) + wallpaperPageNum) / numScreens
        // vertical
        newBounds.top = originalBounds.top / screenHeight
        newBounds.bottom = originalBounds.bottom / screenHeight

        return newBounds
    }

    init {
        sampledView?.context?.display?.getSize(displaySize)
    }

    override fun onColorsChanged(area: RectF?, colors: WallpaperColors?) {
        // update text color when wallpaper color changes
        regionDarkness =
            getRegionDarkness(
                (colors?.colorHints?.and(WallpaperColors.HINT_SUPPORTS_DARK_TEXT)) !=
                    WallpaperColors.HINT_SUPPORTS_DARK_TEXT
            )
        if (DEBUG)
            Log.d(TAG, "onColorsChanged() | region darkness = $regionDarkness for region $area")
        updateForegroundColor()
    }

    companion object {
        private const val TAG = "RegionSampler"
        private const val DEBUG = false
    }
}

typealias UpdateColorCallback = () -> Unit
