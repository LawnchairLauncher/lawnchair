package com.android.systemui.unfold.util

import android.content.Context
import android.os.RemoteException
import android.view.IRotationWatcher
import android.view.IWindowManager
import android.view.Surface
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener

/**
 * [UnfoldTransitionProgressProvider] that emits transition progress only when the display has
 * default rotation or 180 degrees opposite rotation (ROTATION_0 or ROTATION_180).
 * It could be helpful to run the animation only when the display's rotation is perpendicular
 * to the fold.
 */
class NaturalRotationUnfoldProgressProvider(
    private val context: Context,
    private val windowManagerInterface: IWindowManager,
    unfoldTransitionProgressProvider: UnfoldTransitionProgressProvider
) : UnfoldTransitionProgressProvider {

    private val scopedUnfoldTransitionProgressProvider =
            ScopedUnfoldTransitionProgressProvider(unfoldTransitionProgressProvider)
    private val rotationWatcher = RotationWatcher()

    private var isNaturalRotation: Boolean = false

    fun init() {
        try {
            windowManagerInterface.watchRotation(rotationWatcher, context.display.displayId)
        } catch (e: RemoteException) {
            throw e.rethrowFromSystemServer()
        }

        onRotationChanged(context.display.rotation)
    }

    private fun onRotationChanged(rotation: Int) {
        val isNewRotationNatural = rotation == Surface.ROTATION_0 ||
                rotation == Surface.ROTATION_180

        if (isNaturalRotation != isNewRotationNatural) {
            isNaturalRotation = isNewRotationNatural
            scopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(isNewRotationNatural)
        }
    }

    override fun destroy() {
        try {
            windowManagerInterface.removeRotationWatcher(rotationWatcher)
        } catch (e: RemoteException) {
            e.rethrowFromSystemServer()
        }

        scopedUnfoldTransitionProgressProvider.destroy()
    }

    override fun addCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.addCallback(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.removeCallback(listener)
    }

    private inner class RotationWatcher : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            this@NaturalRotationUnfoldProgressProvider.onRotationChanged(rotation)
        }
    }
}
