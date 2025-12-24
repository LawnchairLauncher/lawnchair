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

package com.android.wm.shell.windowdecor

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.View.ALPHA
import android.view.View.SCALE_X
import android.view.View.SCALE_Y
import android.view.View.TRANSLATION_Y
import android.view.View.TRANSLATION_Z
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.window.DesktopExperienceFlags
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.wm.shell.R
import com.android.wm.shell.shared.animation.Interpolators
import kotlin.math.max

/** Animates the Handle Menu opening. */
class HandleMenuAnimator(
    private val context: Context,
    private val handleMenu: View,
    private val menuWidth: Int,
    private val captionHeight: Float,
) {
    companion object {
        // Open animation constants
        private const val MENU_Y_TRANSLATION_OPEN_DURATION: Long = 150
        private const val HEADER_NONFREEFORM_SCALE_OPEN_DURATION: Long = 150
        private const val HEADER_FREEFORM_SCALE_OPEN_DURATION: Long = 217
        private const val HEADER_ELEVATION_OPEN_DURATION: Long = 83
        private const val HEADER_CONTENT_ALPHA_OPEN_DURATION: Long = 100
        private const val BODY_SCALE_OPEN_DURATION: Long = 180
        private const val BODY_ALPHA_OPEN_DURATION: Long = 150
        private const val BODY_ELEVATION_OPEN_DURATION: Long = 83
        private const val BODY_CONTENT_ALPHA_OPEN_DURATION: Long = 167

        private const val ELEVATION_OPEN_DELAY: Long = 33
        private const val HEADER_CONTENT_ALPHA_OPEN_DELAY: Long = 67
        private const val BODY_SCALE_OPEN_DELAY: Long = 50
        private const val BODY_ALPHA_OPEN_DELAY: Long = 133

        private const val HALF_INITIAL_SCALE: Float = 0.5f
        private const val NONFREEFORM_HEADER_INITIAL_SCALE_X: Float = 0.6f
        private const val NONFREEFORM_HEADER_INITIAL_SCALE_Y: Float = 0.05f

        // Close animation constants
        private const val HEADER_CLOSE_DELAY: Long = 20
        private const val HEADER_CLOSE_DURATION: Long = 50
        private const val HEADER_CONTENT_OPACITY_CLOSE_DELAY: Long = 25
        private const val HEADER_CONTENT_OPACITY_CLOSE_DURATION: Long = 25
        private const val BODY_CLOSE_DURATION: Long = 50

        // Handle->menu animation constants
        private const val WIDTH_SWAP_FRACTION = 0.19f
        private const val HEADER_ICON_NAME_SWAP_FRACTION = 0.1f
        private const val HEADER_MENU_NAME_LATE_FADE_IN_FRACTION = 0.8f
        private const val HANDLE_MENU_OPEN_CLOSE_DURATION: Long = 600

        private const val HEADER_MENU_OPEN_CLOSE_DURATION: Long = 600
    }

    private val animators: MutableList<Animator> = mutableListOf()
    private val argbEvaluator: ArgbEvaluator = ArgbEvaluator.getInstance()
    private var runningAnimation: AnimatorSet? = null

    private val appInfoPill: ViewGroup = handleMenu.requireViewById(R.id.app_info_pill)
    private val windowingPill: ViewGroup = handleMenu.requireViewById(R.id.windowing_pill)
    private val moreActionsPill: ViewGroup = handleMenu.requireViewById(R.id.more_actions_pill)
    private val openInAppOrBrowserPill: ViewGroup =
        handleMenu.requireViewById(R.id.open_in_app_or_browser_pill)

    private val windowDecorHeight: Float =
        context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_fullscreen_decor_caption_height)
            .toFloat()
    private val handleHeight: Float =
        context.resources.getDimensionPixelSize(R.dimen.app_handle_height).toFloat()
    private val handleWidth: Float =
        context.resources.getDimensionPixelSize(R.dimen.app_handle_width).toFloat()
    private val handleMenuWidth: Float =
        context.resources.getDimensionPixelSize(R.dimen.desktop_mode_handle_menu_width).toFloat()
    private val appInfoPillHeight: Float =
        context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_handle_menu_app_info_pill_height)
            .toFloat()
    private val marginMenuTop =
        context.resources.getDimensionPixelSize(R.dimen.desktop_mode_handle_menu_margin_top)
    private val marginMenuLeftRightPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.desktop_mode_handle_menu_padding_left_bottom_right
        )
    private val menuItemElevation: Int =
        context.getResources().getDimensionPixelSize(R.dimen.app_menu_elevation)
    private val menuAppIconHeight: Int =
        context.resources.getDimensionPixelSize(R.dimen.desktop_mode_handle_menu_icon_radius)
    private val headerAppIconHeight: Int =
        context.resources.getDimensionPixelSize(R.dimen.desktop_mode_caption_icon_radius)

    /**
     * Animates the App Header from resting state to the menu, giving an illusion they are the same
     * surface.
     */
    fun animateCaptionHeaderExpandToOpen(headerView: View) {
        if (
            DesktopExperienceFlags.ENABLE_DRAWING_APP_HANDLE.isTrue &&
                DesktopExperienceFlags.ENABLE_TALL_APP_HEADERS.isTrue
        ) {
            setupHeaderAnimator(headerView, true /* expand */)
        } else {
            prepareMenuForAnimation()
            appInfoPillExpand()
            animateAppInfoPillOpen()
            animateWindowingPillOpen()
            animateMoreActionsPillOpen()
            animateOpenInAppOrBrowserPill()
        }
        val animationCallback: () -> Unit = {
            appInfoPill.post {
                appInfoPill
                    .requireViewById<View>(R.id.collapse_menu_button)
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
        }
        if (!handleMenu.isAttachedToWindow) {
            handleMenu.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        runAnimations(animationCallback)
                        handleMenu.removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                }
            )
        } else {
            runAnimations(animationCallback)
        }
    }

    /**
     * Animates the opening of the handle menu. The caption handle in full screen and split screen
     * will expand until it assumes the shape of the app info pill. Then, the other two pills will
     * appear.
     */
    fun animateCaptionHandleExpandToOpen(handleView: View) {
        if (DesktopExperienceFlags.ENABLE_DRAWING_APP_HANDLE.isTrue) {

            setupHandleAnimator(handleView, true /* expand */)
        } else {
            prepareMenuForAnimation()
            captionHandleExpandIntoAppInfoPill()
            animateAppInfoPillOpen()
            animateWindowingPillOpen()
            animateMoreActionsPillOpen()
            animateOpenInAppOrBrowserPill()
        }
        val animationCallback: () -> Unit = {
            appInfoPill.post {
                appInfoPill
                    .requireViewById<View>(R.id.collapse_menu_button)
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
        }
        if (!handleMenu.isAttachedToWindow) {
            handleMenu.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        runAnimations(animationCallback)
                        handleMenu.removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                }
            )
        } else {
            runAnimations(animationCallback)
        }
    }

    /**
     * Animates the closing of the handle menu. The windowing and more actions pill vanish. Then,
     * the app info pill will collapse into the shape of the caption handle in full screen and split
     * screen.
     *
     * @param after runs after the animation finishes.
     */
    fun animateCollapseIntoHandleClose(handleView: View, after: () -> Unit) {
        if (DesktopExperienceFlags.ENABLE_DRAWING_APP_HANDLE.isTrue) {
            setupHandleAnimator(handleView, false /* expand */)
        } else {
            appInfoCollapseToHandle()
            animateAppInfoPillFadeOut()
            windowingPillClose()
            moreActionsPillClose()
            openInAppOrBrowserPillClose()
        }
        runAnimations(after)
    }

    /**
     * Animates the closing of the handle menu. The windowing and more actions pill vanish. Then,
     * the app info pill will collapse into the shape of the caption handle in full screen and split
     * screen.
     *
     * @param after runs after animation finishes.
     */
    fun animateCollapseIntoHeaderClose(headerView: View, after: () -> Unit) {
        if (
            DesktopExperienceFlags.ENABLE_DRAWING_APP_HANDLE.isTrue &&
                DesktopExperienceFlags.ENABLE_TALL_APP_HEADERS.isTrue
        ) {
            setupHeaderAnimator(headerView, false /* expand */)
        } else {
            appInfoPillCollapse()
            animateAppInfoPillFadeOut()
            windowingPillClose()
            moreActionsPillClose()
            openInAppOrBrowserPillClose()
        }
        runAnimations(after)
    }

    private fun setupHandleAnimator(handleView: View, expand: Boolean) {
        val showMenuAnimation =
            if (expand) {
                    ValueAnimator.ofFloat(0f, 1f)
                } else {
                    ValueAnimator.ofFloat(1f, 0f)
                }
                .apply {
                    duration = HANDLE_MENU_OPEN_CLOSE_DURATION
                    interpolator = EMPHASIZED
                }
        val widthDiff: Int = (handleMenuWidth - handleWidth).toInt()
        val targetWidth: Float = handleWidth + widthDiff * WIDTH_SWAP_FRACTION
        val swapScale: Float = targetWidth / handleMenuWidth
        val targetHeight: Float = targetWidth / handleMenuWidth * appInfoPillHeight

        // Calculating deltas
        val handleWidthDelta: Float = targetWidth - handleWidth
        val handleHeightDelta = targetHeight - handleHeight

        showMenuAnimation.addUpdateListener { animator ->
            val progress: Float = animator.animatedValue as Float
            val showHandle: Boolean = (progress <= WIDTH_SWAP_FRACTION)
            handleView.isVisible = showHandle
            handleMenu.isVisible = !showHandle
            if (showHandle) {
                val handleAnimationProgress: Float = progress / WIDTH_SWAP_FRACTION
                val heightIncrement = handleHeightDelta * handleAnimationProgress
                val widthIncrement = handleWidthDelta * handleAnimationProgress
                val scaleYGrowth: Float = (heightIncrement + handleHeight) / handleHeight
                val scaleXGrowth: Float = (widthIncrement + handleWidth) / handleWidth
                handleView.pivotY = windowDecorHeight / 2
                handleView.pivotX = handleWidth / 2
                handleView.scaleX = scaleXGrowth
                handleView.scaleY = scaleYGrowth
                handleView.translationY = heightIncrement / 2 * handleAnimationProgress
            } else {
                val menuAnimationProgress: Float =
                    (progress - WIDTH_SWAP_FRACTION) / (1 - WIDTH_SWAP_FRACTION)
                val oldCenterY =
                    captionHeight / 2 + (handleHeight + handleHeightDelta * progress) / 2
                val currentMenuScale = swapScale + (1 - swapScale) * menuAnimationProgress
                val finalCenterY = currentMenuScale * appInfoPillHeight / 2 + marginMenuTop
                val diff = oldCenterY - finalCenterY
                handleMenu.translationY = diff * (1 - menuAnimationProgress)
                handleMenu.pivotY = 0f
                handleMenu.pivotX = handleMenu.width.toFloat() / 2
                handleMenu.scaleX = currentMenuScale
                handleMenu.scaleY = currentMenuScale

                appInfoPill.children.forEach { it.alpha = progress }
                appInfoPill.elevation = menuItemElevation * progress
                animateRestOfTheMenu(menuAnimationProgress)
            }
        }
        animators += showMenuAnimation
    }

    private fun setupHeaderAnimator(headerView: View, expand: Boolean) {
        val showMenuAnimation =
            if (expand) {
                    ValueAnimator.ofFloat(0f, 1f)
                } else {
                    ValueAnimator.ofFloat(1f, 0f)
                }
                .apply {
                    duration = HEADER_MENU_OPEN_CLOSE_DURATION
                    interpolator = EMPHASIZED
                }
        val openMenuView = headerView.findViewById<LinearLayout>(R.id.open_menu_button)
        val headerAppIcon = openMenuView.findViewById<View>(R.id.application_icon)
        val headerAppName = openMenuView.findViewById<TextView>(R.id.application_name)
        val expandMenuButton = openMenuView.findViewById<View>(R.id.expand_menu_button)
        val menuAppIcon = appInfoPill.findViewById<View>(R.id.application_icon)
        val menuAppName = appInfoPill.findViewById<TextView>(R.id.application_name)
        val startingMenuAppNameColor = headerAppName.textColors.defaultColor
        val finalMenuAppNameColor = menuAppName.textColors.defaultColor

        val shouldLateFadeInAppName =
            headerAppName.isGone || headerAppName.text == null || headerAppName.text.isEmpty()
        val startingScaleForMenu = headerAppIconHeight.toFloat() / menuAppIconHeight.toFloat()

        showMenuAnimation.addUpdateListener { animator ->
            val progress: Float = animator.animatedValue as Float
            if (progress < HEADER_ICON_NAME_SWAP_FRACTION) {
                val headerAppNameAndIconProgress = 1f - progress / HEADER_ICON_NAME_SWAP_FRACTION
                headerAppIcon.alpha = headerAppNameAndIconProgress
                headerAppName.alpha = headerAppNameAndIconProgress
                expandMenuButton.alpha = headerAppNameAndIconProgress
                val menuIconsAndNameProgress = progress / HEADER_ICON_NAME_SWAP_FRACTION
                menuAppIcon.alpha = menuIconsAndNameProgress
                if (!shouldLateFadeInAppName) {
                    menuAppName.alpha = menuIconsAndNameProgress
                }
            } else {
                headerAppIcon.alpha = 0f
                headerAppName.alpha = 0f
                expandMenuButton.alpha = 0f
                menuAppIcon.alpha = 1f
                if (!shouldLateFadeInAppName) {
                    menuAppName.alpha = 1f
                }
            }

            val currentMenuScale = (1 - startingScaleForMenu) * progress + startingScaleForMenu
            // First, scale and translate the menu itself accordingly
            handleMenu.pivotY = 0f
            handleMenu.pivotX = marginMenuLeftRightPadding.toFloat() * (1 / startingScaleForMenu)
            handleMenu.scaleX = currentMenuScale
            handleMenu.scaleY = currentMenuScale
            handleMenu.translationY = -marginMenuTop.toFloat() / 2 * (1 - progress)
            handleMenu.translationX =
                marginMenuLeftRightPadding.toFloat() / 3f *
                    (1 / startingScaleForMenu) *
                    (1 - progress)

            // Next, transform the individual parts of the app pill
            // appInfoPill: fade in the background
            // appName: since we want to keep the text the same size, we are un-scaling it, and
            // move it in the x-axis to keep the spacing the same at the beginning
            // CollapseMenuButton: fade it in, but after the handoff fraction period
            appInfoPill.background.alpha = (progress * 255f).toInt()
            appInfoPill.elevation = menuItemElevation * progress
            menuAppName.scaleX = 1f / currentMenuScale
            menuAppName.scaleY = 1f / currentMenuScale
            menuAppName.translationX = marginMenuLeftRightPadding.toFloat() * (3f) * (1f - progress)
            val color =
                argbEvaluator.evaluate(progress, startingMenuAppNameColor, finalMenuAppNameColor)
                    as Int
            menuAppName.setTextColor(ColorStateList.valueOf(color))
            if (shouldLateFadeInAppName) {
                if (progress > (1 - HEADER_MENU_NAME_LATE_FADE_IN_FRACTION)) {
                    menuAppName.alpha =
                        (progress - (1 - HEADER_MENU_NAME_LATE_FADE_IN_FRACTION)) /
                            HEADER_MENU_NAME_LATE_FADE_IN_FRACTION
                } else {
                    menuAppName.alpha = 0f
                }
            }
            val collapsedMenuButtonAnimationProgress: Float =
                (progress - HEADER_ICON_NAME_SWAP_FRACTION) / (1 - HEADER_ICON_NAME_SWAP_FRACTION)
            val collapseMenuButton = appInfoPill.findViewById<View>(R.id.collapse_menu_button)
            collapseMenuButton.alpha = collapsedMenuButtonAnimationProgress

            animateRestOfTheMenu(progress)
        }
        animators += showMenuAnimation
    }

    private fun animateRestOfTheMenu(progress: Float) {
        val showMenuStagesCount = 3
        val actionsBackgroundAlpha =
            max(0f, (progress - 1f / showMenuStagesCount) * (showMenuStagesCount - 1))
        val actionItemsAlpha = max(0f, (progress - 2f / showMenuStagesCount) * showMenuStagesCount)
        windowingPill.setAlpha(actionsBackgroundAlpha)
        windowingPill.setElevation(menuItemElevation * actionsBackgroundAlpha)
        windowingPill.children.forEach { it.alpha = actionItemsAlpha }
        moreActionsPill.setAlpha(actionsBackgroundAlpha)
        moreActionsPill.setElevation(menuItemElevation * actionsBackgroundAlpha)
        moreActionsPill.children.forEach { it.alpha = actionItemsAlpha }
        openInAppOrBrowserPill.setAlpha(actionsBackgroundAlpha)
        openInAppOrBrowserPill.setElevation(menuItemElevation * actionsBackgroundAlpha)
        openInAppOrBrowserPill.children.forEach { it.alpha = actionItemsAlpha }
    }

    /**
     * Prepares the handle menu for animation. Presets the opacity of necessary menu components.
     * Presets pivots of handle menu and body pills for scaling animation.
     */
    private fun prepareMenuForAnimation() {
        // Preset opacity
        appInfoPill.children.forEach { it.alpha = 0f }
        windowingPill.alpha = 0f
        moreActionsPill.alpha = 0f
        openInAppOrBrowserPill.alpha = 0f

        // Setup pivots.
        handleMenu.pivotX = menuWidth / 2f
        handleMenu.pivotY = 0f

        windowingPill.pivotX = menuWidth / 2f
        windowingPill.pivotY = appInfoPill.measuredHeight.toFloat()

        moreActionsPill.pivotX = menuWidth / 2f
        moreActionsPill.pivotY = appInfoPill.measuredHeight.toFloat()

        openInAppOrBrowserPill.pivotX = menuWidth / 2f
        openInAppOrBrowserPill.pivotY = appInfoPill.measuredHeight.toFloat()
    }

    private fun animateAppInfoPillOpen() {
        // Header Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = HEADER_ELEVATION_OPEN_DURATION
            }

        // Content Opacity Animation
        appInfoPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = HEADER_CONTENT_ALPHA_OPEN_DELAY
                    duration = HEADER_CONTENT_ALPHA_OPEN_DURATION
                }
        }
    }

    private fun captionHandleExpandIntoAppInfoPill() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, NONFREEFORM_HEADER_INITIAL_SCALE_X, 1f)
                .apply { duration = HEADER_NONFREEFORM_SCALE_OPEN_DURATION }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, NONFREEFORM_HEADER_INITIAL_SCALE_Y, 1f)
                .apply { duration = HEADER_NONFREEFORM_SCALE_OPEN_DURATION }

        // Downward y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(handleMenu, TRANSLATION_Y, yStart, 0f).apply {
                duration = MENU_Y_TRANSLATION_OPEN_DURATION
            }
    }

    private fun appInfoPillExpand() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                duration = HEADER_FREEFORM_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                duration = HEADER_FREEFORM_SCALE_OPEN_DURATION
            }
    }

    private fun animateWindowingPillOpen() {
        // Windowing X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        // Windowing Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_ALPHA_OPEN_DURATION
            }

        // Windowing Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = BODY_ELEVATION_OPEN_DURATION
            }

        // Windowing Content Opacity Animation
        windowingPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = BODY_ALPHA_OPEN_DELAY
                    duration = BODY_CONTENT_ALPHA_OPEN_DURATION
                    interpolator = Interpolators.FAST_OUT_SLOW_IN
                }
        }
    }

    private fun animateMoreActionsPillOpen() {
        // More Actions X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        // More Actions Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_ALPHA_OPEN_DURATION
            }

        // More Actions Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = BODY_ELEVATION_OPEN_DURATION
            }

        // More Actions Content Opacity Animation
        moreActionsPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = BODY_ALPHA_OPEN_DELAY
                    duration = BODY_CONTENT_ALPHA_OPEN_DURATION
                    interpolator = Interpolators.FAST_OUT_SLOW_IN
                }
        }
    }

    private fun animateOpenInAppOrBrowserPill() {
        // Open in Browser X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        // Open in Browser Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_ALPHA_OPEN_DURATION
            }

        // Open in Browser Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = BODY_ELEVATION_OPEN_DURATION
            }

        // Open in Browser Button Opacity Animation
        val button =
            openInAppOrBrowserPill.requireViewById<View>(R.id.open_in_app_or_browser_button)
        animators +=
            ObjectAnimator.ofFloat(button, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_CONTENT_ALPHA_OPEN_DURATION
                interpolator = Interpolators.FAST_OUT_SLOW_IN
            }
    }

    private fun appInfoPillCollapse() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, 0f).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, 0f).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
    }

    private fun appInfoCollapseToHandle() {
        // Header X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, NONFREEFORM_HEADER_INITIAL_SCALE_X).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, NONFREEFORM_HEADER_INITIAL_SCALE_Y).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
        // Upward y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, TRANSLATION_Y, yStart).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
    }

    private fun animateAppInfoPillFadeOut() {
        // Header Content Opacity Animation
        appInfoPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 0f).apply {
                    startDelay = HEADER_CONTENT_OPACITY_CLOSE_DELAY
                    duration = HEADER_CONTENT_OPACITY_CLOSE_DURATION
                }
        }
    }

    private fun windowingPillClose() {
        // Windowing X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_X, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_Y, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        // windowing Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }
    }

    private fun moreActionsPillClose() {
        // More Actions X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_X, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_Y, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        // More Actions Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        // upward more actions pill y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, TRANSLATION_Y, yStart).apply {
                duration = BODY_CLOSE_DURATION
            }
    }

    private fun openInAppOrBrowserPillClose() {
        // Open in Browser X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, SCALE_X, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, SCALE_Y, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        // Open in Browser Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        // Upward Open in Browser y-translation Animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(openInAppOrBrowserPill, TRANSLATION_Y, yStart).apply {
                duration = BODY_CLOSE_DURATION
            }
    }

    /**
     * Runs the list of hide animators concurrently.
     *
     * @param after runs after animation finishes.
     */
    private fun runAnimations(after: (() -> Unit)? = null) {
        runningAnimation?.apply {
            // Remove all listeners, so that the after function isn't triggered upon cancel.
            removeAllListeners()
            // If an animation runs while running animation is triggered, gracefully cancel.
            cancel()
        }

        runningAnimation =
            AnimatorSet().apply {
                playTogether(animators)
                animators.clear()
                doOnEnd {
                    after?.invoke()
                    runningAnimation = null
                }
                start()
            }
    }
}
