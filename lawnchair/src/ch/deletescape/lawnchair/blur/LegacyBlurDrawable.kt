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

package ch.deletescape.lawnchair.blur

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider.Companion.DOWNSAMPLE_FACTOR
import com.hoko.blur.HokoBlur
import com.hoko.blur.task.AsyncBlurTask

class LegacyBlurDrawable internal constructor(
        private val mProvider: BlurWallpaperProvider,
        private val mRadii: FloatArray,
        private val mAllowTransparencyMode: Boolean) : Drawable(), BlurWallpaperProvider.Listener {

    private val mSimpleRound = mRadii.none { it != mRadii[0] }
    private val mTopRadius = mRadii.take(4).reduce { acc, radius -> Math.max(acc, radius) }
    private val mBottomRadius = mRadii.takeLast(4).reduce { acc, radius -> Math.max(acc, radius) }
    private val mTopRounded = mTopRadius.compareTo(0f) != 0
    private val mBottomRounded = mBottomRadius.compareTo(0f) != 0
    private val mRadius = Math.max(mTopRadius, mBottomRadius)
    private val mRounded = mTopRounded || mBottomRounded

    private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mShaderPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mCornerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            .apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val mBlurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mOpacityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            .apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val mColorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mColorCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            .apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val mClipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mClearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val mRect = RectF()
    private var mPositionX: Float = 0f
    private var mPositionY: Float = 0f
    private var mOffset: Float = 0f
    private var mShouldDraw = true
    private var mUseTransparency: Boolean = false

    private val mDownsampleFactor: Int = BlurWallpaperProvider.DOWNSAMPLE_FACTOR
    private var mOverlayColor: Int = 0

//    private val mClipCanvas = Canvas()

    private var mBlurredView: View? = null
    private var mBlurredViewWidth: Int = 0
    private var mBlurredViewHeight: Int = 0

    private var mDownsampleFactorChanged: Boolean = false
    private var mBitmapToBlur: Bitmap? = null
    private var mBlurredBitmap: Bitmap? = null
    private var mBlurringCanvas: Canvas? = null
    //    private var mTempBitmap: Bitmap? = null
    private var mBlurInvalid: Boolean = false

    private var mBlurredX: Float = 0.toFloat()
    private var mBlurredY: Float = 0.toFloat()
    private var mShouldProvideOutline: Boolean = false
    private var mOpacity = 255
    private var mTransparencyEnabled: Boolean = false

    private var mRoundPath = Path()
    private val mBounds = Rect()
    private val mNormalBounds = Rect()
    private val mNonRoundedBounds = Rect()

    private var mWidth = 0

    private var mTopRoundBitmap: Bitmap? = null
    private var mTopCanvas = Canvas()

    private var mBottomRoundBitmap: Bitmap? = null
    private var mBottomCanvas = Canvas()

    private var mAlpha = 255

    fun setBlurredView(blurredView: View) {
        mBlurredView = blurredView
    }

    fun setOverlayColor(color: Int) {
        if (mOverlayColor != color) {
            mOverlayColor = color
            mColorPaint.color = color
            mColorCornerPaint.color = color
            invalidateSelf()
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        mBounds.set(left, top, right, bottom)
        mNormalBounds.set(left, top, right, bottom)
        mNonRoundedBounds.set(left, top, right, bottom)
        mRect.set(mBounds)
        if (mTopRounded) {
            mNormalBounds.top += mTopRadius.toInt()
        }
        if (mBottomRounded) {
            mNormalBounds.bottom -= mBottomRadius.toInt()
        }
        if (width <= 0) return
        if (width != mWidth) {
            if (mRounded) {
                mRect.set(0f, 0f, width.toFloat(), mTopRadius + mBottomRadius)
                mRoundPath.reset()
                mRoundPath.addRoundRect(mRect, mRadii, Path.Direction.CW)
                mClipPaint.color = -1
                if (mTopRounded) {
                    mTopRoundBitmap?.recycle()
                    mTopRoundBitmap = Bitmap.createBitmap(width, mTopRadius.toInt(),
                            Bitmap.Config.ARGB_8888)
                    mTopCanvas.setBitmap(mTopRoundBitmap)
                }
                if (mBottomRounded) {
                    mBottomRoundBitmap?.recycle()
                    mBottomRoundBitmap = Bitmap.createBitmap(width, mBottomRadius.toInt(),
                            Bitmap.Config.ARGB_8888)
                    mBottomCanvas.setBitmap(mBottomRoundBitmap)
                }
            }
            mWidth = width
        }
    }

    override fun draw(canvas: Canvas) {
        draw(canvas, false)
    }

    fun draw(canvas: Canvas, noRadius: Boolean) {
        val toDraw = bitmap
        mShaderPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (!mShouldDraw || toDraw == null || toDraw.isRecycled) return

        val rounded = mRounded && !noRadius
        val topRounded = mTopRounded && !noRadius
        val bottomRounded = mBottomRounded && !noRadius

        // Don't draw when completely off screen
        if (mBounds.top > canvas.height) return
        if (mBounds.left > canvas.width) return
        if (mBounds.bottom < 0) return
        if (mBounds.right < 0) return

        val blurTranslateX = -mOffset - mPositionX
        val translateX = -mPositionX
        val translateY = -mPositionY

        val bottomY = mBounds.bottom - mBottomRadius

        val saveCount = canvas.save()
        canvas.clipRect(if (noRadius) mNonRoundedBounds else mNormalBounds)

        mRect.set(mBounds)
        if (rounded) {
            if (topRounded) {
                mTopCanvas.save()
                mTopCanvas.drawPaint(mClearPaint)
                mTopCanvas.drawPath(mRoundPath, mClipPaint)
            }
            if (bottomRounded) {
                mBottomCanvas.save()
                mBottomCanvas.translate(0f, -mTopRadius)
                mBottomCanvas.drawPaint(mClearPaint)
                mBottomCanvas.drawPath(mRoundPath, mClipPaint)
                mBottomCanvas.restore()
            }
        }

        if (mTransparencyEnabled) {
            mOpacityPaint.color = mOpacity shl 24
            mTopCanvas.drawRect(mRect, mOpacityPaint)
            mBottomCanvas.drawRect(mRect, mOpacityPaint)
            canvas.drawRect(mRect, mOpacityPaint)
        }

        // Check again if the bitmap is recycled
        if (toDraw.isRecycled) return
        try {
//            canvas.drawBitmap(toDraw, blurTranslateX, translateY - mProvider.wallpaperYOffset, mPaint)
            canvas.translate(-mOffset, 0f)
            canvas.drawRect(mNormalBounds, mShaderPaint)
            canvas.translate(mOffset, 0f)
        } catch (e: Exception) {
            Log.e("BlurDrawable", "Failed to draw blurred bitmasp", e)
        }
        if (topRounded) {
            mTopCanvas.drawBitmap(toDraw, blurTranslateX - mRect.left, translateY - mProvider.wallpaperYOffset - mRect.top, mCornerPaint)
        }
        if (bottomRounded) {
            mBottomCanvas.drawBitmap(toDraw, blurTranslateX - mRect.left, translateY - mProvider.wallpaperYOffset - bottomY, mCornerPaint)
        }

        if (prepare()) {
            if (mBlurInvalid) {
                mBlurInvalid = false
                mBlurredX = mPositionX
                mBlurredY = mPositionY

                val startTime = System.currentTimeMillis()

                mBlurredView!!.draw(mBlurringCanvas)
                mBlurringCanvas!!.drawColor(mProvider.tintColor)
                if (mOverlayColor != 0)
                    mBlurringCanvas!!.drawColor(mOverlayColor)
                blur()

                mBlurringCanvas = null
                mBitmapToBlur?.recycle()
                mBitmapToBlur = null

                Log.d("BlurView", "Took " + (System.currentTimeMillis() - startTime) + "ms to blur")
            }

            canvas.save()
            canvas.translate(mBlurredView!!.x + translateX, mBlurredView!!.y + translateY)
            canvas.scale(mDownsampleFactor.toFloat(), mDownsampleFactor.toFloat())
            canvas.drawBitmap(mBlurredBitmap!!, 0f, 0f, mBlurPaint)
            canvas.restore()
        }

        if (mOverlayColor != 0) {
            canvas.drawRect(mRect, mColorPaint)
            if (topRounded) {
                mTopCanvas.drawPaint(mColorCornerPaint)
            }
            if (bottomRounded) {
                mBottomCanvas.drawPaint(mColorCornerPaint)
            }
        }

        canvas.restoreToCount(saveCount)

        if (topRounded) {
            mTopRoundBitmap?.run { canvas.drawBitmap(this, mRect.left, mRect.top, mPaint) }
        }
        if (bottomRounded) {
            mBottomRoundBitmap?.run { canvas.drawBitmap(this, mRect.left, bottomY, mPaint) }
        }
    }

    private fun prepare(): Boolean {
        if (mBlurredView == null) return false
        if (!mBlurInvalid) return true

        val width = mBlurredView!!.width
        val height = mBlurredView!!.height

        if (mBlurringCanvas == null || mDownsampleFactorChanged
                || mBlurredViewWidth != width || mBlurredViewHeight != height) {
            mDownsampleFactorChanged = false

            mBlurredViewWidth = width
            mBlurredViewHeight = height

            if (mBitmapToBlur == null) {
                return false
            }
        }
        return true
    }

    private fun blur() {
        HokoBlur.with(mProvider.context)
                .scheme(HokoBlur.SCHEME_OPENGL)
                .mode(HokoBlur.MODE_STACK)
                .radius(mProvider.blurRadius)
                .sampleFactor(DOWNSAMPLE_FACTOR.toFloat())
                .forceCopy(false)
                .needUpscale(false)
                .processor()
                .asyncBlur(mBitmapToBlur, object : AsyncBlurTask.Callback {
                    override fun onBlurSuccess(bitmap: Bitmap?) {
                        mBlurredBitmap?.recycle()
                        mBlurredBitmap = bitmap
                        invalidateSelf()
                    }

                    override fun onBlurFailed(error: Throwable?) {
                        mBlurredBitmap?.recycle()
                        mBlurredBitmap = mBitmapToBlur
                        invalidateBlur()
                    }
                })
    }

    val bitmap: Bitmap?
        get() {
            val wallpaper = mProvider.wallpaper
            return if (wallpaper == null || mUseTransparency && mAllowTransparencyMode)
                mProvider.placeholder
            else
                wallpaper
        }

    override fun setAlpha(alpha: Int) {
        if (mAlpha != alpha) {
            mAlpha = alpha
            mShouldDraw = alpha > 0
            mPaint.alpha = alpha
            mShaderPaint.alpha = alpha
        }
    }

    override fun getAlpha(): Int {
        return mAlpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    fun startListening() {
        mProvider.addListener(this)
    }

    fun stopListening() {
        mProvider.removeListener(this)
    }

    override fun onWallpaperChanged() {
        mBlurInvalid = true
        if (!mUseTransparency)
            invalidateSelf()
    }

    override fun onOffsetChanged(offset: Float) {
        mOffset = offset
        if (!mUseTransparency)
            invalidateSelf()
    }

    override fun setUseTransparency(useTransparency: Boolean) {
        if (!mAllowTransparencyMode) return
        mUseTransparency = useTransparency
        invalidateSelf()
    }

    override fun getOutline(outline: Outline) {
        if (!mShouldProvideOutline || mRadius.compareTo(0f) == 0) return

        if (mSimpleRound) {
            outline.setRoundRect(bounds, mRadius)
        } else {
            mRoundPath.reset()
            mRoundPath.addRoundRect(bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat(), mRadii, Path.Direction.CW)
            outline.setConvexPath(mRoundPath)
        }
    }

    fun setShouldProvideOutline(shouldProvideOutline: Boolean) {
        mShouldProvideOutline = shouldProvideOutline
    }

    fun setPositionY(position: Float) {
        mPositionY = position
        invalidateBlur()
        if (!mUseTransparency)
            invalidateSelf()
    }

    fun setPositionX(position: Float) {
        mPositionX = position
        invalidateBlur()
        if (!mUseTransparency)
            invalidateSelf()
    }

    private fun invalidateBlur() {
        mBlurInvalid = mPositionX != mBlurredX || mPositionY != mBlurredY
    }

    fun setOpacity(opacity: Int) {
        if (!mTransparencyEnabled) {
            mTransparencyEnabled = true
            mColorPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            mBlurPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        mOpacity = opacity
    }
}
