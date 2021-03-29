/*
 *     Copyright (C) 2021 Lawnchair Team.
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

package com.android.launcher3.allapps.search

import me.xdrop.fuzzywuzzy.ToStringFunction
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio
import kotlin.math.roundToInt

/**
 * Weighted ratio with higher scores for strings with common prefix like in the Jaro-Winkler algorithm
 */
class WinklerWeightedRatio : WeightedRatio() {

    override fun apply(s1: String?, s2: String?, stringProcessor: ToStringFunction<String>): Int {
        val first = stringProcessor.apply(s1)
        val second = stringProcessor.apply(s2)

        val ratio = super.apply(s1, s2, stringProcessor) / 100.0

        val commonPrefix = first.commonPrefixWith(second, true)
        val commonPrefixLen = commonPrefix.length.coerceAtMost(4)
        return ((ratio + SCALING_FACTOR * commonPrefixLen * (1.0 - ratio)) * 100).roundToInt()
    }

    companion object {
        const val SCALING_FACTOR = .15
    }
} 