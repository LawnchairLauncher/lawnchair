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

package com.android.wm.shell.desktopmode.compatui

import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.app.animation.Interpolators
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil.isClosingMode
import com.android.wm.shell.shared.TransitionUtil.isClosingType
import com.android.wm.shell.shared.TransitionUtil.isOpeningMode
import com.android.wm.shell.shared.TransitionUtil.isOpeningType
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionHandler

/** Handles transitions related to system modals, e.g. launch and close transitions. */
class SystemModalsTransitionHandler(
    private val context: Context,
    private val mainExecutor: ShellExecutor,
    private val animExecutor: ShellExecutor,
    private val shellInit: ShellInit,
    private val transitions: Transitions,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
) : TransitionHandler {

    private val showingSystemModalsIds = mutableSetOf<Int>()

    init {
        shellInit.addInitCallback({ transitions.addHandler(this) }, this)
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (!isDesktopModeShowing(DEFAULT_DISPLAY)) return false
        if (isOpeningType(info.type)) {
            val launchChange = getLaunchingSystemModal(info) ?: return false
            val taskInfo = launchChange.taskInfo
            requireNotNull(taskInfo)
            logV("Animating system modal launch: taskId=%d", taskInfo.taskId)
            showingSystemModalsIds.add(taskInfo.taskId)
            animateSystemModal(
                launchChange.leash,
                startTransaction,
                finishTransaction,
                finishCallback,
                /* toShow= */ true,
            )
            return true
        }
        if (isClosingType(info.type)) {
            val closeChange = getClosingSystemModal(info) ?: return false
            val taskInfo = closeChange.taskInfo
            requireNotNull(taskInfo)
            logV("Animating system modal close: taskId=%d", taskInfo.taskId)
            showingSystemModalsIds.remove(taskInfo.taskId)
            animateSystemModal(
                closeChange.leash,
                startTransaction,
                finishTransaction,
                finishCallback,
                /* toShow= */ false,
            )
            return true
        }
        return false
    }

    fun animateSystemModal(
        leash: SurfaceControl,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        toShow: Boolean, // Whether to show or to hide the system modal
    ) {
        val startAlpha = if (toShow) 0f else 1f
        val endAlpha = if (toShow) 1f else 0f
        val animator =
            createAlphaAnimator(SurfaceControl.Transaction(), leash, startAlpha, endAlpha)
        animator.addListener(
            onEnd = { _ ->
                mainExecutor.execute { finishCallback.onTransitionFinished(/* wct= */ null) }
            }
        )
        if (toShow) {
            finishTransaction.show(leash)
        } else {
            finishTransaction.hide(leash)
        }
        startTransaction.setAlpha(leash, startAlpha)
        startTransaction.apply()
        animExecutor.execute { animator.start() }
    }

    private fun getLaunchingSystemModal(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.find { change ->
            if (!isOpeningMode(change.mode)) {
                return@find false
            }
            val taskInfo = change.taskInfo ?: return@find false
            return@find isSystemModal(taskInfo)
        }

    private fun getClosingSystemModal(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.find { change ->
            if (!isClosingMode(change.mode)) {
                return@find false
            }
            val taskInfo = change.taskInfo ?: return@find false
            return@find isSystemModal(taskInfo) || showingSystemModalsIds.contains(taskInfo.taskId)
        }

    private fun isSystemModal(taskInfo: RunningTaskInfo): Boolean =
        !DesktopWallpaperActivity.isWallpaperTask(taskInfo) &&
            desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(taskInfo)

    private fun createAlphaAnimator(
        transaction: SurfaceControl.Transaction,
        leash: SurfaceControl,
        startVal: Float,
        endVal: Float,
    ): ValueAnimator =
        ValueAnimator.ofFloat(startVal, endVal).apply {
            duration = LAUNCH_ANIM_ALPHA_DURATION_MS
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                transaction.setAlpha(leash, animation.animatedValue as Float).apply()
            }
        }

    private fun isDesktopModeShowing(displayId: Int): Boolean =
        desktopUserRepositories.current.isAnyDeskActive(displayId)

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "SystemModalsTransitionHandler"
        private const val LAUNCH_ANIM_ALPHA_DURATION_MS = 150L
    }
}
