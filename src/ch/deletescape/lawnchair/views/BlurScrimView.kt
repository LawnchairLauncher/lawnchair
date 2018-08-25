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
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.blurWallpaperProvider
import ch.deletescape.lawnchair.runOnMainThread
import com.android.launcher3.anim.Interpolators.ACCEL_2
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

class BlurScrimView(context: Context, attrs: AttributeSet) : ShelfScrimView(context, attrs) {

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

    override fun reInitUi() {
        super.reInitUi()
        blurDrawable = createBlurDrawable()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        blurDrawable?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        blurDrawable?.stopListening()
    }

    override fun setProgress(progress: Float) {
        blurDrawable?.alpha = if (useFlatColor) ((1 - progress) * 255).toInt() else 255
        super.setProgress(progress)
    }

    override fun onDrawFlatColor(canvas: Canvas) {
        blurDrawable?.draw(canvas)
    }

    override fun onDrawRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        blurDrawable?.run {
            setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            draw(canvas)
        }
        super.onDrawRoundRect(canvas, left, top, right, bottom, rx, ry, paint)
    }

    override fun updateColors() {
        super.updateColors()
        if (useFlatColor) {
            blurDrawable?.alpha = ((1 - mProgress) * 255).toInt()
        } else {
            if (mProgress >= mMoveThreshold) {
                blurDrawable?.alpha = Math.round(255 * ACCEL_2.getInterpolation(
                        (1 - mProgress) / (1 - mMoveThreshold)))
            } else {
                blurDrawable?.alpha = 255
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (useFlatColor) {
            blurDrawable?.setBounds(left, top, right, bottom)
        }
    }
}
