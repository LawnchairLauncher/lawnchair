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
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Property
import android.view.View
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.util.InvertedMultiValueAlpha
import com.android.launcher3.Insettable
import com.android.launcher3.Utilities

class LawnchairBackgroundView(context: Context, attrs: AttributeSet) : View(context, attrs),
        Insettable, BlurWallpaperProvider.Listener {

    private val blurProvider by lazy { BlurWallpaperProvider.getInstance(context) }
    private var fullBlurDrawable: BlurDrawable? = null
    val blurAlphas = InvertedMultiValueAlpha({ alpha ->
        blurAlpha = Math.round(255 * alpha)
        invalidate()
    }, 3)
    private var blurAlpha = 0

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

    init {
        createFullBlurDrawable()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        BlurWallpaperProvider.getInstance(context).addListener(this)
        fullBlurDrawable?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        BlurWallpaperProvider.getInstance(context).removeListener(this)
        fullBlurDrawable?.stopListening()
    }

    override fun onDraw(canvas: Canvas) {
        fullBlurDrawable?.apply {
            alpha = blurAlpha
            draw(canvas)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) {
            fullBlurDrawable?.setBounds(left, top, right, bottom)
        }
    }

    private fun createFullBlurDrawable() {
        fullBlurDrawable?.let { if (isAttachedToWindow) it.stopListening() }
        fullBlurDrawable = if (BlurWallpaperProvider.isEnabled) {
            blurProvider.createDrawable().apply {
                callback = blurDrawableCallback
                setBounds(left, top, right, bottom)
                if (isAttachedToWindow) startListening()
            }
        } else {
            null
        }
    }

    override fun onEnabledChanged() {
        createFullBlurDrawable()
    }

    override fun setInsets(insets: Rect) {

    }

    companion object {

        const val ALPHA_INDEX_OVERLAY = 0
        const val ALPHA_INDEX_STATE = 1
        const val ALPHA_INDEX_TRANSITIONS = 2
    }
}
