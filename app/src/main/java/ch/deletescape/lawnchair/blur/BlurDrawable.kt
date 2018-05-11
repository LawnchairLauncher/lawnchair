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

class BlurDrawable internal constructor(private val mProvider: BlurWallpaperProvider, private val mRadius: Float, private val mAllowTransparencyMode: Boolean) : Drawable(), BlurWallpaperProvider.Listener {

    private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mBlurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mOpacityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mColorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mClipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mRect = RectF()
    private var mTranslation: Float = 0.toFloat()
    private var mOffset: Float = 0.toFloat()
    private var mShouldDraw = true
    private var mOverscroll: Float = 0.toFloat()
    private var mUseTransparency: Boolean = false

    private val mDownsampleFactor: Int
    private var mOverlayColor: Int = 0

    private val mClipCanvas = Canvas()

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
    private var mTempBitmap: Bitmap? = null
    private var mBlurInvalid: Boolean = false

    private var mBlurredX: Float = 0.toFloat()
    private var mBlurredY: Float = 0.toFloat()
    private var mShouldProvideOutline: Boolean = false
    private var mOpacity = 255
    private var mTransparencyEnabled: Boolean = false

    init {

        if (mRadius > 0) {
            mColorPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            mBlurPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        mDownsampleFactor = BlurWallpaperProvider.DOWNSAMPLE_FACTOR
        initializeRenderScript(mProvider.context)
    }

    fun setBlurredView(blurredView: View) {
        mBlurredView = blurredView
    }

    fun setOverlayColor(color: Int) {
        if (mOverlayColor != color) {
            mOverlayColor = color
            mColorPaint.color = color
            invalidateSelf()
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)

        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return
        mTempBitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888)
        mClipCanvas.setBitmap(mTempBitmap)
    }

    override fun draw(canvas: Canvas) {
        val toDraw = bitmap
        if (!mShouldDraw || toDraw == null) return

        val blurTranslateX = -mOffset - mOverscroll
        val translateX = -mOverscroll
        val translateY = -mTranslation

        val drawTo = if (mBlurredView == null) canvas else mClipCanvas

        mRect.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        if (mRadius > 0) {
            drawTo.drawRoundRect(mRect, mRadius, mRadius, mClipPaint)
        }

        if (mTransparencyEnabled) {
            mOpacityPaint.color = mOpacity shl 24
            drawTo.drawRect(mRect, mOpacityPaint)
        }

        drawTo.drawBitmap(toDraw, blurTranslateX, translateY - mProvider.wallpaperYOffset, mPaint)

        if (prepare()) {
            if (mBlurInvalid) {
                mBlurInvalid = false
                mBlurredX = mOverscroll
                mBlurredY = mTranslation

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

            mClipCanvas.save()
            mClipCanvas.translate(mBlurredView!!.x + translateX, mBlurredView!!.y + translateY)
            mClipCanvas.scale(mDownsampleFactor.toFloat(), mDownsampleFactor.toFloat())
            mClipCanvas.drawBitmap(mBlurredBitmap!!, 0f, 0f, mBlurPaint)
            mClipCanvas.restore()
        }

        if (mBlurredView != null) {
            canvas.drawBitmap(mTempBitmap!!, 0f, 0f, null)
        } else if (mOverlayColor != 0) {
            canvas.drawRect(mRect, mColorPaint)
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
            if (wallpaper == null || mUseTransparency && mAllowTransparencyMode)
                return mProvider.placeholder
            else
                return wallpaper
        }

    override fun setAlpha(alpha: Int) {
        mShouldDraw = alpha == 255
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
        if (mShouldProvideOutline)
            outline.setRoundRect(bounds, mRadius)
    }

    fun setShouldProvideOutline(shouldProvideOutline: Boolean) {
        mShouldProvideOutline = shouldProvideOutline
    }

    fun setTranslation(translation: Float) {
        mTranslation = translation
        invalidateBlur()
        if (!mUseTransparency)
            invalidateSelf()
    }

    fun setOverscroll(progress: Float) {
        mOverscroll = progress
        invalidateBlur()
        if (!mUseTransparency)
            invalidateSelf()
    }

    private fun invalidateBlur() {
        mBlurInvalid = mOverscroll != mBlurredX || mTranslation != mBlurredY
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
