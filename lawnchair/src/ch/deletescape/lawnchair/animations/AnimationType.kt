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

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.*

abstract class AnimationType {

    open val allowWallpaperOpenRemoteAnimation = true

    open fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
        return null
    }

    open fun playLaunchAnimation(launcher: Launcher, v: View?, intent: Intent) {

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
            val bounds = getBounds(v) ?: return super.getActivityLaunchOptions(launcher, v)
            return ActivityOptions.makeClipRevealAnimation(v, bounds.left, bounds.top, bounds.width(), bounds.height())
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
