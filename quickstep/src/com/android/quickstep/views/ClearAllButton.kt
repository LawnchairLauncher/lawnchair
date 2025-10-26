/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.quickstep.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.FloatProperty
import android.widget.Button
import com.android.launcher3.Flags.enableFocusOutline
import com.android.launcher3.R
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.MultiPropertyDelegate
import com.android.launcher3.util.MultiValueAlpha
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import kotlin.math.abs
import kotlin.math.min

class ClearAllButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Button(context, attrs) {

    private val clearAllButtonAlpha =
        object : MultiValueAlpha(this, Alpha.entries.size) {
            override fun apply(value: Float) {
                super.apply(value)
                isClickable = value >= 1f
            }
        }
    var scrollAlpha by MultiPropertyDelegate(clearAllButtonAlpha, Alpha.SCROLL)
    var contentAlpha by MultiPropertyDelegate(clearAllButtonAlpha, Alpha.CONTENT)
    var visibilityAlpha by MultiPropertyDelegate(clearAllButtonAlpha, Alpha.VISIBILITY)
    var dismissAlpha by MultiPropertyDelegate(clearAllButtonAlpha, Alpha.DISMISS)

    var fullscreenProgress = 1f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyPrimaryTranslation()
        }

    /**
     * Moves ClearAllButton between carousel and 2 row grid.
     *
     * 0 = carousel; 1 = 2 row grid.
     */
    var gridProgress = 1f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyPrimaryTranslation()
        }

    private var normalTranslationPrimary = 0f
    var fullscreenTranslationPrimary = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyPrimaryTranslation()
        }

    var gridTranslationPrimary = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyPrimaryTranslation()
        }

    /** Used to put the button at the middle in the secondary coordinate. */
    var taskAlignmentTranslationY = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applySecondaryTranslation()
        }

    var gridScrollOffset = 0f
    var scrollOffsetPrimary = 0f

    private var sidePadding = 0
    var borderEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            focusBorderAnimator?.setBorderVisibility(visible = field && isFocused, animated = true)
        }

    private val focusBorderAnimator: BorderAnimator? =
        if (enableFocusOutline())
            createSimpleBorderAnimator(
                context.resources.getDimensionPixelSize(R.dimen.recents_clear_all_outline_radius),
                context.resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_border_width),
                this::getBorderBounds,
                this,
                context
                    .obtainStyledAttributes(attrs, R.styleable.ClearAllButton)
                    .getColor(
                        R.styleable.ClearAllButton_focusBorderColor,
                        BorderAnimator.DEFAULT_BORDER_COLOR,
                    ),
            )
        else null

    private fun getBorderBounds(bounds: Rect) {
        bounds.set(0, 0, width, height)
        val outlinePadding =
            context.resources.getDimensionPixelSize(R.dimen.recents_clear_all_outline_padding)
        // Make the value negative to form a padding between button and outline
        bounds.inset(-outlinePadding, -outlinePadding)
    }

    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (borderEnabled) {
            focusBorderAnimator?.setBorderVisibility(gainFocus, /* animated= */ true)
        }
    }

    override fun draw(canvas: Canvas) {
        focusBorderAnimator?.drawBorder(canvas)
        super.draw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        sidePadding =
            recentsView?.let { it.pagedOrientationHandler?.getClearAllSidePadding(it, isLayoutRtl) }
                ?: 0
    }

    private val recentsView: RecentsView<*, *>?
        get() = parent as? RecentsView<*, *>?

    override fun hasOverlappingRendering() = false

    fun onRecentsViewScroll(scroll: Int, gridEnabled: Boolean) {
        val recentsView = recentsView ?: return

        val orientationSize =
            recentsView.pagedOrientationHandler.getPrimaryValue(width, height).toFloat()
        if (orientationSize == 0f) {
            return
        }

        val clearAllScroll = recentsView.clearAllScroll
        val adjustedScrollFromEdge = abs((scroll - clearAllScroll)).toFloat()
        val shift = min(adjustedScrollFromEdge, orientationSize)
        normalTranslationPrimary = if (isLayoutRtl) -shift else shift
        if (!gridEnabled) {
            normalTranslationPrimary += sidePadding.toFloat()
        }
        applyPrimaryTranslation()
        applySecondaryTranslation()
        var clearAllSpacing = recentsView.pageSpacing + recentsView.clearAllExtraPageSpacing
        clearAllSpacing = if (isLayoutRtl) -clearAllSpacing else clearAllSpacing
        scrollAlpha =
            ((clearAllScroll + clearAllSpacing - scroll) / clearAllSpacing.toFloat()).coerceAtLeast(
                0f
            )
    }

    fun getScrollAdjustment(fullscreenEnabled: Boolean, gridEnabled: Boolean): Float {
        var scrollAdjustment = 0f
        if (fullscreenEnabled) {
            scrollAdjustment += fullscreenTranslationPrimary
        }
        if (gridEnabled) {
            scrollAdjustment += gridTranslationPrimary + gridScrollOffset
        }
        scrollAdjustment += scrollOffsetPrimary
        return scrollAdjustment
    }

    fun getOffsetAdjustment(fullscreenEnabled: Boolean, gridEnabled: Boolean) =
        getScrollAdjustment(fullscreenEnabled, gridEnabled)

    private fun applyPrimaryTranslation() {
        val recentsView = recentsView ?: return
        val orientationHandler = recentsView.pagedOrientationHandler
        orientationHandler.primaryViewTranslate.set(
            this,
            (orientationHandler.getPrimaryValue(0f, taskAlignmentTranslationY) +
                normalTranslationPrimary +
                getFullscreenTrans(fullscreenTranslationPrimary) +
                getGridTrans(gridTranslationPrimary)),
        )
    }

    private fun applySecondaryTranslation() {
        val recentsView = recentsView ?: return
        val orientationHandler = recentsView.pagedOrientationHandler
        orientationHandler.secondaryViewTranslate.set(
            this,
            orientationHandler.getSecondaryValue(0f, taskAlignmentTranslationY),
        )
    }

    private fun getFullscreenTrans(endTranslation: Float) =
        if (fullscreenProgress > 0) endTranslation else 0f

    private fun getGridTrans(endTranslation: Float) = if (gridProgress > 0) endTranslation else 0f

    companion object {
        private enum class Alpha {
            SCROLL,
            CONTENT,
            VISIBILITY,
            DISMISS,
        }

        @JvmField
        val VISIBILITY_ALPHA: FloatProperty<ClearAllButton> =
            KFloatProperty(ClearAllButton::visibilityAlpha)

        @JvmField
        val DISMISS_ALPHA: FloatProperty<ClearAllButton> =
            KFloatProperty(ClearAllButton::dismissAlpha)
    }
}
