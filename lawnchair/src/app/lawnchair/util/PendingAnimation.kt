package app.lawnchair.util

import android.animation.Animator
import com.android.launcher3.anim.PendingAnimation

fun PendingAnimation.runOnEnd(block: () -> Unit) {
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {

        }

        override fun onAnimationEnd(animation: Animator) {
            block()
        }

        override fun onAnimationCancel(animation: Animator) {

        }

        override fun onAnimationRepeat(animation: Animator) {

        }
    })
}
