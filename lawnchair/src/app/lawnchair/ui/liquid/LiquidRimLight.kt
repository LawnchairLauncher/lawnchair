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

import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.android.launcher3.graphics.ShapeDelegate

/**
 * A lit edge traced along a shape: light from straight above, and from straight
 * below.
 *
 * The same light the glass panes catch, drawn the only way a plain canvas can.
 * Backdrop gets this from a runtime shader that takes the magnitude of the dot
 * product between the surface normal and the light direction, so one direction
 * lights both the side facing it and the side opposite. On a rounded shape that
 * is a bright arc along the top and another along the bottom, fading out down
 * the left and right sides -- and a vertical gradient reproduces it closely,
 * because the top edge is at the top, the bottom edge at the bottom, and the
 * sides are exactly what sits between them.
 *
 * A single flat colour all the way round, which is what this replaces, reads as
 * an outline drawn on rather than as light landing on something.
 */
object LiquidRimLight {

    /**
     * How strongly the lit part of the edge shows.
     *
     * Shared so that every rim in the launcher is the same light: the glass
     * panes get theirs from a runtime shader and the baked icons from a gradient
     * stroke, and two different numbers in two different engines is what makes
     * the edge visibly change at the end of a folder closing.
     */
    const val ALPHA = 0.55f

    /**
     * How dark the shadow down either side goes.
     *
     * A separate thing from the light, and it lives on the other side of the
     * edge. The light lands *on* the surface, so it is drawn inside the shape;
     * the shadow is cast *by* the surface onto what is behind it, so it is drawn
     * outside. Rolled into the one stroke -- a single gradient running white,
     * black, white down the inside -- it reads as a two-tone border painted on
     * the shape rather than as a lit object sitting above a background.
     *
     * Thin on purpose: at the left edge of a plate the reference runs 178, then
     * 135 and 93, and is back above 155 two pixels later. Two pixels, outside.
     */
    const val SHADE_ALPHA = 0.5f

    /**
     * The glow the lit edges cast inwards, and how far in it reaches.
     *
     * The top and bottom are not a line but a lit *area*: past the bright edge
     * itself the reference falls 255, 254, 242, 219, 215, 211, 208 and is still
     * declining fifteen pixels in. A stroke alone gives the line and none of
     * that, which is most of what makes the surface look curved.
     */
    const val GLOW_ALPHA = 0.12f
    private const val GLOW_DEPTH = 0.06f

    /** The bright edge measures two to three pixels on a 1170px-wide screen. */
    const val WIDTH_DP = 0.9f

    /**
     * The cast shadow is drawn thinner than the light that faces it.
     *
     * It only has to separate the surface from what is behind it. At the same
     * weight as the lit edge it stops being a shadow and becomes the dark half
     * of a two-tone outline, which is the thing this was rebuilt to avoid.
     */
    private const val SHADOW_WIDTH_SCALE = 0.55f

    /**
     * Where along an edge the light actually lands.
     *
     * Not evenly: a curved surface under a light catches it brightest where it
     * faces the light square on, which is the middle of the top edge, and gives
     * up as the surface turns away towards the corners. Measured across the
     * reference's top edge the strength holds at full from the middle out to
     * about a quarter of the way from either end, then falls to nothing by the
     * corner. Without this the edge is uniformly bright for its whole length,
     * which is exactly what reads as a drawn-on line rather than as light.
     */
    private val SPAN_STOPS = floatArrayOf(0f, 0.28f, 0.72f, 1f)
    private val SPAN_COLORS = intArrayOf(CLEAR_W, OPAQUE_W, OPAQUE_W, CLEAR_W)

    private val LIT = (ALPHA * 255f).toInt() shl 24 or 0xFFFFFF
    private val SHADE = (SHADE_ALPHA * 255f).toInt() shl 24 // black
    private val GLOW = (GLOW_ALPHA * 255f).toInt() shl 24 or 0xFFFFFF
    private const val CLEAR = 0

    /** Mask colours: alpha is all that is read from these. */
    private const val OPAQUE_W = 0xFFFFFFFF.toInt()
    private const val CLEAR_W = 0x00FFFFFF

