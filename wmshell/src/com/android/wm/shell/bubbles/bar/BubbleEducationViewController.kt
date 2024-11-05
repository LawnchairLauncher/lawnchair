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
package com.android.wm.shell.bubbles.bar

import android.annotation.LayoutRes
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.BubbleDebugConfig.DEBUG_USER_EDUCATION
import com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES
import com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME
import com.android.wm.shell.bubbles.BubbleEducationController
import com.android.wm.shell.bubbles.BubbleViewProvider
import com.android.wm.shell.bubbles.setup
import com.android.wm.shell.common.bubbles.BubblePopupDrawable
import com.android.wm.shell.common.bubbles.BubblePopupView
import com.android.wm.shell.shared.animation.PhysicsAnimator
import kotlin.math.roundToInt

/** Manages bubble education presentation and animation */
class BubbleEducationViewController(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onEducationVisibilityChanged(isVisible: Boolean)
    }

    private var rootView: ViewGroup? = null
    private var educationView: BubblePopupView? = null
    private var animator: PhysicsAnimator<BubblePopupView>? = null

    private val springConfig by lazy {
        PhysicsAnimator.SpringConfig(
            SpringForce.STIFFNESS_MEDIUM,
            SpringForce.DAMPING_RATIO_LOW_BOUNCY
        )
    }

    private val scrimView by lazy {
        View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { hideEducation(animated = true) }
        }
    }

    private val controller by lazy { BubbleEducationController(context) }

    /** Whether the education view is visible or being animated */
    val isEducationVisible: Boolean
        get() = educationView != null && rootView != null

    /**
     * Hide the current education view if visible
     *
     * @param animated whether should hide with animation
     */
    @JvmOverloads
    fun hideEducation(animated: Boolean, endActions: () -> Unit = {}) {
        log { "hideEducation animated: $animated" }

        if (animated) {
            animateTransition(show = false) {
                cleanUp()
                endActions()
                listener.onEducationVisibilityChanged(isVisible = false)
            }
        } else {
            cleanUp()
            endActions()
            listener.onEducationVisibilityChanged(isVisible = false)
        }
    }

    /**
     * Show bubble bar stack user education.
     *
     * @param position the reference position for the user education in Screen coordinates.
     * @param root the view to show user education in.
     * @param educationClickHandler the on click handler for the user education view
     */
    fun showStackEducation(position: Point, root: ViewGroup, educationClickHandler: () -> Unit) {
        hideEducation(animated = false)
        log { "showStackEducation at: $position" }

        educationView =
            createEducationView(R.layout.bubble_bar_stack_education, root).apply {
                setArrowDirection(BubblePopupDrawable.ArrowDirection.DOWN)
                setArrowPosition(BubblePopupDrawable.ArrowPosition.End)
                updateEducationPosition(view = this, position, root)
                val arrowToEdgeOffset = popupDrawable?.config?.cornerRadius ?: 0f
                doOnLayout {
                    it.pivotX = it.width - arrowToEdgeOffset
                    it.pivotY = it.height.toFloat()
                }
                setOnClickListener { educationClickHandler() }
            }

        rootView = root
        animator = createAnimator()

        root.addView(scrimView)
        root.addView(educationView)
        animateTransition(show = true) {
            controller.hasSeenStackEducation = true
            listener.onEducationVisibilityChanged(isVisible = true)
        }
    }

    /**
     * Show manage bubble education if hasn't been shown before
     *
     * @param bubble the bubble used for the manage education check
     * @param root the view to show manage education in
     */
    fun maybeShowManageEducation(bubble: BubbleViewProvider, root: ViewGroup) {
        log { "maybeShowManageEducation bubble: $bubble" }
        if (!controller.shouldShowManageEducation(bubble)) return
        showManageEducation(root)
    }

    /**
     * Show manage education with animation
     *
     * @param root the view to show manage education in
     */
    private fun showManageEducation(root: ViewGroup) {
        hideEducation(animated = false)
        log { "showManageEducation" }

        educationView =
            createEducationView(R.layout.bubble_bar_manage_education, root).apply {
                pivotY = 0f
                doOnLayout { it.pivotX = it.width / 2f }
                setOnClickListener { hideEducation(animated = true) }
            }

        rootView = root
        animator = createAnimator()

        root.addView(scrimView)
        root.addView(educationView)
        animateTransition(show = true) {
            controller.hasSeenManageEducation = true
            listener.onEducationVisibilityChanged(isVisible = true)
        }
    }

    /**
     * Animate show/hide transition for the education view
     *
     * @param show whether to show or hide the view
     * @param endActions a closure to be called when the animation completes
     */
    private fun animateTransition(show: Boolean, endActions: () -> Unit) {
        animator
            ?.spring(DynamicAnimation.ALPHA, if (show) 1f else 0f)
            ?.spring(DynamicAnimation.SCALE_X, if (show) 1f else EDU_SCALE_HIDDEN)
            ?.spring(DynamicAnimation.SCALE_Y, if (show) 1f else EDU_SCALE_HIDDEN)
            ?.withEndActions(endActions)
            ?.start()
            ?: endActions()
    }

    /** Remove education view from the root and clean up all relative properties */
    private fun cleanUp() {
        log { "cleanUp" }
        rootView?.removeView(educationView)
        rootView?.removeView(scrimView)
        educationView = null
        rootView = null
        animator = null
    }

    /**
     * Create education view by inflating layout provided.
     *
     * @param layout layout resource id to inflate. The root view should be [BubblePopupView]
     * @param root view group to use as root for inflation, is not attached to root
     */
    private fun createEducationView(@LayoutRes layout: Int, root: ViewGroup): BubblePopupView {
        val view = LayoutInflater.from(context).inflate(layout, root, false) as BubblePopupView
        view.setup()
        view.alpha = 0f
        view.scaleX = EDU_SCALE_HIDDEN
        view.scaleY = EDU_SCALE_HIDDEN
        return view
    }

    /** Create animator for the user education transitions */
    private fun createAnimator(): PhysicsAnimator<BubblePopupView>? {
        return educationView?.let {
            PhysicsAnimator.getInstance(it).apply { setDefaultSpringConfig(springConfig) }
        }
    }

    /**
     * Update user education view position relative to the reference position
     *
     * @param view the user education view to layout
     * @param position the reference position in Screen coordinates
     * @param root the root view to use for the layout
     */
    private fun updateEducationPosition(view: BubblePopupView, position: Point, root: ViewGroup) {
        val rootBounds = Rect()
        // Get root bounds on screen as position is in screen coordinates
        root.getBoundsOnScreen(rootBounds)
        // Get the offset to the arrow from the edge of the education view
        val arrowToEdgeOffset =
            view.popupDrawable?.config?.let { it.cornerRadius + it.arrowWidth / 2f }?.roundToInt()
                ?: 0
        // Calculate education view margins
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.bottomMargin = rootBounds.bottom - position.y
        params.rightMargin = rootBounds.right - position.x - arrowToEdgeOffset
        view.layoutParams = params
    }

    private fun log(msg: () -> String) {
        if (DEBUG_USER_EDUCATION) Log.d(TAG, msg())
    }

    companion object {
        private val TAG = if (TAG_WITH_CLASS_NAME) "BubbleEducationViewController" else TAG_BUBBLES
        private const val EDU_SCALE_HIDDEN = 0.5f
    }
}
