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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Property
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import androidx.core.view.postDelayed
import com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.launcher3.R
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext

/** Popup view with arrow for taskbar pinning */
class TaskbarDividerPopupView<T : TaskbarActivityContext>
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ArrowPopup<T>(context, attrs, defStyleAttr) {
    companion object {
        private const val TAG = "TaskbarDividerPopupView"
        private const val DIVIDER_POPUP_CLOSING_DELAY = 333L
        private const val DIVIDER_POPUP_CLOSING_ANIMATION_DURATION = 83L

        @JvmStatic
        fun createAndPopulate(
            view: View,
            taskbarActivityContext: TaskbarActivityContext,
        ): TaskbarDividerPopupView<*> {
            val taskMenuViewWithArrow =
                taskbarActivityContext.layoutInflater.inflate(
                    R.layout.taskbar_divider_popup_menu,
                    taskbarActivityContext.dragLayer,
                    false
                ) as TaskbarDividerPopupView<*>

            return taskMenuViewWithArrow.populateForView(view)
        }
    }

    private lateinit var dividerView: View

    private val popupCornerRadius = Themes.getDialogCornerRadius(context)
    private val arrowWidth = resources.getDimension(R.dimen.popup_arrow_width)
    private val arrowHeight = resources.getDimension(R.dimen.popup_arrow_height)
    private val arrowPointRadius = resources.getDimension(R.dimen.popup_arrow_corner_radius)

    private var alwaysShowTaskbarOn = !DisplayController.isTransientTaskbar(context)
    private var didPreferenceChange = false
    private var verticalOffsetForPopupView =
        resources.getDimensionPixelSize(R.dimen.taskbar_pinning_popup_menu_vertical_margin)

    /** Callback invoked when the pinning popup view is closing. */
    var onCloseCallback: (preferenceChanged: Boolean) -> Unit = {}

    init {
        // This synchronizes the arrow and menu to open at the same time
        mOpenChildFadeStartDelay = mOpenFadeStartDelay
        mOpenChildFadeDuration = mOpenFadeDuration
        mCloseFadeStartDelay = mCloseChildFadeStartDelay
        mCloseFadeDuration = mCloseChildFadeDuration
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_TASKBAR_PINNING_POPUP != 0

    override fun getTargetObjectLocation(outPos: Rect) {
        popupContainer.getDescendantRectRelativeToSelf(dividerView, outPos)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode", "ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()
        val taskbarSwitchOption = requireViewById<LinearLayout>(R.id.taskbar_switch_option)
        val alwaysShowTaskbarSwitch = requireViewById<Switch>(R.id.taskbar_pinning_switch)
        val taskbarVisibilityIcon = requireViewById<View>(R.id.taskbar_pinning_visibility_icon)

        alwaysShowTaskbarSwitch.isChecked = alwaysShowTaskbarOn
        alwaysShowTaskbarSwitch.setOnTouchListener { view, event ->
            (view.parent as View).onTouchEvent(event)
        }
        alwaysShowTaskbarSwitch.setOnClickListener { view -> (view.parent as View).performClick() }

        if (ActivityContext.lookupContext<TaskbarActivityContext>(context).isGestureNav) {
            taskbarSwitchOption.setOnClickListener {
                alwaysShowTaskbarSwitch.isChecked = !alwaysShowTaskbarOn
                onClickAlwaysShowTaskbarSwitchOption()
            }
        } else {
            alwaysShowTaskbarSwitch.isEnabled = false
        }

        if (!alwaysShowTaskbarSwitch.isEnabled) {
            taskbarVisibilityIcon.background.setTint(
                resources.getColor(android.R.color.system_neutral2_500, context.theme)
            )
        }
    }

    /** Orient object as usual and then center object horizontally. */
    override fun orientAboutObject() {
        super.orientAboutObject()
        x = mTempRect.centerX() - measuredWidth / 2f
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (!popupContainer.isEventOverView(this, ev)) {
                close(true)
            }
        } else if (popupContainer.isEventOverView(dividerView, ev)) {
            return true
        }
        return false
    }

    private fun populateForView(view: View): TaskbarDividerPopupView<*> {
        dividerView = view
        tryUpdateBackground()
        return this
    }

    /** Updates the text background to match the shape of this background (when applicable). */
    private fun tryUpdateBackground() {
        if (background !is GradientDrawable) {
            return
        }
        val background = background as GradientDrawable
        val color = context.getColor(R.color.popup_shade_first)
        val backgroundMask = GradientDrawable()
        backgroundMask.setColor(color)
        backgroundMask.shape = GradientDrawable.RECTANGLE
        if (background.cornerRadii != null) {
            backgroundMask.cornerRadii = background.cornerRadii
        } else {
            backgroundMask.cornerRadius = background.cornerRadius
        }

        setBackground(backgroundMask)
    }

    override fun addArrow() {
        super.addArrow()
        // Change arrow location to the middle of popup.
        mArrow.x = (dividerView.x + dividerView.width / 2) - (mArrowWidth / 2)
    }

    override fun updateArrowColor() {
        if (!Gravity.isVertical(mGravity)) {
            mArrow.background =
                RoundedArrowDrawable(
                    arrowWidth,
                    arrowHeight,
                    arrowPointRadius,
                    popupCornerRadius,
                    measuredWidth.toFloat(),
                    measuredHeight.toFloat(),
                    (measuredWidth - arrowWidth) / 2, // arrowOffsetX
                    -mArrowOffsetVertical.toFloat(), // arrowOffsetY
                    false, // isPointingUp
                    true, // leftAligned
                    context.getColor(R.color.popup_shade_first),
                )
            elevation = mElevation
            mArrow.elevation = mElevation
        }
    }

    override fun getExtraVerticalOffset(): Int {
        return (mActivityContext.deviceProfile.taskbarHeight -
            mActivityContext.deviceProfile.taskbarIconSize) / 2 + verticalOffsetForPopupView
    }

    override fun onCreateCloseAnimation(anim: AnimatorSet?) {
        // If taskbar pinning preference changed insert custom close animation for popup menu.
        if (didPreferenceChange) {
            mOpenCloseAnimator = getCloseAnimator()
        }
        onCloseCallback(didPreferenceChange)
        onCloseCallback = {}
    }

    /** Aligning the view pivot to center for animation. */
    override fun setPivotForOpenCloseAnimation() {
        pivotX = measuredWidth / 2f
        pivotY = measuredHeight.toFloat()
    }

    private fun getCloseAnimator(): AnimatorSet {
        val alphaValues = floatArrayOf(1f, 0f)
        val translateYValue =
            if (!alwaysShowTaskbarOn) verticalOffsetForPopupView else -verticalOffsetForPopupView
        val alpha = getAnimatorOfFloat(this, ALPHA, *alphaValues)
        val arrowAlpha = getAnimatorOfFloat(mArrow, ALPHA, *alphaValues)
        val translateY =
            ObjectAnimator.ofFloat(
                this,
                TRANSLATION_Y,
                *floatArrayOf(this.translationY, this.translationY + translateYValue)
            )
        val arrowTranslateY =
            ObjectAnimator.ofFloat(
                mArrow,
                TRANSLATION_Y,
                *floatArrayOf(mArrow.translationY, mArrow.translationY + translateYValue)
            )
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alpha, arrowAlpha, translateY, arrowTranslateY)
        return animatorSet
    }

    private fun getAnimatorOfFloat(
        view: View,
        property: Property<View, Float>,
        vararg values: Float
    ): Animator {
        val animator: Animator = ObjectAnimator.ofFloat(view, property, *values)
        animator.setDuration(DIVIDER_POPUP_CLOSING_ANIMATION_DURATION)
        animator.interpolator = EMPHASIZED_ACCELERATE
        return animator
    }

    private fun onClickAlwaysShowTaskbarSwitchOption() {
        didPreferenceChange = true
        // Allow switch animation to finish and then close the popup.
        postDelayed(DIVIDER_POPUP_CLOSING_DELAY) {
            if (isOpen) {
                close(true)
            }
        }
    }
}
