package app.lawnchair.fold

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * BOOK, "Open book": both halves start turned about the hinge like a half-open book, the moving
 * half by [FoldTunables.BOOK_MOVING_DEGREES] and the page you were on by
 * [FoldTunables.BOOK_FIXED_DEGREES], their outer edges away from you, and flatten together. Each
 * has a shade at the hinge that fades as they open. The page you were on is the live DragLayer
 * turned by its animation matrix; the moving half is the live copy turned the same way. A fold
 * on the large layout runs it back; on the cover after a fold, the cover page lands from the fixed
 * half's angle.
 *
 * Each turn is a perspective [Matrix] (the four corners of the page, seen from in front of the
 * middle of the hinge), set every frame without allocating; the shades are gradients built once
 * per geometry.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class BookFoldStyle : SplitFoldStyle() {

    private val frame = Matrix()
    private val fixedTurn = Matrix()
    private val movingTurn = Matrix()
    private val flat = FloatArray(8)
    private val turned = FloatArray(8)
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val fixedShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val movingShade = Paint(Paint.ANTI_ALIAS_FLAG)

    private var geometryVersion = -1
    private var span = 0f

    override fun usesOverlay(scene: FoldScene): Boolean = true

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        val geometry = scene.geometry
        val large = scene.largeLayout
        if (geometry.version != geometryVersion) {
            geometryVersion = geometry.version
            geometry.getHingeFrame(frame, large)
            span = geometry.sweepSpan(large)
            // In hinge space; on the large layout the fixed half is on the negative side
            fixedShade.shader = shade(FoldTunables.BOOK_FIXED_SHADE, if (large) -span else span)
            movingShade.shader = shade(FoldTunables.BOOK_MOVING_SHADE, span)
        }
        val p = progress.coerceIn(0f, 1f)
        // How far from flat: 1 - progress on the large layout, progress on the cover
        val open = if (large) 1f - p else p
        val length = geometry.hingeLength
        if (large) {
            clipDragLayerToFixedHalf(scene)
            turn(fixedTurn, -span, length, FoldTunables.BOOK_FIXED_DEGREES * open)
            turn(movingTurn, span, length, FoldTunables.BOOK_MOVING_DEGREES * open)
        } else {
            turn(fixedTurn, span, length, FoldTunables.BOOK_FIXED_DEGREES * open)
        }
        transformDragLayer(scene, fixedTurn)
        val alpha = (open * 255f).roundToInt().coerceIn(0, 255)
        fixedShade.alpha = alpha
        movingShade.alpha = alpha
    }

    override fun onDrawOverlay(scene: FoldScene, canvas: Canvas, progress: Float) {
        val geometry = scene.geometry
        if (!geometry.isValid) return
        // The shade on the page you were on, which the live views below draw
        if (fixedShade.alpha > 0) {
            canvas.save()
            canvas.concat(fixedTurn)
            if (scene.largeLayout) {
                canvas.drawRect(geometry.fixedRect, fixedShade)
            } else {
                canvas.drawRect(0f, 0f, geometry.width.toFloat(), geometry.height.toFloat(), fixedShade)
            }
            canvas.restore()
        }
        if (!scene.largeLayout) return
        recordMovingHalf(scene)
        canvas.save()
        canvas.concat(movingTurn)
        canvas.drawRenderNode(movingNode)
        if (movingShade.alpha > 0) canvas.drawRect(geometry.movingRect, movingShade)
        canvas.restore()
    }

    /**
     * Sets [out] to the page from the hinge (u = 0) to [end] in hinge space, turned by [degrees]
     * about the hinge with its outer edge going away, seen from [FoldTunables.BOOK_EYE_DISTANCE]
     * page widths in front of the middle of the hinge: the outer edge comes closer to the hinge
     * and shorter.
     */
    private fun turn(out: Matrix, end: Float, length: Float, degrees: Float) {
        if (degrees <= 0f || end == 0f) {
            out.reset()
            return
        }
        val radians = degrees * (PI.toFloat() / 180f)
        val distance = FoldTunables.BOOK_EYE_DISTANCE * abs(end)
        val depth = distance / (distance + abs(end) * sin(radians))
        val middle = length / 2f
        setCorners(flat, length, end, 0f, length)
        setCorners(turned, length, end * cos(radians) * depth, middle - middle * depth, middle + middle * depth)
        frame.mapPoints(src, flat)
        frame.mapPoints(dst, turned)
        if (!out.setPolyToPoly(src, 0, dst, 0, 4)) out.reset()
    }

    /** The hinge corners, then the outer corners at [end] from [endStart] to [endStop] along the hinge. */
    private fun setCorners(points: FloatArray, length: Float, end: Float, endStart: Float, endStop: Float) {
        points[0] = 0f
        points[1] = 0f
        points[2] = 0f
        points[3] = length
        points[4] = end
        points[5] = endStart
        points[6] = end
        points[7] = endStop
    }

    /** Dark ([darkness]) at the hinge, clear at [end], in root coordinates. */
    private fun shade(darkness: Float, end: Float): Shader = LinearGradient(
        0f,
        0f,
        end,
        0f,
        Color.argb((darkness * 255f).roundToInt(), 0, 0, 0),
        Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
    ).apply { setLocalMatrix(frame) }
}
