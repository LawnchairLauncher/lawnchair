package com.android.systemui.animation

import android.annotation.UiThread
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import android.view.WindowManager

/**
 * A component capable of running remote animations.
 *
 * Expands the IRemoteAnimationRunner API by allowing for different types of more specialized
 * callbacks.
 */
interface RemoteAnimationDelegate<in T : IRemoteAnimationFinishedCallback> {
    /**
     * Called on the UI thread when the animation targets are received. Sets up and kicks off the
     * animation.
     */
    @UiThread
    fun onAnimationStart(
        @WindowManager.TransitionOldType transit: Int,
        apps: Array<out RemoteAnimationTarget>?,
        wallpapers: Array<out RemoteAnimationTarget>?,
        nonApps: Array<out RemoteAnimationTarget>?,
        callback: T?
    )

    /** Called on the UI thread when a signal is received to cancel the animation. */
    @UiThread fun onAnimationCancelled(isKeyguardOccluded: Boolean)
}
