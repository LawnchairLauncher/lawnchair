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

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.graphics.HardwareRenderer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Blurs a bitmap, on the GPU where that is possible.
 *
 * [blur] is the one to call. It asks for a real gaussian and keeps [resample] --
 * shrink the image and grow it back -- only as the fallback.
 *
 * The fallback used to be the whole of it, on the reasoning that RenderNode's
 * blur effect renders only into a hardware canvas and a Canvas over a Bitmap is
 * always software, so the result could never be read back. It can: a RenderNode
 * drawn through a HardwareRenderer into an ImageReader comes back as an ordinary
 * bitmap, which is how [LiquidGlassCapture] already draws the launcher's own
 * themed icons. Resampling does not survive being magnified back: blurring the
 * whole screen means shrinking it to about twenty pixels across, and twenty
 * pixels stretched over a phone is a handful of flat patches with soft seams
 * between them, not a haze.
 */
object LiquidGlassBlur {

    private const val TAG = "LiquidGlassBlur"

    /**
     * Returns [source] blurred, or `null` if it could not be.
     *
     * [downscale] sets the strength: the blur radius works out to roughly one
     * pixel of the shrunken image measured back at full size, so a larger factor
     * is both blurrier and cheaper. The caller keeps the result -- this is far
     * too slow to run while anything is animating.
     */
    /**
     * Returns [source] blurred by [radiusPx], or `null` if it could not be.
     *
     * The radius is in pixels of [source]. Run once per wallpaper and never on a
     * frame, so it is worth the real thing rather than an approximation.
     */
    fun blur(source: Bitmap, radiusPx: Float): Bitmap? {
        if (radiusPx <= 0f) return source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            gaussianOnGpu(source, radiusPx)?.let { return it }
        }
        // Shrinking to roughly the radius and growing back is the same amount of
        // smoothing, coarsely done; it is what there is before S, and what is
        // left if the GPU path cannot be set up.
        return resample(source, Math.round(radiusPx).coerceAtLeast(1))
    }

    /**
     * A gaussian, drawn by the GPU and copied back into an ordinary bitmap.
     *
     * The renderer and its reader are built and thrown away around each call
     * rather than kept. This runs when the wallpaper changes and at no other
     * time, and an ImageReader the size of the screen is far too much memory to
     * hold onto for something asked for that rarely.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun gaussianOnGpu(source: Bitmap, radiusPx: Float): Bitmap? = runCatching {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return@runCatching null

        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer().apply { setSurface(reader.surface) }
        try {
            val node = RenderNode("sora-blur")
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording()
            try {
                recording.drawBitmap(source, 0f, 0f, null)
            } finally {
                node.endRecording()
            }
            // CLAMP, so the edges of the image are extended rather than treated
            // as transparent. Without it a blur this wide pulls nothing in from
            // beyond the frame and the whole border fades out.
            node.setRenderEffect(
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
            )

            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = reader.acquireLatestImage() ?: return@runCatching null
            try {
                val buffer = image.hardwareBuffer ?: return@runCatching null
                try {
                    Bitmap.wrapHardwareBuffer(buffer, null)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    buffer.close()
                }
            } finally {
                image.close()
            }
        } finally {
            renderer.destroy()
            reader.close()
        }
    }.onFailure { Log.w(TAG, "GPU blur unavailable, falling back to resampling", it) }
        .getOrNull()

    fun resample(source: Bitmap, downscale: Int): Bitmap? = runCatching {
        val smallWidth = (source.width / downscale).coerceAtLeast(1)
        val smallHeight = (source.height / downscale).coerceAtLeast(1)

        // Shrunk in halves rather than in one jump. Bilinear sampling only ever
        // reads the four pixels around each sample point, so a single large
        // reduction ignores almost everything in between -- the result keeps the
        // original's hard edges as diagonal steps, which reads as a blocky image
        // rather than a blurred one. Halving averages every pixel on the way
        // down, which is what makes the falloff look gaussian.
        var current = source
        while (current.width / 2 > smallWidth && current.height / 2 > smallHeight) {
            current = step(current, current.width / 2, current.height / 2, source)
        }
        current = step(current, smallWidth, smallHeight, source)

        // Grown back the same way, for the same reason.
        while (current.width * 2 < source.width && current.height * 2 < source.height) {
            current = step(current, current.width * 2, current.height * 2, source)
        }
        step(current, source.width, source.height, source)
    }.onFailure { Log.w(TAG, "could not blur a ${source.width}x${source.height} bitmap", it) }
        .getOrNull()

    /**
     * One rescaling stage, freeing the stage it replaces.
     *
     * [source] is the caller's own bitmap and is never freed here; the
     * intermediates are ours alone, and there can be several of them, so they
     * are dropped as soon as the next stage has been built. Bitmap.createScaled
     * returns its argument unchanged when nothing has to change, so identity is
     * what decides whether there is anything to free.
     */
    private fun step(from: Bitmap, width: Int, height: Int, source: Bitmap): Bitmap {
        val scaled = Bitmap.createScaledBitmap(from, width, height, true)
        if (scaled !== from && from !== source) from.recycle()
        return scaled
    }
}
