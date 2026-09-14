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

import android.graphics.Path
import android.graphics.Region
import com.android.launcher3.graphics.ShapeDelegate
import kotlin.math.sqrt

/**
 * The rounded rectangle that best stands in for an icon shape.
 *
 * Glass panes are drawn as rounded rectangles, so anything that has to line up
 * with an icon needs that shape expressed as a single radius. Reading one off
 * the shape's own definition does not work: a squircle states its corner as
 * filling the whole quadrant, the same as a circle does, and differs only in how
 * the curve between the two edges bulges. Taken at face value it comes out a
 * circle -- which is exactly what a folder set to squircle used to close into.
 *
 * So the shape is measured rather than asked. The corner is where a shape
 * differs from its bounding box, and the deepest part of that difference is on
 * the diagonal: for a rounded rectangle of radius R the gap there is
 * `R * (sqrt(2) - 1)`, whatever else the shape does elsewhere. Measuring the gap
 * and inverting that gives the radius that matches, for a circle, a rounded
 * square, a squircle, or a shape somebody drew by hand.
 */
object ShapeCorner {

    private const val DIAGONAL_GAP_PER_RADIUS = 0.41421356f // sqrt(2) - 1
    private val ROOT_TWO = sqrt(2f)

    private val path = Path()
    private val region = Region()
    private val clip = Region()

    private var cachedShape: ShapeDelegate? = null
    private var cachedSize = 0
    private var cachedRadius = 0f

    /**
     * The matching corner radius for [shape] drawn at [size] pixels square.
     *
     * Cached on the shape and size, because this walks the diagonal a pixel at a
     * time and its callers ask once per frame.
     */
    @JvmStatic
    fun radiusFor(shape: ShapeDelegate, size: Int): Float {
        if (size <= 0) return 0f
        if (shape === cachedShape && size == cachedSize) return cachedRadius

        val half = size / 2f
        val radius = runCatching { measure(shape, size, half) }.getOrDefault(half)
        cachedShape = shape
        cachedSize = size
        cachedRadius = radius
        return radius
    }

    private fun measure(shape: ShapeDelegate, size: Int, half: Float): Float {
        path.reset()
        shape.addToPath(path, 0f, 0f, half)
        clip.set(0, 0, size, size)
        if (!region.setPath(path, clip)) return half

        // From the bounding box's corner, inward along the diagonal, until the
        // first point that is actually part of the shape.
        val maxSteps = (half * ROOT_TWO).toInt()
        var step = 0
        while (step < maxSteps) {
            val inset = step / ROOT_TWO
            val x = (size - 1 - inset).toInt()
            val y = (size - 1 - inset).toInt()
            if (x < 0 || y < 0) break
            if (region.contains(x, y)) break
            step++
        }
        return (step / DIAGONAL_GAP_PER_RADIUS).coerceIn(0f, half)
    }
}
