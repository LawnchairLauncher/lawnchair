/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiPropertyFactory.FloatBiFunction
import com.android.launcher3.util.MultiValueAlpha
import com.android.quickstep.util.RecentsOrientedState
import kotlin.math.max
import kotlin.math.min

/** An icon app menu view which can be used in place of an IconView in overview TaskViews. */
class IconAppChipView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), TaskViewIcon {

    private var iconView: IconView? = null
    private var iconArrowView: ImageView? = null
    private var menuAnchorView: View? = null
    // Two textview so we can ellipsize the collapsed view and crossfade on expand to the full name.
    private var iconTextCollapsedView: TextView? = null
    private var iconTextExpandedView: TextView? = null

    private val backgroundRelativeLtrLocation = Rect()
    private val backgroundAnimationRectEvaluator = RectEvaluator(backgroundRelativeLtrLocation)

    // Menu dimensions
    private val collapsedMenuDefaultWidth: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_width)
    private val expandedMenuDefaultWidth: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_width)
    private val collapsedMenuDefaultHeight =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_height)
    private val expandedMenuDefaultHeight =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_height)
    private val iconMenuMarginTopStart =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_top_start_margin)
    private val menuToChipGap: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_gap)

    // Background dimensions
    private val backgroundMarginTopStart: Int =
        resources.getDimensionPixelSize(
            R.dimen.task_thumbnail_icon_menu_background_margin_top_start
        )

    // Contents dimensions
    private val appNameHorizontalMargin =
        resources.getDimensionPixelSize(
            R.dimen.task_thumbnail_icon_menu_app_name_margin_horizontal_collapsed
        )
    private val arrowMarginEnd =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_arrow_margin)
    private val iconViewMarginStart =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_view_start_margin)
    private val appIconSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_app_icon_collapsed_size)
    private val arrowSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_arrow_size)
    private val iconViewDrawableExpandedSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_app_icon_expanded_size)

    private var animator: AnimatorSet? = null

    private val multiValueAlpha: MultiValueAlpha =
        MultiValueAlpha(this, NUM_ALPHA_CHANNELS).apply { setUpdateVisibility(true) }

    private val viewTranslationX: MultiPropertyFactory<View> =
        MultiPropertyFactory(this, VIEW_TRANSLATE_X, INDEX_COUNT_TRANSLATION, SUM_AGGREGATOR)

    private val viewTranslationY: MultiPropertyFactory<View> =
        MultiPropertyFactory(this, VIEW_TRANSLATE_Y, INDEX_COUNT_TRANSLATION, SUM_AGGREGATOR)

    var maxWidth = Int.MAX_VALUE
        /**
         * Sets the maximum width of this Icon Menu. This is usually used when space is limited for
         * split screen.
         */
        set(value) {
            // Width showing only the app icon and arrow. Max width should not be set to less than
            // this.
            val minMaxWidth = iconViewMarginStart + appIconSize + arrowSize + arrowMarginEnd
            field = max(value, minMaxWidth)
        }

    var status: AppChipStatus = AppChipStatus.Collapsed
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconView = findViewById(R.id.icon_view)
        iconTextCollapsedView = findViewById(R.id.icon_text_collapsed)
        iconTextExpandedView = findViewById(R.id.icon_text_expanded)
        iconArrowView = findViewById(R.id.icon_arrow)
        menuAnchorView = findViewById(R.id.icon_view_menu_anchor)
    }

    override fun setText(text: CharSequence?) {
        iconTextCollapsedView?.text = text
        iconTextExpandedView?.text = text
    }

    override fun getDrawable(): Drawable? = iconView?.drawable

    override fun setDrawable(icon: Drawable?) {
        iconView?.drawable = icon
    }

    override fun setDrawableSize(iconWidth: Int, iconHeight: Int) {
        iconView?.setDrawableSize(iconWidth, iconHeight)
    }

    override fun setIconOrientation(orientationState: RecentsOrientedState, isGridTask: Boolean) {
        val orientationHandler = orientationState.orientationHandler
        // Layout params for anchor view
        val anchorLayoutParams = menuAnchorView!!.layoutParams as LayoutParams
        anchorLayoutParams.topMargin = expandedMenuDefaultHeight + menuToChipGap
        menuAnchorView!!.layoutParams = anchorLayoutParams

        // Layout Params for the Menu View (this)
        val iconMenuParams = layoutParams as LayoutParams
        iconMenuParams.width = expandedMenuDefaultWidth
        iconMenuParams.height = expandedMenuDefaultHeight
        orientationHandler.setIconAppChipMenuParams(
            this,
            iconMenuParams,
            iconMenuMarginTopStart,
            iconMenuMarginTopStart,
        )
        layoutParams = iconMenuParams

        // Layout params for the background
        val collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds()
        backgroundRelativeLtrLocation.set(collapsedBackgroundBounds)
        outlineProvider =
            object : ViewOutlineProvider() {
                val mRtlAppliedOutlineBounds: Rect = Rect()

                override fun getOutline(view: View, outline: Outline) {
                    mRtlAppliedOutlineBounds.set(backgroundRelativeLtrLocation)
                    if (isLayoutRtl) {
                        val width = width
                        mRtlAppliedOutlineBounds.left = width - backgroundRelativeLtrLocation.right
                        mRtlAppliedOutlineBounds.right = width - backgroundRelativeLtrLocation.left
                    }
                    outline.setRoundRect(
                        mRtlAppliedOutlineBounds,
                        mRtlAppliedOutlineBounds.height() / 2f,
                    )
                }
            }

        // Layout Params for the Icon View
        val iconParams = iconView!!.layoutParams as LayoutParams
        val iconMarginStartRelativeToParent = iconViewMarginStart + backgroundMarginTopStart
        orientationHandler.setIconAppChipChildrenParams(iconParams, iconMarginStartRelativeToParent)

        iconView!!.layoutParams = iconParams
        iconView!!.setDrawableSize(appIconSize, appIconSize)

        // Layout Params for the collapsed Icon Text View
        val textMarginStart =
            iconMarginStartRelativeToParent + appIconSize + appNameHorizontalMargin
        val iconTextCollapsedParams = iconTextCollapsedView!!.layoutParams as LayoutParams
        orientationHandler.setIconAppChipChildrenParams(iconTextCollapsedParams, textMarginStart)
        val collapsedTextWidth =
            (collapsedBackgroundBounds.width() -
                iconViewMarginStart -
                appIconSize -
                arrowSize -
                appNameHorizontalMargin -
                arrowMarginEnd)
        iconTextCollapsedParams.width = collapsedTextWidth
        iconTextCollapsedView!!.layoutParams = iconTextCollapsedParams
        iconTextCollapsedView!!.alpha = 1f

        // Layout Params for the expanded Icon Text View
        val iconTextExpandedParams = iconTextExpandedView!!.layoutParams as LayoutParams
        orientationHandler.setIconAppChipChildrenParams(iconTextExpandedParams, textMarginStart)
        iconTextExpandedView!!.layoutParams = iconTextExpandedParams
        iconTextExpandedView!!.alpha = 0f
        iconTextExpandedView!!.setRevealClip(
            true,
            0f,
            appIconSize / 2f,
            collapsedTextWidth.toFloat(),
        )

        // Layout Params for the Icon Arrow View
        val iconArrowParams = iconArrowView!!.layoutParams as LayoutParams
        val arrowMarginStart = collapsedBackgroundBounds.right - arrowMarginEnd - arrowSize
        orientationHandler.setIconAppChipChildrenParams(iconArrowParams, arrowMarginStart)
        iconArrowView!!.pivotY = iconArrowParams.height / 2f
        iconArrowView!!.layoutParams = iconArrowParams

        // This method is called twice sometimes (like when rotating split tasks). It is called
        // once before onMeasure and onLayout, and again after onMeasure but before onLayout with
        // a new width. This happens because we update widths on rotation and on measure of
        // grouped task views. Calling requestLayout() does not guarantee a call to onMeasure if
        // it has just measured, so we explicitly call it here.
        measure(
            MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY),
        )
    }

    override fun setIconColorTint(color: Int, amount: Float) {
        // RecentsView's COLOR_TINT animates between 0 and 0.5f, we want to hide the app chip menu.
        val colorTintAlpha = Utilities.mapToRange(amount, 0f, 0.5f, 1f, 0f, Interpolators.LINEAR)
        multiValueAlpha[INDEX_COLOR_FILTER_ALPHA].value = colorTintAlpha
    }

    override fun setContentAlpha(alpha: Float) {
        multiValueAlpha[INDEX_CONTENT_ALPHA].value = alpha
    }

    override fun setModalAlpha(alpha: Float) {
        multiValueAlpha[INDEX_MODAL_ALPHA].value = alpha
    }

    override fun setFlexSplitAlpha(alpha: Float) {
        multiValueAlpha[INDEX_MINIMUM_RATIO_ALPHA].value = alpha
    }

    override fun getDrawableWidth(): Int = iconView?.drawableWidth ?: 0

    override fun getDrawableHeight(): Int = iconView?.drawableHeight ?: 0

    /** Gets the view split x-axis translation */
    fun getSplitTranslationX(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationX.get(INDEX_SPLIT_TRANSLATION)

    /**
     * Sets the view split x-axis translation
     *
     * @param value x-axis translation
     */
    fun setSplitTranslationX(value: Float) {
        getSplitTranslationX().value = value
    }

    /** Gets the view split y-axis translation */
    fun getSplitTranslationY(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationY[INDEX_SPLIT_TRANSLATION]

    /**
     * Sets the view split y-axis translation
     *
     * @param value y-axis translation
     */
    fun setSplitTranslationY(value: Float) {
        getSplitTranslationY().value = value
    }

    /** Gets the menu x-axis translation for split task */
    fun getMenuTranslationX(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationX[INDEX_MENU_TRANSLATION]

    /** Gets the menu y-axis translation for split task */
    fun getMenuTranslationY(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationY[INDEX_MENU_TRANSLATION]

    internal fun revealAnim(isRevealing: Boolean, animated: Boolean = true) {
        cancelInProgressAnimations()
        val collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds()
        val expandedBackgroundBounds = getExpandedBackgroundLtrBounds()
        val initialBackground = Rect(backgroundRelativeLtrLocation)
        animator = AnimatorSet()

        if (isRevealing) {
            val isRtl = isLayoutRtl
            bringToFront()
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            val expandedTextRevealAnim =
                ViewAnimationUtils.createCircularReveal(
                    iconTextExpandedView,
                    0,
                    iconTextExpandedView!!.height / 2,
                    iconTextCollapsedView!!.width.toFloat(),
                    iconTextExpandedView!!.width.toFloat(),
                )
            // Animate background clipping
            val backgroundAnimator =
                ValueAnimator.ofObject(
                    backgroundAnimationRectEvaluator,
                    initialBackground,
                    expandedBackgroundBounds,
                )
            backgroundAnimator.addUpdateListener { invalidateOutline() }

            val iconViewScaling = iconViewDrawableExpandedSize / appIconSize.toFloat()
            val arrowTranslationX =
                (expandedBackgroundBounds.right - collapsedBackgroundBounds.right).toFloat()
            val iconCenterToTextCollapsed = appIconSize / 2f + appNameHorizontalMargin
            val iconCenterToTextExpanded =
                iconViewDrawableExpandedSize / 2f + appNameHorizontalMargin
            val textTranslationX = iconCenterToTextExpanded - iconCenterToTextCollapsed

            val textTranslationXWithRtl = if (isRtl) -textTranslationX else textTranslationX
            val arrowTranslationWithRtl = if (isRtl) -arrowTranslationX else arrowTranslationX

            animator!!.playTogether(
                expandedTextRevealAnim,
                backgroundAnimator,
                ObjectAnimator.ofFloat(iconView, SCALE_X, iconViewScaling),
                ObjectAnimator.ofFloat(iconView, SCALE_Y, iconViewScaling),
                ObjectAnimator.ofFloat(
                    iconTextCollapsedView,
                    TRANSLATION_X,
                    textTranslationXWithRtl,
                ),
                ObjectAnimator.ofFloat(
                    iconTextExpandedView,
                    TRANSLATION_X,
                    textTranslationXWithRtl,
                ),
                ObjectAnimator.ofFloat(iconTextCollapsedView, ALPHA, 0f),
                ObjectAnimator.ofFloat(iconTextExpandedView, ALPHA, 1f),
                ObjectAnimator.ofFloat(iconArrowView, TRANSLATION_X, arrowTranslationWithRtl),
                ObjectAnimator.ofFloat(iconArrowView, SCALE_Y, -1f),
            )
            animator!!.duration = MENU_BACKGROUND_REVEAL_DURATION.toLong()
            status = AppChipStatus.Expanded
        } else {
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            val expandedTextClipAnim =
                ViewAnimationUtils.createCircularReveal(
                    iconTextExpandedView,
                    0,
                    iconTextExpandedView!!.height / 2,
                    iconTextExpandedView!!.width.toFloat(),
                    iconTextCollapsedView!!.width.toFloat(),
                )

            // Animate background clipping
            val backgroundAnimator =
                ValueAnimator.ofObject(
                    backgroundAnimationRectEvaluator,
                    initialBackground,
                    collapsedBackgroundBounds,
                )
            backgroundAnimator.addUpdateListener { valueAnimator: ValueAnimator? ->
                invalidateOutline()
            }

            animator!!.playTogether(
                expandedTextClipAnim,
                backgroundAnimator,
                ObjectAnimator.ofFloat(iconView, SCALE_PROPERTY, 1f),
                ObjectAnimator.ofFloat(iconTextCollapsedView, TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(iconTextExpandedView, TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(iconTextCollapsedView, ALPHA, 1f),
                ObjectAnimator.ofFloat(iconTextExpandedView, ALPHA, 0f),
                ObjectAnimator.ofFloat(iconArrowView, TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(iconArrowView, SCALE_Y, 1f),
            )
            animator!!.duration = MENU_BACKGROUND_HIDE_DURATION.toLong()
            status = AppChipStatus.Collapsed
        }

        if (!animated) animator!!.duration = 0
        animator!!.interpolator = Interpolators.EMPHASIZED
        animator!!.start()
    }

    private fun getCollapsedBackgroundLtrBounds(): Rect {
        val bounds =
            Rect(0, 0, min(maxWidth, collapsedMenuDefaultWidth), collapsedMenuDefaultHeight)
        bounds.offset(backgroundMarginTopStart, backgroundMarginTopStart)
        return bounds
    }

    private fun getExpandedBackgroundLtrBounds() =
        Rect(0, 0, expandedMenuDefaultWidth, expandedMenuDefaultHeight)

    private fun cancelInProgressAnimations() {
        // We null the `AnimatorSet` because it holds references to the `Animators` which aren't
        // expecting to be mutable and will cause a crash if they are re-used.
        if (animator != null && animator!!.isStarted) {
            animator!!.cancel()
            animator = null
        }
    }

    override fun focusSearch(direction: Int): View? {
        if (mParent == null) return null
        return when (direction) {
            FOCUS_RIGHT,
            FOCUS_DOWN -> mParent.focusSearch(this, View.FOCUS_FORWARD)
            FOCUS_UP,
            FOCUS_LEFT -> mParent.focusSearch(this, View.FOCUS_BACKWARD)
            else -> super.focusSearch(direction)
        }
    }

    fun reset() {
        setText(null)
        setDrawable(null)
    }

    override fun asView(): View = this

    enum class AppChipStatus {
        Expanded,
        Collapsed,
    }

    private companion object {
        private val SUM_AGGREGATOR = FloatBiFunction { a: Float, b: Float -> a + b }

        private const val MENU_BACKGROUND_REVEAL_DURATION = 417
        private const val MENU_BACKGROUND_HIDE_DURATION = 333

        private const val NUM_ALPHA_CHANNELS = 4
        private const val INDEX_CONTENT_ALPHA = 0
        private const val INDEX_COLOR_FILTER_ALPHA = 1
        private const val INDEX_MODAL_ALPHA = 2
        /** Used to hide the app chip for 90:10 flex split. */
        private const val INDEX_MINIMUM_RATIO_ALPHA = 3

        private const val INDEX_SPLIT_TRANSLATION = 0
        private const val INDEX_MENU_TRANSLATION = 1
        private const val INDEX_COUNT_TRANSLATION = 2
    }
}
