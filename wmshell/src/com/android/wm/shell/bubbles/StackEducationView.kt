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
import android.graphics.PointF
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.android.internal.util.ContrastColorUtil
import com.android.wm.shell.R
import com.android.wm.shell.animation.Interpolators

/**
 * User education view to highlight the collapsed stack of bubbles. Shown only the first time a user
 * taps the stack.
 */
class StackEducationView(
    context: Context,
    private val positioner: BubblePositioner,
    private val manager: Manager
) : LinearLayout(context) {

    companion object {
        const val PREF_STACK_EDUCATION: String = "HasSeenBubblesOnboarding"
        private const val ANIMATE_DURATION: Long = 200
        private const val ANIMATE_DURATION_SHORT: Long = 40
    }

    /** Callbacks to notify managers of [StackEducationView] about events. */
    interface Manager {
        /** Notifies whether backpress should be intercepted. */
        fun updateWindowFlagsForBackpress(interceptBack: Boolean)
    }

    private val view by lazy { requireViewById<View>(R.id.stack_education_layout) }
    private val titleTextView by lazy { requireViewById<TextView>(R.id.stack_education_title) }
    private val descTextView by lazy { requireViewById<TextView>(R.id.stack_education_description) }

    var isHiding = false
        private set

    init {
        LayoutInflater.from(context).inflate(R.layout.bubble_stack_user_education, this)

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
        setTextColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setFocusableInTouchMode(true)
        setOnKeyListener(object : OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                // if the event is a key down event on the enter button
                if (event.action == KeyEvent.ACTION_UP &&
                        keyCode == KeyEvent.KEYCODE_BACK && !isHiding) {
                    hide(false)
                    return true
                }
                return false
            }
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setOnKeyListener(null)
        manager.updateWindowFlagsForBackpress(false /* interceptBack */)
    }

    private fun setTextColor() {
        val ta = mContext.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent,
            android.R.attr.textColorPrimaryInverse))
        val bgColor = ta.getColor(0 /* index */, Color.BLACK)
        var textColor = ta.getColor(1 /* index */, Color.WHITE)
        ta.recycle()
        textColor = ContrastColorUtil.ensureTextContrast(textColor, bgColor, true)
        titleTextView.setTextColor(textColor)
        descTextView.setTextColor(textColor)
    }

    private fun setDrawableDirection(isOnLeft: Boolean) {
        view.setBackgroundResource(
            if (isOnLeft) R.drawable.bubble_stack_user_education_bg
            else R.drawable.bubble_stack_user_education_bg_rtl
        )
    }

    /**
     * If necessary, shows the user education view for the bubble stack. This appears the first time
     * a user taps on a bubble.
     *
     * @return true if user education was shown and wasn't showing before, false otherwise.
     */
    fun show(stackPosition: PointF): Boolean {
        isHiding = false
        if (visibility == VISIBLE) return false

        manager.updateWindowFlagsForBackpress(true /* interceptBack */)
        layoutParams.width =
                if (positioner.isLargeScreen || positioner.isLandscape)
                    context.resources.getDimensionPixelSize(R.dimen.bubbles_user_education_width)
                else ViewGroup.LayoutParams.MATCH_PARENT

        val isStackOnLeft = positioner.isStackOnLeft(stackPosition)
        (view.layoutParams as MarginLayoutParams).apply {
            // Update the horizontal margins depending on the stack position
            val edgeMargin =
                resources.getDimensionPixelSize(R.dimen.bubble_user_education_margin_horizontal)
            leftMargin = if (isStackOnLeft) 0 else edgeMargin
            rightMargin = if (isStackOnLeft) edgeMargin else 0
        }

        val stackPadding =
            context.resources.getDimensionPixelSize(R.dimen.bubble_user_education_stack_padding)
        setAlpha(0f)
        setVisibility(View.VISIBLE)
        setDrawableDirection(isOnLeft = isStackOnLeft)
        post {
            requestFocus()
            with(view) {
                if (isStackOnLeft) {
                    setPadding(
                        positioner.bubbleSize + stackPadding,
                        paddingTop,
                        paddingRight,
                        paddingBottom
                    )
                    translationX = 0f
                } else {
                    setPadding(
                        paddingLeft,
                        paddingTop,
                        positioner.bubbleSize + stackPadding,
                        paddingBottom
                    )
                    translationX = (positioner.screenRect.right - width - stackPadding).toFloat()
                }
                translationY = stackPosition.y + positioner.bubbleSize / 2 - getHeight() / 2
            }
            animate()
                .setDuration(ANIMATE_DURATION)
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .alpha(1f)
        }
        updateStackEducationSeen()
        return true
    }

    /**
     * If necessary, hides the stack education view.
     *
     * @param isExpanding if true this indicates the hide is happening due to the bubble being
     *   expanded, false if due to a touch outside of the bubble stack.
     */
    fun hide(isExpanding: Boolean) {
        if (visibility != VISIBLE || isHiding) return
        isHiding = true

        manager.updateWindowFlagsForBackpress(false /* interceptBack */)
        animate()
            .alpha(0f)
            .setDuration(if (isExpanding) ANIMATE_DURATION_SHORT else ANIMATE_DURATION)
            .withEndAction { visibility = GONE }
    }

    private fun updateStackEducationSeen() {
        context
            .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_STACK_EDUCATION, true)
            .apply()
    }
}
