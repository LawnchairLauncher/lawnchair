package ch.deletescape.lawnchair.blur

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.support.v4.graphics.ColorUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import ch.deletescape.lawnchair.blurWallpaperProvider
import ch.deletescape.lawnchair.hasStoragePermission
import ch.deletescape.lawnchair.launcherAppState
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.util.*

class BlurWallpaperProvider(val context: Context) {

    private val prefs by lazy { context.lawnchairPrefs }
    private val mWallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = ArrayList<Listener>()
    private val mDisplayMetrics = DisplayMetrics()
    var wallpaper: Bitmap? = null
        private set
    var placeholder: Bitmap? = null
        private set
    private var mOffset: Float = 0.5f
    var blurRadius = 25
        private set
    private val mNotifyRunnable = Runnable {
        for (listener in mListeners) {
            listener.onWallpaperChanged()
        }
    }

    private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mVibrancyPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mColorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mPath = Path()

    private var mWallpaperWidth: Int = 0
    private var mDisplayHeight: Int = 0
    var wallpaperYOffset: Float = 0f
        private set
    private val sCanvas = Canvas()

    private val mUpdateRunnable = Runnable { updateWallpaper() }

    init {
        isEnabled = mWallpaperManager.wallpaperInfo == null && prefs.enableBlur

        updateBlurRadius()
    }

    private fun updateBlurRadius() {
        blurRadius = prefs.blurRadius.toInt() / DOWNSAMPLE_FACTOR
        blurRadius = Math.max(1, Math.min(blurRadius, 25))
    }

    private fun updateWallpaper() {
        val launcher = context.launcherAppState.launcher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !context.hasStoragePermission){
            prefs.enableBlur = false
            return
        }
        val enabled = mWallpaperManager.wallpaperInfo == null && prefs.enableBlur
        if (enabled != isEnabled) {
            prefs.restart()
        }

        if (!isEnabled) return

        updateBlurRadius()

        var wallpaper = upscaleToScreenSize((mWallpaperManager.drawable as BitmapDrawable).bitmap)
        val wallpaperHeight = wallpaper.height
        wallpaperYOffset = if (wallpaperHeight > mDisplayHeight) {
            (wallpaperHeight - mDisplayHeight) * 0.5f
        } else {
            0f
        }

        mWallpaperWidth = wallpaper.width

        this.wallpaper = null
        placeholder = createPlaceholder(wallpaper.width, wallpaper.height)
        launcher.runOnUiThread(mNotifyRunnable)
        if (prefs.enableVibrancy) {
            wallpaper = applyVibrancy(wallpaper)
        }
        try {
            Log.d("BWP", "starting blur")
            this.wallpaper = blur(wallpaper)
            Log.d("BWP", "blur done")
            launcher.runOnUiThread(mNotifyRunnable)
        } catch(oom: OutOfMemoryError){
            prefs.enableBlur = false
            Toast.makeText(context, R.string.blur_oom, Toast.LENGTH_LONG).show()
        }
    }

    private fun upscaleToScreenSize(bitmap: Bitmap): Bitmap {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        display.getRealMetrics(mDisplayMetrics)

        val width = mDisplayMetrics.widthPixels
        val height = mDisplayMetrics.heightPixels
        mDisplayHeight = height

        var widthFactor = 0f
        var heightFactor = 0f
        if (width > bitmap.width) {
            widthFactor = width.toFloat() / bitmap.width
        }
        if (height > bitmap.height) {
            heightFactor = height.toFloat() / bitmap.height
        }

        val upscaleFactor = Math.max(widthFactor, heightFactor)
        if (upscaleFactor <= 0) {
            return bitmap
        }

        val scaledWidth = (bitmap.width * upscaleFactor).toInt()
        val scaledHeight = (bitmap.height * upscaleFactor).toInt()
        val scaled = Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight, false)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas()
        canvas.setBitmap(result)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (widthFactor > heightFactor) {
            canvas.drawBitmap(scaled, 0f, ((height - scaledHeight) / 2).toFloat(), paint)
        } else {
            canvas.drawBitmap(scaled, ((width - scaledWidth) / 2).toFloat(), 0f, paint)
        }

        return result
    }

    fun blur(image: Bitmap): Bitmap {
        val width = Math.round((image.width / DOWNSAMPLE_FACTOR).toFloat())
        val height = Math.round((image.height / DOWNSAMPLE_FACTOR).toFloat())

        val inputBitmap = Bitmap.createScaledBitmap(image, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)

        val rs = RenderScript.create(context)
        val theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val tmpIn = Allocation.createFromBitmap(rs, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(rs, outputBitmap)
        theIntrinsic.setRadius(blurRadius.toFloat())
        theIntrinsic.setInput(tmpIn)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)

        // Have to scale it back to full resolution because antialiasing is too expensive to be done each frame
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(DOWNSAMPLE_FACTOR.toFloat(), DOWNSAMPLE_FACTOR.toFloat())
        canvas.drawBitmap(outputBitmap, 0f, 0f, mPaint)
        canvas.restore()

        return bitmap
    }

    private fun createPlaceholder(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        sCanvas.setBitmap(bitmap)

        mPath.moveTo(0f, 0f)
        mPath.lineTo(0f, height.toFloat())
        mPath.lineTo(width.toFloat(), height.toFloat())
        mPath.lineTo(width.toFloat(), 0f)
        mColorPaint.xfermode = null
        mColorPaint.color = tintColor
        sCanvas.drawPath(mPath, mColorPaint)

        return bitmap
    }

    val tintColor: Int
        get() = 0x45ffffff // TODO: replace this with theme attr
