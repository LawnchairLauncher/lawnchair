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
package com.android.launcher3.taskbar

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.provider.Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.systemui.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.systemui.animation.Interpolators.EMPHASIZED_DECELERATE
import com.android.systemui.animation.Interpolators.STANDARD

private const val ENTER_DURATION_MS = 300L
private const val EXIT_DURATION_MS = 150L

/** Floating tooltip for Taskbar education. */
class TaskbarEduTooltip
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    private val backgroundColor =
        Themes.getAttrColor(context, com.android.internal.R.attr.colorSurface)

    private val tooltipCornerRadius = Themes.getDialogCornerRadius(context)
    private val arrowWidth = resources.getDimension(R.dimen.popup_arrow_width)
    private val arrowHeight = resources.getDimension(R.dimen.popup_arrow_height)
    private val arrowPointRadius = resources.getDimension(R.dimen.popup_arrow_corner_radius)

    private val enterYDelta = resources.getDimension(R.dimen.taskbar_edu_tooltip_enter_y_delta)
    private val exitYDelta = resources.getDimension(R.dimen.taskbar_edu_tooltip_exit_y_delta)

    /** Container where the tooltip's body should be inflated. */
    lateinit var content: ViewGroup
        private set
    private lateinit var arrow: View

    /** Callback invoked when the tooltip is being closed. */
    var onCloseCallback: () -> Unit = {}
    private var openCloseAnimator: AnimatorSet? = null

    /** Animates the tooltip into view. */
    fun show() {
        if (isOpen) {
            return
        }
        mIsOpen = true
        activityContext.dragLayer.addView(this)
        openCloseAnimator = createOpenCloseAnimator(isOpening = true).apply { start() }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        content = requireViewById(R.id.content)
        arrow = requireViewById(R.id.arrow)
        arrow.background =
            RoundedArrowDrawable(
                arrowWidth,
                arrowHeight,
                arrowPointRadius,
                tooltipCornerRadius,
                measuredWidth.toFloat(),
                measuredHeight.toFloat(),
                (measuredWidth - arrowWidth) / 2, // arrowOffsetX
                0f, // arrowOffsetY
                false, // isPointingUp
                true, // leftAligned
                backgroundColor,
            )
    }

    override fun handleClose(animate: Boolean) {
        if (!isOpen) {
            return
        }

        onCloseCallback()
        if (!animate) {
            return closeComplete()
        }

        openCloseAnimator?.cancel()
        openCloseAnimator = createOpenCloseAnimator(isOpening = false)
        openCloseAnimator?.addListener(AnimatorListeners.forEndCallback(this::closeComplete))
        openCloseAnimator?.start()
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_TASKBAR_EDUCATION_DIALOG != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == ACTION_DOWN && !activityContext.dragLayer.isEventOverView(this, ev)) {
            close(true)
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Settings.Secure.putInt(mContext.contentResolver, LAUNCHER_TASKBAR_EDUCATION_SHOWING, 0)
    }

    private fun closeComplete() {
        openCloseAnimator?.cancel()
        openCloseAnimator = null
        mIsOpen = false
        activityContext.dragLayer.removeView(this)
    }

    private fun createOpenCloseAnimator(isOpening: Boolean): AnimatorSet {
        val duration: Long
        val alphaValues: FloatArray
        val translateYValues: FloatArray
        val fadeInterpolator: Interpolator
        val translateYInterpolator: Interpolator

        if (isOpening) {
            duration = ENTER_DURATION_MS
            alphaValues = floatArrayOf(0f, 1f)
            translateYValues = floatArrayOf(enterYDelta, 0f)
            fadeInterpolator = STANDARD
            translateYInterpolator = EMPHASIZED_DECELERATE
        } else {
            duration = EXIT_DURATION_MS
            alphaValues = floatArrayOf(1f, 0f)
            translateYValues = floatArrayOf(0f, exitYDelta)
            fadeInterpolator = EMPHASIZED_ACCELERATE
            translateYInterpolator = EMPHASIZED_ACCELERATE
        }

        val fade =
            ValueAnimator.ofFloat(*alphaValues).apply {
                interpolator = fadeInterpolator
                addUpdateListener {
                    val alpha = it.animatedValue as Float
                    content.alpha = alpha
                    arrow.alpha = alpha
                }
            }

        val translateY =
            ValueAnimator.ofFloat(*translateYValues).apply {
                interpolator = translateYInterpolator
                addUpdateListener {
                    val translationY = it.animatedValue as Float
                    content.translationY = translationY
                    arrow.translationY = translationY
                }
            }

        return AnimatorSet().apply {
            this.duration = duration
            playTogether(fade, translateY)
        }
    }
}
