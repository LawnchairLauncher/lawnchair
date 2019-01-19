/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.colors

import android.support.annotation.ColorInt
import android.support.v4.graphics.ColorUtils
import kotlin.math.max

class ColorPalette private constructor(@ColorInt color: Int, size: Int) {

    private val colorHSL = FloatArray(3).apply {
        ColorUtils.colorToHSL(color, this)
        // Workaround for default colors being too bright
        this[1] = max(0f, this[1] - 0.03f)
        this[2] = max(0f, this[2] - 0.03f)
    }
    private val fraction = 360f / size
    private val colors = IntArray(size).apply {
        for (i in (0 until size)) {
            with(colorHSL.copyOf()) {
                this[0] += fraction * i
                if (this[0] > 360) this[0] -= 360f
                this@apply[(size - 1) - i] = ColorUtils.HSLToColor(this)
            }
        }
    }

    operator fun get(index: Int, shuffled: Boolean = false) = colors[if (shuffled) {
        getShuffledIndex(index)
    } else {
        index
    }]

    companion object {
        private val SHUFFLE_PRESET = intArrayOf(0, 10, 6, 1, 9, 2, 8, 5, 7, 4, 3)
        private val cache = mutableMapOf<Pair<Int, Int>, ColorPalette>()

        @JvmStatic
        fun getPalette(@ColorInt color: Int, size: Int) = cache.getOrPut(Pair(color, size)) {
            ColorPalette(color, size)
        }

        private fun getShuffledIndex(i: Int): Int {
            var idx = i
            idx -= SHUFFLE_PRESET.size * (idx / SHUFFLE_PRESET.size)
            return SHUFFLE_PRESET[idx]
        }
    }
}