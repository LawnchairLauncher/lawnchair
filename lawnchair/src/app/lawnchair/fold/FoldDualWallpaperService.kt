package app.lawnchair.fold

import android.annotation.SuppressLint
import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import app.lawnchair.fold.FoldDualWallpaper.Screen
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lawnchair's dual wallpaper: a live wallpaper that shows one image on a foldable's cover screen
 * and another on its inner screen ([FoldWallpaper.EACH] where the system has no wallpaper per
 * screen). The user picks it once in the system's live wallpaper chooser; the images come from
 * Foldable screen > Wallpaper ([FoldDualWallpaper]).
 *
 * Folding resizes the wallpaper surface ([Engine.onSurfaceChanged]); the shape of the surface tells
 * the screens apart (the inner screen is close to square). Each image is decoded once at the size
 * of its screen, center-cropped (about 16 MB for the 7.6" inner screen, 8 MB for the cover), and
 * both are kept so a fold switches at once. A missing image falls back to the other one, then to a
 * solid colour. The colours reported for Material You are always those of the cover image (or the
 * inner one if there is no cover image), so folding does not re-theme the system. Nothing is
 * stored in memory only: after process death the engine reloads the stored files.
 */
class FoldDualWallpaperService : WallpaperService() {

    private lateinit var worker: HandlerThread
    private lateinit var workerHandler: Handler

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("FoldDualWallpaper").also { it.start() }
        workerHandler = Handler(worker.looper)
    }

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = DualEngine()

    private inner class DualEngine :
        Engine(),
        Runnable {

        private val mainHandler = Handler(Looper.getMainLooper())
        private val bitmaps = arrayOfNulls<Bitmap>(Screen.entries.size)
        private val bitmapStamps = LongArray(Screen.entries.size)
        private val loading = BooleanArray(Screen.entries.size)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = Rect()
        private var width = 0
        private var height = 0
        private var colors: WallpaperColors? = null
        private var colorsStamp = -1L
        private var destroyed = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            FoldDualWallpaper.addListener(this)
            updateColors()
        }

        override fun onDestroy() {
            destroyed = true
            FoldDualWallpaper.removeListener(this)
            for (i in bitmaps.indices) {
                bitmaps[i]?.recycle()
                bitmaps[i] = null
            }
            super.onDestroy()
        }

        /** An image changed in the settings. */
        override fun run() {
            for (i in bitmapStamps.indices) bitmapStamps[i] = -1L
            updateColors()
            ensureLoaded()
            draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            ensureLoaded()
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                // The files may have changed while another process owned them (backup, restore)
                ensureLoaded()
                draw()
            }
        }

        override fun onComputeColors(): WallpaperColors? = colors

        private fun currentScreen(): Screen {
            if (width <= 0 || height <= 0) return Screen.COVER
            // Inner screens of book-style foldables are close to square, cover screens are tall
            return if (min(width, height).toFloat() / max(width, height) >= INNER_MIN_RATIO) Screen.INNER else Screen.COVER
        }

        /** The screen whose image to show on [screen]: its own, else the other one, else none. */
        private fun sourceFor(screen: Screen): Screen? = when {
            FoldDualWallpaper.hasImage(this@FoldDualWallpaperService, screen) -> screen
            FoldDualWallpaper.hasImage(this@FoldDualWallpaperService, screen.other) -> screen.other
            else -> null
        }

        private fun ensureLoaded() {
            if (width <= 0 || height <= 0) return
            val screen = currentScreen()
            val source = sourceFor(screen) ?: return
            val index = screen.ordinal
            val file = FoldDualWallpaper.file(this@FoldDualWallpaperService, source)
            val stamp = file.lastModified() xor source.ordinal.toLong()
            val bitmap = bitmaps[index]
            if (bitmap != null && bitmapStamps[index] == stamp && bitmap.width == width && bitmap.height == height) return
            if (loading[index]) return
            loading[index] = true
            val targetWidth = width
            val targetHeight = height
            workerHandler.post {
                val decoded = decodeCenterCrop(file.path, targetWidth, targetHeight)
                mainHandler.post {
                    loading[index] = false
                    if (destroyed) {
                        decoded?.recycle()
                        return@post
                    }
                    if (decoded != null) {
                        bitmaps[index]?.recycle()
                        bitmaps[index] = decoded
                        bitmapStamps[index] = stamp
                    }
                    draw()
                    // The surface may have changed while decoding
                    if (decoded != null && (targetWidth != width || targetHeight != height)) ensureLoaded()
                }
            }
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            if (width <= 0 || height <= 0 || destroyed) return
            val screen = currentScreen()
            // The screen's own bitmap, else whatever is loaded (scaled), else a solid colour
            val bitmap = bitmaps[screen.ordinal] ?: bitmaps[screen.other.ordinal]
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas() ?: return
                if (bitmap == null || bitmap.isRecycled) {
                    canvas.drawColor(FALLBACK_COLOR)
                } else {
                    centerCrop(bitmap.width, bitmap.height, width, height, srcRect)
                    dst.set(0, 0, width, height)
                    canvas.drawBitmap(bitmap, srcRect, dst, paint)
                }
            } catch (e: Exception) {
                Log.w(TAG, "can't draw", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.w(TAG, "can't post", e)
                    }
                }
            }
        }

        private val srcRect = Rect()

        /** Colours of the cover image (else the inner one), so a fold never re-themes the system. */
        private fun updateColors() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
            val source = sourceFor(Screen.COVER)
            val stamp = source?.let { FoldDualWallpaper.file(this@FoldDualWallpaperService, it).lastModified() xor it.ordinal.toLong() } ?: 0L
            if (stamp == colorsStamp) return
            colorsStamp = stamp
            val path = source?.let { FoldDualWallpaper.file(this@FoldDualWallpaperService, it).path }
            workerHandler.post {
                val computed = computeColors(path)
                mainHandler.post {
                    if (destroyed) return@post
                    colors = computed
                    notifyColorsChanged()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun computeColors(path: String?): WallpaperColors? {
        if (path == null) return WallpaperColors(Color.valueOf(FALLBACK_COLOR), null, null)
        val small = decodeCenterCrop(path, COLORS_SIZE, COLORS_SIZE) ?: return null
        return try {
            WallpaperColors.fromBitmap(small)
        } catch (e: Exception) {
            Log.w(TAG, "can't compute colours", e)
            null
        } finally {
            small.recycle()
        }
    }

    private companion object {
        const val TAG = "LC-FoldDualWallpaper"

        /** Short side / long side above which a surface is an inner (large) screen. */
        const val INNER_MIN_RATIO = 0.65f

        const val FALLBACK_COLOR = Color.BLACK
        const val COLORS_SIZE = 128

        /**
         * Decodes [path] scaled and center-cropped to exactly [width] x [height], into one bitmap of
         * that size. Null when it can't (a missing or broken file, no memory): the engine then shows
         * the other image, else [FALLBACK_COLOR].
         */
        @SuppressLint("NewApi") // ImageDecoder is API 28; the service is only enabled on foldables, API 30+
        fun decodeCenterCrop(path: String, width: Int, height: Int): Bitmap? = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                val scale = max(width.toFloat() / info.size.width, height.toFloat() / info.size.height)
                // At least the target size, so the crop always fits
                val scaledWidth = (info.size.width * scale).roundToInt().coerceAtLeast(width)
                val scaledHeight = (info.size.height * scale).roundToInt().coerceAtLeast(height)
                decoder.setTargetSize(scaledWidth, scaledHeight)
                val left = (scaledWidth - width) / 2
                val top = (scaledHeight - height) / 2
                decoder.crop = Rect(left, top, left + width, top + height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (t: Throwable) {
            Log.w(TAG, "can't decode the wallpaper at ${width}x$height", t)
            null
        }

        /** The part of a [srcWidth] x [srcHeight] image that fills [width] x [height], centered. */
        fun centerCrop(srcWidth: Int, srcHeight: Int, width: Int, height: Int, out: Rect) {
            val scale = max(width.toFloat() / srcWidth, height.toFloat() / srcHeight)
            val cropWidth = (width / scale).roundToInt().coerceAtMost(srcWidth)
            val cropHeight = (height / scale).roundToInt().coerceAtMost(srcHeight)
            val left = (srcWidth - cropWidth) / 2
            val top = (srcHeight - cropHeight) / 2
            out.set(left, top, left + cropWidth, top + cropHeight)
        }
    }
}
