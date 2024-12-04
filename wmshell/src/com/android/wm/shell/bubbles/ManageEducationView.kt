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
package com.android.wm.shell.bubbles

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.android.internal.R.color.system_neutral1_900
import com.android.wm.shell.R
import com.android.wm.shell.animation.Interpolators

/**
 * User education view to highlight the manage button that allows a user to configure the settings
 * for the bubble. Shown only the first time a user expands a bubble.
 */
class ManageEducationView(
    context: Context,
    private val positioner: BubblePositioner
) : LinearLayout(context) {

    companion object {
        const val PREF_MANAGED_EDUCATION: String = "HasSeenBubblesManageOnboarding"
        private const val ANIMATE_DURATION: Long = 200
    }

    private val manageView by lazy { requireViewById<ViewGroup>(R.id.manage_education_view) }
    private val manageButton by lazy { requireViewById<Button>(R.id.manage_button) }
    private val gotItButton by lazy { requireViewById<Button>(R.id.got_it) }

    private var isHiding = false
    private var realManageButtonRect = Rect()
    private var bubbleExpandedView: BubbleExpandedView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.bubbles_manage_button_education, this)
        visibility = View.GONE
        elevation = resources.getDimensionPixelSize(R.dimen.bubble_elevation).toFloat()

        // BubbleStackView forces LTR by default
        // since most of Bubble UI direction depends on positioning by the user.
        // This view actually lays out differently in RTL, so we set layout LOCALE here.
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }

    override fun setLayoutDirection(layoutDirection: Int) {
        super.setLayoutDirection(layoutDirection)
        setDrawableDirection(layoutDirection == LAYOUT_DIRECTION_LTR)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        layoutDirection = resources.configuration.layoutDirection
    }

    private fun setButtonColor() {
        val typedArray =
            mContext.obtainStyledAttributes(
                intArrayOf(com.android.internal.R.attr.colorAccentPrimary)
            )
        val buttonColor = typedArray.getColor(0 /* index */, Color.TRANSPARENT)
        typedArray.recycle()

        manageButton.setTextColor(mContext.getColor(system_neutral1_900))
        manageButton.setBackgroundDrawable(ColorDrawable(buttonColor))
        gotItButton.setBackgroundDrawable(ColorDrawable(buttonColor))
    }

    private fun setDrawableDirection(isOnLeft: Boolean) {
        manageView.setBackgroundResource(
            if (isOnLeft) R.drawable.bubble_stack_user_education_bg
            else R.drawable.bubble_stack_user_education_bg_rtl
        )
    }

    /**
     * If necessary, toggles the user education view for the manage button. This is shown when the
     * bubble stack is expanded for the first time.
     *
     * @param expandedView the expandedView the user education is shown on top of.
     * @param isStackOnLeft the bubble stack position on the screen
     */
    fun show(expandedView: BubbleExpandedView, isStackOnLeft: Boolean) {
        setButtonColor()
        if (visibility == VISIBLE) return

        bubbleExpandedView = expandedView
        expandedView.taskView?.setObscuredTouchRect(Rect(positioner.screenRect))

        alpha = 0f
        visibility = View.VISIBLE
        expandedView.getManageButtonBoundsOnScreen(realManageButtonRect)
        layoutManageView(realManageButtonRect, expandedView.manageButtonMargin, isStackOnLeft)

        post {
            manageButton.setOnClickListener {
                hide()
                expandedView.requireViewById<View>(R.id.manage_button).performClick()
            }
            gotItButton.setOnClickListener { hide() }
            setOnClickListener { hide() }

            val offsetViewBounds = Rect()
            manageButton.getDrawingRect(offsetViewBounds)
            manageView.offsetDescendantRectToMyCoords(manageButton, offsetViewBounds)
            translationY = (realManageButtonRect.top - offsetViewBounds.top).toFloat()
            bringToFront()
            animate()
                .setDuration(ANIMATE_DURATION)
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .alpha(1f)
        }
        updateManageEducationSeen()
    }

    /**
     * On tablet the user education is aligned to the left or to right side depending on where the
     * stack is positioned when collapsed. On phone the user education follows the layout direction.
     *
     * @param manageButtonRect the manage button rect on the screen
     * @param manageButtonMargin the manage button margin
     * @param isStackOnLeft the bubble stack position on the screen
     */
    private fun layoutManageView(
        manageButtonRect: Rect,
        manageButtonMargin: Int,
        isStackOnLeft: Boolean
    ) {
        val isLTR = resources.configuration.layoutDirection == LAYOUT_DIRECTION_LTR
        val isPinnedLeft = if (positioner.isLargeScreen) isStackOnLeft else isLTR
        val paddingHorizontal =
            resources.getDimensionPixelSize(R.dimen.bubble_user_education_padding_horizontal)

        // The user education view background image direction
        setDrawableDirection(isPinnedLeft)

        // The user education view layout gravity
        gravity = if (isPinnedLeft) Gravity.LEFT else Gravity.RIGHT

        // The user education view width
        manageView.layoutParams.width =
            when {
                // Left-to-Right direction and the education is on the right side
                isLTR && !isPinnedLeft ->
                    positioner.screenRect.right -
                        (manageButtonRect.left - manageButtonMargin - paddingHorizontal)
                // Right-to-Left direction and the education is on the left side
                !isLTR && isPinnedLeft ->
                    manageButtonRect.right + manageButtonMargin + paddingHorizontal
                // Large screen and the education position matches the layout direction
                positioner.isLargeScreen -> ViewGroup.LayoutParams.WRAP_CONTENT
                // Small screen, landscape orientation
                positioner.isLandscape ->
                    resources.getDimensionPixelSize(R.dimen.bubbles_user_education_width)
                // Otherwise
                else -> ViewGroup.LayoutParams.MATCH_PARENT
            }

        // The user education view margin on the opposite side of where it's pinned
        (manageView.layoutParams as MarginLayoutParams).apply {
            val edgeMargin =
                resources.getDimensionPixelSize(R.dimen.bubble_user_education_margin_horizontal)
            leftMargin = if (isPinnedLeft) 0 else edgeMargin
            rightMargin = if (isPinnedLeft) edgeMargin else 0
        }

        // The user education view padding
        manageView.apply {
            val paddingLeft =
                if (isLTR && isPinnedLeft) {
                    // Offset on the left to align with the manage button
                    manageButtonRect.left - manageButtonMargin
                } else {
                    // Use default padding
                    paddingHorizontal
                }
            val paddingRight =
                if (!isLTR && !isPinnedLeft) {
                    // Offset on the right to align with the manage button
                    positioner.screenRect.right - manageButtonRect.right - manageButtonMargin
                } else {
                    // Use default padding
                    paddingHorizontal
                }
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }
    }

    fun hide() {
        bubbleExpandedView?.taskView?.setObscuredTouchRect(null)
        if (visibility != VISIBLE || isHiding) return

        animate()
            .withStartAction { isHiding = true }
            .alpha(0f)
            .setDuration(ANIMATE_DURATION)
            .withEndAction {
                isHiding = false
                visibility = GONE
            }
    }

    private fun updateManageEducationSeen() {
        context
            .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_MANAGED_EDUCATION, true)
            .apply()
    }
}
