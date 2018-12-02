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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.blurWallpaperProvider
import ch.deletescape.lawnchair.dpToPx
import ch.deletescape.lawnchair.runOnMainThread
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.Interpolators.ACCEL
import com.android.launcher3.anim.Interpolators.ACCEL_2
import com.android.launcher3.graphics.NinePatchDrawHelper
import com.android.launcher3.graphics.ShadowGenerator
import com.android.launcher3.uioverrides.OverviewState
import com.android.launcher3.util.Themes
import com.android.quickstep.views.ShelfScrimView

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

class BlurScrimView(context: Context, attrs: AttributeSet) : ShelfScrimView(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val key_radius = "pref_dockRadius"
    private val key_opacity = "pref_allAppsOpacitySB"
    private val key_dock_opacity = "pref_hotseatCustomOpacity"
    private val key_dock_arrow = "pref_hotseatShowArrow"

    private val prefsToWatch = arrayOf(key_radius, key_opacity, key_dock_opacity, key_dock_arrow)

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable?, what: Runnable?) {

            }

            override fun invalidateDrawable(who: Drawable?) {
                runOnMainThread { invalidate() }
            }

            override fun scheduleDrawable(who: Drawable?, what: Runnable?, `when`: Long) {

            }
        }
    }

    private val provider by lazy { context.blurWallpaperProvider }
    private val useFlatColor get() = mLauncher.deviceProfile.isVerticalBarLayout
    private val blurRadius get() = if (useFlatColor) 0f else mRadius
    private var blurDrawable: BlurDrawable? = null
    private val shadowHelper by lazy { NinePatchDrawHelper() }
    private val shadowBlur by lazy { resources.getDimension(R.dimen.all_apps_scrim_blur) }
    private var shadowBitmap = generateShadowBitmap()

    private val enableShadow get() = prefs.dockShadow && !useFlatColor

    private fun createBlurDrawable(): BlurDrawable? {
        blurDrawable?.apply { if (isAttachedToWindow) stopListening() }
        return if (BlurWallpaperProvider.isEnabled) {
            provider.createDrawable(blurRadius, false).apply { callback = blurDrawableCallback }
        } else {
            null
        }?.apply {
            setBounds(left, top, right, bottom)
            if (isAttachedToWindow) startListening()
        }
    }

    private fun generateShadowBitmap(): Bitmap {
        val tmp = mRadius + shadowBlur
        val builder = ShadowGenerator.Builder(0)
        builder.radius = mRadius
        builder.shadowBlur = shadowBlur
        val round = 2 * Math.round(tmp) + 20
        val bitmap = Bitmap.createBitmap(round, round / 2, Bitmap.Config.ARGB_8888)
        val f = 2.0f * tmp + 20.0f - shadowBlur
        builder.bounds.set(shadowBlur, shadowBlur, f, f)
        builder.drawShadow(Canvas(bitmap))
        return bitmap
    }

    override fun reInitUi() {
        blurDrawable = createBlurDrawable()
        shadowBitmap = generateShadowBitmap()
        blurDrawable?.alpha = 0
        super.reInitUi()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener(this, *prefsToWatch)
        blurDrawable?.startListening()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when (key) {
            key_radius -> {
                mRadius = dpToPx(prefs.dockRadius)
            }
            key_opacity -> {
                mEndAlpha = prefs.allAppsOpacity.takeIf { it >= 0 } ?: DEFAULT_END_ALPHA
                mEndScrim = ColorUtils.setAlphaComponent(mEndScrim, mEndAlpha)
                mEndFlatColor = ColorUtils.compositeColors(mEndScrim, ColorUtils.setAlphaComponent(
                        mScrimColor, mMaxScrimAlpha))
                mEndFlatColorAlpha = Color.alpha(mEndFlatColor)
            }
            key_dock_opacity -> {
                mMidAlpha = prefs.dockOpacity.takeIf { it >= 0 } ?: Themes.getAttrInteger(context, R.attr.allAppsInterimScrimAlpha)
            }
            key_dock_arrow -> {
                updateDragHandleVisibility()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener(this, *prefsToWatch)
        blurDrawable?.stopListening()
    }

    override fun onDrawFlatColor(canvas: Canvas) {
        blurDrawable?.run {
            setBounds(0, 0, width, (height + mRadius).toInt())
            draw(canvas)
        }
    }

    override fun onDrawRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        blurDrawable?.run {
            setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            draw(canvas)
        }
        if (enableShadow) {
            val scrimHeight = mShelfTop
            val f = paddingLeft.toFloat() - shadowBlur
            val f2 = scrimHeight - shadowBlur
            val f3 = shadowBlur + width
            if (paddingLeft <= 0 && paddingRight <= 0) {
                shadowHelper.draw(shadowBitmap, canvas, f, f2, f3)
            } else {
                shadowHelper.drawVerticallyStretched(shadowBitmap, canvas, f, f2, f3, scrimHeight)
            }
        }
        super.onDrawRoundRect(canvas, left, top, right, bottom, rx, ry, paint)
    }

    override fun updateColors() {
        super.updateColors()
        val alpha = when {
            useFlatColor -> ((1 - mProgress) * 255).toInt()
            mProgress >= mMidProgress -> Math.round(255 * ACCEL_2.getInterpolation(
                    Math.max(0f, 1 - mProgress) / (1 - mMidProgress)))
            else -> {
                val startAlpha = if (mMidProgress >= 1f) 0f else 255f
                Math.round(
                        Utilities.mapToRange(mProgress, 0.toFloat(), mMidProgress, 255f,
                                startAlpha, Interpolators.clampToProgress(ACCEL, 0.5f, 1f)))
            }
        }
        blurDrawable?.alpha = alpha
        shadowHelper.paint.alpha = alpha
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (useFlatColor) {
            blurDrawable?.setBounds(left, top, right, bottom)
        }
    }

    override fun getMidProgress(): Float {
        if (!prefs.dockGradientStyle) return OverviewState.getNormalVerticalProgress(mLauncher)
        return super.getMidProgress()
    }
}
