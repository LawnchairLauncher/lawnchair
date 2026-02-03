/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles.flyout

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.ArgbEvaluator
import com.android.launcher3.R
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.wm.shell.shared.TypefaceUtils
import com.android.wm.shell.shared.TypefaceUtils.Companion.setTypeface
import kotlin.math.min

/** The flyout view used to notify the user of a new bubble notification. */
class BubbleBarFlyoutView(
    context: Context,
    private val positioner: BubbleBarFlyoutPositioner,
    scheduler: FlyoutScheduler? = null,
) : ConstraintLayout(context) {

    companion object {
        // the rate multiple for the background color animation relative to the morph animation.
        const val BACKGROUND_COLOR_CHANGE_RATE = 5
        // the minimum progress of the expansion animation before the content starts fading in.
        private const val MIN_EXPANSION_PROGRESS_FOR_CONTENT_ALPHA = 0.75f

        private const val TEXT_ROW_HEIGHT_SP = 20
        private const val MAX_ROWS_COUNT = 3

        /** Returns the maximum possible height of the flyout view. */
        fun getMaximumViewHeight(context: Context): Int {
            val verticalPaddings = getFlyoutPadding(context) * 2
            val textSizeSp = TEXT_ROW_HEIGHT_SP * MAX_ROWS_COUNT
            val textSizePx = textSizeSp * Resources.getSystem().displayMetrics.scaledDensity
            val triangleHeight =
                context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_height)
            return verticalPaddings + textSizePx.toInt() + triangleHeight
        }

        private fun getFlyoutPadding(context: Context) =
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_padding)
    }

    private val scheduler: FlyoutScheduler = scheduler ?: HandlerScheduler(this)
    private val title: TextView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_title) }

    private val icon: ImageView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_icon) }

    private val message: TextView by
        lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.bubble_flyout_text) }

    private val flyoutPadding by lazy(LazyThreadSafetyMode.NONE) { getFlyoutPadding(context) }

    private val triangleHeight by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_height)
        }

    private val triangleOverlap by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(
                R.dimen.bubblebar_flyout_triangle_overlap_amount
            )
        }

    private val triangleWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_width)
        }

    private val triangleRadius by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_triangle_radius)
        }

    private val minFlyoutWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_min_width)
        }

    private val maxFlyoutWidth by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_max_width)
        }

    private val flyoutElevation by
        lazy(LazyThreadSafetyMode.NONE) {
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_elevation).toFloat()
        }

    /** The bounds of the background rect. */
    private val backgroundRect = RectF()
    private val cornerRadius: Float
    private val triangle: Path = Path()
    private val triangleOutline = Outline()
    private var backgroundColor = Color.BLACK
    /** Represents the progress of the expansion animation. 0 when collapsed. 1 when expanded. */
    private var expansionProgress = 0f
    /** Translation x-y values to move the flyout to its collapsed position. */
    private var translationToCollapsedPosition = PointF(0f, 0f)
    /** The size of the flyout when it's collapsed. */
    private var collapsedSize = 0f
    /** The corner radius of the flyout when it's collapsed. */
    private var collapsedCornerRadius = 0f
    /** The color of the flyout when collapsed. */
    private var collapsedColor = 0
    /** The elevation of the flyout when collapsed. */
    private var collapsedElevation = 0f
    /** The minimum progress of the expansion animation before the triangle is made visible. */
    private var minExpansionProgressForTriangle = 0f

    /** The corner radius of the background according to the progress of the animation. */
    private val currentCornerRadius
        get() = collapsedCornerRadius + (cornerRadius - collapsedCornerRadius) * expansionProgress

    /** Translation X of the background. */
    private val backgroundRectTx
        get() = translationToCollapsedPosition.x * (1 - expansionProgress)

    /** Translation Y of the background. */
    private val backgroundRectTy
        get() = translationToCollapsedPosition.y * (1 - expansionProgress)

    /**
     * The paint used to draw the background, whose color changes as the flyout transitions to the
     * tinted notification dot.
     */
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** The bounds of the flyout relative to the parent view. */
    val bounds = Rect()

    init {
        LayoutInflater.from(context).inflate(R.layout.bubblebar_flyout, this, true)
        id = R.id.bubble_bar_flyout_view

        setTypeface(title, TypefaceUtils.FontFamily.GSF_LABEL_LARGE)
        setTypeface(message, TypefaceUtils.FontFamily.GSF_BODY_MEDIUM)

        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.dialogCornerRadius))
        cornerRadius = ta.getDimensionPixelSize(0, 0).toFloat()
        ta.recycle()

        setWillNotDraw(false)
        clipChildren = true
        clipToPadding = false

        val padding = context.resources.getDimensionPixelSize(R.dimen.bubblebar_flyout_padding)
        // add extra padding to the bottom of the view to include the triangle
        setPadding(padding, padding, padding, padding + triangleHeight - triangleOverlap)
        translationZ = flyoutElevation

        RoundedArrowDrawable.addDownPointingRoundedTriangleToPath(
            triangleWidth.toFloat(),
            triangleHeight.toFloat(),
            triangleRadius.toFloat(),
            triangle,
        )
        triangleOutline.setPath(triangle)

        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    this@BubbleBarFlyoutView.getOutline(outline)
                }
            }
        clipToOutline = true

        applyConfigurationColors(resources.configuration)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        bounds.left = left
        bounds.top = top
        bounds.right = right
        bounds.bottom = bottom
    }

    /** Sets the data for the flyout and starts playing the expand animation. */
    fun showFromCollapsed(flyoutMessage: BubbleBarFlyoutMessage, expandAnimation: () -> Unit) {
        icon.alpha = 0f
        title.alpha = 0f
        message.alpha = 0f
        setData(flyoutMessage)

        updateTranslationToCollapsedPosition()
        collapsedSize = positioner.collapsedSize
        collapsedCornerRadius = collapsedSize / 2
        collapsedColor = positioner.collapsedColor
        collapsedElevation = positioner.collapsedElevation

        // calculate the expansion progress required before we start showing the triangle as part of
        // the expansion animation
        minExpansionProgressForTriangle =
            positioner.distanceToRevealTriangle / translationToCollapsedPosition.y

        backgroundPaint.color = collapsedColor

        // post the request to start the expand animation to the looper so the view can measure
        // itself
        scheduler.runAfterLayout(expandAnimation)
    }

    /** Updates the content of the flyout and schedules [afterLayout] to run after a layout pass. */
    fun updateData(flyoutMessage: BubbleBarFlyoutMessage, afterLayout: () -> Unit) {
        setData(flyoutMessage)
        scheduler.runAfterLayout(afterLayout)
    }

    private fun setData(flyoutMessage: BubbleBarFlyoutMessage) {
        if (flyoutMessage.icon != null) {
            icon.visibility = VISIBLE
            icon.setImageDrawable(flyoutMessage.icon)
        } else {
            icon.visibility = GONE
        }

        val minTextViewWidth: Int
        val maxTextViewWidth: Int
        if (icon.visibility == VISIBLE) {
            minTextViewWidth = minFlyoutWidth - icon.width - flyoutPadding * 2
            maxTextViewWidth = maxFlyoutWidth - icon.width - flyoutPadding * 2
        } else {
            // when there's no avatar, the width of the text view is constant, so we're setting the
            // min and max to the same value
            minTextViewWidth = minFlyoutWidth - flyoutPadding * 2
            maxTextViewWidth = minTextViewWidth
        }

        if (flyoutMessage.title.isEmpty()) {
            title.visibility = GONE
        } else {
            title.minWidth = minTextViewWidth
            title.maxWidth = maxTextViewWidth
            title.text = flyoutMessage.title
            title.visibility = VISIBLE
        }

        message.minWidth = minTextViewWidth
        message.maxWidth = maxTextViewWidth
        message.text = flyoutMessage.message
    }

    /**
     * This should be called to update [translationToCollapsedPosition] before we start expanding or
     * collapsing to make sure that we're animating the flyout to and from the correct position.
     */
    fun updateTranslationToCollapsedPosition() {
        val txToCollapsedPosition =
            if (positioner.isOnLeft) {
                positioner.distanceToCollapsedPosition.x
            } else {
                -positioner.distanceToCollapsedPosition.x
            }
        val tyToCollapsedPosition =
            positioner.distanceToCollapsedPosition.y + triangleHeight - triangleOverlap
        translationToCollapsedPosition = PointF(txToCollapsedPosition, tyToCollapsedPosition)
    }

    /** Updates the flyout view with the progress of the animation. */
    fun updateExpansionProgress(fraction: Float) {
        expansionProgress = fraction

        updateTranslationForAnimation(message)
        updateTranslationForAnimation(title)
        updateTranslationForAnimation(icon)

        // start fading in the content only after we're past the threshold
        val alpha =
            ((expansionProgress - MIN_EXPANSION_PROGRESS_FOR_CONTENT_ALPHA) /
                    (1f - MIN_EXPANSION_PROGRESS_FOR_CONTENT_ALPHA))
                .coerceIn(0f, 1f)
        title.alpha = alpha
        message.alpha = alpha
        icon.alpha = alpha

        translationZ =
            collapsedElevation + (flyoutElevation - collapsedElevation) * expansionProgress

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // interpolate the width, height, corner radius and translation based on the progress of the
        // animation.
        // the background is drawn from the bottom left corner to the top right corner if we're
        // positioned on the left, and from the bottom right corner to the top left if we're
        // positioned on the right.

        // the current width of the background rect according to the progress of the animation
        val currentWidth = collapsedSize + (width - collapsedSize) * expansionProgress
        val rectBottom = height - triangleHeight + triangleOverlap
        val currentHeight = collapsedSize + (rectBottom - collapsedSize) * expansionProgress

        backgroundRect.set(
            if (positioner.isOnLeft) 0f else width.toFloat() - currentWidth,
            height.toFloat() - triangleHeight + triangleOverlap - currentHeight,
            if (positioner.isOnLeft) currentWidth else width.toFloat(),
            height.toFloat() - triangleHeight + triangleOverlap,
        )

        // transform the flyout color between the collapsed and expanded states. the color
        // transformation completes at a faster rate (BACKGROUND_COLOR_CHANGE_RATE) than the
        // expansion animation. this helps make the color change smooth.
        backgroundPaint.color =
            ArgbEvaluator.getInstance()
                .evaluate(
                    min(expansionProgress * BACKGROUND_COLOR_CHANGE_RATE, 1f),
                    collapsedColor,
                    backgroundColor,
                )

        canvas.save()
        canvas.translate(backgroundRectTx, backgroundRectTy)
        // draw the background starting from the bottom left if we're positioned left, or the bottom
        // right if we're positioned right.
        canvas.drawRoundRect(
            backgroundRect,
            currentCornerRadius,
            currentCornerRadius,
            backgroundPaint,
        )
        if (expansionProgress >= minExpansionProgressForTriangle) {
            drawTriangle(canvas)
        }
        canvas.restore()
        invalidateOutline()
        super.onDraw(canvas)
    }

    private fun drawTriangle(canvas: Canvas) {
        canvas.save()
        val triangleX =
            if (positioner.isOnLeft) {
                currentCornerRadius
            } else {
                width - currentCornerRadius - triangleWidth
            }
        // instead of scaling the triangle, increasingly reveal it from the background. this has the
        // effect of the triangle scaling.

        // the translation y of the triangle before we start revealing it. align its bottom with the
        // bottom of the rect
        val triangleYCollapsed = height - triangleHeight - (triangleHeight - triangleOverlap)
        // the translation y of the triangle when it's fully revealed
        val triangleYExpanded = height - triangleHeight
        val interpolatedExpansion =
            ((expansionProgress - minExpansionProgressForTriangle) /
                    (1 - minExpansionProgressForTriangle))
                .coerceIn(0f, 1f)
        val triangleY =
            triangleYCollapsed + (triangleYExpanded - triangleYCollapsed) * interpolatedExpansion
        canvas.translate(triangleX, triangleY)
        canvas.drawPath(triangle, backgroundPaint)
        triangleOutline.setPath(triangle)
        triangleOutline.offset(triangleX.toInt(), triangleY.toInt())
        canvas.restore()
    }

    private fun getOutline(outline: Outline) {
        val path = Path()
        path.addRoundRect(
            backgroundRect,
            currentCornerRadius,
            currentCornerRadius,
            Path.Direction.CW,
        )
        if (expansionProgress >= minExpansionProgressForTriangle) {
            path.addPath(triangleOutline.mPath)
        }
        outline.setPath(path)
        outline.offset(backgroundRectTx.toInt(), backgroundRectTy.toInt())
    }

    private fun updateTranslationForAnimation(view: View) {
        val tx =
            if (positioner.isOnLeft) {
                translationToCollapsedPosition.x - view.left
            } else {
                width - view.left - translationToCollapsedPosition.x
            }
        val ty = height - view.top + translationToCollapsedPosition.y
        view.translationX = tx * (1f - expansionProgress)
        view.translationY = ty * (1f - expansionProgress)
    }

    private fun applyConfigurationColors(configuration: Configuration) {
        val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        val defaultBackgroundColor = if (isNightModeOn) Color.BLACK else Color.WHITE
        val defaultTextColor = if (isNightModeOn) Color.WHITE else Color.BLACK

        backgroundColor =
            context.getColor(com.android.internal.R.color.materialColorSurfaceContainer)
        title.setTextColor(context.getColor(com.android.internal.R.color.materialColorOnSurface))
        message.setTextColor(
            context.getColor(com.android.internal.R.color.materialColorOnSurfaceVariant)
        )
        backgroundPaint.color = backgroundColor
    }
}
