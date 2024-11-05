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

package com.android.wm.shell.common.bubbles

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.IntProperty
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_LOW_BOUNCY
import androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW
import com.android.wm.shell.shared.animation.PhysicsAnimator

/**
 * View that handles interactions between DismissCircleView and BubbleStackView.
 *
 * @note [setup] method should be called after initialisation
 */
class DismissView(context: Context) : FrameLayout(context) {
    /**
     * The configuration is used to provide module specific resource ids
     *
     * @see [setup] method
     */
    data class Config(
            /** The resource id to set on the dismiss target circle view */
            val dismissViewResId: Int,
            /** dimen resource id of the dismiss target circle view size */
            @DimenRes val targetSizeResId: Int,
            /** dimen resource id of the icon size in the dismiss target */
            @DimenRes val iconSizeResId: Int,
            /** dimen resource id of the bottom margin for the dismiss target */
            @DimenRes var bottomMarginResId: Int,
            /** dimen resource id of the height for dismiss area gradient */
            @DimenRes val floatingGradientHeightResId: Int,
            /** color resource id of the dismiss area gradient color */
            @ColorRes val floatingGradientColorResId: Int,
            /** drawable resource id of the dismiss target background */
            @DrawableRes val backgroundResId: Int,
            /** drawable resource id of the icon for the dismiss target */
            @DrawableRes val iconResId: Int
    )

    companion object {
        private const val SHOULD_SETUP =
                "The view isn't ready. Should be called after `setup`"
        private val TAG = DismissView::class.simpleName
    }

    var circle = DismissCircleView(context)
    var isShowing = false
    var config: Config? = null

    private val animator = PhysicsAnimator.getInstance(circle)
    private val spring = PhysicsAnimator.SpringConfig(STIFFNESS_LOW, DAMPING_RATIO_LOW_BOUNCY)
    private val DISMISS_SCRIM_FADE_MS = 200L
    private var wm: WindowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var gradientDrawable: GradientDrawable? = null

    private val GRADIENT_ALPHA: IntProperty<GradientDrawable> =
            object : IntProperty<GradientDrawable>("alpha") {
        override fun setValue(d: GradientDrawable, percent: Int) {
            d.alpha = percent
        }
        override fun get(d: GradientDrawable): Int {
            return d.alpha
        }
    }

    init {
        setClipToPadding(false)
        setClipChildren(false)
        setVisibility(View.INVISIBLE)
        addView(circle)
    }

    /**
     * Sets up view with the provided resource ids.
     *
     * Decouples resource dependency in order to be used externally (e.g. Launcher). Usually called
     * with default params in module specific extension:
     * @see [DismissView.setup] in DismissViewExt.kt
     */
    fun setup(config: Config) {
        this.config = config

        // Setup layout
        layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(config.floatingGradientHeightResId),
                Gravity.BOTTOM)
        updatePadding()

        // Setup gradient
        gradientDrawable = createGradient(color = config.floatingGradientColorResId)
        setBackgroundDrawable(gradientDrawable)

        // Setup DismissCircleView
        circle.id = config.dismissViewResId
        circle.setup(config.backgroundResId, config.iconResId, config.iconSizeResId)
        val targetSize: Int = resources.getDimensionPixelSize(config.targetSizeResId)
        circle.layoutParams = LayoutParams(targetSize, targetSize,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        // Initial position with circle offscreen so it's animated up
        circle.translationY = resources.getDimensionPixelSize(config.floatingGradientHeightResId)
                .toFloat()
    }

    /**
     * Animates this view in.
     */
    fun show() {
        if (isShowing) return
        val gradientDrawable = checkExists(gradientDrawable) ?: return
        isShowing = true
        setVisibility(View.VISIBLE)
        val alphaAnim = ObjectAnimator.ofInt(gradientDrawable, GRADIENT_ALPHA,
                gradientDrawable.alpha, 255)
        alphaAnim.setDuration(DISMISS_SCRIM_FADE_MS)
        alphaAnim.start()

        animator.cancel()
        animator
            .spring(DynamicAnimation.TRANSLATION_Y, 0f, spring)
            .start()
    }

    /**
     * Animates this view out, as well as the circle that encircles the bubbles, if they
     * were dragged into the target and encircled.
     */
    fun hide() {
        if (!isShowing) return
        val gradientDrawable = checkExists(gradientDrawable) ?: return
        isShowing = false
        val alphaAnim = ObjectAnimator.ofInt(gradientDrawable, GRADIENT_ALPHA,
                gradientDrawable.alpha, 0)
        alphaAnim.setDuration(DISMISS_SCRIM_FADE_MS)
        alphaAnim.start()
        animator
            .spring(DynamicAnimation.TRANSLATION_Y, height.toFloat(),
                spring)
            .withEndActions({
                visibility = View.INVISIBLE
                circle.scaleX = 1f
                circle.scaleY = 1f
            })
            .start()
    }

    /**
     * Cancels the animator for the dismiss target.
     */
    fun cancelAnimators() {
        animator.cancel()
    }

    fun updateResources() {
        val config = checkExists(config) ?: return
        updatePadding()
        layoutParams.height = resources.getDimensionPixelSize(config.floatingGradientHeightResId)
        val targetSize = resources.getDimensionPixelSize(config.targetSizeResId)
        circle.layoutParams.width = targetSize
        circle.layoutParams.height = targetSize
        circle.requestLayout()
    }

    private fun createGradient(@ColorRes color: Int): GradientDrawable {
        val gradientColor = ContextCompat.getColor(context, color)
        val alpha = 0.7f * 255
        val gradientColorWithAlpha = Color.argb(alpha.toInt(),
                Color.red(gradientColor),
                Color.green(gradientColor),
                Color.blue(gradientColor))
        val gd = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(gradientColorWithAlpha, Color.TRANSPARENT))
        gd.setDither(true)
        gd.setAlpha(0)
        return gd
    }

    private fun updatePadding() {
        val config = checkExists(config) ?: return
        val insets: WindowInsets = wm.getCurrentWindowMetrics().getWindowInsets()
        val navInset = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars())
        setPadding(0, 0, 0, navInset.bottom +
                resources.getDimensionPixelSize(config.bottomMarginResId))
    }

    /**
     * Checks if the value is set up and exists, if not logs an exception.
     * Used for convenient logging in case `setup` wasn't called before
     *
     * @return value provided as argument
     */
    private fun <T>checkExists(value: T?): T? {
        if (value == null) Log.e(TAG, SHOULD_SETUP)
        return value
    }
}
