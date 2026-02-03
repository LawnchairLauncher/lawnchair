package app.lawnchair.icons.shape

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.FloatProperty
import android.view.View
import android.view.ViewOutlineProvider
import androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.SvgPathParser
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import com.android.launcher3.Flags
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.graphics.ShapeDelegate
import com.android.launcher3.graphics.ShapeDelegate.Companion.DEFAULT_PATH_SIZE
import com.android.launcher3.graphics.ShapeDelegate.Companion.createRoundedRect
import com.android.launcher3.views.ClipPathView

/**
 * A ShapeDelegate that is initialized with an IconShape object,
 * providing proper animation support through RoundedPolygon morphing
 * when the IconShape provides an SVG path string. The shape is assumed
 * to be defined within a [0, 0, 100, 100] viewport.
 */
data class PathShapeDelegate(private val iconShape: IconShape) : ShapeDelegate {

    private val basePath: Path = iconShape.getMaskPath()
    private val tmpPath = Path()
    private val tmpMatrix = Matrix()

    // Create RoundedPolygon from SVG path if available for morph animations
    private val polygon: RoundedPolygon? = iconShape.svgPathString?.let { pathString ->
        try {
            RoundedPolygon(
                features = SvgPathParser.parseFeatures(pathString),
                centerX = 50f,
                centerY = 50f,
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun drawShape(
        canvas: Canvas,
        offsetX: Float,
        offsetY: Float,
        radius: Float,
        paint: Paint,
    ) {
        tmpPath.reset()
        addToPath(tmpPath, offsetX, offsetY, radius, tmpMatrix)
        canvas.drawPath(tmpPath, paint)
    }

    override fun addToPath(path: Path, offsetX: Float, offsetY: Float, radius: Float) {
        addToPath(path, offsetX, offsetY, radius, Matrix())
    }

    private fun addToPath(
        path: Path,
        offsetX: Float,
        offsetY: Float,
        radius: Float,
        matrix: Matrix,
    ) {
        // The base path is 100x100. We need to scale it to fit the desired 2 * radius.
        // The radius in ShapeDelegate is half the size of the icon.
        val scale = radius / 50f
        matrix.setScale(scale, scale)
        matrix.postTranslate(offsetX, offsetY)
        // Apply the transformation to our base path and add it to the destination path.
        basePath.transform(matrix, path)
    }

    /** LC-Note: See [ShapeDelegate.createRevealAnimator] */
    override fun <T> createRevealAnimator(
        target: T,
        startRect: Rect,
        endRect: Rect,
        endRadius: Float,
        isReversed: Boolean,
    ): ValueAnimator where T : View, T : ClipPathView {
        val pathProvider: (Float, Path) -> Unit = if (polygon != null) {
            // Use proper Morph animation with RoundedPolygon for smooth folder animations
            val morph = Morph(
                start = polygon.transformed(
                    Matrix().apply {
                        setRectToRect(
                            RectF(0f, 0f, DEFAULT_PATH_SIZE, DEFAULT_PATH_SIZE),
                            RectF(startRect),
                            Matrix.ScaleToFit.FILL,
                        )
                    },
                ),
                end = createRoundedRect(
                    left = endRect.left.toFloat(),
                    top = endRect.top.toFloat(),
                    right = endRect.right.toFloat(),
                    bottom = endRect.bottom.toFloat(),
                    cornerR = endRadius,
                ),
            )
            morph::toPath
        } else {
            // Fallback: Use IconShape's addToPath with progress interpolation for corner-based shapes
            { progress: Float, path: Path ->
                // Interpolate the bounds from start to end
                val left = (1 - progress) * startRect.left + progress * endRect.left
                val top = (1 - progress) * startRect.top + progress * endRect.top
                val right = (1 - progress) * startRect.right + progress * endRect.right
                val bottom = (1 - progress) * startRect.bottom + progress * endRect.bottom

                // Calculate the size (half of the average dimension) for the icon shape
                val startSize = (startRect.width() + startRect.height()) / 4f

                // Use IconShape's addToPath with progress for smooth interpolation
                iconShape.addToPath(
                    path = path,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    size = startSize,
                    endSize = endRadius,
                    progress = progress,
                )
            }
        }

        val shouldUseSpringAnimation =
            Flags.enableLauncherIconShapes() && Flags.enableExpressiveFolderExpansion()
        return if (shouldUseSpringAnimation) {
            ClipSpringAnimBuilder(target, pathProvider).toAnim(isReversed)
        } else {
            ClipAnimBuilder(target, pathProvider).toAnim(isReversed)
        }
    }

    private class ClipAnimBuilder<T>(val target: T, val pathProvider: (Float, Path) -> Unit) :
        AnimatorListenerAdapter(),
        ValueAnimator.AnimatorUpdateListener where T : View, T : ClipPathView {

        private var oldOutlineProvider: ViewOutlineProvider? = null
        val path = Path()

        override fun onAnimationStart(animation: Animator) {
            target.apply {
                oldOutlineProvider = outlineProvider
                outlineProvider = null
                translationZ = -target.elevation
            }
        }

        override fun onAnimationEnd(animation: Animator) {
            target.apply {
                translationZ = 0f
                setClipPath(null)
                outlineProvider = oldOutlineProvider
            }
        }

        override fun onAnimationUpdate(anim: ValueAnimator) {
            path.reset()
            pathProvider.invoke(anim.animatedValue as Float, path)
            target.setClipPath(path)
        }

        fun toAnim(isReversed: Boolean) = (if (isReversed) ValueAnimator.ofFloat(1f, 0f) else ValueAnimator.ofFloat(0f, 1f))
            .also {
                it.addListener(this)
                it.addUpdateListener(this)
            }
    }

    private class ClipSpringAnimBuilder<T>(val target: T, val pathProvider: (Float, Path) -> Unit) : AnimatorListenerAdapter() where T : View, T : ClipPathView {

        private var oldOutlineProvider: ViewOutlineProvider? = null
        val path = Path()
        private val animatorBuilder = SpringAnimationBuilder(target.context)
        private val progressProperty =
            object : FloatProperty<ClipSpringAnimBuilder<T>>("progress") {
                override fun setValue(obj: ClipSpringAnimBuilder<T>, value: Float) {
                    // Don't want to go below 0 or above 1 for progress.
                    val clampedValue = minOf(maxOf(value, 0f), 1f)
                    path.reset()
                    pathProvider.invoke(clampedValue, path)
                    target.setClipPath(path)
                }

                override fun get(obj: ClipSpringAnimBuilder<T>): Float {
                    return 0f
                }
            }

        override fun onAnimationStart(animation: Animator) {
            target.apply {
                oldOutlineProvider = outlineProvider
                outlineProvider = null
                translationZ = -target.elevation
            }
        }

        override fun onAnimationEnd(animation: Animator) {
            target.apply {
                translationZ = 0f
                outlineProvider = oldOutlineProvider
            }
        }

        fun toAnim(isReversed: Boolean): ValueAnimator {
            val mStartValue = if (isReversed) 1f else 0f
            val mEndValue = if (isReversed) 0f else 1f
            pathProvider.invoke(mStartValue, path)
            target.setClipPath(path)
            val animator =
                animatorBuilder
                    .setStiffness(SPRING_STIFFNESS_SHAPE_POSITION)
                    .setDampingRatio(SPRING_DAMPING_SHAPE_POSITION)
                    .setStartValue(mStartValue)
                    .setEndValue(mEndValue)
                    .setMinimumVisibleChange(MIN_VISIBLE_CHANGE_SCALE)
                    .build(this, progressProperty)
            animator.addListener(this)
            return animator
        }
    }

    companion object {
        private const val SPRING_STIFFNESS_SHAPE_POSITION = 380f
        private const val SPRING_DAMPING_SHAPE_POSITION = 0.8f
    }
}
