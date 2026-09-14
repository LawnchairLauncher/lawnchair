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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.core.graphics.ColorUtils
import java.lang.ref.WeakReference

/**
 * Liquid glass background for Sora's View-based surfaces: folder icons, the open
 * folder, and the long-press popup.
 *
 * Backdrop exposes its glass only through Compose modifiers, and these surfaces
 * are Java Views, so this drives Backdrop's refraction shader directly through
 * [RuntimeShader] instead of going via Compose.
 *
 * What gets refracted is the user's wallpaper. A launcher window is translucent
 * and the system composites the wallpaper behind it, so there is nothing in the
 * view tree to sample -- the bitmap has to be read separately and sampled at the
 * pane's own position on screen, otherwise the glass would not line up with what
 * the user sees through it.
 *
 * Below Android 13 there is no AGSL, so it falls back to a translucent tint.
 */
class LiquidGlassDrawable(
    private val context: Context,
    private var cornerRadius: Float = 0f,
) : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val boundsF = RectF()
    private val clipPath = Path()

    /** Where this pane sits on screen, so the wallpaper crop lines up. */
    private var screenLeft = 0
    private var screenTop = 0

    private var alphaValue = 255

    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val renderNode by lazy(LazyThreadSafetyMode.NONE) { RenderNode("sora-liquid-glass") }
    private val refraction by lazy(LazyThreadSafetyMode.NONE) {
        RuntimeShader(ROUNDED_RECT_REFRACTION_SHADER)
    }

    var blurRadius: Float = DEFAULT_BLUR
    var refractionHeight: Float = DEFAULT_REFRACTION_HEIGHT
    var refractionAmount: Float = DEFAULT_REFRACTION_AMOUNT
    var showBorder: Boolean = true

    private var radiusTopLeft = cornerRadius
    private var radiusTopRight = cornerRadius
    private var radiusBottomRight = cornerRadius
    private var radiusBottomLeft = cornerRadius

    /**
     * Largest of the four corner radii.
     *
     * Folder open/close animations need this to clip the reveal, and used to read
     * it off a GradientDrawable, so it has to stay reachable without a cast.
     */
    val cornerRadiusPx: Float get() = cornerRadius

    fun setCornerRadius(radius: Float) {
        setCornerRadii(radius, radius, radius, radius)
    }

    /** Corner radii in the shader's order: top-left, top-right, bottom-right, bottom-left. */
    fun setCornerRadii(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        if (radiusTopLeft == topLeft && radiusTopRight == topRight &&
            radiusBottomRight == bottomRight && radiusBottomLeft == bottomLeft
        ) {
            return
        }
        radiusTopLeft = topLeft
        radiusTopRight = topRight
        radiusBottomRight = bottomRight
        radiusBottomLeft = bottomLeft
        cornerRadius = maxOf(topLeft, topRight, bottomRight, bottomLeft)
        invalidateSelf()
    }

    /**
     * Remembers the view this is a background of, so the pane can find its own
     * position on screen at draw time instead of every host having to push it.
     */
    fun attachTo(view: View) {
        host = WeakReference(view)
        followPositionOf(view)
    }

    private var host: WeakReference<View>? = null

    /**
     * What this pane refracts, when it should not be the wallpaper.
     *
     * A pane inside the app drawer sits on the frosted home screen, not on the
     * desktop, so the wallpaper is the wrong picture: it would show a patch of
     * scenery that does not match anything around it. Left null the wallpaper is
     * used, which is right for every pane that really is sitting on the desktop.
     */
    var scene: Bitmap? = null

    /**
     * Pins the pane at a point on screen the host view cannot supply.
     *
     * A folder's plate is offset within its icon, so the icon's own position is
     * not where the glass is. Set this and [attachTo] is not used.
     */
    fun setScreenPosition(x: Int, y: Int) {
        if (x != screenLeft || y != screenTop) {
            screenLeft = x
            screenTop = y
            invalidateSelf()
        }
    }

    /**
     * Tracks [view]'s position so the wallpaper stays registered with the screen
     * rather than sliding along with the pane. Call whenever the host moves.
     */
    fun followPositionOf(view: View) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (location[0] != screenLeft || location[1] != screenTop) {
            screenLeft = location[0]
            screenTop = location[1]
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return

        host?.get()?.let(::followPositionOf)

        boundsF.set(bounds)
        val maxRadius = minOf(width, height) / 2f
        val radius = cornerRadius.coerceAtMost(maxRadius)
        val wallpaper = scene?.takeUnless(Bitmap::isRecycled) ?: LiquidGlassWallpaper.get(context)

        if (!supported || wallpaper == null || !canvas.isHardwareAccelerated) {
            drawFallback(canvas, radius)
            return
        }

        // Map this pane's screen rect onto the downscaled wallpaper.
        val display = context.resources.displayMetrics
        val scaleX = wallpaper.width.toFloat() / display.widthPixels.toFloat()
        val scaleY = wallpaper.height.toFloat() / display.heightPixels.toFloat()
        srcRect.set(
            (screenLeft * scaleX).toInt().coerceIn(0, wallpaper.width),
            (screenTop * scaleY).toInt().coerceIn(0, wallpaper.height),
            ((screenLeft + width) * scaleX).toInt().coerceIn(0, wallpaper.width),
            ((screenTop + height) * scaleY).toInt().coerceIn(0, wallpaper.height),
        )
        if (srcRect.isEmpty) {
            drawFallback(canvas, radius)
            return
        }
        dstRect.set(0, 0, width, height)

        renderNode.setPosition(0, 0, width, height)
        val recording = renderNode.beginRecording()
        try {
            recording.drawBitmap(wallpaper, srcRect, dstRect, bitmapPaint)
        } finally {
            renderNode.endRecording()
        }

        refraction.setFloatUniform("size", width.toFloat(), height.toFloat())
        refraction.setFloatUniform("offset", 0f, 0f)
        refraction.setFloatUniform(
            "cornerRadii",
            radiusTopLeft.coerceAtMost(maxRadius),
            radiusTopRight.coerceAtMost(maxRadius),
            radiusBottomRight.coerceAtMost(maxRadius),
            radiusBottomLeft.coerceAtMost(maxRadius),
        )
        refraction.setFloatUniform("refractionHeight", refractionHeight)
        // Negative pulls the sampled image inward, which is what reads as glass.
        refraction.setFloatUniform("refractionAmount", -refractionAmount)
        refraction.setFloatUniform("depthEffect", 0f)

        val refract = RenderEffect.createRuntimeShaderEffect(refraction, "content")
        renderNode.setRenderEffect(
            if (blurRadius > 0f) {
                RenderEffect.createChainEffect(
                    refract,
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP),
                )
            } else {
                refract
            },
        )
        renderNode.alpha = alphaValue / 255f

        clipPath.reset()
        clipPath.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            floatArrayOf(
                radiusTopLeft, radiusTopLeft,
                radiusTopRight, radiusTopRight,
                radiusBottomRight, radiusBottomRight,
                radiusBottomLeft, radiusBottomLeft,
            ),
            Path.Direction.CW,
        )

        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.clipPath(clipPath)
        canvas.drawRenderNode(renderNode)
        canvas.restoreToCount(save)

        drawSheen(canvas, radius)
    }

    /** Below Android 13, or with no readable wallpaper: a plain translucent pane. */
    private fun drawFallback(canvas: Canvas, radius: Float) {
        tintPaint.color = ColorUtils.setAlphaComponent(
            LiquidGlassWallpaper.fallbackColor(context),
            (alphaValue * FALLBACK_TINT_ALPHA).toInt().coerceIn(0, 255),
        )
        canvas.drawRoundRect(boundsF, radius, radius, tintPaint)
        drawSheen(canvas, radius)
    }

    /** The bright rim that makes a pane read as glass rather than as a blur. */
    private fun drawSheen(canvas: Canvas, radius: Float) {
        if (!showBorder) return
        val stroke = context.resources.displayMetrics.density
        borderPaint.strokeWidth = stroke
        borderPaint.alpha = (BORDER_ALPHA * alphaValue / 255f).toInt().coerceIn(0, 255)
        boundsF.set(bounds)
        boundsF.inset(stroke / 2f, stroke / 2f)
        canvas.drawRoundRect(boundsF, radius, radius, borderPaint)
    }

    override fun setAlpha(alpha: Int) {
        if (alphaValue != alpha) {
            alphaValue = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        tintPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val DEFAULT_BLUR = 12f
        private const val DEFAULT_REFRACTION_HEIGHT = 24f
        private const val DEFAULT_REFRACTION_AMOUNT = 32f
        private const val FALLBACK_TINT_ALPHA = 0.55f
        private const val BORDER_ALPHA = 56
    }
}
