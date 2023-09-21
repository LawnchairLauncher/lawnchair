/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.animation

import android.graphics.fonts.Font
import android.graphics.fonts.FontVariationAxis
import android.util.LruCache
import android.util.MathUtils
import android.util.MathUtils.abs
import androidx.annotation.VisibleForTesting
import java.lang.Float.max
import java.lang.Float.min

private const val TAG_WGHT = "wght"
private const val TAG_ITAL = "ital"

private const val FONT_WEIGHT_DEFAULT_VALUE = 400f
private const val FONT_WEIGHT_ANIMATION_FRAME_COUNT = 100

private const val FONT_ITALIC_MAX = 1f
private const val FONT_ITALIC_MIN = 0f
private const val FONT_ITALIC_ANIMATION_STEP = 0.1f
private const val FONT_ITALIC_DEFAULT_VALUE = 0f

// Benchmarked via Perfetto, difference between 10 and 50 entries is about 0.3ms in
// frame draw time on a Pixel 6.
@VisibleForTesting const val FONT_CACHE_MAX_ENTRIES = 10

/** Provide interpolation of two fonts by adjusting font variation settings. */
class FontInterpolator {

    /**
     * Cache key for the interpolated font.
     *
     * This class is mutable for recycling.
     */
    private data class InterpKey(var l: Font?, var r: Font?, var progress: Float) {
        fun set(l: Font, r: Font, progress: Float) {
            this.l = l
            this.r = r
            this.progress = progress
        }
    }

    /**
     * Cache key for the font that has variable font.
     *
     * This class is mutable for recycling.
     */
    private data class VarFontKey(
        var sourceId: Int,
        var index: Int,
        val sortedAxes: MutableList<FontVariationAxis>
    ) {
        constructor(
            font: Font,
            axes: List<FontVariationAxis>
        ) : this(
            font.sourceIdentifier,
            font.ttcIndex,
            axes.toMutableList().apply { sortBy { it.tag } }
        )

        fun set(font: Font, axes: List<FontVariationAxis>) {
            sourceId = font.sourceIdentifier
            index = font.ttcIndex
            sortedAxes.clear()
            sortedAxes.addAll(axes)
            sortedAxes.sortBy { it.tag }
        }
    }

    // Font interpolator has two level caches: one for input and one for font with different
    // variation settings. No synchronization is needed since FontInterpolator is not designed to be
    // thread-safe and can be used only on UI thread.
    private val interpCache = LruCache<InterpKey, Font>(FONT_CACHE_MAX_ENTRIES)
    private val verFontCache = LruCache<VarFontKey, Font>(FONT_CACHE_MAX_ENTRIES)

    // Mutable keys for recycling.
    private val tmpInterpKey = InterpKey(null, null, 0f)
    private val tmpVarFontKey = VarFontKey(0, 0, mutableListOf())

