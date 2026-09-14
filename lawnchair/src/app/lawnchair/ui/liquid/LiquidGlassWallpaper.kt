/*
 * Copyright 2026, Renns Project
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

package app.lawnchair.ui.liquid

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import app.lawnchair.wallpaper.WallpaperManagerCompat

/**
 * Supplies the image Sora's glass surfaces refract.
 *
 * A launcher never actually holds its wallpaper: the system composites it behind
 * the (translucent) launcher window, so nothing in the view hierarchy can be
 * sampled to get it. The bitmap therefore has to be asked for directly.
 *
 * The result is cached, because decoding a wallpaper is far too slow to do while
 * a folder is opening, and it only changes when the user changes it.
 */
object LiquidGlassWallpaper {

    private const val TAG = "LiquidGlassWallpaper"

    /**
     * How hard the cached wallpaper is blurred: the gaussian's radius, as a
     * fraction of the image's longer side.
     *
     * A fraction rather than a pixel count so that the drawer looks the same on
     * any screen -- the cache is kept at the screen's own size, so a fixed pixel
     * radius would read softer the smaller the display.
     *
     * 16/720 is what the old shrink-and-grow blur worked out to, and is a
     * starting point rather than a settled number: a gaussian of a given radius
     * is a good deal gentler than shrinking an image by that factor and
     * stretching it back, so this is the knob to turn if the drawer wants more
     * haze.
     */
    private const val BLUR_FRACTION = 16f / 720f

    /**
     * Bumped whenever the wallpaper is replaced.
     *
     * Surfaces that cache something derived from the wallpaper have no other way
     * to notice it has changed underneath them, and a backdrop still showing the
     * previous wallpaper is worse than one that costs a redraw.
     */
    @Volatile
    var generation: Int = 0
        private set

    private var cached: Bitmap? = null
    private var blurred: Bitmap? = null
    private var stale = false
    private var listenerAttached = false

    /**
     * The wallpaper, downscaled, or `null` when it cannot be read.
     *
     * Reading the wallpaper needs storage access on Android 13 and newer. That
     * can legitimately be denied, so callers must handle null rather than assume
     * a bitmap: [fallbackColor] covers that case.
     */
    @Synchronized
    fun get(context: Context): Bitmap? {
        val appContext = context.applicationContext
        attachChangeListener(appContext)

        val existing = cached?.takeUnless(Bitmap::isRecycled)
        if (existing != null && !stale) return existing

        val drawable: Drawable? = try {
            WallpaperManager.getInstance(appContext).drawable
        } catch (e: SecurityException) {
            // No storage permission. Not an error worth crashing over.
            Log.w(TAG, "wallpaper not readable, glass falls back to a flat tint", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "could not read wallpaper", e)
            null
        }

        val bitmap = drawable?.let { toScaledBitmap(it, appContext) }
        if (bitmap != null) {
            cached = bitmap
            stale = false
            return bitmap
        }

        // The re-read failed -- most often because reading the wallpaper needs
        // storage access. Keeping the last good copy is far better than dropping
        // to nothing: a null scene leaves the glass with a transparent image to
        // refract, which reads as the blur having vanished.
        stale = false
        return existing
    }

    /** Flat colour to tint glass with when the wallpaper is unavailable. */
    fun fallbackColor(context: Context): Int {
        val colors = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors
        return colors?.primaryColor ?: Color.DKGRAY
    }

    /**
     * Marks the cached wallpaper for reloading without throwing it away.
     *
     * The colours-changed callback also fires on a light/dark switch, when the
     * wallpaper itself has not changed at all. Discarding the bitmap there and
     * then failing to read it back is what made the blur disappear on a theme
     * change, so the old copy is held until a new one actually arrives.
     */
    /**
     * The wallpaper, blurred once and cached.
     *
     * Surfaces that sit directly on the workspace -- a closed folder's icon, for
     * one -- are drawn on every frame of a home screen scroll, so blurring per
     * frame is out of the question. Blurring once turns each frame into a plain
     * cropped bitmap draw, and because the image stays fixed in screen space the
     * icon slides over it as the pages move, which is what gives a static image
     * the feel of live glass.
     */
    @Synchronized
    fun getBlurred(context: Context): Bitmap? {
        val source = get(context) ?: return null
        blurred?.let { if (!it.isRecycled && !stale) return it }

        val result = blurByResampling(source) ?: source

        blurred = result
        return result
    }

    private fun blurByResampling(source: Bitmap): Bitmap? {
        val longSide = maxOf(source.width, source.height)
        return LiquidGlassBlur.blur(source, longSide * BLUR_FRACTION)
    }

    @Synchronized
    fun invalidate() {
        stale = true
        blurred = null
        generation++
    }

    private fun attachChangeListener(context: Context) {
        if (listenerAttached) return
        listenerAttached = true
        WallpaperManagerCompat.INSTANCE.get(context).addOnChangeListener(
            object : WallpaperManagerCompat.OnColorsChangedListener {
                override fun onColorsChanged() = invalidate()
            },
        )
    }

    /**
     * Renders [drawable] into a bitmap that matches what the screen actually shows.
     *
     * A wallpaper is usually wider than the display -- the extra width is what the
     * system pans through for parallax -- and it is centre-cropped to fill. Storing
     * it at its natural size and assuming it lines up with the screen puts the glass
     * on the wrong part of the image, so the crop is baked in here once instead.
     */
    private fun toScaledBitmap(drawable: Drawable, context: Context): Bitmap? {
        val srcWidth = drawable.intrinsicWidth
        val srcHeight = drawable.intrinsicHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val display = context.resources.displayMetrics
        val screenWidth = display.widthPixels
        val screenHeight = display.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null

        // The screen's own size, uncapped.
        //
        // This used to be held to 720 on the reasoning that a blurred, refracted
        // pane can show no more detail than that. It can: the drawer's backdrop
        // is this bitmap stretched over the whole screen, so a 720 copy was being
        // magnified by half again before anyone looked at it, and the lens in
        // each pane bends whatever detail is left at its rim. The cost is a copy
        // of the wallpaper at screen size and one resample of it, both paid once
        // when the wallpaper changes and neither on any frame.
        val outWidth = screenWidth
        val outHeight = screenHeight

        // Centre-crop: scale so the wallpaper covers, then centre the overflow.
        val coverScale = maxOf(
            outWidth.toFloat() / srcWidth.toFloat(),
            outHeight.toFloat() / srcHeight.toFloat(),
        )
        val drawnWidth = (srcWidth * coverScale).toInt()
        val drawnHeight = (srcHeight * coverScale).toInt()
        val left = (outWidth - drawnWidth) / 2
        val top = (outHeight - drawnHeight) / 2

        return runCatching {
            val bitmap = createBitmap(outWidth, outHeight)
            val canvas = Canvas(bitmap)
            val target = android.graphics.Rect(left, top, left + drawnWidth, top + drawnHeight)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                canvas.drawBitmap(drawable.bitmap, null, target, null)
            } else {
                drawable.setBounds(target)
                drawable.draw(canvas)
            }
            bitmap
        }.onFailure { Log.w(TAG, "could not rasterise wallpaper", it) }.getOrNull()
    }
}