    /**
     * Light at the very top and the very bottom, and nothing down the sides.
     *
     * One direction of light lights the face turned towards it and the face
     * turned away from it, and leaves the two faces side-on to it unlit -- so
     * the stops are tight at either end and the long middle is simply absent.
     * What happens at the sides is the shadow's business, and that is drawn
     * separately, outside the shape.
     */
    private val STOPS = floatArrayOf(0f, 0.12f, 0.88f, 1f)
    private val COLORS = intArrayOf(LIT, CLEAR, CLEAR, LIT)

    /** The inward wash, which fades far sooner than the lit edge itself. */
    private val GLOW_STOPS = floatArrayOf(0f, GLOW_DEPTH, 1f - GLOW_DEPTH, 1f)
    private val GLOW_COLORS = intArrayOf(GLOW, CLEAR, CLEAR, GLOW)

    /** The cast shadow: down the sides only, exactly where the light is not. */
    private val SHADOW_STOPS = floatArrayOf(0f, 0.14f, 0.86f, 1f)
    private val SHADOW_COLORS = intArrayOf(CLEAR, SHADE, SHADE, CLEAR)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var shadowGradient: LinearGradient? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val bounds = RectF()

    // Rebuilt only when the shape changes size. This draws once per icon, on
    // every frame of every scroll, so the gradient is not worth reallocating.
    private var gradient: Shader? = null
    private var gradientTop = Float.NaN
    private var gradientBottom = Float.NaN
    private var gradientLeft = Float.NaN
    private var gradientRight = Float.NaN

    /**
     * The vertical light, faded along the edge it runs down.
     *
     * DST_IN keeps the vertical gradient only where the horizontal mask has
     * alpha, so the two multiply: full strength across the middle of an edge,
     * nothing at the corners.
     */
    private fun spanMask(left: Float, right: Float) = LinearGradient(
        left, 0f, right, 0f, SPAN_COLORS, SPAN_STOPS, Shader.TileMode.CLAMP,
    )

    private fun litShader(left: Float, top: Float, right: Float, bottom: Float) =
        ComposeShader(
            LinearGradient(0f, top, 0f, bottom, COLORS, STOPS, Shader.TileMode.CLAMP),
            spanMask(left, right),
            PorterDuff.Mode.DST_IN,
        )

    private fun glowShader(left: Float, top: Float, right: Float, bottom: Float) =
        ComposeShader(
            LinearGradient(0f, top, 0f, bottom, GLOW_COLORS, GLOW_STOPS, Shader.TileMode.CLAMP),
            spanMask(left, right),
            PorterDuff.Mode.DST_IN,
        )

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var glowGradient: Shader? = null

