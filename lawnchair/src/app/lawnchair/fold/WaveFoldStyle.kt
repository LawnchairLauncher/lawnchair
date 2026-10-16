package app.lawnchair.fold

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.android.launcher3.util.Themes
import kotlin.math.roundToInt

/**
 * WAVE, Lawnchair's signature: a band of light in the accent color (Material You by default)
 * sweeps from the hinge across the moving half. The moving half is dim ahead of the band and
 * revealed behind it; each icon glows, lifts [FoldTunables.WAVE_LIFT_DP] and settles as the band
 * passes, so its reveal time is its distance from the hinge. Closing runs the same path back: the
 * band returns to the hinge and dims the icons behind it.
 *
 * On the cover screen after a fold the band sweeps back to the hinge edge once, lighting the
 * icons it passes. The fixed half never changes.
 *
 * Drawn on live views like the other split styles; the band, the reveal mask and the glows are
 * gradients created once per geometry and positioned with canvas transforms, so nothing is
 * allocated per frame.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class WaveFoldStyle : SplitFoldStyle() {

    private val tracker = FoldItemTracker { item ->
        item.resetTranslation()
        item.glow = 0f
        item.value = 0f
    }
    private val frame = Matrix()
    private val maskPaint = Paint().apply { blendMode = BlendMode.DST_IN }
    private val bandPaint = Paint().apply { blendMode = BlendMode.SCREEN }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.SCREEN }

    private var geometryVersion = -1
    private var bandHalf = 0f
    private var liftPx = 0f
    private var settlePx = 0f
    private var span = 0f
    private var length = 0f
    private var bandCenter = 0f

    override fun usesOverlay(scene: FoldScene): Boolean = true

    override fun onStart(scene: FoldScene): Boolean {
        bandHalf = scene.dpToPx(FoldTunables.WAVE_BAND_WIDTH_DP) / 2f
        liftPx = scene.dpToPx(FoldTunables.WAVE_LIFT_DP)
        settlePx = scene.dpToPx(FoldTunables.WAVE_SETTLE_DP)
        val accent = Themes.getColorAccent(scene.launcher)
        val light = ColorUtils.blendARGB(accent, Color.WHITE, 0.55f)
        bandPaint.shader = LinearGradient(
            -bandHalf,
            0f,
            bandHalf,
            0f,
            intArrayOf(Color.TRANSPARENT, withAlpha(accent, 0.45f), light, withAlpha(accent, 0.45f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Behind the band (toward the hinge) revealed, ahead of it dim.
        val dim = FoldTunables.WAVE_DIM_ALPHA
        maskPaint.shader = LinearGradient(
            -bandHalf,
            0f,
            bandHalf,
            0f,
            intArrayOf(Color.BLACK, withAlpha(Color.BLACK, (1f + dim) / 2f), withAlpha(Color.BLACK, dim)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowPaint.shader = RadialGradient(
            0f,
            0f,
            1f,
            intArrayOf(light, withAlpha(accent, 0.5f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val geometry = scene.geometry
        if (geometry.version != geometryVersion) {
            geometryVersion = geometry.version
            geometry.getHingeFrame(frame, scene.largeLayout)
            span = geometry.sweepSpan(scene.largeLayout)
            length = geometry.hingeLength
        }
        val p = progress.coerceIn(0f, 1f)
        if (scene.largeLayout) {
            clipDragLayerToFixedHalf(scene)
            // From the hinge (half of the band shows there) to past the free edge.
            bandCenter = p * (span + bandHalf)
            bandPaint.alpha = (FoldTunables.WAVE_BAND_ALPHA * 255f).roundToInt()
        } else {
            // Cover: from past the far edge back to past the hinge edge.
            bandCenter = -bandHalf + p * (span + 2f * bandHalf)
            bandPaint.alpha = (FoldTunables.WAVE_BAND_ALPHA * COVER_FACTOR * 255f).roundToInt()
        }

        tracker.update(scene)
        val items = tracker.items
        for (i in items.indices) {
            val item = items[i]
            val swept = item.onMovingHalf || !scene.largeLayout
            if (!swept || item.isWidget) {
                item.resetTranslation()
                item.glow = 0f
                item.value = 0f
                continue
            }
            // 0 until the band reaches the item, 1 once it has settled behind it.
            val t = ((bandCenter - item.distance * span + bandHalf) / (2f * bandHalf + settlePx)).coerceIn(0f, 1f)
            val bump = bump(t)
            // value = how far the icon is lifted
            item.value = if (scene.largeLayout) liftPx * bump else 0f
            item.setTranslation(0f, -item.value)
            item.glow = bump * FoldTunables.WAVE_GLOW_ALPHA * if (scene.largeLayout) 1f else COVER_FACTOR
        }
    }

    override fun onDrawOverlay(scene: FoldScene, canvas: Canvas, progress: Float) {
        val geometry = scene.geometry
        if (!geometry.isValid) return
        val save = canvas.save()
        if (scene.largeLayout) {
            canvas.clipRect(geometry.movingRect)
            // The moving half, dimmed ahead of the band.
            recordMovingHalf(scene)
            val layer = canvas.saveLayer(
                geometry.movingRect.left.toFloat(),
                geometry.movingRect.top.toFloat(),
                geometry.movingRect.right.toFloat(),
                geometry.movingRect.bottom.toFloat(),
                null,
            )
            canvas.drawRenderNode(movingNode)
            canvas.concat(frame)
            canvas.translate(bandCenter, 0f)
            canvas.drawRect(-bandCenter, 0f, span - bandCenter, length, maskPaint)
            canvas.restoreToCount(layer)
        }
        // The band of light, then the glows on top of the icons it passes.
        canvas.save()
        canvas.concat(frame)
        canvas.translate(bandCenter, 0f)
        canvas.drawRect(-bandHalf, 0f, bandHalf, length, bandPaint)
        canvas.restore()
        val items = tracker.items
        for (i in items.indices) {
            val item = items[i]
            if (item.glow < MIN_GLOW) continue
            val radius = item.iconRadius * GLOW_SIZE
            if (radius <= 0f) continue
            glowPaint.alpha = (item.glow * 255f).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(item.centerX + item.iconOffsetX, item.centerY + item.iconOffsetY - item.value)
            canvas.scale(radius, radius)
            canvas.drawCircle(0f, 0f, 1f, glowPaint)
            canvas.restore()
        }
        canvas.restoreToCount(save)
    }

    override fun onEnd(scene: FoldScene) {
        tracker.clear()
        super.onEnd(scene)
    }

    /** Rises quickly to 1 as the band arrives, then settles back to 0. */
    private fun bump(t: Float): Float {
        if (t <= 0f || t >= 1f) return 0f
        return if (t < RISE) {
            val x = t / RISE
            1f - (1f - x) * (1f - x)
        } else {
            val x = (t - RISE) / (1f - RISE)
            1f - x * x * (3f - 2f * x)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = ColorUtils.setAlphaComponent(color, (alpha * 255f).roundToInt().coerceIn(0, 255))

    private companion object {
        const val RISE = 0.3f
        const val GLOW_SIZE = 1.7f
        const val MIN_GLOW = 0.004f
        const val COVER_FACTOR = 0.6f
    }
}
