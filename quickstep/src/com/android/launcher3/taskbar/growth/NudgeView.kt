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
package com.android.launcher3.taskbar.growth

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.window.OnBackInvokedDispatcher
import androidx.core.view.updateLayoutParams
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.views.ActivityContext

private const val ENTER_DURATION_MS = 300L
private const val EXIT_DURATION_MS = 150L

/** Floating nudge. */
class NudgeView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractFloatingView(context, attrs, defStyleAttr) {

    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    private val enterYDelta = resources.getDimension(R.dimen.nudge_enter_y_delta)
    private val exitYDelta = resources.getDimension(R.dimen.nudge_exit_y_delta)

    /** Container where the nudge's body should be inflated. */
    lateinit var content: ViewGroup
        private set

    /** Callback invoked when the nudge is being closed. */
    var onCloseCallback: () -> Unit = {}
    private var openCloseAnimator: AnimatorSet? = null
    /** Used to set whether users can tap outside the current nudge window to dismiss it */
    var allowTouchDismissal = true

    /** Animates the nudge into view. */
    fun show() {
        if (isOpen) {
            return
        }

        mIsOpen = true
        activityContext.dragLayer.addView(this)

        // Make sure we have enough height to display all of the content, which can be an issue on
        // large text and display scaling configurations. If we run out of height, remove the width
        // constraint to reduce the number of lines of text and hopefully free up some height.
        activityContext.dragLayer.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        if (
            measuredHeight + activityContext.deviceProfile.taskbarProfile.height >=
                activityContext.deviceProfile.deviceProperties.availableHeightPx
        ) {
            updateLayoutParams { width = LayoutParams.MATCH_PARENT }
        }

        openCloseAnimator = createOpenCloseAnimator(isOpening = true).apply { start() }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        content = requireViewById(R.id.content)
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
        openCloseAnimator?.apply {
            addListener(AnimatorListeners.forEndCallback(this@NudgeView::closeComplete))
            start()
        }
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_NUDGE != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (
            ev?.action == MotionEvent.ACTION_DOWN &&
                !activityContext.dragLayer.isEventOverView(this, ev) &&
                allowTouchDismissal
        ) {
            close(true)
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findOnBackInvokedDispatcher()
            ?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(this)
        // TODO: b/396507770 - investigate and coordinate with letterbox flow.
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
            fadeInterpolator = Interpolators.STANDARD
            translateYInterpolator = Interpolators.EMPHASIZED_DECELERATE
        } else {
            duration = EXIT_DURATION_MS
            alphaValues = floatArrayOf(1f, 0f)
            translateYValues = floatArrayOf(0f, exitYDelta)
            fadeInterpolator = Interpolators.EMPHASIZED_ACCELERATE
            translateYInterpolator = Interpolators.EMPHASIZED_ACCELERATE
        }

        val fade =
            ValueAnimator.ofFloat(*alphaValues).apply {
                interpolator = fadeInterpolator
                addUpdateListener {
                    val alpha = it.animatedValue as Float
                    content.alpha = alpha
                }
            }

        val translateY =
            ValueAnimator.ofFloat(*translateYValues).apply {
                interpolator = translateYInterpolator
                addUpdateListener {
                    val translationY = it.animatedValue as Float
                    content.translationY = translationY
                }
            }

        return AnimatorSet().apply {
            this.duration = duration
            playTogether(fade, translateY)
        }
    }
}
