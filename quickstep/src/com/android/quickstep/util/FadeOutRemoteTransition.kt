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

import android.animation.ValueAnimator
import android.os.IBinder
import android.os.RemoteException
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.util.Executors
import com.android.wm.shell.shared.TransitionUtil

/** Remote animation which fades out the closing targets */
class FadeOutRemoteTransition : IRemoteTransition.Stub() {

    override fun mergeAnimation(
        iBinder: IBinder,
        transitionInfo: TransitionInfo,
        transaction: Transaction,
        mergeTarget: IBinder,
        finishCB: IRemoteTransitionFinishedCallback
    ) {
        // Do not report finish if we don't know how to handle this transition.
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startT: Transaction,
        finishCB: IRemoteTransitionFinishedCallback
    ) {
        val anim = ValueAnimator.ofFloat(1f, 0f)

        val closingControls: MutableList<SurfaceControl> = mutableListOf()
        for (chg in info.changes) {
            startT.show(chg.leash)
            if (TransitionUtil.isClosingType(chg.mode)) {
                closingControls.add(chg.leash)
            }
        }
        startT.apply()

        anim.addUpdateListener {
            val t = Transaction()
            closingControls.forEach { t.setAlpha(it, anim.animatedValue as Float) }
            t.apply()
        }
        anim.addListener(
            forEndCallback(
                Runnable {
                    val t = Transaction()
                    closingControls.forEach { t.hide(it) }
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

    override fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {}
}
