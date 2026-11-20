package app.lawnchair.fold

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * WINDOW (default): the whole screen comes into focus, and the new half opens like a window onto
 * the content, which is a fixed plane.
 *
 * The moving half rotates toward the viewer around the hinge. To keep the content still in space,
 * the panel shows the plane through a perspective warp ([Matrix.setPolyToPoly] of the four corners,
 * computed from an eye over the fixed half): at the hinge the content is untouched; toward the free
 * edge it narrows. On top of that, toward the free edge only (cubic falloff, so the hinge edge stays
 * sharp), the content blurs (API 31+, [RenderEffect]; none below) and the panel darkens. The fixed
 * half, the page you were on, starts lightly blurred and sharpens first (by
 * [FoldTunables.WINDOW_FIXED_SHARP_AT]); and the whole screen settles from
 * [FoldTunables.WINDOW_SETTLE_SCALE] to 1 about the fixed half's outer edge. Everything is a function
 * of progress, so it follows the hinge when it is tracked.
 *
 * On the cover layout after a fold, the cover page settles the same way about its outer edge and
 * sharpens from the same light blur.
 *
 * The blur is "progressive": three images of the moving half (sharp, a middle radius, the full
 * radius) are mixed by gradient masks, all inside one RenderEffect on the moving half's RenderNode.
 * Radii are pre-built in [FoldTunables.WINDOW_BLUR_LEVELS] steps, so no effect is created per frame.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class WindowFoldStyle : SplitFoldStyle() {

    private val warp = Matrix()
    private val settle = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val shadePaint = Paint()

    private var geometryVersion = -1
    private var movingBlurLevels: Array<RenderEffect?> = emptyArray()
    private var fixedBlurLevels: Array<RenderEffect?> = emptyArray()
    private var appliedMovingLevel = -1
    private var appliedFixedLevel = -1

    override fun onStart(scene: FoldScene): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fixedBlurLevels = buildFixedBlurLevels(scene.dpToPx(FoldTunables.WINDOW_FIXED_BLUR_DP))
        }
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val geometry = scene.geometry
        // How far from the rest state: 1 - progress on the large layout, progress on the cover
        val amount = if (scene.largeLayout) 1f - p else p
        val zoom = 1f + (FoldTunables.WINDOW_SETTLE_SCALE - 1f) * amount
        settle.setScale(zoom, zoom, geometry.outerEdgeX, geometry.outerEdgeY)
        transformDragLayer(scene, settle)
        if (!scene.largeLayout) {
            // Cover layout: the page comes into focus as the fold settles (1 -> 0).
            setFixedBlur(scene, amount)
            return
        }
        if (geometry.version != geometryVersion) {
            geometryVersion = geometry.version
            onGeometryChanged(scene)
        }
        clipDragLayerToFixedHalf(scene)
        // The page you were on is sharp before the new half
        setFixedBlur(scene, (1f - p / FoldTunables.WINDOW_FIXED_SHARP_AT).coerceAtLeast(0f))
        updateWarp(geometry, amount)
        setMovingBlur(amount)
        shadePaint.alpha = (FoldTunables.WINDOW_MAX_SHADE * amount * 255f).roundToInt().coerceIn(0, 255)
    }

    override fun onDrawOverlay(scene: FoldScene, canvas: Canvas, progress: Float) {
        val geometry = scene.geometry
        if (!scene.largeLayout || !geometry.isValid) return
        recordMovingHalf(scene)
        val save = canvas.save()
        canvas.concat(settle)
        canvas.clipRect(geometry.movingRect)
        canvas.save()
        canvas.concat(warp)
        canvas.drawRenderNode(movingNode)
        canvas.restore()
        if (shadePaint.alpha > 0) canvas.drawRect(geometry.movingRect, shadePaint)
        canvas.restoreToCount(save)
    }

    override fun onEnd(scene: FoldScene) {
        super.onEnd(scene)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (appliedFixedLevel > 0) scene.dragLayer.setRenderEffect(null)
            movingNode.setRenderEffect(null)
        }
        appliedFixedLevel = -1
        appliedMovingLevel = -1
    }

    private fun onGeometryChanged(scene: FoldScene) {
        val geometry = scene.geometry
        val span = geometry.halfSpan
        // Darkening, in root coordinates: from the hinge to the free edge.
        val shadeColors = IntArray(SHADE_STOPS) { i ->
            val t = i / (SHADE_STOPS - 1f)
            Color.argb(((SHADE_AT_HINGE + (1f - SHADE_AT_HINGE) * t * t) * 255f).roundToInt(), 0, 0, 0)
        }
        shadePaint.shader = LinearGradient(
            geometry.toRootX(0f, 0f),
            geometry.toRootY(0f, 0f),
            geometry.toRootX(span, 0f),
            geometry.toRootY(span, 0f),
            shadeColors,
            null,
            Shader.TileMode.CLAMP,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            movingNode.setRenderEffect(null)
            appliedMovingLevel = -1
            movingBlurLevels = buildMovingBlurLevels(scene)
        }
    }

    /**
     * The moving half is tilted toward an eye over the fixed half by [amount] of the max tilt. A ray
     * from the eye through the free edge of the panel meets the content plane [freeEdgeScale] times
     * further out, at [freeEdgeU] from the hinge; the hinge edge maps to itself. The four corners
     * give the homography between the plane and the panel.
     */
    private fun updateWarp(geometry: FoldGeometry, amount: Float) {
        val span = geometry.halfSpan
        val length = geometry.hingeLength
        val scale = freeEdgeScale(geometry, amount)
        val freeU = freeEdgeU(geometry, amount, scale)
        val eyeV = length / 2f
        val freeStartV = eyeV - eyeV * scale
        val freeEndV = eyeV + (length - eyeV) * scale
        // Plane points seen at the panel corners.
        setPoint(src, 0, geometry, 0f, 0f)
        setPoint(src, 1, geometry, 0f, length)
        setPoint(src, 2, geometry, freeU, freeStartV)
        setPoint(src, 3, geometry, freeU, freeEndV)
        // Panel corners.
        setPoint(dst, 0, geometry, 0f, 0f)
        setPoint(dst, 1, geometry, 0f, length)
        setPoint(dst, 2, geometry, span, 0f)
        setPoint(dst, 3, geometry, span, length)
        // The plane stays still: it is drawn through the panel (plane -> panel)
        if (!warp.setPolyToPoly(src, 0, dst, 0, 4)) warp.reset()
    }

    private fun tiltRadians(amount: Float) = FoldTunables.WINDOW_MAX_TILT_DEGREES * amount * (PI.toFloat() / 180f)

    private fun freeEdgeScale(geometry: FoldGeometry, amount: Float): Float {
        val span = geometry.halfSpan
        val eyeDistance = FoldTunables.WINDOW_EYE_DISTANCE * max(2f * span, geometry.hingeLength)
        return eyeDistance / (eyeDistance - span * sin(tiltRadians(amount)))
    }

    private fun freeEdgeU(geometry: FoldGeometry, amount: Float, scale: Float): Float {
        val span = geometry.halfSpan
        val eyeU = -FoldTunables.WINDOW_EYE_OFFSET * span
        return eyeU + (span * cos(tiltRadians(amount)) - eyeU) * scale
    }

    private fun setPoint(points: FloatArray, index: Int, geometry: FoldGeometry, u: Float, v: Float) {
        points[index * 2] = geometry.toRootX(u, v)
        points[index * 2 + 1] = geometry.toRootY(u, v)
    }

    private fun setFixedBlur(scene: FoldScene, amount: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || fixedBlurLevels.isEmpty()) return
        val level = (amount * (fixedBlurLevels.size - 1)).roundToInt().coerceIn(0, fixedBlurLevels.size - 1)
        if (level == appliedFixedLevel) return
        if (level == 0 && appliedFixedLevel <= 0) {
            appliedFixedLevel = 0
            return
        }
        appliedFixedLevel = level
        scene.dragLayer.setRenderEffect(fixedBlurLevels[level])
    }

    private fun setMovingBlur(amount: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || movingBlurLevels.isEmpty()) return
        val level = (amount * (movingBlurLevels.size - 1)).roundToInt().coerceIn(0, movingBlurLevels.size - 1)
        if (level == appliedMovingLevel) return
        appliedMovingLevel = level
        movingNode.setRenderEffect(movingBlurLevels[level])
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildFixedBlurLevels(maxRadius: Float): Array<RenderEffect?> {
        if (maxRadius < MIN_BLUR_RADIUS) return emptyArray()
        return Array(FIXED_BLUR_LEVELS + 1) { i ->
            if (i == 0) {
                null
            } else {
                val radius = maxRadius * i / FIXED_BLUR_LEVELS
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            }
        }
    }

    /**
     * Blur effects for the moving half, in its RenderNode's coordinates, one per radius step.
     * The relative radius at distance t (0 hinge, 1 free edge) is t³; it is rendered by mixing a
     * sharp, a middle ([FoldTunables.WINDOW_BLUR_BAND_SPLIT] of the radius) and a full blur.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildMovingBlurLevels(scene: FoldScene): Array<RenderEffect?> {
        val maxRadius = scene.dpToPx(FoldTunables.WINDOW_MAX_BLUR_DP)
        val levels = FoldTunables.WINDOW_BLUR_LEVELS
        if (maxRadius < MIN_BLUR_RADIUS) return emptyArray()
        val geometry = scene.geometry
        val rect = geometry.movingRect
        // Only the content up to freeEdgeU is visible at the max tilt: make that the end of the
        // masks so the free edge of the panel gets the full blur.
        val maskSpan = freeEdgeU(geometry, 1f, freeEdgeScale(geometry, 1f)).coerceIn(geometry.halfSpan / 2f, geometry.halfSpan)
        val hingeX = geometry.toRootX(0f, 0f) - rect.left
        val hingeY = geometry.toRootY(0f, 0f) - rect.top
        val freeX = geometry.toRootX(maskSpan, 0f) - rect.left
        val freeY = geometry.toRootY(maskSpan, 0f) - rect.top
        val split = FoldTunables.WINDOW_BLUR_BAND_SPLIT

        val sharpColors = IntArray(MASK_STOPS)
        val middleColors = IntArray(MASK_STOPS)
        val fullColors = IntArray(MASK_STOPS)
        for (i in 0 until MASK_STOPS) {
            val t = i / (MASK_STOPS - 1f)
            val r = t * t * t
            val sharp: Float
            val middle: Float
            val full: Float
            if (r <= split) {
                middle = r / split
                sharp = 1f - middle
                full = 0f
            } else {
                full = (r - split) / (1f - split)
                middle = 1f - full
                sharp = 0f
            }
            sharpColors[i] = Color.argb((sharp * 255f).roundToInt(), 0, 0, 0)
            middleColors[i] = Color.argb((middle * 255f).roundToInt(), 0, 0, 0)
            fullColors[i] = Color.argb((full * 255f).roundToInt(), 0, 0, 0)
        }
        fun mask(colors: IntArray) = RenderEffect.createShaderEffect(
            LinearGradient(hingeX, hingeY, freeX, freeY, colors, null, Shader.TileMode.CLAMP),
        )
        val sharpMask = mask(sharpColors)
        val middleMask = mask(middleColors)
        val fullMask = mask(fullColors)
        val sharp = RenderEffect.createBlendModeEffect(sharpMask, RenderEffect.createOffsetEffect(0f, 0f), BlendMode.SRC_IN)

        return Array(levels + 1) { i ->
            if (i == 0) {
                null
            } else {
                val radius = maxRadius * i / levels
                val middleRadius = max(radius * split, MIN_BLUR_RADIUS)
                val middle = RenderEffect.createBlendModeEffect(
                    middleMask,
                    RenderEffect.createBlurEffect(middleRadius, middleRadius, Shader.TileMode.CLAMP),
                    BlendMode.SRC_IN,
                )
                val full = RenderEffect.createBlendModeEffect(
                    fullMask,
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
                    BlendMode.SRC_IN,
                )
                RenderEffect.createBlendModeEffect(
                    RenderEffect.createBlendModeEffect(sharp, middle, BlendMode.PLUS),
                    full,
                    BlendMode.PLUS,
                )
            }
        }
    }

    private companion object {
        const val SHADE_STOPS = 8
        const val SHADE_AT_HINGE = 0.1f
        const val MASK_STOPS = 16
        const val FIXED_BLUR_LEVELS = 6
        const val MIN_BLUR_RADIUS = 0.5f
    }
}
