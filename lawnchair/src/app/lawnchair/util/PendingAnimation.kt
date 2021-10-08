package app.lawnchair.util

import android.animation.Animator
import com.android.launcher3.anim.PendingAnimation

fun PendingAnimation.runOnEnd(block: (isSuccess: Boolean) -> Unit) {
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {

        }

        override fun onAnimationEnd(animation: Animator) {
            block(true)
        }

        override fun onAnimationCancel(animation: Animator) {
            block(false)
        }

        override fun onAnimationRepeat(animation: Animator) {

        }
    })
}
