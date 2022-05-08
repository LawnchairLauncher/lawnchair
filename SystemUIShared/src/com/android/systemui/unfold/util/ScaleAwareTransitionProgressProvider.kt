package com.android.systemui.unfold.util

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.Settings
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener

/** Wraps [UnfoldTransitionProgressProvider] to disable transitions when animations are disabled. */
class ScaleAwareTransitionProgressProvider(
    unfoldTransitionProgressProvider: UnfoldTransitionProgressProvider,
    private val contentResolver: ContentResolver
) : UnfoldTransitionProgressProvider {

    private val scopedUnfoldTransitionProgressProvider =
            ScopedUnfoldTransitionProgressProvider(unfoldTransitionProgressProvider)

    private val animatorDurationScaleObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            onAnimatorScaleChanged()
        }
    }

    init {
        contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                /* notifyForDescendants= */ false,
                animatorDurationScaleObserver)
        onAnimatorScaleChanged()
    }

    private fun onAnimatorScaleChanged() {
        val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
        scopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(animationsEnabled)
    }

    override fun addCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.addCallback(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.removeCallback(listener)
    }

    override fun destroy() {
        contentResolver.unregisterContentObserver(animatorDurationScaleObserver)
        scopedUnfoldTransitionProgressProvider.destroy()
    }
}
