package com.android.systemui.unfold.progress

import android.os.Trace
import android.util.Log
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.unfold.UnfoldTransitionProgressProvider

/**
 * Makes progress received from other processes resilient to jank.
 *
 * Sender and receiver processes might have different frame-rates. If the sending process is
 * dropping a frame due to jank (or generally because it's main thread is too busy), we don't want
 * the receiving process to drop progress frames as well. For this reason, a spring animator pass
 * (with very high stiffness) is applied to the incoming progress. This adds a small delay to the
 * progress (~30ms), but guarantees an always smooth animation on the receiving end.
 */
class UnfoldRemoteFilter(
    private val listener: UnfoldTransitionProgressProvider.TransitionProgressListener
) : UnfoldTransitionProgressProvider.TransitionProgressListener {

    private val springAnimation =
        SpringAnimation(this, AnimationProgressProperty).apply {
            spring =
                SpringForce().apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                    stiffness = 100_000f
                    finalPosition = 1.0f
                }
            setMinValue(0f)
            setMaxValue(1f)
            minimumVisibleChange = 0.001f
        }

    private var inProgress = false

    private var processedProgress: Float = 1.0f
        set(newProgress) {
            if (inProgress) {
                logCounter({ "$TAG#filtered_progress" }, newProgress)
                listener.onTransitionProgress(newProgress)
            } else {
                Log.e(TAG, "Filtered progress received received while animation not in progress.")
            }
            field = newProgress
        }

    override fun onTransitionStarted() {
        listener.onTransitionStarted()
        inProgress = true
    }

    override fun onTransitionProgress(progress: Float) {
        logCounter({ "$TAG#plain_remote_progress" }, progress)
        if (inProgress) {
            springAnimation.animateToFinalPosition(progress)
        } else {
            Log.e(TAG, "Progress received while not in progress.")
        }
    }

    override fun onTransitionFinished() {
        inProgress = false
        listener.onTransitionFinished()
    }

    private object AnimationProgressProperty :
        FloatPropertyCompat<UnfoldRemoteFilter>("UnfoldRemoteFilter") {

        override fun setValue(provider: UnfoldRemoteFilter, value: Float) {
            provider.processedProgress = value
        }

        override fun getValue(provider: UnfoldRemoteFilter): Float = provider.processedProgress
    }
    private fun logCounter(name: () -> String, progress: Float) {
        if (DEBUG) {
            Trace.setCounter(name(), (progress * 100).toLong())
        }
    }
}

private val TAG = "UnfoldRemoteFilter"
private val DEBUG = false
