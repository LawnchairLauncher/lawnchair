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

package com.android.launcher3.taskbar

import android.animation.Animator
import android.animation.AnimatorSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewRootImpl
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.LauncherState
import com.android.launcher3.taskbar.TaskbarManagerImpl.TASKBAR_UI_THREAD
import com.android.launcher3.taskbar.customization.TASKBAR_OVERFLOW_PIN_LIMIT
import com.android.launcher3.util.AsyncView
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.ImmediateExecutorService
import com.android.quickstep.GestureState
import com.android.quickstep.RecentsAnimationCallbacks
import com.android.quickstep.ViewUtils
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Future
import javax.annotation.concurrent.ThreadSafe

/**
 * Expose [TaskbarUIController] APIs to launcher, gesture nav and recents to be called on taskbar's
 * per-window UI thread.
 */
@ThreadSafe
class TaskbarInteractor(private val taskbarUIController: TaskbarUIController) {

    private val executor: AbstractExecutorService =
        if (enableTaskbarUiThread()) TASKBAR_UI_THREAD else ImmediateExecutorService

    @AnyThread
    fun setUserIsNotGoingHome(isNotGoingHome: Boolean) {
        executor.execute { taskbarUIController.setUserIsNotGoingHome(isNotGoingHome) }
    }

    @AnyThread
    fun hideOverlayWindow() {
        executor.execute { taskbarUIController.hideOverlayWindow() }
    }

    @AnyThread
    fun startTranslationSpring() {
        executor.execute { taskbarUIController.startTranslationSpring() }
    }

    @AnyThread
    fun onExpandPip() {
        executor.execute { taskbarUIController.onExpandPip() }
    }

    @AnyThread
    fun onLauncherVisibilityChanged(visible: Boolean) {
        executor.execute { taskbarUIController.onLauncherVisibilityChanged(visible) }
    }

    @AnyThread
    fun onStateTransitionCompletedAfterSwipeToHome(finalState: LauncherState) {
        executor.execute {
            taskbarUIController.onStateTransitionCompletedAfterSwipeToHome(finalState)
        }
    }

    @AnyThread
    fun notifyRenderer(reason: String) {
        executor.execute {
            val rootViewImpl: ViewRootImpl = taskbarUIController.rootView.viewRootImpl
            rootViewImpl.notifyRendererOfExpensiveFrame()
            rootViewImpl.notifyRendererForGpuLoadUp(reason)
        }
    }

