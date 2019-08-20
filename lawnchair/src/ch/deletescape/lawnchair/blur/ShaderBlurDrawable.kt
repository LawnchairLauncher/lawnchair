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

package ch.deletescape.lawnchair.blur

import android.graphics.*

class ShaderBlurDrawable internal constructor(
        private val blurProvider: BlurWallpaperProvider) : BlurDrawable() {

    private var blurAlpha = 255
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var blurBitmap: Bitmap? = null
        set(value) {
            if (field != value) {
                field = value
                blurPaint.shader = value?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            }
        }
    private var blurOffset = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
                blurPathValid = false
            }
        }
    private var wallpaperOffsetX = 0f
        set(value) {
            field = value
            blurOffset = value + viewOffsetX
        }
    override var viewOffsetX = 0f
        set(value) {
            field = value
            blurOffset = wallpaperOffsetX + value
        }
    private val radii = FloatArray(8)
    override var blurRadii = Radii()
        set(value) {
            if (field != value) {
                field = value
                radii[0] = value.topLeft
                radii[1] = value.topLeft
                radii[2] = value.topRight
                radii[3] = value.topRight
                radii[4] = value.bottomRight
                radii[5] = value.bottomRight
                radii[6] = value.bottomLeft
                radii[7] = value.bottomLeft
                blurPathValid = false
            }
        }
    private val blurBounds = RectF()
    private val blurPath = Path()
    private var blurPathValid = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) {
                    invalidateSelf()
                }
            }
        }

    override fun draw(canvas: Canvas, noRadius: Boolean) {
        if (blurAlpha == 0) return
        blurBitmap = blurProvider.wallpaper
        setupBlurPath()

        canvas.translate(-blurOffset, 0f)
        if (noRadius) {
            canvas.drawRect(blurBounds.left + blurOffset, blurBounds.top,
                            blurBounds.right + blurOffset, blurBounds.bottom,
                            blurPaint)
        } else {
            canvas.drawPath(blurPath, blurPaint)
        }
        canvas.translate(blurOffset, 0f)
    }

    private fun setupBlurPath() {
        if (blurPathValid) return

        blurPath.reset()
        blurPath.addRoundRect(blurBounds.left + blurOffset, blurBounds.top,
                              blurBounds.right + blurOffset, blurBounds.bottom,
                              radii, Path.Direction.CW)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        setBlurBounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    override fun setBlurBounds(left: Float, top: Float, right: Float, bottom: Float) {
        if (blurBounds.left != left ||
            blurBounds.top != top ||
            blurBounds.right != right ||
            blurBounds.bottom != bottom) {
            blurBounds.set(left, top, right, bottom)
            blurPathValid = false
        }
    }

    override fun setAlpha(alpha: Int) {
        blurAlpha = alpha
        blurPaint.alpha = alpha
    }

    override fun getAlpha(): Int {
        return blurAlpha
    }

    override fun onWallpaperChanged() {
        invalidateSelf()
    }

    override fun onOffsetChanged(offset: Float) {
        wallpaperOffsetX = offset
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    override fun startListening() {
        blurProvider.addListener(this)
    }

    override fun stopListening() {
        blurProvider.removeListener(this)
    }
}
