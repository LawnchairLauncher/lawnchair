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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontVariationAxis
import android.text.Layout
import android.util.LruCache
import kotlin.math.roundToInt
import android.util.Log

private const val DEFAULT_ANIMATION_DURATION: Long = 300
private const val TYPEFACE_CACHE_MAX_ENTRIES = 5

typealias GlyphCallback = (TextAnimator.PositionedGlyph, Float) -> Unit

interface TypefaceVariantCache {
    fun getTypefaceForVariant(fvar: String?): Typeface?

    companion object {
        fun createVariantTypeface(baseTypeface: Typeface, fVar: String?): Typeface {
            if (fVar.isNullOrEmpty()) {
                return baseTypeface
            }

            val axes = FontVariationAxis.fromFontVariationSettings(fVar)
                ?.toMutableList()
                ?: mutableListOf()
            axes.removeIf { !baseTypeface.isSupportedAxes(it.getOpenTypeTagValue()) }
            if (axes.isEmpty()) {
                return baseTypeface
            }
            return Typeface.createFromTypefaceWithVariation(baseTypeface, axes)
        }
    }
}

class TypefaceVariantCacheImpl(
    var baseTypeface: Typeface,
) : TypefaceVariantCache {
    private val cache = LruCache<String, Typeface>(TYPEFACE_CACHE_MAX_ENTRIES)
    override fun getTypefaceForVariant(fvar: String?): Typeface? {
        if (fvar == null) {
            return baseTypeface
        }
        cache.get(fvar)?.let {
            return it
        }

        return TypefaceVariantCache.createVariantTypeface(baseTypeface, fvar).also {
            cache.put(fvar, it)
        }
    }
}

