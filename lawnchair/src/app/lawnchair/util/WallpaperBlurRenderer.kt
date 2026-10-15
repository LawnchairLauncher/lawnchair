package app.lawnchair.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * A bounded, optional wallpaper effect. Wallpaper I/O and bitmap allocation never
 * block LauncherRootView's constructor. Failure leaves the system wallpaper visible.
 */
object WallpaperBlurRenderer {
    private const val TAG = "OmniWallpaperBlur"
    private val generation = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
    ) { command ->
        Thread(command, "OmniWallpaperBlur").apply { isDaemon = true }
    }

    @JvmStatic
    fun schedule(view: View, requestedRadius: Int) {
        val requestId = generation.incrementAndGet()
        executor.queue.clear()
        val target = WeakReference(view)
        val context = view.context.applicationContext ?: view.context
        executor.execute {
            val bitmap = try {
                createSmallBlur(context, requestedRadius, requestId)
            } catch (error: Exception) {
                Log.w(TAG, "Wallpaper blur unavailable; using system wallpaper", error)
                null
            }
            if (bitmap != null) {
                val currentView = target.get()
                if (currentView == null || requestId != generation.get()) {
                    bitmap.recycle()
                } else {
                    currentView.post {
                        if (requestId == generation.get() && currentView.isAttachedToWindow) {
                            currentView.background = BitmapDrawable(currentView.resources, bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun cancelPending() {
        generation.incrementAndGet()
        executor.queue.clear()
    }

    private fun createSmallBlur(context: Context, requestedRadius: Int, requestId: Int): Bitmap? {
        if (requestId != generation.get()) return null
        val maxSide = if (DeviceTierManager.getInstance(context).tier == DeviceTier.LOW) {
            192
        } else {
            320
        }
        val metrics = context.resources.displayMetrics
        val scale = maxSide.toFloat() /
            max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val width = max(1, (metrics.widthPixels * scale).toInt())
        val height = max(1, (metrics.heightPixels * scale).toInt())
        val drawable = WallpaperManager.getInstance(context).drawable ?: return null
        val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(input)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            val source = IntArray(width * height)
            input.getPixels(source, 0, width, 0, 0, width, height)
            val horizontal = IntArray(source.size)
            val result = IntArray(source.size)
            // Two bounded CPU passes: no GPU context work after a heavy game.
            val radius = (requestedRadius / 5).coerceIn(1, 6)
            for (y in 0 until height) {
                // Wallpaper/theme changed: stop the obsolete CPU passes immediately.
                if (requestId != generation.get()) return null
                for (x in 0 until width) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    var count = 0
                    for (dx in -radius..radius) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val pixel = source[y * width + xx]
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                        count++
                    }
                    horizontal[y * width + x] = Color.rgb(red / count, green / count, blue / count)
                }
            }
            for (y in 0 until height) {
                if (requestId != generation.get()) return null
                for (x in 0 until width) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    var count = 0
                    for (dy in -radius..radius) {
                        val yy = (y + dy).coerceIn(0, height - 1)
                        val pixel = horizontal[yy * width + x]
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                        count++
                    }
                    result[y * width + x] = Color.rgb(red / count, green / count, blue / count)
                }
            }
            return Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
        } finally {
            input.recycle()
        }
    }
}
