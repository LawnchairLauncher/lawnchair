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
 * limitations under the License.
 */
package com.android.quickstep.util.unfold

import android.app.Activity
import android.os.Trace
import android.view.Surface
import com.android.launcher3.Alarm
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener

/** Controls animations that are happening during unfolding foldable devices */
class LauncherUnfoldTransitionController(
    private val launcher: QuickstepLauncher,
    private val progressProvider: ProxyUnfoldTransitionProvider
) : OnDeviceProfileChangeListener, ActivityLifecycleCallbacksAdapter, TransitionProgressListener {

    private var isTablet: Boolean? = null
    private var hasUnfoldTransitionStarted = false
    private val timeoutAlarm =
        Alarm().apply {
            setOnAlarmListener {
                onTransitionFinished()
                Trace.endAsyncSection("$TAG#startedPreemptively", 0)
            }
        }

    init {
        launcher.addOnDeviceProfileChangeListener(this)
        launcher.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPaused(activity: Activity) {
        progressProvider.removeCallback(this)
    }

    override fun onActivityResumed(activity: Activity) {
        progressProvider.addCallback(this)
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        if (!FeatureFlags.PREEMPTIVE_UNFOLD_ANIMATION_START.get()) {
            return
        }

        if (isTablet != null && dp.isTablet != isTablet) {
            // We should preemptively start the animation only if:
            // - We changed to the unfolded screen
            // - SystemUI IPC connection is alive, so we won't end up in a situation that we won't
            //   receive transition progress events from SystemUI later because there was no
            //   IPC connection established (e.g. because of SystemUI crash)
            // - SystemUI has not already sent unfold animation progress events. This might happen
            //   if Launcher was not open during unfold, in this case we receive the configuration
            //   change only after we went back to home screen and we don't want to start the
            //   animation in this case.
            if (dp.isTablet && progressProvider.isActive && !hasUnfoldTransitionStarted) {
                // Preemptively start the unfold animation to make sure that we have drawn
                // the first frame of the animation before the screen gets unblocked
                onTransitionStarted()
                Trace.beginAsyncSection("$TAG#startedPreemptively", 0)
                timeoutAlarm.setAlarm(PREEMPTIVE_UNFOLD_TIMEOUT_MS)
            }
            if (!dp.isTablet) {
                // Reset unfold transition status when folded
                hasUnfoldTransitionStarted = false
            }
        }

        isTablet = dp.isTablet
    }

    override fun onTransitionStarted() {
        hasUnfoldTransitionStarted = true
        launcher.animationCoordinator.setAnimation(
            provider = this,
            factory = this::onPrepareUnfoldAnimation,
            duration =
                1000L // The expected duration for the animation. Then only comes to play if we have
            // to run the animation ourselves in case sysui misses the end signal
        )
        timeoutAlarm.cancelAlarm()
    }

    override fun onTransitionProgress(progress: Float) {
        hasUnfoldTransitionStarted = true
        launcher.animationCoordinator.getPlaybackController(this)?.setPlayFraction(progress)
    }

    override fun onTransitionFinished() {
        // Run the animation to end the animation in case it is not already at end progress. It
        // will scale the duration to the remaining progress
        launcher.animationCoordinator.getPlaybackController(this)?.start()
        timeoutAlarm.cancelAlarm()
    }

    private fun onPrepareUnfoldAnimation(anim: PendingAnimation) {
        val dp = launcher.deviceProfile
        val rotation = dp.displayInfo.rotation
        val isVertical = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
        UnfoldAnimationBuilder.buildUnfoldAnimation(
            launcher,
            isVertical,
            dp.displayInfo.currentSize,
            anim
        )
    }

    companion object {
        private const val TAG = "LauncherUnfoldTransitionController"
    }
}