    /**
     * Lights the edge of a path that is already built, allocating as it goes.
     *
     * For baking the rim into an icon bitmap, which happens once per icon on
     * whichever thread the icon loader is using. The cached paint the folder
     * path uses would not survive that: it is shared, and icon generation is not
     * on the UI thread. One allocation per icon, paid once and never at draw
     * time, is the right trade here.
     */
    @JvmStatic
    fun bake(canvas: Canvas, path: Path, strokeWidth: Float) {
        val edge = RectF()
        path.computeBounds(edge, true)
        if (edge.isEmpty || strokeWidth <= 0f) return

        // Twice the width, kept only where the icon already has pixels.
        //
        // A stroke straddles the line it follows, so half of it lands outside
        // the shape -- on transparency, in an icon bitmap -- and what is left
        // reads as a line laid over the icon rather than as its edge catching
        // light. SRC_ATOP throws that outer half away, leaving an inner edge of
        // exactly the width asked for, following the silhouette precisely and
        // still anti-aliased.
        // The light only. The cast shadow belongs outside the silhouette, and in
        // a bitmap sized to the icon there is no outside to put it in -- it
        // would be clipped by the bitmap's own edge into a dark line hugging the
        // icon, which is the very thing it is meant not to look like. Icons get
        // their shadow from whoever draws them, not from their own pixels.
        //
        // The inward wash first, so the lit edge lands on top of its own glow --
        // the same order, and the same coats, the drawn rim uses.
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = glowShader(edge.left, edge.top, edge.right, edge.bottom)
        }
        canvas.drawPath(path, wash)

        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 2f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shader = litShader(edge.left, edge.top, edge.right, edge.bottom)
        }
        canvas.drawPath(path, brush)
    }

    /**
     * Traces [shape] and lights its edge.
     *
     * [offsetX] and [offsetY] are the shape's top-left corner and [radius] is
     * half its edge, matching what [ShapeDelegate.addToPath] expects.
     */
    @JvmOverloads
    @JvmStatic
    fun draw(
        canvas: Canvas,
        shape: ShapeDelegate,
        offsetX: Float,
        offsetY: Float,
        radius: Float,
        strokeWidth: Float,
        alpha: Float = 1f,
    ) {
        if (radius <= 0f || alpha <= 0f) return

        path.reset()
        shape.addToPath(path, offsetX, offsetY, radius)
        drawPath(canvas, path, strokeWidth, alpha)
    }

    /**
     * The shadow a glass surface casts on what is behind it, down either side.
     *
     * Drawn outside the shape and nowhere else -- clipped away within it and
     * stroked at twice the width, so only the outer half survives, which is the
     * mirror of what the lit edge does on the inside.
     *
     * For glass only: the folder panes, the open folder, the folder icon on the
     * home screen, and the press-and-hold menu. An app icon has no glass and no
     * gap behind it to cast into, and its bitmap has no room outside the
     * silhouette to hold a shadow anyway.
     */
    @JvmOverloads
    @JvmStatic
    fun drawOuterShadow(canvas: Canvas, path: Path, strokeWidth: Float, alpha: Float = 1f) {
        if (strokeWidth <= 0f || alpha <= 0f) return
        path.computeBounds(bounds, true)
        if (bounds.isEmpty) return

        val gradient = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom,
            SHADOW_COLORS, SHADOW_STOPS, Shader.TileMode.CLAMP,
        )
        val save = canvas.save()
        canvas.clipOutPath(path)
        shadowPaint.shader = gradient
        shadowPaint.strokeWidth = strokeWidth * 2f * SHADOW_WIDTH_SCALE
        shadowPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawPath(path, shadowPaint)
        canvas.restoreToCount(save)
        shadowPaint.shader = null
        shadowPaint.alpha = 255
    }

    /**
     * The same light, on an outline the caller has already built.
     *
     * A folder given more than one cell is a rounded rectangle rather than an
     * icon shape, and its rim has to follow that outline instead.
     */
    @JvmOverloads
    @JvmStatic
    fun drawPath(canvas: Canvas, path: Path, strokeWidth: Float, alpha: Float = 1f) {
        if (strokeWidth <= 0f || alpha <= 0f) return
        path.computeBounds(bounds, true)
        if (bounds.isEmpty) return

        if (gradient == null || bounds.top != gradientTop || bounds.bottom != gradientBottom ||
            bounds.left != gradientLeft || bounds.right != gradientRight
        ) {
            gradientTop = bounds.top
            gradientBottom = bounds.bottom
            gradientLeft = bounds.left
            gradientRight = bounds.right
            gradient = litShader(bounds.left, bounds.top, bounds.right, bounds.bottom)
            glowGradient = glowShader(bounds.left, bounds.top, bounds.right, bounds.bottom)
            shadowGradient = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                SHADOW_COLORS, SHADOW_STOPS, Shader.TileMode.CLAMP,
            )
        }

        drawOuterShadow(canvas, path, strokeWidth, alpha)

        // Clipped to the shape and stroked at twice the width, so only the
        // inner half survives. Stroking the line itself would leave half the rim
        // hanging outside the surface it belongs to.
        val save = canvas.save()
        canvas.clipPath(path)

        // The wash first, so the bright edge sits on top of its own glow.
        glowPaint.shader = glowGradient
        glowPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawPath(path, glowPaint)
        glowPaint.shader = null
        glowPaint.alpha = 255

        paint.shader = gradient
        paint.strokeWidth = strokeWidth * 2f
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
        paint.shader = null
        paint.alpha = 255
    }
}
