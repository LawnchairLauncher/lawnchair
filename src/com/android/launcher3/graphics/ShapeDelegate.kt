/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.graphics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit.FILL
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.PathParser
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.SvgPathParser
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.views.ClipPathView

/** Abstract representation of the shape of an icon shape */
interface ShapeDelegate {

    fun getPath(pathSize: Float = DEFAULT_PATH_SIZE) =
        Path().apply { addToPath(this, 0f, 0f, pathSize / 2) }

    fun getPath(bounds: Rect) =
        Path().apply {
            addToPath(
                this,
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                // Radius is half of the average size of the icon
                (bounds.width() + bounds.height()) / 4f,
            )
        }

    fun drawShape(canvas: Canvas, offsetX: Float, offsetY: Float, radius: Float, paint: Paint)

    fun addToPath(path: Path, offsetX: Float, offsetY: Float, radius: Float)

    fun <T> createRevealAnimator(
        target: T,
        startRect: Rect,
        endRect: Rect,
        endRadius: Float,
        isReversed: Boolean,
    ): ValueAnimator where T : View, T : ClipPathView

    class Circle : RoundedSquare(1f) {

        override fun drawShape(
            canvas: Canvas,
            offsetX: Float,
            offsetY: Float,
            radius: Float,
            paint: Paint,
        ) = canvas.drawCircle(radius + offsetX, radius + offsetY, radius, paint)

