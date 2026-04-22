/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import androidx.core.animation.addListener
import androidx.core.util.Supplier
import com.android.app.animation.Interpolators
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType
import com.android.launcher3.desktop.DesktopAppLaunchTransition.Companion.LAUNCH_CHANGE_MODES
import com.android.wm.shell.shared.animation.MinimizeAnimator
import com.android.wm.shell.shared.animation.WindowAnimator

/**
 * Helper class responsible for creating and managing animators for desktop app launch and related
 * transitions.
 *
 * <p>This class handles the complex logic of creating various animators, including launch,
 * minimize, and trampoline close animations, based on the provided transition information and
 * launch type. It also utilizes {@link InteractionJankMonitor} to monitor animation jank.
 *
 * @param context The application context.
 * @param launchType The type of app launch, containing animation parameters.
 * @param cujType The CUJ (Critical User Journey) type for jank monitoring.
 */
class DesktopAppLaunchAnimatorHelper(
    private val context: Context,
    private val launchType: AppLaunchType,
    @Cuj.CujType private val cujType: Int,
    private val transactionSupplier: Supplier<Transaction>,
) {

    private val interactionJankMonitor = InteractionJankMonitor.getInstance()

    fun createAnimators(info: TransitionInfo, finishCallback: (Animator) -> Unit): List<Animator> {
        val launchChange = getLaunchChange(info)
        if (launchChange == null) {
            val tasksInfo =
                info.changes.joinToString(", ") { change ->
                    "${change.taskInfo?.taskId}:${change.taskInfo?.isFreeform}"
                }
            Log.e(TAG, "No launch change found: Transition info=$info, tasks state=$tasksInfo")
            return emptyList()
        }

        val transaction = transactionSupplier.get()

        val minimizeChange = getMinimizeChange(info)
        val trampolineCloseChange = getTrampolineCloseChange(info)

        val launchAnimator =
            createLaunchAnimator(
                launchChange,
                transaction,
                finishCallback,
                isTrampoline = trampolineCloseChange != null,
            )
        val animatorsList = mutableListOf(launchAnimator)
        if (minimizeChange != null) {
            val minimizeAnimator =
                createMinimizeAnimator(minimizeChange, transaction, finishCallback)
            animatorsList.add(minimizeAnimator)
        }
        if (trampolineCloseChange != null) {
            val trampolineCloseAnimator =
                createTrampolineCloseAnimator(trampolineCloseChange, transaction, finishCallback)
            animatorsList.add(trampolineCloseAnimator)
        }
        return animatorsList
    }

    private fun getLaunchChange(info: TransitionInfo): Change? =
        info.changes.firstOrNull { change ->
            change.mode in LAUNCH_CHANGE_MODES && change.taskInfo?.isFreeform == true
        }

    private fun getMinimizeChange(info: TransitionInfo): Change? =
        info.changes.firstOrNull { change ->
            change.mode == TRANSIT_TO_BACK && change.taskInfo?.isFreeform == true
        }

    private fun getTrampolineCloseChange(info: TransitionInfo): Change? {
        if (
            info.changes.size < 2 ||
            !DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX.isTrue
        ) {
            return null
        }
        val openChange =
            info.changes.firstOrNull { change ->
                change.mode == TRANSIT_OPEN && change.taskInfo?.isFreeform == true
            }
        val closeChange =
            info.changes.firstOrNull { change ->
                change.mode == TRANSIT_CLOSE && change.taskInfo?.isFreeform == true
            }
        val openPackage = openChange?.taskInfo?.baseIntent?.component?.packageName
        val closePackage = closeChange?.taskInfo?.baseIntent?.component?.packageName
        return if (openPackage != null && closePackage != null && openPackage == closePackage) {
            closeChange
        } else {
            null
        }
    }

    private fun createLaunchAnimator(
        change: Change,
        transaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
        isTrampoline: Boolean,
    ): Animator {
        val boundsAnimator =
            WindowAnimator.createBoundsAnimator(
                context.resources.displayMetrics,
                launchType.boundsAnimationParams,
                change,
                transaction,
            )
        val alphaAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = launchType.alphaDurationMs
                interpolator = Interpolators.LINEAR
                addUpdateListener { animation ->
                    transaction
                        .setAlpha(change.leash, animation.animatedValue as Float)
                        .setFrameTimeline(Choreographer.getInstance().vsyncId)
                        .apply()
                }
            }
        val clipRect = Rect(change.endAbsBounds).apply { offsetTo(0, 0) }
        transaction.setCrop(change.leash, clipRect)
        transaction.setCornerRadius(
            change.leash,
            ScreenDecorationsUtils.getWindowCornerRadius(context),
        )
        return AnimatorSet().apply {
            interactionJankMonitor.begin(change.leash, context, context.mainThreadHandler, cujType)
            if (isTrampoline) {
                play(alphaAnimator)
            } else {
                playTogether(boundsAnimator, alphaAnimator)
            }
            addListener(
                onEnd = { animation ->
                    onAnimFinish(animation)
                    interactionJankMonitor.end(cujType)
                }
            )
        }
    }

    private fun createMinimizeAnimator(
        change: Change,
        transaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator {
        return MinimizeAnimator.create(
            context,
            change,
            transaction,
            onAnimFinish,
            interactionJankMonitor,
            context.mainThreadHandler,
        )
    }

    private fun createTrampolineCloseAnimator(
        change: Change,
        transaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator {
        return ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100L
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                transaction.setAlpha(change.leash, animation.animatedValue as Float).apply()
            }
            addListener(
                onEnd = { animation ->
                    onAnimFinish(animation)
                }
            )
        }
    }

    private companion object {
        const val TAG = "DesktopAppLaunchAnimatorHelper"
    }
}
