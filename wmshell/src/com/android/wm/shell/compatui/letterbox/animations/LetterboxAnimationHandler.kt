/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.animations

import android.animation.Animator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.app.animation.Interpolators
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags.appCompatRefactoring
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.suppliers.TransactionSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.MixedLetterboxController
import com.android.wm.shell.compatui.letterbox.events.LetterboxState
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MOVE_LETTERBOX_REACHABILITY
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import javax.inject.Inject

/**
 * The [Transitions.TransitionHandler] to handle Reachability animations.
 */
@WMSingleton
class LetterboxAnimationHandler @Inject constructor(
    shellInit: ShellInit,
    transitions: Transitions,
    @ShellMainThread private val animExecutor: ShellExecutor,
    private val transactionSupplier: TransactionSupplier,
    private val mixedLetterboxController: dagger.Lazy<MixedLetterboxController>,
    private val letterboxState: LetterboxState
) : Transitions.TransitionHandler {

    companion object {
        @JvmStatic
        private val TAG = "LetterboxAnimationHandler"

        @JvmStatic
        private val ANIMATION_DURATION_MS = 500L

        @JvmStatic
        private val ANIMATION_INTERPOLATOR = Interpolators.EMPHASIZED
    }

    private var boundsAnimator: ValueAnimator? = null

    private val rectEvaluator = RectEvaluator(Rect())

    init {
        if (appCompatRefactoring()) {
            ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, "Initializing...")
            shellInit.addInitCallback({
                transitions.addHandler(this)
            }, this)
        }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback
    ): Boolean {
        if (info.type != TRANSIT_MOVE_LETTERBOX_REACHABILITY || info.changes.isEmpty()) {
            return false
        }
        // We get current change
        val change = info.changes[0]
        val controller = mixedLetterboxController.get()
        val letterboxId = letterboxState.lastInputSourceId
        if (letterboxId >= 0) {
            val key = LetterboxKey(change.endDisplayId, letterboxId)
            val taskBounds = change.endAbsBounds
            val startBounds = change.startAbsBounds
            val finalX = change.endRelOffset.x
            val finalY = change.endRelOffset.y
            if (finalX == startBounds.left && finalY == startBounds.top) {
                return false
            }
            controller.updateLetterboxSurfaceBounds(
                key,
                startTransaction,
                taskBounds,
                startBounds
            )
            val tx: Transaction = transactionSupplier.get()
            animExecutor.execute {
                boundsAnimator?.cancel()
            }
            // Only the position changes.
            val endBounds = Rect(
                finalX,
                finalY,
                finalX + startBounds.width(),
                finalY + startBounds.height()
            )
            ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds)
                .setDuration(ANIMATION_DURATION_MS).apply {
                    setInterpolator { value -> ANIMATION_INTERPOLATOR.getInterpolation(value) }
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            finishTransaction.apply()
                            finishCallback.onTransitionFinished(null)
                            boundsAnimator = null
                        }

                        override fun onAnimationCancel(animation: Animator) {
                        }

                        override fun onAnimationRepeat(animation: Animator) {
                        }
                    })
                    addUpdateListener { animation ->
                        val rect =
                            animation.animatedValue as Rect

                        for (c in info.changes) {
                            tx.setPosition(
                                c.leash,
                                rect.left.toFloat(),
                                rect.top.toFloat()
                            )
                        }
                        controller.updateLetterboxSurfaceBounds(
                            key,
                            tx,
                            taskBounds,
                            rect
                        )
                        tx.apply()
                    }
                    animExecutor.execute {
                        for (c in info.changes) {
                            tx.show(c.leash).apply()
                        }
                        start()
                    }
                }
            return true
        }
        return false
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? = null
}