/**
 * This class provides text animation between two styles.
 *
 * Currently this class can provide text style animation for text weight and text size. For example
 * the simple view that draws text with animating text size is like as follows:
 * <pre> <code>
 * ```
 *     class SimpleTextAnimation : View {
 *         @JvmOverloads constructor(...)
 *
 *         private val layout: Layout = ... // Text layout, e.g. StaticLayout.
 *
 *         // TextAnimator tells us when needs to be invalidate.
 *         private val animator = TextAnimator(layout) { invalidate() }
 *
 *         override fun onDraw(canvas: Canvas) = animator.draw(canvas)
 *
 *         // Change the text size with animation.
 *         fun setTextSize(sizePx: Float, animate: Boolean) {
 *             animator.setTextStyle("" /* unchanged fvar... */, sizePx, animate)
 *         }
 *     }
 * ```
 * </code> </pre>
 */
class TextAnimator(
    layout: Layout,
    numberOfAnimationSteps: Int? = null, // Only do this number of discrete animation steps.
    private val invalidateCallback: () -> Unit,
) {
    var typefaceCache: TypefaceVariantCache = TypefaceVariantCacheImpl(layout.paint.typeface)
        get() = field
        set(value) {
            field = value
            textInterpolator.typefaceCache = value
        }

    // Following two members are for mutable for testing purposes.
    public var textInterpolator: TextInterpolator =
        TextInterpolator(layout, typefaceCache, numberOfAnimationSteps)
    public var animator: ValueAnimator =
        ValueAnimator.ofFloat(1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
            addUpdateListener {
                textInterpolator.progress =
                    calculateProgress(it.animatedValue as Float, numberOfAnimationSteps)
                invalidateCallback()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = textInterpolator.rebase()
                    override fun onAnimationCancel(animation: Animator) = textInterpolator.rebase()
                }
            )
        }

    private fun calculateProgress(animProgress: Float, numberOfAnimationSteps: Int?): Float {
        if (numberOfAnimationSteps != null) {
            // This clamps the progress to the nearest value of "numberOfAnimationSteps"
            // discrete values between 0 and 1f.
            return (animProgress * numberOfAnimationSteps).roundToInt() /
                numberOfAnimationSteps.toFloat()
        }

        return animProgress
    }

    sealed class PositionedGlyph {
        /** Mutable X coordinate of the glyph position relative from drawing offset. */
        var x: Float = 0f

        /** Mutable Y coordinate of the glyph position relative from the baseline. */
        var y: Float = 0f

        /** The current line of text being drawn, in a multi-line TextView. */
        var lineNo: Int = 0

        /** Mutable text size of the glyph in pixels. */
        var textSize: Float = 0f

        /** Mutable color of the glyph. */
        var color: Int = 0

        /** Immutable character offset in the text that the current font run start. */
        abstract var runStart: Int
            protected set

        /** Immutable run length of the font run. */
        abstract var runLength: Int
            protected set

        /** Immutable glyph index of the font run. */
        abstract var glyphIndex: Int
            protected set

        /** Immutable font instance for this font run. */
        abstract var font: Font
            protected set

        /** Immutable glyph ID for this glyph. */
        abstract var glyphId: Int
            protected set
    }

    private val fontVariationUtils = FontVariationUtils()

    fun updateLayout(layout: Layout, textSize: Float = -1f) {
        textInterpolator.layout = layout

        if (textSize >= 0) {
            textInterpolator.targetPaint.textSize = textSize
            textInterpolator.basePaint.textSize = textSize
            textInterpolator.onTargetPaintModified()
            textInterpolator.onBasePaintModified()
        }
    }

    fun isRunning(): Boolean {
        return animator.isRunning
    }

    /**
     * GlyphFilter applied just before drawing to canvas for tweaking positions and text size.
     *
     * This callback is called for each glyphs just before drawing the glyphs. This function will be
     * called with the intrinsic position, size, color, glyph ID and font instance. You can mutate
     * the position, size and color for tweaking animations. Do not keep the reference of passed
     * glyph object. The interpolator reuses that object for avoiding object allocations.
     *
     * Details: The text is drawn with font run units. The font run is a text segment that draws
     * with the same font. The {@code runStart} and {@code runLimit} is a range of the font run in
     * the text that current glyph is in. Once the font run is determined, the system will convert
     * characters into glyph IDs. The {@code glyphId} is the glyph identifier in the font and {@code
     * glyphIndex} is the offset of the converted glyph array. Please note that the {@code
     * glyphIndex} is not a character index, because the character will not be converted to glyph
     * one-by-one. If there are ligatures including emoji sequence, etc, the glyph ID may be
     * composed from multiple characters.
     *
     * Here is an example of font runs: "fin. 終わり"
     *
     * Characters :    f      i      n      .      _      終     わ     り
     * Code Points: \u0066 \u0069 \u006E \u002E \u0020 \u7D42 \u308F \u308A
     * Font Runs  : <-- Roboto-Regular.ttf          --><-- NotoSans-CJK.otf -->
     *                  runStart = 0, runLength = 5        runStart = 5, runLength = 3
     * Glyph IDs  :      194        48     7      8     4367   1039   1002
     * Glyph Index:       0          1     2      3       0      1      2
     *
     * In this example, the "fi" is converted into ligature form, thus the single glyph ID is
     * assigned for two characters, f and i.
     *
     * Example:
     * ```
     * private val glyphFilter: GlyphCallback = {　glyph, progress ->
     *     val index = glyph.runStart
     *     val i = glyph.glyphIndex
     *     val moveAmount = 1.3f
     *     val sign = (-1 + 2 * ((i + index) % 2))
     *     val turnProgress = if (progress < .5f) progress / 0.5f else (1.0f - progress) / 0.5f
     *
     *     // You can modify (x, y) coordinates, textSize and color during animation.
     *     glyph.textSize += glyph.textSize * sign * moveAmount * turnProgress
     *     glyph.y += glyph.y * sign * moveAmount * turnProgress
     *     glyph.x += glyph.x * sign * moveAmount * turnProgress
     * }
     * ```
     */
    var glyphFilter: GlyphCallback?
        get() = textInterpolator.glyphFilter
        set(value) {
            textInterpolator.glyphFilter = value
        }

    fun draw(c: Canvas) = textInterpolator.draw(c)

    /**
     * Set text style with animation.
     *
     * By passing -1 to weight, the view preserve the current weight.
     * By passing -1 to textSize, the view preserve the current text size.
     * Bu passing -1 to duration, the default text animation, 1000ms, is used.
     * By passing false to animate, the text will be updated without animation.
     *
     * @param fvar an optional text fontVariationSettings.
     * @param textSize an optional font size.
     * @param colors an optional colors array that must be the same size as numLines passed to
     *               the TextInterpolator
     * @param strokeWidth an optional paint stroke width
     * @param animate an optional boolean indicating true for showing style transition as animation,
     *                false for immediate style transition. True by default.
     * @param duration an optional animation duration in milliseconds. This is ignored if animate is
     *                 false.
     * @param interpolator an optional time interpolator. If null is passed, last set interpolator
     *                     will be used. This is ignored if animate is false.
     */
    fun setTextStyle(
        fvar: String? = "",
        textSize: Float = -1f,
        color: Int? = null,
        strokeWidth: Float = -1f,
        animate: Boolean = true,
        duration: Long = -1L,
        interpolator: TimeInterpolator? = null,
        delay: Long = 0,
        onAnimationEnd: Runnable? = null,
    ) = setTextStyleInternal(fvar, textSize, color, strokeWidth, animate, duration,
        interpolator, delay, onAnimationEnd, updateLayoutOnFailure = true)

    private fun setTextStyleInternal(
        fvar: String?,
        textSize: Float,
        color: Int?,
        strokeWidth: Float,
        animate: Boolean,
        duration: Long,
        interpolator: TimeInterpolator?,
        delay: Long,
        onAnimationEnd: Runnable?,
        updateLayoutOnFailure: Boolean,
    ) {
        try {
            if (animate) {
                animator.cancel()
                textInterpolator.rebase()
            }

            if (textSize >= 0) {
                textInterpolator.targetPaint.textSize = textSize
            }
            if (!fvar.isNullOrBlank()) {
                textInterpolator.targetPaint.typeface = typefaceCache.getTypefaceForVariant(fvar)
            }
            if (color != null) {
                textInterpolator.targetPaint.color = color
            }
            if (strokeWidth >= 0F) {
                textInterpolator.targetPaint.strokeWidth = strokeWidth
            }
            textInterpolator.onTargetPaintModified()

            if (animate) {
                animator.startDelay = delay
                animator.duration =
                    if (duration == -1L) {
                        DEFAULT_ANIMATION_DURATION
                    } else {
                        duration
                    }
                interpolator?.let { animator.interpolator = it }
                if (onAnimationEnd != null) {
                    val listener = object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onAnimationEnd.run()
                            animator.removeListener(this)
                        }
                        override fun onAnimationCancel(animation: Animator) {
                            animator.removeListener(this)
                        }
                    }
                    animator.addListener(listener)
                }
                animator.start()
            } else {
                // No animation is requested, thus set base and target state to the same state.
                textInterpolator.progress = 1f
                textInterpolator.rebase()
                invalidateCallback()
            }
        } catch (ex: IllegalArgumentException) {
            if (updateLayoutOnFailure) {
                Log.e(TAG, "setTextStyleInternal: Exception caught but retrying. This is usually" +
                    " due to the layout having changed unexpectedly without being notified.", ex)
                updateLayout(textInterpolator.layout)
                setTextStyleInternal(fvar, textSize, color, strokeWidth, animate, duration,
                    interpolator, delay, onAnimationEnd, updateLayoutOnFailure = false)
            } else {
                throw ex
            }
        }
    }

    /**
     * Set text style with animation. Similar as
     * fun setTextStyle(
     *      fvar: String? = "",
     *      textSize: Float = -1f,
     *      color: Int? = null,
     *      strokeWidth: Float = -1f,
     *      animate: Boolean = true,
     *      duration: Long = -1L,
     *      interpolator: TimeInterpolator? = null,
     *      delay: Long = 0,
     *      onAnimationEnd: Runnable? = null
     * )
     *
     * @param weight an optional style value for `wght` in fontVariationSettings.
     * @param width an optional style value for `wdth` in fontVariationSettings.
     * @param opticalSize an optional style value for `opsz` in fontVariationSettings.
     * @param roundness an optional style value for `ROND` in fontVariationSettings.
     */
    fun setTextStyle(
        weight: Int = -1,
        width: Int = -1,
        opticalSize: Int = -1,
        roundness: Int = -1,
        textSize: Float = -1f,
        color: Int? = null,
        strokeWidth: Float = -1f,
        animate: Boolean = true,
        duration: Long = -1L,
        interpolator: TimeInterpolator? = null,
        delay: Long = 0,
        onAnimationEnd: Runnable? = null
    ) = setTextStyleInternal(
            fvar = fontVariationUtils.updateFontVariation(
                weight = weight,
                width = width,
                opticalSize = opticalSize,
                roundness = roundness,
            ),
            textSize = textSize,
            color = color,
            strokeWidth = strokeWidth,
            animate = animate,
            duration = duration,
            interpolator = interpolator,
            delay = delay,
            onAnimationEnd = onAnimationEnd,
            updateLayoutOnFailure = true,
        )

    companion object {
        private val TAG = TextAnimator::class.simpleName!!
    }
}
