package app.lawnchair.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider
import kotlin.math.roundToInt

/** Reveals the panel without scaling or rearranging its app icons. */
object FolderPanelAnimation {
    @JvmStatic
    fun create(view: View, iconSize: Int, cornerRadius: Float, opening: Boolean): AnimatorSet {
        val width = view.layoutParams.width
        val height = view.layoutParams.height
        val startWidth = iconSize.coerceIn(1, width)
        val startHeight = iconSize.coerceIn(1, height)
        val left = (view.pivotX - startWidth / 2f).roundToInt().coerceIn(0, width - startWidth)
        val top = (view.pivotY - startHeight / 2f).roundToInt().coerceIn(0, height - startHeight)
        val reveal = RoundedRectRevealOutlineProvider(
            minOf(cornerRadius, minOf(startWidth, startHeight) / 2f),
            cornerRadius,
            Rect(left, top, left + startWidth, top + startHeight),
            Rect(0, 0, width, height),
        ).createRevealAnimator(view, !opening)
        return AnimatorSet().apply {
            playTogether(reveal, ObjectAnimator.ofFloat(view, View.ALPHA, if (opening) 0f else 1f, if (opening) 1f else 0f))
            duration = if (opening) 200L else 150L
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Cancellation also ends the animation; never leave a reopened folder faded.
                    view.alpha = 1f
                }
            })
        }
    }
}
