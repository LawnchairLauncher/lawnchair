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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.graphics.ColorUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.hoko.blur.HokoBlur
import com.hoko.blur.task.AsyncBlurTask
import java.util.*
import java.util.concurrent.Future
import kotlin.collections.ArrayList

class BlurWallpaperProvider(val context: Context) {

    private val prefs by lazy { context.lawnchairPrefs }
    private val mWallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = ArrayList<Listener>()
    private val mDisplayMetrics = DisplayMetrics()
    var wallpaper: Bitmap? = null
        private set(value) {
            if (field != value) {
                field?.recycle()
                field = value
            }
        }
    var placeholder: Bitmap? = null
        private set(value) {
            if (field != value) {
                field?.recycle()
                field = value
            }
        }
    private var mOffset: Float = 0.5f
    var blurRadius = 25
        private set
    private val mNotifyRunnable = Runnable {
        for (listener in mListeners) {
            listener.onWallpaperChanged()
        }
    }

    private val mVibrancyPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mColorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mPath = Path()

    private var mWallpaperWidth: Int = 0
    private var mDisplayHeight: Int = 0
    var wallpaperYOffset: Float = 0f
        private set
    private val sCanvas = Canvas()

    private val mUpdateRunnable = Runnable { updateWallpaper() }

    private var blurFuture: Future<Any>? = null

    init {
        isEnabled = getEnabledStatus()

        updateBlurRadius()
        updateAsync()
    }

    private fun getEnabledStatus() = mWallpaperManager.wallpaperInfo == null && prefs.enableBlur

    private fun updateBlurRadius() {
        blurRadius = prefs.blurRadius.toInt() / DOWNSAMPLE_FACTOR
        blurRadius = Math.max(1, Math.min(blurRadius, 25))
    }

    private fun updateWallpaper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !context.hasStoragePermission){
            prefs.enableBlur = false
            return
        }
        val enabled = getEnabledStatus()
        if (enabled != isEnabled) {
            isEnabled = enabled
            runOnMainThread {
                mListeners.safeForEach(Listener::onEnabledChanged)
            }
        }

        if (!isEnabled) {
            wallpaper = null
            placeholder = null
            return
        }

        updateBlurRadius()

        var wallpaper = scaleToScreenSize((mWallpaperManager.drawable as BitmapDrawable).bitmap)
        val wallpaperHeight = wallpaper.height
        wallpaperYOffset = if (wallpaperHeight > mDisplayHeight) {
            (wallpaperHeight - mDisplayHeight) * 0.5f
        } else {
            0f
        }

        mWallpaperWidth = wallpaper.width

        placeholder = createPlaceholder(wallpaper.width, wallpaper.height)
        wallpaper = applyVibrancy(wallpaper)
        Log.d("BWP", "starting blur")
        blurFuture?.cancel(true)
        blurFuture = HokoBlur.with(context)
                .scheme(HokoBlur.SCHEME_OPENGL)
                .mode(HokoBlur.MODE_STACK)
                .radius(blurRadius)
                .sampleFactor(DOWNSAMPLE_FACTOR.toFloat())
                .forceCopy(false)
                .needUpscale(true)
                .processor()
                .asyncBlur(wallpaper, object : AsyncBlurTask.Callback {
                    override fun onBlurSuccess(bitmap: Bitmap) {
                        this@BlurWallpaperProvider.wallpaper = bitmap
                        Log.d("BWP", "blur done")
                        blurFuture = null
                        runOnMainThread(::notifyWallpaperChanged)
                        wallpaper.recycle()
                    }

                    override fun onBlurFailed(error: Throwable?) {
                        if (error is OutOfMemoryError) {
                            prefs.enableBlur = false
                            runOnMainThread {
                                Toast.makeText(context, R.string.blur_oom, Toast.LENGTH_LONG).show()
                                notifyWallpaperChanged()
                            }
                        }
                        blurFuture = null
                        wallpaper.recycle()
                    }
                })
    }

    private fun notifyWallpaperChanged() {
        mListeners.forEach(Listener::onWallpaperChanged)
    }

    private fun scaleToScreenSize(bitmap: Bitmap): Bitmap {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        display.getRealMetrics(mDisplayMetrics)

        val width = mDisplayMetrics.widthPixels
        val height = mDisplayMetrics.heightPixels
        mDisplayHeight = height

        val widthFactor = width.toFloat() / bitmap.width
        val heightFactor = height.toFloat() / bitmap.height

        val upscaleFactor = Math.max(widthFactor, heightFactor)
        if (upscaleFactor <= 0) {
            return bitmap
        }

        val scaledWidth = Math.max(width, (bitmap.width * upscaleFactor).ceilToInt())
        val scaledHeight = Math.max(height, (bitmap.height * upscaleFactor).ceilToInt())
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
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

        wallpaper.recycle()

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
        return BlurDrawable(this, FloatArray(8) { radius }, allowTransparencyMode)
    }

    fun setWallpaperOffset(offset: Float) {
        if (!isEnabled) return
        if (wallpaper == null) return

        val availw = mDisplayMetrics.widthPixels - mWallpaperWidth
        var xPixels = availw / 2

        if (availw < 0)
            xPixels += (availw * (offset - .5f) + .5f).toInt()

        mOffset = Utilities.boundToRange((-xPixels).toFloat(),
                0f, (mWallpaperWidth - mDisplayMetrics.widthPixels).toFloat())

        for (listener in ArrayList(mListeners)) {
            listener.onOffsetChanged(mOffset)
        }
    }

    fun setUseTransparency(useTransparency: Boolean) {
        for (listener in mListeners) {
            listener.setUseTransparency(useTransparency)
        }
    }

    interface Listener {

        fun onWallpaperChanged() {}
        fun onOffsetChanged(offset: Float) {}
        fun setUseTransparency(useTransparency: Boolean) {}
        fun onEnabledChanged() {}
    }

    companion object : SingletonHolder<BlurWallpaperProvider, Context>(ensureOnMainThread(useApplicationContext(::BlurWallpaperProvider))) {

        const val BLUR_QSB = 1
        const val BLUR_FOLDER = 2
        const val BLUR_ALLAPPS = 4
        const val DOWNSAMPLE_FACTOR = 8

        var isEnabled: Boolean = false
        private var sEnabledFlag: Int = 0

        fun isEnabled(flag: Int): Boolean {
            return isEnabled && sEnabledFlag and flag != 0
        }
    }
}
