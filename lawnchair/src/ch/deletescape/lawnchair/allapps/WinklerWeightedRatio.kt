/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.allapps

import me.xdrop.fuzzywuzzy.ToStringFunction
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import kotlin.math.roundToInt

/**
 * Weighted ratio with higher scores for strings with common prefix like in the Jaro-Winkler algorithm
 */
class WinklerWeightedRatio: WeightedRatio() {

    override fun apply(s1: String?, s2: String?, stringProcessor: ToStringFunction<String>): Int {
        val first = stringProcessor.apply(s1)
        val second = stringProcessor.apply(s2)

        val ratio = super.apply(s1, s2, stringProcessor) / 100.0
        val cl = commonPrefixLength(first, second)
        return ((ratio + SCALING_FACTOR * cl * (1.0 - ratio)) * 100).roundToInt()
    }

    /**
     * Calculates the number of characters from the beginning of the strings that match exactly one-to-one,
     * up to a maximum of four (4) characters.
     * @param first The first string.
     * @param second The second string.
     * @return A number between 0 and 4.
     * @see https://github.com/rrice/java-string-similarity/blob/master/src/main/java/net/ricecode/similarity/JaroWinklerStrategy.java
     */
    private fun commonPrefixLength(first: String, second: String): Int {
        val shorter: String
        val longer: String

        // Determine which string is longer.
        if (first.length > second.length) {
            longer = first.toLowerCase()
            shorter = second.toLowerCase()
        } else {
            longer = second.toLowerCase()
            shorter = first.toLowerCase()
        }

        var result = 0

        // Iterate through the shorter string.
        for (i in 0 until shorter.length) {
            if (shorter[i] != longer[i]) {
                break
            }
            result++
        }

        // Limit the result to 4.
        return if (result > 4) 4 else result
    }


    companion object {
        const val SCALING_FACTOR = .15
    }
}