//        get() = Utilities.resolveAttributeData(context, R.attr.blurTintColor)

    fun updateAsync() {
        Utilities.THREAD_POOL_EXECUTOR.execute(mUpdateRunnable)
    }

    private fun applyVibrancy(wallpaper: Bitmap): Bitmap {
        val width = wallpaper.width
        val height = wallpaper.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas()
        canvas.setBitmap(bitmap)

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(1.25f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        mVibrancyPaint.colorFilter = filter
        canvas.drawBitmap(wallpaper, 0f, 0f, mVibrancyPaint)

        return bitmap
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
        listener.onOffsetChanged(mOffset)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    fun createDrawable(): BlurDrawable {
        return BlurDrawable(this, FloatArray(8), false)
    }

    fun createDrawable(radius: Float, allowTransparencyMode: Boolean): BlurDrawable {
        return BlurDrawable(this, FloatArray(8, { radius }), allowTransparencyMode)
    }

    fun setWallpaperOffset(offset: Float) {
        if (!isEnabled) return
        if (wallpaper == null) return

        val availw = mDisplayMetrics.widthPixels - mWallpaperWidth
        var xPixels = availw / 2

        if (availw < 0)
            xPixels += (availw * (offset - .5f) + .5f).toInt()

        mOffset = (-xPixels).toFloat()

        for (listener in mListeners) {
            listener.onOffsetChanged(mOffset)
        }
    }

    fun setUseTransparency(useTransparency: Boolean) {
        for (listener in mListeners) {
            listener.setUseTransparency(useTransparency)
        }
    }

    interface Listener {

        fun onWallpaperChanged()
        fun onOffsetChanged(offset: Float)
        fun setUseTransparency(useTransparency: Boolean)
    }

    companion object {

        const val BLUR_QSB = 1
        const val BLUR_FOLDER = 2
        const val BLUR_ALLAPPS = 4
        const val DOWNSAMPLE_FACTOR = 8

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: BlurWallpaperProvider? = null

        fun applyBlurBackground(activity: Activity) {
            if (!isEnabled) return

            var color = 0x45ffffff // TODO: replace this with theme attr
//            var color = activity.getColorAttr(R.attr.blurTintColor)
            color = ColorUtils.setAlphaComponent(color, 220)

            val drawable = activity.blurWallpaperProvider.createDrawable()
            drawable.setOverlayColor(color)
            activity.findViewById<View>(android.R.id.content).background = drawable
        }

        var isEnabled: Boolean = false
        private var sEnabledFlag: Int = 0

        fun isEnabled(flag: Int): Boolean {
            return isEnabled && sEnabledFlag and flag != 0
        }

        fun getInstance(context: Context): BlurWallpaperProvider {
            return LauncherAppState.getInstance(context).launcher.blurWallpaperProvider
        }
    }
}
