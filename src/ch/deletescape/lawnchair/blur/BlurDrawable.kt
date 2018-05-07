package ch.deletescape.lawnchair.blur

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.View

class BlurDrawable internal constructor(
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
    private var mPositionX: Float = 0.toFloat()
    private var mPositionY: Float = 0.toFloat()
    private var mOffset: Float = 0.toFloat()
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
    private var mRenderScript: RenderScript? = null
    private var mBlurScript: ScriptIntrinsicBlur? = null
    private var mBlurInput: Allocation? = null
    private var mBlurOutput: Allocation? = null
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

    private var mWidth = 0

    private var mTopRoundBitmap: Bitmap? = null
    private var mTopCanvas = Canvas()

    private var mBottomRoundBitmap: Bitmap? = null
    private var mBottomCanvas = Canvas()

    init {
        initializeRenderScript(mProvider.context)
    }

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
                    mTopRoundBitmap = Bitmap.createBitmap(width, mTopRadius.toInt(),
                            Bitmap.Config.ARGB_8888)
                    mTopCanvas.setBitmap(mTopRoundBitmap)
                }
                if (mBottomRounded) {
                    mBottomRoundBitmap = Bitmap.createBitmap(width, mBottomRadius.toInt(),
                            Bitmap.Config.ARGB_8888)
                    mBottomCanvas.setBitmap(mBottomRoundBitmap)
                }
            }
            mWidth = width
        }
    }

    override fun draw(canvas: Canvas) {
        val toDraw = bitmap
        if (!mShouldDraw || toDraw == null) return

        // Don't draw when completely off screen
        if (bounds.top > canvas.height) return
        if (bounds.left > canvas.width) return
        if (bounds.bottom < 0) return
        if (bounds.right < 0) return

        val blurTranslateX = -mOffset - mPositionX
        val translateX = -mPositionX
        val translateY = -mPositionY

        val bottomY = bounds.bottom - mBottomRadius

        val saveCount = canvas.save()
        canvas.clipRect(mNormalBounds)

        mRect.set(mBounds)
        if (mRounded) {
            if (mTopRounded) {
                mTopCanvas.save()
                mTopCanvas.drawPaint(mClearPaint)
                mTopCanvas.drawPath(mRoundPath, mClipPaint)
            }
            if (mBottomRounded) {
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

        canvas.drawBitmap(toDraw, blurTranslateX, translateY - mProvider.wallpaperYOffset, mPaint)
        if (mTopRounded) {
            mTopCanvas.drawRect(mRect, mOpacityPaint)
            mTopCanvas.drawBitmap(toDraw, blurTranslateX - mRect.left, translateY - mProvider.wallpaperYOffset - mRect.top, mCornerPaint)
        }
        if (mBottomRounded) {
            mBottomCanvas.drawRect(mRect, mOpacityPaint)
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
                mBitmapToBlur = null
                mBlurInput = null
                mBlurOutput = null

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
            if (mTopRounded) {
                mTopCanvas.drawPaint(mColorCornerPaint)
            }
            if (mBottomRounded) {
                mBottomCanvas.drawPaint(mColorCornerPaint)
            }
        }

        canvas.restoreToCount(saveCount)

        if (mTopRounded) {
            mTopRoundBitmap?.run { canvas.drawBitmap(this, mRect.left, mRect.top, mPaint) }
        }
        if (mBottomRounded) {
            mBottomRoundBitmap?.run { canvas.drawBitmap(this, mRect.left, bottomY, mPaint) }
        }
    }

    private fun initializeRenderScript(context: Context) {
        mRenderScript = RenderScript.create(context)
        mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript))
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

            var scaledWidth = width / mDownsampleFactor
            var scaledHeight = height / mDownsampleFactor

            // The following manipulation is to avoid some RenderScript artifacts at the edge.
            scaledWidth = scaledWidth - scaledWidth % 4 + 4
            scaledHeight = scaledHeight - scaledHeight % 4 + 4

            if (mBitmapToBlur == null || mBlurredBitmap == null
                    || mBlurredBitmap!!.width != scaledWidth
                    || mBlurredBitmap!!.height != scaledHeight) {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888)
                if (mBitmapToBlur == null) {
                    return false
                }

                mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight,
                        Bitmap.Config.ARGB_8888)
                if (mBlurredBitmap == null) {
                    return false
                }
            }

            mBlurringCanvas = Canvas(mBitmapToBlur!!)
            mBlurringCanvas!!.scale(1f / mDownsampleFactor, 1f / mDownsampleFactor)
            mBlurInput = Allocation.createFromBitmap(mRenderScript, mBitmapToBlur,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
            mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput!!.type)
        }
        return true
    }

    private fun blur() {
        mBlurInput!!.copyFrom(mBitmapToBlur)
        mBlurScript!!.setInput(mBlurInput)
        mBlurScript!!.forEach(mBlurOutput)
        mBlurOutput!!.copyTo(mBlurredBitmap)
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
        mShouldDraw = alpha > 0
        mPaint.alpha = alpha
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
        mBlurScript!!.setRadius(mProvider.blurRadius.toFloat())
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

    fun setPotitionX(position: Float) {
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
