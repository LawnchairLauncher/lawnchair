/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.extensions.w
import com.android.launcher3.*
import com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS
import com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE
import com.android.launcher3.anim.Interpolators.LINEAR
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.quickstep.util.MultiValueUpdateListener
import java.lang.ref.WeakReference

abstract class AnimationType {

    open val allowWallpaperOpenRemoteAnimation = true

    open fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
        return null
    }

    open fun playLaunchAnimation(launcher: Launcher, v: View?, intent: Intent,
                                 manager: LawnchairAppTransitionManagerImpl) {

    }

    open fun overrideResumeAnimation(launcher: Launcher) {

    }

    fun getBounds(v: View?): Rect? {
        if (v == null) return null
        var left = 0
        var top = 0
        var width = v.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            // Launch from center of icon, not entire view
            val icon = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left = (width - bounds.width()) / 2
                top = v.getPaddingTop()
                width = bounds.width()
                height = bounds.height()
            }
        }
        return Rect(left, top, left + width, top + height)
    }

    class DefaultAnimation : AnimationType()

    class FadeAnimation : AnimationType() {

        override val allowWallpaperOpenRemoteAnimation = false

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            return ActivityOptions.makeCustomAnimation(
                    launcher, R.anim.fade_in_short, R.anim.no_anim_short)
        }

        override fun overrideResumeAnimation(launcher: Launcher) {
            launcher.overridePendingTransition(R.anim.no_anim_short, R.anim.fade_out_short)
        }
    }

    class BlinkAnimation : AnimationType() {

        override val allowWallpaperOpenRemoteAnimation = false

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            return ActivityOptions.makeCustomAnimation(
                    launcher, R.anim.blink_open_enter, R.anim.blink_open_exit)
        }

        override fun overrideResumeAnimation(launcher: Launcher) {
            launcher.overridePendingTransition(R.anim.blink_close_enter, R.anim.blink_close_exit)
        }
    }

    class ScaleUpAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            val bounds = getBounds(v) ?: return super.getActivityLaunchOptions(launcher, v)
            return ActivityOptions.makeScaleUpAnimation(v, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
    }

    class SlideUpAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            return ActivityOptions.makeCustomAnimation(
                    launcher, R.anim.task_open_enter, R.anim.no_anim)
        }
    }

    class RevealAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            if (!Utilities.ATLEAST_MARSHMALLOW) return super.getActivityLaunchOptions(launcher, v)
            val bounds = getBounds(v) ?: return super.getActivityLaunchOptions(launcher, v)
            return ActivityOptions.makeClipRevealAnimation(v, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
    }

    class PieAnimation : AnimationType() {

        private var lastView: WeakReference<View>? = null

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            if (hasControlRemoteAppTransitionPermission(launcher)) return null
            lastView = v?.let { WeakReference(it) }
            return ActivityOptions.makeCustomAnimation(
                    launcher, R.anim.dummy_anim_enter, R.anim.dummy_anim_exit)
        }

        override fun playLaunchAnimation(launcher: Launcher, v: View?, intent: Intent,
                                         manager: LawnchairAppTransitionManagerImpl) {
            val view = v ?: lastView?.get() ?: return
            if (!hasControlRemoteAppTransitionPermission(launcher)) {
                val prefs = launcher.lawnchairPrefs
                if (prefs.useScaleAnim) {
                    w("scale anim is not supported, turning it off")
                    prefs.useScaleAnim = false
                }

                val anim = AnimatorSet()

                val splashData = SplashResolver.getInstance(launcher).loadSplash(intent)
                val splashView = SplashLayout(launcher).apply {
                    alpha = 0f
                    applySplash(splashData)
                }
                val dp = launcher.deviceProfile
                val rootView = launcher.dragLayer.parent as ViewGroup
                rootView.addView(splashView, dp.widthPx, dp.heightPx)

                // Set the state animation first so that any state listeners are called
                // before our internal listeners.
                launcher.stateManager.setCurrentAnimation(anim)

                val windowTargetBounds = getWindowTargetBounds(launcher)
                manager.playIconAnimators(anim, view, windowTargetBounds)
                val launcherContentAnimator = manager.getLauncherContentAnimator(true /* isAppOpening */)
                anim.play(launcherContentAnimator.first)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        launcher.addForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        launcher.clearForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS)
                    }
                })
                anim.play(getOpeningWindowAnimators(
                        launcher, view, splashView, manager.floatingView, windowTargetBounds))

                launcher.addOnResumeCallback {
                    rootView.removeView(splashView)
                    launcherContentAnimator.second.run()
                }

                anim.start()
            }
        }

        override fun overrideResumeAnimation(launcher: Launcher) {
            if (!hasControlRemoteAppTransitionPermission(launcher)) {
                launcher.overridePendingTransition(R.anim.pie_like_close_enter, R.anim.pie_like_close_exit)
            }
        }

        private fun getWindowTargetBounds(launcher: Launcher): Rect {
            return Rect(0, 0, launcher.deviceProfile.widthPx, launcher.deviceProfile.heightPx)
        }

        private fun getOpeningWindowAnimators(launcher: Launcher, v2: View, splashView: SplashLayout,
                                              floatingView: View,
                                              windowTargetBounds: Rect): ValueAnimator {
            val v = if (v2 is FolderIcon) v2.folderName else v2

            val bounds = Rect()
            when {
                v.parent is DeepShortcutView -> {
                    // Deep shortcut views have their icon drawn in a separate view.
                    val view = v.parent as DeepShortcutView
                    launcher.dragLayer.getDescendantRectRelativeToSelf(view.iconView, bounds)
                }
                v is BubbleTextView -> v.getIconBounds(bounds)
                else -> launcher.dragLayer.getDescendantRectRelativeToSelf(v, bounds)
            }
            val floatingViewBounds = IntArray(2)

            val crop = Rect()

            val appAnimator = ValueAnimator.ofFloat(0f, 1f)
            appAnimator.duration = 500.toLong()
            appAnimator.addUpdateListener(object : MultiValueUpdateListener() {
                // Fade alpha for the app window.
                val mAlpha = createFloatProp(0f, 1f, 0f, 60f, LINEAR)

                override fun onUpdate(percent: Float) {
                    val easePercent = (AGGRESSIVE_EASE)
                            .getInterpolation(percent)

                    // Calculate app icon size.
                    val iconWidth = bounds.width() * floatingView.scaleX
                    val iconHeight = bounds.height() * floatingView.scaleY

                    // Scale the app window to match the icon size.
                    val scaleX = iconWidth / windowTargetBounds.width()
                    val scaleY = iconHeight / windowTargetBounds.height()
                    val scale = Math.min(1f, Math.min(scaleX, scaleY))

                    // Position the scaled window on top of the icon
                    val windowWidth = windowTargetBounds.width()
                    val windowHeight = windowTargetBounds.height()
                    val scaledWindowWidth = windowWidth * scale
                    val scaledWindowHeight = windowHeight * scale

                    val offsetX = (scaledWindowWidth - iconWidth) / 2
                    val offsetY = (scaledWindowHeight - iconHeight) / 2
                    floatingView.getLocationOnScreen(floatingViewBounds)

                    val transX0 = floatingViewBounds[0] - offsetX
                    val transY0 = floatingViewBounds[1] - offsetY

                    // Animate the window crop so that it starts off as a square, and then reveals
                    // horizontally.
                    val cropHeight = windowHeight * easePercent + windowWidth * (1 - easePercent)
                    val initialTop = (windowHeight - windowWidth) / 2f
                    crop.left = 0
                    crop.top = (initialTop * (1 - easePercent)).toInt()
                    crop.right = windowWidth
                    crop.bottom = (crop.top + cropHeight).toInt()

                    splashView.pivotX = 0f
                    splashView.pivotY = 0f
                    splashView.scaleX = scale
                    splashView.scaleY = scale
                    splashView.translationX = transX0
                    splashView.translationY = transY0
                    splashView.alpha = mAlpha.value
                    splashView.setCrop(crop)
                }
            })
            return appAnimator
        }
    }

    companion object {

        private const val CONTROL_REMOTE_APP_TRANSITION_PERMISSION =
                "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS"

        const val TYPE_PIE = "pie"
        const val TYPE_REVEAL = "reveal"
        const val TYPE_SLIDE_UP = "slideUp"
        const val TYPE_SCALE_UP = "scaleUp"
        const val TYPE_BLINK = "blink"
        const val TYPE_FADE = "fade"

        fun fromString(type: String): AnimationType {
            return when (type) {
                TYPE_PIE -> PieAnimation()
                TYPE_REVEAL -> RevealAnimation()
                TYPE_SLIDE_UP -> SlideUpAnimation()
                TYPE_SCALE_UP -> ScaleUpAnimation()
                TYPE_BLINK -> BlinkAnimation()
                TYPE_FADE -> FadeAnimation()
                else -> DefaultAnimation()
            }
        }

        fun toString(type: AnimationType): String {
            return when (type) {
                is PieAnimation -> TYPE_PIE
                is RevealAnimation -> TYPE_REVEAL
                is SlideUpAnimation -> TYPE_SLIDE_UP
                is ScaleUpAnimation -> TYPE_SCALE_UP
                is BlinkAnimation -> TYPE_BLINK
                is FadeAnimation -> TYPE_FADE
                else -> ""
            }
        }

        fun hasControlRemoteAppTransitionPermission(context: Context): Boolean {
            return !context.lawnchairPrefs.forceFakePieAnims
                    && Utilities.hasPermission(context, CONTROL_REMOTE_APP_TRANSITION_PERMISSION)
        }
    }
}
