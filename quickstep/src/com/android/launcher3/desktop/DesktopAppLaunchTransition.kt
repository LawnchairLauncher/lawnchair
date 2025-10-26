/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.desktop

import android.animation.Animator
import android.content.Context
import android.os.IBinder
import android.util.Log
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import androidx.core.util.Supplier
import com.android.app.animation.Interpolators
import com.android.internal.jank.Cuj
import com.android.quickstep.RemoteRunnable
import com.android.wm.shell.shared.animation.WindowAnimator
import java.util.concurrent.Executor

/**
 * [android.window.RemoteTransition] for Desktop app launches.
 *
 * This transition supports minimize-changes, i.e. in a launch-transition, if a window is moved back
 * ([android.view.WindowManager.TRANSIT_TO_BACK]) this transition will apply a minimize animation to
 * that window.
 */
class DesktopAppLaunchTransition
@JvmOverloads
constructor(
    context: Context,
    private val launchType: AppLaunchType,
    @Cuj.CujType private val cujType: Int,
    private val mainExecutor: Executor,
    transactionSupplier: Supplier<Transaction> = Supplier { Transaction() },
) : RemoteTransitionStub() {

    private val animatorHelper: DesktopAppLaunchAnimatorHelper =
        DesktopAppLaunchAnimatorHelper(context, launchType, cujType, transactionSupplier)

    enum class AppLaunchType(
        val boundsAnimationParams: WindowAnimator.BoundsAnimationParams,
        val alphaDurationMs: Long,
    ) {
        LAUNCH(launchBoundsAnimationDef, /* alphaDurationMs= */ 200L),
        UNMINIMIZE(unminimizeBoundsAnimationDef, /* alphaDurationMs= */ 100L),
    }

    override fun startAnimation(
        token: IBinder,
        info: TransitionInfo,
        transaction: Transaction,
        transitionFinishedCallback: IRemoteTransitionFinishedCallback,
    ) {
        Log.v(TAG, "startAnimation: launchType=$launchType, cujType=$cujType")
        val safeTransitionFinishedCallback = RemoteRunnable {
            transitionFinishedCallback.onTransitionFinished(/* wct= */ null, /* sct= */ null)
        }
        mainExecutor.execute {
            runAnimators(info, safeTransitionFinishedCallback)
            transaction.apply()
        }
    }

    private fun runAnimators(info: TransitionInfo, finishedCallback: RemoteRunnable) {
        val animators = mutableListOf<Animator>()
        val animatorFinishedCallback: (Animator) -> Unit = { animator ->
            animators -= animator
            if (animators.isEmpty()) finishedCallback.run()
        }
        animators += animatorHelper.createAnimators(info, animatorFinishedCallback)
        if (animators.isEmpty()) {
            finishedCallback.run()
            return
        }
        animators.forEach { it.start() }
    }

    companion object {
        const val TAG = "DesktopAppLaunchTransition"
        /** Change modes that represent a task becoming visible / launching in Desktop mode. */
        val LAUNCH_CHANGE_MODES = intArrayOf(TRANSIT_OPEN, TRANSIT_TO_FRONT)

        private val launchBoundsAnimationDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 600,
                startOffsetYDp = 36f,
                startScale = 0.95f,
                interpolator = Interpolators.STANDARD_DECELERATE,
            )

        private val unminimizeBoundsAnimationDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 300,
                startOffsetYDp = 12f,
                startScale = 0.97f,
                interpolator = Interpolators.STANDARD_DECELERATE,
            )
    }
}
