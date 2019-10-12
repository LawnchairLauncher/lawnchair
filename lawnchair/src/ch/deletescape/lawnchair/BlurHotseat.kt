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

package ch.deletescape.lawnchair

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.graphics.NinePatchDrawHelper
import com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound
import com.android.launcher3.icons.ShadowGenerator
import com.android.launcher3.util.Themes
import kotlin.math.max
import kotlin.math.roundToInt

class BlurHotseat @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
                                           ) : CustomHotseat(context, attrs, defStyleAttr),
                                               LawnchairPreferences.OnPreferenceChangeListener,
                                               ColorEngine.OnColorChangeListener,
                                               BlurWallpaperProvider.Listener {

    private val launcher = Launcher.getLauncher(context)
    private val prefs = context.lawnchairPrefs
    private val prefsToWatch = arrayOf("pref_dockBackground", "pref_dockRadius", "pref_dockShadow",
                                       "pref_hotseatCustomOpacity")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowBlur = resources.getDimension(R.dimen.all_apps_scrim_blur)
    private var viewAlpha = 1f
        set(value) {
            field = value
            setBgColor()
        }

    private var bgEnabled = prefs.dockBackground
    private var radius = prefs.dockRadius
    private var shadow = prefs.dockShadow
    private val shadowHelper = NinePatchDrawHelper()
    private var shadowBitmap = generateShadowBitmap()

    private var noAlphaBgColor = 0
        set(value) {
            field = value
            setBgColor()
        }
    var bgAlpha = 0f
        private set(value) {
            field = value
            setBgColor()
        }
    var bgColor = 0
        private set

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

    init {
        setWillNotDraw(!bgEnabled || launcher.useVerticalBarLayout())
    }

    private fun createBlurDrawable() {
        blurDrawable = if (isVisible && BlurWallpaperProvider.isEnabled) {
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

    private fun drawBackground(canvas: Canvas) {
        val adjustmentX = left.toFloat() + translationX
        val adjustmentY = top.toFloat() + translationY
        val left = 0f + adjustmentX
        val top = -radius + adjustmentY
        val right = width.toFloat() + adjustmentX
        val bottom = height * 2f + adjustmentY
        canvas.save()
        canvas.translate(-adjustmentX, -adjustmentY)
        blurDrawable?.run {
            blurScaleX = 1 / scaleX
            blurScaleY = 1 / scaleY
            blurPivotX = pivotX
            blurPivotY = pivotY
            alpha = (viewAlpha * 255).toInt()
            setBlurBounds(left, top, right, bottom)
            draw(canvas)
        }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        if (shadow) {
            shadowHelper.paint.alpha = (viewAlpha * 255).toInt()
            shadowHelper.drawVerticallyStretched(
                    shadowBitmap, canvas,
                    left - shadowBlur,
                    top - shadowBlur,
                    right + shadowBlur,
                    bottom)
        }
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.addOnPreferenceChangeListener(this, *prefsToWatch)
        blurProvider.addListener(this)
        blurDrawable?.startListening()
        val colorEngine = ColorEngine.getInstance(context)
        colorEngine.addColorChangeListeners(this, ColorEngine.Resolvers.DOCK_BACKGROUND)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.removeOnPreferenceChangeListener(this, *prefsToWatch)
        blurProvider.removeListener(this)
        blurDrawable?.stopListening()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, ColorEngine.Resolvers.DOCK_BACKGROUND)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        noAlphaBgColor = resolveInfo.color
        invalidate()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        reloadPrefs()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setWillNotDraw(!bgEnabled || launcher.useVerticalBarLayout())
    }

    private fun reloadPrefs() {
        bgEnabled = prefs.dockBackground
        radius = dpToPx(prefs.dockRadius)
        shadow = prefs.dockShadow
        bgAlpha = (prefs.dockOpacity.takeIf { it >= 0 }
                  ?: Themes.getAttrInteger(context, R.attr.allAppsInterimScrimAlpha)).toFloat() / 255f
        shadowBitmap = generateShadowBitmap()
        setWillNotDraw(!bgEnabled || launcher.useVerticalBarLayout())
        createBlurDrawable()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (bgEnabled) {
            drawBackground(canvas)
        }
        super.draw(canvas)
    }

    private fun setBgColor() {
        bgColor = setColorAlphaBound(noAlphaBgColor, (bgAlpha * viewAlpha * 255).toInt())
        paint.color = bgColor
        invalidate()
    }

    override fun setAlpha(alpha: Float) {
        this.viewAlpha = max(0f, alpha)
        shortcutsAndWidgets.alpha = alpha
    }

    override fun getAlpha(): Float {
        return shortcutsAndWidgets.alpha
    }

    override fun setTranslationX(translationX: Float) {
        super.setTranslationX(translationX)
        invalidateBlur()
    }

    private fun generateShadowBitmap(): Bitmap {
        val tmp = radius + shadowBlur
        val builder = ShadowGenerator.Builder(0)
        builder.radius = radius
        builder.shadowBlur = shadowBlur
        val round = 2 * tmp.roundToInt() + 20
        val bitmap = Bitmap.createBitmap(round, round / 2, Bitmap.Config.ARGB_8888)
        val f = 2f * tmp + 20f - shadowBlur
        builder.bounds.set(shadowBlur, shadowBlur, f, f)
        builder.drawShadow(Canvas(bitmap))
        return bitmap
    }

    private fun invalidateBlur() {
        if (blurDrawable != null) {
            invalidate()
        }
    }

    override fun onEnabledChanged() {
        super.onEnabledChanged()
        createBlurDrawable()
        invalidate()
    }
}