        override fun addToPath(path: Path, offsetX: Float, offsetY: Float, radius: Float) =
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW)
    }

    /** Rounded square with [radiusRatio] as a ratio of its half edge size */
    @VisibleForTesting
    open class RoundedSquare(val radiusRatio: Float) : ShapeDelegate {

        override fun drawShape(
            canvas: Canvas,
            offsetX: Float,
            offsetY: Float,
            radius: Float,
            paint: Paint,
        ) {
            val cx = radius + offsetX
            val cy = radius + offsetY
            val cr = radius * radiusRatio
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr, paint)
        }

        override fun addToPath(path: Path, offsetX: Float, offsetY: Float, radius: Float) {
            val cx = radius + offsetX
            val cy = radius + offsetY
            val cr = radius * radiusRatio
            path.addRoundRect(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius,
                cr,
                cr,
                Path.Direction.CW,
            )
        }

        override fun <T> createRevealAnimator(
            target: T,
            startRect: Rect,
            endRect: Rect,
            endRadius: Float,
            isReversed: Boolean,
        ): ValueAnimator where T : View, T : ClipPathView {
            val startRadius = (startRect.width() / 2f) * radiusRatio
            return ClipAnimBuilder(target) { progress, path ->
                val radius = (1 - progress) * startRadius + progress * endRadius
                path.addRoundRect(
                    (1 - progress) * startRect.left + progress * endRect.left,
                    (1 - progress) * startRect.top + progress * endRect.top,
                    (1 - progress) * startRect.right + progress * endRect.right,
                    (1 - progress) * startRect.bottom + progress * endRect.bottom,
                    radius,
                    radius,
                    Path.Direction.CW,
                )
            }
                .toAnim(isReversed)
        }

        override fun equals(other: Any?) =
            other is RoundedSquare && other.radiusRatio == radiusRatio

        override fun hashCode() = radiusRatio.hashCode()
    }

    /** Generic shape delegate with pathString in bounds [0, 0, 100, 100] */
    data class GenericPathShape(private val pathString: String) : ShapeDelegate {
        private val poly =
            RoundedPolygon(
                features = SvgPathParser.parseFeatures(pathString),
                centerX = 50f,
                centerY = 50f,
            )
        // This ensures that a valid morph is possible from the provided path
        private val basePath =
            Path().apply {
                Morph(poly, createRoundedRect(0f, 0f, 100f, 100f, 25f)).toPath(0f, this)
            }
        private val tmpPath = Path()
        private val tmpMatrix = Matrix()

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
            matrix.setScale(radius / 50, radius / 50)
            matrix.postTranslate(offsetX, offsetY)
            basePath.transform(matrix, path)
        }

        override fun <T> createRevealAnimator(
            target: T,
            startRect: Rect,
            endRect: Rect,
            endRadius: Float,
            isReversed: Boolean,
        ): ValueAnimator where T : View, T : ClipPathView {
            // End poly is defined as a rectangle starting at top/center so that the
            // transformation has minimum motion
            val morph =
                Morph(
                    start =
                        poly.transformed(
                            Matrix().apply {
                                setRectToRect(
                                    RectF(0f, 0f, DEFAULT_PATH_SIZE, DEFAULT_PATH_SIZE),
                                    RectF(startRect),
                                    FILL,
                                )
                            }
                        ),
                    end =
                        createRoundedRect(
                            left = endRect.left.toFloat(),
                            top = endRect.top.toFloat(),
                            right = endRect.right.toFloat(),
                            bottom = endRect.bottom.toFloat(),
                            cornerR = endRadius,
                        ),
                )

            return ClipAnimBuilder(target, morph::toPath).toAnim(isReversed)
        }
    }

    private class ClipAnimBuilder<T>(val target: T, val pathProvider: (Float, Path) -> Unit) :
        AnimatorListenerAdapter(), AnimatorUpdateListener where T : View, T : ClipPathView {

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

        fun toAnim(isReversed: Boolean) =
            (if (isReversed) ValueAnimator.ofFloat(1f, 0f) else ValueAnimator.ofFloat(0f, 1f))
                .also {
                    it.addListener(this)
                    it.addUpdateListener(this)
                }
    }

    companion object {

        const val TAG = "IconShape"
        const val DEFAULT_PATH_SIZE = 100f
        const val AREA_CALC_SIZE = 1000
        // .1% error margin
        const val AREA_DIFF_THRESHOLD = AREA_CALC_SIZE * AREA_CALC_SIZE / 1000

        /** Returns a function to calculate area diff from [base] */
        @VisibleForTesting
        fun areaDiffCalculator(base: Path): (ShapeDelegate) -> Int {
            val fullRegion = Region(0, 0, AREA_CALC_SIZE, AREA_CALC_SIZE)
            val iconRegion = Region().apply { setPath(base, fullRegion) }

            val shapePath = Path()
            val shapeRegion = Region()
            return fun(shape: ShapeDelegate): Int {
                shapePath.reset()
                shape.addToPath(shapePath, 0f, 0f, AREA_CALC_SIZE / 2f)
                shapeRegion.setPath(shapePath, fullRegion)
                shapeRegion.op(iconRegion, Region.Op.XOR)
                return GraphicsUtils.getArea(shapeRegion)
            }
        }

        fun pickBestShape(shapeStr: String): ShapeDelegate {
            val baseShape =
                if (shapeStr.isNotEmpty()) {
                    PathParser.createPathFromPathData(shapeStr).apply {
                        transform(
                            Matrix().apply {
                                setScale(
                                    AREA_CALC_SIZE / DEFAULT_PATH_SIZE,
                                    AREA_CALC_SIZE / DEFAULT_PATH_SIZE,
                                )
                            }
                        )
                    }
                } else {
                    AdaptiveIconDrawable(null, ColorDrawable(Color.BLACK)).let {
                        it.setBounds(0, 0, AREA_CALC_SIZE, AREA_CALC_SIZE)
                        it.iconMask
                    }
                }
            return pickBestShape(baseShape, shapeStr)
        }

        fun pickBestShape(baseShape: Path, shapeStr: String): ShapeDelegate {
            val calcAreaDiff = areaDiffCalculator(baseShape)

            // Find the shape with minimum area of divergent region.
            var closestShape: ShapeDelegate = Circle()
            var minAreaDiff = calcAreaDiff(closestShape)

            // Try some common rounded rect edges
            for (f in 0..20) {
                val rectShape = RoundedSquare(f.toFloat() / 20)
                val rectArea = calcAreaDiff(rectShape)
                if (rectArea < minAreaDiff) {
                    minAreaDiff = rectArea
                    closestShape = rectShape
                }
            }

            // Use the generic shape only if we have more than .1% error
            if (shapeStr.isNotEmpty() && minAreaDiff > AREA_DIFF_THRESHOLD) {
                try {
                    val generic = GenericPathShape(shapeStr)
                    closestShape = generic
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting mask to generic shape", e)
                }
            }
            return closestShape
        }

        /**
         * Create RoundedRect using RoundedPolygon API. Ensures smoother animation morphing between
         * generic polygon by using [RoundedPolygon.Companion.rectangle] directly.
         */
        fun createRoundedRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            cornerR: Float,
        ) =
            RoundedPolygon.rectangle(
                width = right - left,
                height = bottom - top,
                centerX = (right - left) / 2,
                centerY = (bottom - top) / 2,
                rounding = CornerRounding(cornerR),
            )
    }
}
