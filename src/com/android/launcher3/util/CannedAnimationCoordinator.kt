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
 * limitations under the License
 */

package com.android.launcher3.util

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.util.Log
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.view.OneShotPreDrawListener
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.states.StateAnimationConfig.HANDLE_STATE_APPLY
import com.android.launcher3.states.StateAnimationConfig.USER_CONTROLLED
import java.util.function.Consumer

private const val TAG = "CannedAnimCoordinator"

/**
 * Utility class to run a canned animation on Launcher.
 *
 * This class takes care to registering animations with stateManager and ensures that only one
 * animation is playing at a time.
 */
class CannedAnimationCoordinator(private val activity: StatefulActivity<*>) {

    private val launcherLayoutListener = OnGlobalLayoutListener { scheduleRecreateAnimOnPreDraw() }
    private var recreatePending = false

    private var animationProvider: Any? = null

    private var animationDuration: Long = 0L
    private var animationFactory: Consumer<PendingAnimation>? = null
    private var animationController: AnimatorPlaybackController? = null

    private var currentAnim: AnimatorPlaybackController? = null

    /**
     * Sets the current animation cancelling any previously set animation.
     *
     * Callers can control the animation using {@link #getPlaybackController}. The state is
     * automatically cleared when the playback controller ends. The animation is automatically
     * recreated when any layout change happens. Callers can also ask for recreation by calling
     * {@link #recreateAnimation}
     */
    fun setAnimation(provider: Any, factory: Consumer<PendingAnimation>, duration: Long) {
        if (provider != animationProvider) {
            Log.e(TAG, "Trying to play two animations together, $provider and $animationProvider")
        }

        // Cancel any previously running animation
        endCurrentAnimation(false)
        animationController?.dispatchOnCancel()?.dispatchOnEnd()

        animationProvider = provider
        animationFactory = factory
        animationDuration = duration

        // Setup a new controller and link it with launcher state animation
        val anim = AnimatorSet()
        anim.play(
            ValueAnimator.ofFloat(0f, 1f).apply {
                interpolator = LINEAR
                this.duration = duration
                addUpdateListener { anim -> currentAnim?.setPlayFraction(anim.animatedFraction) }
            }
        )
        val controller = AnimatorPlaybackController.wrap(anim, duration)
        anim.addListener(
            AnimatorListeners.forEndCallback { success ->
                if (animationController != controller) {
                    return@forEndCallback
                }

                endCurrentAnimation(success)
                animationController = null
                animationFactory = null
                animationProvider = null

                activity.rootView.viewTreeObserver.apply {
                    if (isAlive) {
                        removeOnGlobalLayoutListener(launcherLayoutListener)
                    }
                }
            }
        )

        // Recreate animation whenever layout happens in case transforms change during layout
        activity.rootView.viewTreeObserver.apply {
            if (isAlive) {
                addOnGlobalLayoutListener(launcherLayoutListener)
            }
        }
        // Link this to the state manager so that it auto-cancels when state changes
        recreatePending = false
        // Animator coordinator takes care of reapplying the animation due to state reset. Set the
        // flags accordingly
        animationController =
            controller.apply {
                activity
                    .stateManager
                    .setCurrentAnimation(this, USER_CONTROLLED or HANDLE_STATE_APPLY)
            }
        recreateAnimation(provider)
    }

    private fun endCurrentAnimation(success: Boolean) {
        currentAnim?.apply {
            // When cancelling an animation, apply final progress so that all transformations
            // are restored
            setPlayFraction(1f)
            if (!success) dispatchOnCancel()
            dispatchOnEnd()
        }
        currentAnim = null
    }

    /** Returns the current animation controller to control the animation */
    fun getPlaybackController(provider: Any): AnimatorPlaybackController? {
        return if (provider == animationProvider) animationController
        else {
            Log.d(TAG, "Wrong controller access from $provider, actual provider $animationProvider")
            null
        }
    }

    private fun scheduleRecreateAnimOnPreDraw() {
        if (!recreatePending) {
            recreatePending = true
            OneShotPreDrawListener.add(activity.rootView) {
                if (recreatePending) {
                    recreatePending = false
                    animationProvider?.apply { recreateAnimation(this) }
                }
            }
        }
    }

    /** Notify the controller to recreate the animation. The animation progress is preserved */
    fun recreateAnimation(provider: Any) {
        if (provider != animationProvider) {
            Log.e(TAG, "Ignore recreate request from $provider, actual provider $animationProvider")
            return
        }
        endCurrentAnimation(false /* success */)

        if (animationFactory == null || animationController == null) {
            return
        }
        currentAnim =
            PendingAnimation(animationDuration)
                .apply { animationFactory?.accept(this) }
                .createPlaybackController()
                .apply { setPlayFraction(animationController!!.progressFraction) }
    }
}
