/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.dpToPx
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.runOnMainThread
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.BACKGROUND_APP
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators.*
import com.android.launcher3.graphics.NinePatchDrawHelper
import com.android.launcher3.icons.ShadowGenerator
import com.android.launcher3.util.Themes
import com.android.quickstep.SysUINavigationMode
import com.android.quickstep.views.ShelfScrimView
import com.google.android.apps.nexuslauncher.qsb.AbstractQsbLayout

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class BlurScrimView(context: Context, attrs: AttributeSet) : ShelfScrimView(context, attrs),
        LawnchairPreferences.OnPreferenceChangeListener,
        BlurWallpaperProvider.Listener, ColorEngine.OnColorChangeListener {

    private val key_radius = "pref_dockRadius"
    private val key_opacity = "pref_allAppsOpacitySB"
    private val key_dock_opacity = "pref_hotseatCustomOpacity"
    private val key_dock_arrow = "pref_hotseatShowArrow"
    private val key_search_radius = "pref_searchbarRadius"
    private val key_debug_state = "pref_debugDisplayState"

    private val prefsToWatch =
            arrayOf(key_radius, key_opacity, key_dock_opacity, key_dock_arrow, key_search_radius,
                    key_debug_state)
    private val colorsToWatch = arrayOf(ColorEngine.Resolvers.ALLAPPS_BACKGROUND, ColorEngine.Resolvers.DOCK_BACKGROUND)

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {

            }

            override fun invalidateDrawable(who: Drawable) {
                runOnMainThread { invalidate() }
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {

            }
        }
    }

    private val provider by lazy { BlurWallpaperProvider.getInstance(context) }
    private val useFlatColor get() = mLauncher.deviceProfile.isVerticalBarLayout
    private var blurDrawable: BlurDrawable? = null

    private val insets = Rect()

    private val colorRanges = ArrayList<ColorRange>()

    private var allAppsBackground = 0
    private var dockBackground = 0

    private val reInitUiRunnable = this::reInitUi
    private var fullBlurProgress = 0f

    private var shouldDrawDebug = false
    private val debugTextPaint = Paint().apply {
        textSize = DEBUG_TEXT_SIZE
        color = Color.RED
        typeface = Typeface.DEFAULT_BOLD
    }

    private val defaultEndAlpha = Color.alpha(mEndScrim)

    val currentBlurAlpha get() = blurDrawable?.alpha
    val shelfPaint get() = mPaint!!

    private fun createBlurDrawable(): BlurDrawable? {
        blurDrawable?.let { if (isAttachedToWindow) it.stopListening() }
        return if (BlurWallpaperProvider.isEnabled) {
            provider.createDrawable(mRadius, 0f).apply {
                callback = blurDrawableCallback
                setBounds(left, top, right, bottom)
                if (isAttachedToWindow) startListening()
            }
        } else {
            null
        }
    }

    override fun reInitUi() {
        blurDrawable = createBlurDrawable()
        blurDrawable?.alpha = 0
        rebuildColors()
        super.reInitUi()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener(this, *prefsToWatch)
        ColorEngine.getInstance(context).addColorChangeListeners(this, *colorsToWatch)
        BlurWallpaperProvider.getInstance(context).addListener(this)
        blurDrawable?.startListening()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when (key) {
            key_radius -> {
                mRadius = dpToPx(prefs.dockRadius)
                blurDrawable?.also {
                    it.blurRadii = BlurDrawable.Radii(mRadius, 0f)
                }
            }
            key_opacity -> {
                mEndAlpha = prefs.allAppsOpacity.takeIf { it >= 0 } ?: defaultEndAlpha
                calculateEndScrim()
                mEndFlatColorAlpha = Color.alpha(mEndFlatColor)
                postReInitUi()
            }
            key_dock_opacity -> {
                postReInitUi()
            }
            key_dock_arrow -> {
                updateDragHandleVisibility()
            }
            key_debug_state -> {
                shouldDrawDebug = prefs.displayDebugOverlay
            }
        }
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.ALLAPPS_BACKGROUND -> {
                allAppsBackground = resolveInfo.color
                calculateEndScrim()
                postReInitUi()
            }
            ColorEngine.Resolvers.DOCK_BACKGROUND -> {
                dockBackground = resolveInfo.color
                postReInitUi()
            }
        }
    }

    private fun calculateEndScrim() {
        mEndScrim = ColorUtils.setAlphaComponent(allAppsBackground, mEndAlpha)
        mEndFlatColor = ColorUtils.compositeColors(mEndScrim, ColorUtils.setAlphaComponent(
                mScrimColor, mMaxScrimAlpha))
    }

    private fun rebuildColors() {
        val recentsProgress = LauncherState.OVERVIEW.getScrimProgress(mLauncher)

        val hasRecents = Utilities.isRecentsEnabled() && recentsProgress < 1f

        val fullShelfColor = ColorUtils.setAlphaComponent(allAppsBackground, mEndAlpha)
        val recentsShelfColor = ColorUtils.setAlphaComponent(allAppsBackground, super.getMidAlpha())
        val nullShelfColor = ColorUtils.setAlphaComponent(allAppsBackground, 0)

        val colors = ArrayList<Pair<Float, Int>>()
        colors.add(Pair(Float.NEGATIVE_INFINITY, fullShelfColor))
        colors.add(Pair(0.5f, fullShelfColor))
        fullBlurProgress = 0.5f
        if (hasRecents) {
            colors.add(Pair(recentsProgress, recentsShelfColor))
            fullBlurProgress = recentsProgress
        } else if (hasRecents) {
            colors.add(Pair(recentsProgress, recentsShelfColor))
            fullBlurProgress = recentsProgress
        }
        colors.add(Pair(1f, nullShelfColor))
        colors.add(Pair(Float.POSITIVE_INFINITY, nullShelfColor))

        colorRanges.clear()
        for (i in (1 until colors.size)) {
            val color1 = colors[i - 1]
            val color2 = colors[i]
            colorRanges.add(ColorRange(color1.first, color2.first, color1.second, color2.second))
        }
    }

    override fun getMidAlpha(): Int {
        return prefs.dockOpacity.takeIf { it >= 0 } ?: super.getMidAlpha()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener(this, *prefsToWatch)
        ColorEngine.getInstance(context).removeColorChangeListeners(this, *colorsToWatch)
        BlurWallpaperProvider.getInstance(context).removeListener(this)
        blurDrawable?.stopListening()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (shouldDrawDebug) {
            drawDebug(canvas)
        }
    }

    override fun setInsets(insets: Rect) {
        super.setInsets(insets)
        this.insets.set(insets)
        postReInitUi()
    }

    override fun onDrawFlatColor(canvas: Canvas) {
        blurDrawable?.run {
            setBounds(0, 0, width, height)
            draw(canvas, true)
        }
    }

    override fun onDrawRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        blurDrawable?.run {
            setBlurBounds(left, top, right, bottom)
            draw(canvas)
        }
        super.onDrawRoundRect(canvas, left, top, right, bottom, rx, ry, paint)
    }

    override fun updateColors() {
        super.updateColors()
        val alpha = when {
            useFlatColor -> ((1 - mProgress) * 255).toInt()
            mProgress >= fullBlurProgress -> Math.round(255 * ACCEL_2.getInterpolation(
                    Math.max(0f, 1 - mProgress) / (1 - fullBlurProgress)))
            else -> 255
        }
        blurDrawable?.alpha = alpha

        mDragHandleOffset = Math.max(0f, mDragHandleBounds.top + mDragHandleSize - mShelfTop)

        if (!useFlatColor) {
            if (mProgress >= 1 && mSysUINavigationMode == SysUINavigationMode.Mode.NO_BUTTON
                && mLauncher.stateManager.state == BACKGROUND_APP) {
                mShelfColor = ColorUtils.setAlphaComponent(allAppsBackground, mMidAlpha)
            } else {
                mShelfColor = getColorForProgress(mProgress)
            }
        }
    }

    private fun getColorForProgress(progress: Float): Int {
        val interpolatedProgress: Float = when {
            progress >= 1 -> progress
            progress >= mMidProgress -> Utilities.mapToRange(
                    progress, mMidProgress, 1f, mMidProgress, 1f,
                    mBeforeMidProgressColorInterpolator)
            else -> Utilities.mapToRange(
                    progress, 0f, mMidProgress, 0f, mMidProgress,
                    mAfterMidProgressColorInterpolator)
        }
        colorRanges.forEach {
            if (interpolatedProgress in it) {
                return it.getColor(interpolatedProgress)
            }
        }
        return 0
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (useFlatColor) {
            blurDrawable?.setBounds(left, top, right, bottom)
        }
    }

    override fun getMidProgress(): Float {
        /* TODO: implement this
        if (!prefs.dockGradientStyle) {
            return Math.max(HomeState.getNormalProgress(mLauncher), OverviewState.getNormalVerticalProgress(mLauncher))
        }
         */
        return super.getMidProgress()
    }

    override fun onEnabledChanged() {
        postReInitUi()
    }

    private fun drawDebug(canvas: Canvas) {
        listOf(
                "version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "state: ${mLauncher.stateManager.state::class.java.simpleName}",
                "toState: ${mLauncher.stateManager.toState::class.java.simpleName}"
              ).forEachIndexed { index, line ->
            canvas.drawText(line, 50f, 200f + (DEBUG_LINE_HEIGHT * index), debugTextPaint)
        }
    }

    private fun postReInitUi() {
        handler?.removeCallbacks(reInitUiRunnable)
        handler?.post(reInitUiRunnable)
    }

    fun setOverlayScroll(scroll: Float) {
        blurDrawable?.viewOffsetX = scroll
    }

    companion object {
        private const val DEBUG_TEXT_SIZE = 30f
        private const val DEBUG_LINE_HEIGHT = DEBUG_TEXT_SIZE + 3f
    }

    class ColorRange(private val start: Float, private val end: Float,
                     private val startColor: Int, private val endColor: Int) {

        private val range = start..end

        fun getColor(progress: Float): Int {
            if (start == Float.NEGATIVE_INFINITY) return endColor
            if (end == Float.POSITIVE_INFINITY) return startColor
            val amount = Utilities.mapToRange(progress, start, end, 0f, 1f, LINEAR)
            return ColorUtils.blendARGB(startColor, endColor, amount)
        }

        operator fun contains(value: Float) = value in range
    }
}