    @AnyThread
    fun onTaskbarInAppDisplayProgressUpdate(progress: Float, flag: Int) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute {
                taskbarUIController.onTaskbarInAppDisplayProgressUpdate(progress, flag)
            }
        }
    }

    @AnyThread
    fun setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute {
                taskbarUIController.setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim)
            }
        }
    }

    @AnyThread
    fun showEduOnAppLaunch() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.showEduOnAppLaunch() }
        }
    }

    @AnyThread
    fun openQuickSwitchView() {
        executor.execute { taskbarUIController.openQuickSwitchView() }
    }

    @AnyThread
    fun refreshResumedState() {
        executor.execute { taskbarUIController.refreshResumedState() }
    }

    @AnyThread
    fun setSkipLauncherVisibilityChange(skip: Boolean) {
        executor.execute { taskbarUIController.setSkipLauncherVisibilityChange(skip) }
    }

    @AnyThread
    fun onLauncherResume() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherResume() }
        }
    }

    @AnyThread
    fun onLauncherPause() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherPause() }
        }
    }

    @AnyThread
    fun onLauncherStop() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherStop() }
        }
    }

    @AnyThread
    fun setIgnoreInAppFlagForSync(enabled: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.setIgnoreInAppFlagForSync(enabled) }
        }
    }

    @AnyThread
    fun createAnimToAppAndPlay(animatorSet: AnimatorSet) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.createAnimToApp().let { animatorSet.play(it) } }
        }
    }

    @AnyThread
    fun updateTaskbarLauncherStateGoingHome() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.updateTaskbarLauncherStateGoingHome() }
        }
    }

    /**
     * This API both launches focused tasks and returns focused task ids, so it cannot be converted
     * to a one-way API where caller can just fire and forget.
     *
     * We decided to return a [Future] so that caller can call [Future.get] to wait for (and be
     * blocked by) taskbar thread to finish the task due to 2 reasons:
     * 1. caller is keyboard switch handling which is a low use cases
     * 2. caller can be moved off main thread in the future, so blocking a bg thread is less a
     *    performance issue.
     */
    @AnyThread
    fun launchFocusedTask(): Future<Set<Int>?> =
        executor.submit<Set<Int>?> { taskbarUIController.launchFocusedTask() }

    @AnyThread
    fun postOnRootViewDraw(callback: Runnable, callbackExecutor: Executor): Boolean {
        val rootView = taskbarUIController.rootView
        return if (rootView != null) {
            executor.execute {
                ViewUtils.postFrameDrawn(rootView) { callbackExecutor.execute(callback) }
            }
            true
        } else {
            false
        }
    }

    // TODO(b/404636836): remove after revert ag/34711156
    @MainThread fun getControllers(): TaskbarControllers? = taskbarUIController.mControllers

    @AnyThread
    fun getMaxPinnableCount() =
        if (TaskbarPopupController.canPinAppsOverflow()) {
            TASKBAR_OVERFLOW_PIN_LIMIT
        } else {
            taskbarUIController.mControllers
                ?.taskbarActivityContext
                ?.deviceProfile
                ?.numShownHotseatIcons ?: -1
        }

    @AnyThread
    fun findMatchingAsyncView(v: View): AsyncView {
        return AsyncView(if (enableTaskbarUiThread()) TASKBAR_UI_THREAD else MAIN_EXECUTOR) {
            taskbarUIController.findMatchingView(v)
        }
    }

    // TODO(b/404636836): start this animation within taskbar.
    @MainThread
    fun getParallelAnimationToGestureEndTarget(
        endTarget: GestureState.GestureEndTarget,
        duration: Long,
        callbacks: RecentsAnimationCallbacks,
    ): Animator? =
        taskbarUIController.getParallelAnimationToGestureEndTarget(endTarget, duration, callbacks)

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isEventOverBubbleBarViews()"),
    )
    fun isEventOverBubbleBarViews(ev: MotionEvent) =
        taskbarUIController.isEventOverBubbleBarViews(ev)

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isEventOverAnyTaskbarView()"),
    )
    fun isEventOverAnyTaskbarItem(ev: MotionEvent) =
        taskbarUIController.isEventOverAnyTaskbarItem(ev)

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShowDesktopTaskbarForFreeformDisplayRef.value()"),
    )
    fun canPinAppWithContextMenu() = taskbarUIController.canPinAppWithContextMenu()

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getHasBubblesRef().value()"),
    )
    fun hasBubbles() =
        if (taskbarUIController is LauncherTaskbarUIController) {
            taskbarUIController.hasBubbles()
        } else null

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShouldShowEduOnAppLaunchRef().value()"),
    )
    fun shouldShowEduOnAppLaunch() =
        if (taskbarUIController is LauncherTaskbarUIController) {
            taskbarUIController.shouldShowEduOnAppLaunch()
        } else null

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isDraggingItemRef().value()"),
    )
    fun isDraggingItem() = taskbarUIController.isDraggingItem

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isTaskbarStashedRef().value()"),
    )
    fun isTaskbarStashed() = taskbarUIController.isTaskbarStashed

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isTaskbarAllAppsOpenRef().value()"),
    )
    fun isTaskbarAllAppsOpen() = taskbarUIController.isTaskbarAllAppsOpen

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShowDesktopTaskbarForFreeformDisplayRef().value()"),
    )
    fun shouldAllowTaskbarToAutoStash() = taskbarUIController.shouldAllowTaskbarToAutoStash()
}
