/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.allapps

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.views.BlurScrimView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.google.android.apps.nexuslauncher.qsb.AbstractQsbLayout
import com.google.android.apps.nexuslauncher.qsb.AllAppsQsbLayout

class BlurQsbLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AllAppsQsbLayout(context, attrs, defStyleAttr), BlurWallpaperProvider.Listener,
      LawnchairPreferences.OnPreferenceChangeListener {

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

    private val blurProvider by lazy { BlurWallpaperProvider.getInstance(context) }
    private var blurDrawable: BlurDrawable? = null
        set(value) {
            if (isAttachedToWindow) {
                field?.stopListening()
            }
            field = value
            if (isAttachedToWindow) {
                field?.startListening()
            }
        }
    var scrimView: BlurScrimView? = null

    private val isRtl = Utilities.isRtl(resources)
    private val bubbleGap = resources.getDimensionPixelSize(R.dimen.qsb_two_bubble_gap)
    private val micWidth = resources.getDimensionPixelSize(R.dimen.qsb_mic_width)
    private val tmpRectF = RectF()

    private fun createBlurDrawable() {
        blurDrawable = if (isVisible && BlurWallpaperProvider.isEnabled) {
            val height = height - paddingTop - paddingBottom
            val radius = AbstractQsbLayout.getCornerRadius(context, height / 2f)
            val drawable = blurDrawable ?: blurProvider.createDrawable(radius, radius)
            drawable.apply {
                blurRadii = BlurDrawable.Radii(radius)
                callback = blurDrawableCallback
                setBounds(left, top, right, bottom)
                if (isAttachedToWindow) startListening()
            }
        } else {
            null
        }
    }

    override fun onEnabledChanged() {
        createBlurDrawable()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.lawnchairPrefs.addOnPreferenceChangeListener("pref_searchbarRadius", this)
        blurDrawable?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.lawnchairPrefs.removeOnPreferenceChangeListener("pref_searchbarRadius", this)
        blurDrawable?.stopListening()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        createBlurDrawable()
    }

    override fun drawQsb(canvas: Canvas) {
        if (scrimView?.currentBlurAlpha != 255) {
            blurDrawable?.run {
                val appsView = parent as View
                val searchBox = this@BlurQsbLayout
                val adjustmentX = left.toFloat()
                val adjustmentY = top + translationY + appsView.top + appsView.translationY + 1
                val left = paddingLeft + adjustmentX
                val top = paddingTop + adjustmentY
                val right = width - paddingRight + adjustmentX
                val bottom = height - paddingBottom + adjustmentY
                val isBubbleUi = (searchBox as? AbstractQsbLayout)?.useTwoBubbles() != false
                val bubbleAdjustmentLeft = if (isBubbleUi && isRtl) micWidth + bubbleGap else 0
                val bubbleAdjustmentRight = if (isBubbleUi && !isRtl) micWidth + bubbleGap else 0
                tmpRectF.set(left + bubbleAdjustmentLeft, top,
                             right - bubbleAdjustmentRight, bottom)
                setBlurBounds(tmpRectF)
                alpha = (searchBox.alpha * 255).toInt()
                canvas.save()
                canvas.translate(-adjustmentX, -adjustmentY)
                draw(canvas)
                canvas.drawRoundRect(tmpRectF, blurRadius, blurRadius, scrimView!!.shelfPaint)
                if (isBubbleUi) {
                    tmpRectF.set((if (!isRtl) right - micWidth else left).toFloat(),
                                 top, (if (isRtl) left + micWidth else right).toFloat(),
                                 bottom)
                    setBlurBounds(tmpRectF)
                    draw(canvas)
                    canvas.drawRoundRect(tmpRectF, blurRadius, blurRadius, scrimView!!.shelfPaint)
                }
                canvas.restore()
            }
        }
        super.drawQsb(canvas)
    }

    fun setOverlayScroll(scroll: Float) {
        blurDrawable?.viewOffsetX = scroll
    }

    override fun setTranslationY(translationY: Float) {
        super.setTranslationY(translationY)
        invalidateBlur()
    }

    fun invalidateBlur() {
        if (blurDrawable != null) {
            invalidate()
        }
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        createBlurDrawable()
        invalidateBlur()
    }
}
