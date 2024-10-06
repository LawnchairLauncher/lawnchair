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
package com.android.quickstep.util

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.graphics.Rect
import android.os.IBinder
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.util.Executors
import com.android.wm.shell.shared.TransitionUtil

/** Remote animation which slides the opening targets in and the closing targets out */
class SlideInRemoteTransition(
    private val isRtl: Boolean,
    private val pageSpacing: Int,
    private val cornerRadius: Float,
    private val interpolator: TimeInterpolator,
) : RemoteTransitionStub() {
    private val animationDurationMs = 500L

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startT: Transaction,
        finishCB: IRemoteTransitionFinishedCallback
    ) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.interpolator = interpolator
        anim.duration = animationDurationMs

        val closingStartBounds: HashMap<SurfaceControl, Rect> = HashMap()
        val openingEndBounds: HashMap<SurfaceControl, Rect> = HashMap()
        for (chg in info.changes) {
            val leash = chg.leash
            startT.show(leash)

            val taskInfo = chg.taskInfo
            if (taskInfo?.activityType == ACTIVITY_TYPE_HOME || taskInfo?.parentTaskId != -1) {
                continue
            }
            if (TransitionUtil.isClosingType(chg.mode)) {
                closingStartBounds[leash] = chg.startAbsBounds
                startT.setCrop(leash, chg.startAbsBounds).setCornerRadius(leash, cornerRadius)
            }
            if (TransitionUtil.isOpeningType(chg.mode)) {
                openingEndBounds[leash] = chg.endAbsBounds
                startT.setCrop(leash, chg.endAbsBounds).setCornerRadius(leash, cornerRadius)
            }
        }
        startT.apply()

        anim.addUpdateListener {
            val t = Transaction()
            closingStartBounds.keys.forEach {
                // Translate the surface from its original position on-screen to off-screen on the
                // right (or left in RTL)
                val startBounds = closingStartBounds[it]
                val targetX = (if (isRtl) -1 else 1) * (startBounds!!.right + pageSpacing)
                t.setPosition(it, anim.animatedValue as Float * targetX, 0f)
            }
            openingEndBounds.keys.forEach {
                // Set the alpha in the update listener to prevent one visible frame at the
                // beginning
                t.setAlpha(it, 1f)
                // Translate the surface from off-screen on the left (or left in RTL) to its final
                // position on-screen
                val endBounds = openingEndBounds[it]
                val targetX = (if (isRtl) -1 else 1) * (endBounds!!.right + pageSpacing)
                t.setPosition(it, (1f - anim.animatedValue as Float) * -targetX, 0f)
            }
            t.apply()
        }
        anim.addListener(
            forEndCallback(
                Runnable {
                    val t = Transaction()
                    try {
                        finishCB.onTransitionFinished(null, t)
                    } catch (e: RemoteException) {
                        // Ignore
                    }
                }
            )
        )

        Executors.MAIN_EXECUTOR.execute { anim.start() }
    }
}
