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
import android.util.Log
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.android.app.animation.Interpolators

typealias GlyphCallback = (TextAnimator.PositionedGlyph, Float) -> Unit

interface TypefaceVariantCache {
    val fontCache: FontCache
    val animationFrameCount: Int

    fun getTypefaceForVariant(fvar: String?): Typeface?

    companion object {
        @JvmStatic
        fun createVariantTypeface(baseTypeface: Typeface, fVar: String?): Typeface {
            if (fVar.isNullOrEmpty()) {
                return baseTypeface
            }

            val axes =
                FontVariationAxis.fromFontVariationSettings(fVar)?.toMutableList()
                    ?: mutableListOf()
            axes.removeIf { !baseTypeface.isSupportedAxes(it.getOpenTypeTagValue()) }

            if (axes.isEmpty()) {
                return baseTypeface
            } else {
                return Typeface.createFromTypefaceWithVariation(baseTypeface, axes)
            }
        }
    }
}

class TypefaceVariantCacheImpl(var baseTypeface: Typeface, override val animationFrameCount: Int) :
    TypefaceVariantCache {
    private val cache = LruCache<String, Typeface>(TYPEFACE_CACHE_MAX_ENTRIES)
    override val fontCache = FontCacheImpl(animationFrameCount)

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

    companion object {
        private const val TYPEFACE_CACHE_MAX_ENTRIES = 5
    }
}

interface TextAnimatorListener : TextInterpolatorListener {
    fun onInvalidate() {}
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
    private val typefaceCache: TypefaceVariantCache,
    private val listener: TextAnimatorListener? = null,
) {
    var textInterpolator = TextInterpolator(layout, typefaceCache, listener)
    @VisibleForTesting var createAnimator: () -> ValueAnimator = { ValueAnimator.ofFloat(1f) }

    var animator: ValueAnimator? = null

    val progress: Float
        get() = textInterpolator.progress

    val linearProgress: Float
        get() = textInterpolator.linearProgress

    val fontVariationUtils = FontVariationUtils()

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

    fun updateLayout(layout: Layout, textSize: Float = -1f) {
        textInterpolator.layout = layout

        if (textSize >= 0) {
            textInterpolator.targetPaint.textSize = textSize
            textInterpolator.basePaint.textSize = textSize
            textInterpolator.onTargetPaintModified()
            textInterpolator.onBasePaintModified()
        }
    }

    val isRunning: Boolean
        get() = animator?.isRunning ?: false

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
     * ```
     * Characters :    f      i      n      .      _      終     わ     り
     * Code Points: \u0066 \u0069 \u006E \u002E \u0020 \u7D42 \u308F \u308A
     * Font Runs  : <-- Roboto-Regular.ttf          --><-- NotoSans-CJK.otf -->
     *                  runStart = 0, runLength = 5        runStart = 5, runLength = 3
     * Glyph IDs  :      194        48     7      8     4367   1039   1002
     * Glyph Index:       0          1     2      3       0      1      2
     * ```
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

    /** Style spec to use when rendering the font */
    data class Style(
        val fVar: String? = null,
        val textSize: Float? = null,
        val color: Int? = null,
        val strokeWidth: Float? = null,
    ) {
        fun withUpdatedFVar(
            fontVariationUtils: FontVariationUtils,
            weight: Int = -1,
            width: Int = -1,
            opticalSize: Int = -1,
            roundness: Int = -1,
        ): Style {
            return this.copy(
                fVar =
                    fontVariationUtils.updateFontVariation(
                        weight = weight,
                        width = width,
                        opticalSize = opticalSize,
                        roundness = roundness,
                    )
            )
        }
    }

    /** Animation Spec for use when style changes should be animated */
    data class Animation(
        val animate: Boolean = true,
        val startDelay: Long = 0,
        val duration: Long = DEFAULT_ANIMATION_DURATION,
        val interpolator: TimeInterpolator = Interpolators.LINEAR,
        val onAnimationEnd: Runnable? = null,
    ) {
        fun configureAnimator(animator: Animator) {
            animator.startDelay = startDelay
            animator.duration = duration
            animator.interpolator = interpolator
            if (onAnimationEnd != null) {
                animator.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onAnimationEnd.run()
                        }
                    }
                )
            }
        }

        companion object {
            val DISABLED = Animation(animate = false)
        }
    }

    /** Sets the text style, optionally with animation */
    fun setTextStyle(style: Style, animation: Animation = Animation.DISABLED) {
        animator?.cancel()
        setTextStyleInternal(style, rebase = animation.animate)

        if (animation.animate) {
            animator = buildAnimator(animation).apply { start() }
        } else {
            textInterpolator.progress = 1f
            textInterpolator.linearProgress = 1f
            textInterpolator.rebase()
            listener?.onInvalidate()
        }
    }

    /** Builds a ValueAnimator from the specified animation parameters */
    private fun buildAnimator(animation: Animation): ValueAnimator {
        return createAnimator().apply {
            duration = DEFAULT_ANIMATION_DURATION
            animation.configureAnimator(this)

            addUpdateListener {
                textInterpolator.progress = it.animatedValue as Float
                textInterpolator.linearProgress = it.currentPlayTime / it.duration.toFloat()
                listener?.onInvalidate()
            }

            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) = textInterpolator.rebase()

                    override fun onAnimationCancel(animator: Animator) = textInterpolator.rebase()
                }
            )
        }
    }

    private fun setTextStyleInternal(
        style: Style,
        rebase: Boolean,
        updateLayoutOnFailure: Boolean = true,
    ) {
        try {
            if (rebase) textInterpolator.rebase()
            style.color?.let { textInterpolator.targetPaint.color = it }
            style.textSize?.let { textInterpolator.targetPaint.textSize = it }
            style.strokeWidth?.let { textInterpolator.targetPaint.strokeWidth = it }
            style.fVar?.let {
                textInterpolator.targetPaint.typeface = typefaceCache.getTypefaceForVariant(it)
            }
            textInterpolator.onTargetPaintModified()
        } catch (ex: IllegalArgumentException) {
            if (updateLayoutOnFailure) {
                Log.e(
                    TAG,
                    "setTextStyleInternal: Exception caught but retrying. This is usually" +
                        " due to the layout having changed unexpectedly without being notified.",
                    ex,
                )

                updateLayout(textInterpolator.layout)
                setTextStyleInternal(style, rebase, updateLayoutOnFailure = false)
            } else {
                throw ex
            }
        }
    }

    companion object {
        private val TAG = TextAnimator::class.simpleName!!
        const val DEFAULT_ANIMATION_DURATION = 300L
    }
}