    /** Linear interpolate the font variation settings. */
    fun lerp(start: Font, end: Font, progress: Float): Font {
        if (progress == 0f) {
            return start
        } else if (progress == 1f) {
            return end
        }

        val startAxes = start.axes ?: EMPTY_AXES
        val endAxes = end.axes ?: EMPTY_AXES

        if (startAxes.isEmpty() && endAxes.isEmpty()) {
            return start
        }

        // Check we already know the result. This is commonly happens since we draws the different
        // text chunks with the same font.
        tmpInterpKey.set(start, end, progress)
        val cachedFont = interpCache[tmpInterpKey]
        if (cachedFont != null) {
            return cachedFont
        }

        // General axes interpolation takes O(N log N), this is came from sorting the axes. Usually
        // this doesn't take much time since the variation axes is usually up to 5. If we need to
        // support more number of axes, we may want to preprocess the font and store the sorted axes
        // and also pre-fill the missing axes value with default value from 'fvar' table.
        val newAxes =
            lerp(startAxes, endAxes) { tag, startValue, endValue ->
                when (tag) {
                    // TODO: Good to parse 'fvar' table for retrieving default value.
                    TAG_WGHT -> {
                        adaptiveAdjustWeight(
                            MathUtils.lerp(
                                startValue ?: FONT_WEIGHT_DEFAULT_VALUE,
                                endValue ?: FONT_WEIGHT_DEFAULT_VALUE,
                                progress
                            ),
                            startValue ?: FONT_WEIGHT_DEFAULT_VALUE,
                            endValue ?: FONT_WEIGHT_DEFAULT_VALUE,
                        )
                    }
                    TAG_ITAL ->
                        adjustItalic(
                            MathUtils.lerp(
                                startValue ?: FONT_ITALIC_DEFAULT_VALUE,
                                endValue ?: FONT_ITALIC_DEFAULT_VALUE,
                                progress
                            )
                        )
                    else -> {
                        require(startValue != null && endValue != null) {
                            "Unable to interpolate due to unknown default axes value : $tag"
                        }
                        MathUtils.lerp(startValue, endValue, progress)
                    }
                }
            }

        // Check if we already make font for this axes. This is typically happens if the animation
        // happens backward.
        tmpVarFontKey.set(start, newAxes)
        val axesCachedFont = verFontCache[tmpVarFontKey]
        if (axesCachedFont != null) {
            interpCache.put(InterpKey(start, end, progress), axesCachedFont)
            return axesCachedFont
        }

        // This is the first time to make the font for the axes. Build and store it to the cache.
        // Font.Builder#build won't throw IOException since creating fonts from existing fonts will
        // not do any IO work.
        val newFont = Font.Builder(start).setFontVariationSettings(newAxes.toTypedArray()).build()
        interpCache.put(InterpKey(start, end, progress), newFont)
        verFontCache.put(VarFontKey(start, newAxes), newFont)
        return newFont
    }

    private fun lerp(
        start: Array<FontVariationAxis>,
        end: Array<FontVariationAxis>,
        filter: (tag: String, left: Float?, right: Float?) -> Float
    ): List<FontVariationAxis> {
        // Safe to modify result of Font#getAxes since it returns cloned object.
        start.sortBy { axis -> axis.tag }
        end.sortBy { axis -> axis.tag }

        val result = mutableListOf<FontVariationAxis>()
        var i = 0
        var j = 0
        while (i < start.size || j < end.size) {
            val tagA = if (i < start.size) start[i].tag else null
            val tagB = if (j < end.size) end[j].tag else null

            val comp =
                when {
                    tagA == null -> 1
                    tagB == null -> -1
                    else -> tagA.compareTo(tagB)
                }

            val axis =
                when {
                    comp == 0 -> {
                        val v = filter(tagA!!, start[i++].styleValue, end[j++].styleValue)
                        FontVariationAxis(tagA, v)
                    }
                    comp < 0 -> {
                        val v = filter(tagA!!, start[i++].styleValue, null)
                        FontVariationAxis(tagA, v)
                    }
                    else -> { // comp > 0
                        val v = filter(tagB!!, null, end[j++].styleValue)
                        FontVariationAxis(tagB, v)
                    }
                }

            result.add(axis)
        }
        return result
    }

    // For the performance reasons, we animate weight with adaptive step. This helps
    // Cache hit ratio in the Skia glyph cache.
    // The reason we don't use fix step is because the range of weight axis is not normalized,
    // some are from 50 to 100, others are from 0 to 1000, so we cannot give a constant proper step
    private fun adaptiveAdjustWeight(value: Float, start: Float, end: Float): Float {
        val step = max(abs(end - start) / FONT_WEIGHT_ANIMATION_FRAME_COUNT, 1F)
        return coerceInWithStep(value, min(start, end), max(start, end), step)
    }

    // For the performance reasons, we animate italic with FONT_ITALIC_ANIMATION_STEP. This helps
    // Cache hit ratio in the Skia glyph cache.
    private fun adjustItalic(value: Float) =
        coerceInWithStep(value, FONT_ITALIC_MIN, FONT_ITALIC_MAX, FONT_ITALIC_ANIMATION_STEP)

    private fun coerceInWithStep(v: Float, min: Float, max: Float, step: Float) =
        (v.coerceIn(min, max) / step).toInt() * step

    companion object {
        private val EMPTY_AXES = arrayOf<FontVariationAxis>()

        // Returns true if given two font instance can be interpolated.
        fun canInterpolate(start: Font, end: Font) =
            start.ttcIndex == end.ttcIndex && start.sourceIdentifier == end.sourceIdentifier
    }
}
