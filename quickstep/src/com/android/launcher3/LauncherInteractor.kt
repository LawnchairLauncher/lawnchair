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

package com.android.launcher3

import android.animation.AnimatorSet
import android.view.View
import android.window.RemoteTransition
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.android.app.animation.Interpolators
import com.android.launcher3.Hotseat.ALPHA_CHANNEL_TASKBAR_ALIGNMENT
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statemanager.StateManager.StateListener
import com.android.launcher3.taskbar.TaskbarInteractor
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.util.ScalingWorkspaceRevealAnim
import com.android.quickstep.util.SplitTask
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.animation.ViewRootSync.synchronizeNextDraw
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

/** Expose [QuickstepLauncher] APIs to taskbar rendered on per-window UI thread. */
@ThreadSafe
class LauncherInteractor(private val launcher: QuickstepLauncher, val executor: Executor) {

    private var hotseatTranslationXAnimation: AnimatorSet? = null

    @AnyThread fun getLauncherAsRecentViewContainer(): RecentsViewContainer = launcher

    @AnyThread
    fun startScalingWorkspaceRevealAnim(playAlphaReveal: Boolean = true, playBlur: Boolean = true) {
        executor.execute {
            ScalingWorkspaceRevealAnim(launcher, null, null, playAlphaReveal, playBlur).start()
        }
    }

    @AnyThread
    fun updateHotseatAndQsbTranslationX(
        targetValue: Float,
        animate: Boolean,
        isQsbInline: Boolean,
    ) {
        executor.execute {
            updateHotseatAndQsbTranslationXInternal(targetValue, animate, isQsbInline)
        }
    }

    /** Used to translate hotseat and QSB to make room for bubbles. */
    @MainThread
    private fun updateHotseatAndQsbTranslationXInternal(
        targetValue: Float,
        animate: Boolean,
        isQsbInline: Boolean,
    ) {
        // cancel existing animation
        hotseatTranslationXAnimation?.cancel()
        hotseatTranslationXAnimation = null
        val hotseat = launcher.hotseat
        val translationXAnimation = AnimatorSet()
        val iconsTranslationX =
            launcher.hotseat.getIconsTranslationX(Hotseat.ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT)
        if (animate) {
            translationXAnimation.playTogether(iconsTranslationX.animateToValue(targetValue))
        } else {
            iconsTranslationX.setValue(targetValue)
        }
        val qsbTargetX = if (isQsbInline) targetValue else 0f
        val qsbTranslationX = hotseat.qsbTranslationX
        if (qsbTranslationX != null) {
            if (animate) {
                translationXAnimation.playTogether(qsbTranslationX.animateToValue(qsbTargetX))
            } else {
                qsbTranslationX.setValue(qsbTargetX)
            }
        }
        if (!animate) {
            return
        }
        hotseatTranslationXAnimation = translationXAnimation
        translationXAnimation.startDelay = BubbleBarView.FADE_OUT_ANIM_POSITION_DURATION_MS
        translationXAnimation.setDuration(BubbleBarView.FADE_IN_ANIM_ALPHA_DURATION_MS)
        translationXAnimation.interpolator = Interpolators.EMPHASIZED
        translationXAnimation.start()
    }

    @AnyThread
    fun synchronizeNextDraw(view: View) {
        executor.execute { synchronizeNextDraw(launcher.hotseat, view, Runnable {}) }
    }

    @AnyThread
    fun setHotseatIconsAlpha(alpha: Float, @Hotseat.HotseatQsbAlphaId channelId: Int) {
        executor.execute {
            if (channelId == ALPHA_CHANNEL_TASKBAR_ALIGNMENT) {
                launcher.getLauncherUiState().setTaskbarAlignmentChannelAlpha(alpha)
            }
            launcher.hotseat.setIconsAlpha(alpha, channelId)
        }
    }

    @AnyThread
    fun setHotseatQsbAlpha(alpha: Float, @Hotseat.HotseatQsbAlphaId channelId: Int) {
        executor.execute { launcher.hotseat.setQsbAlpha(alpha, channelId) }
    }

    @AnyThread
    fun addStateListener(listener: StateListener<LauncherState>): SafeCloseable {
        val wrapperListener =
            object : StateListener<LauncherState>, SafeCloseable {
                override fun onStateTransitionStart(toState: LauncherState?) {
                    executor.execute { listener.onStateTransitionStart(toState) }
                }

                override fun onStateTransitionComplete(finalState: LauncherState?) {
                    executor.execute { listener.onStateTransitionComplete(finalState) }
                }

                override fun close() {
                    executor.execute { launcher.stateManager.removeStateListener(this) }
                }
            }
        executor.execute { launcher.stateManager.addStateListener(wrapperListener) }
        return wrapperListener
    }

    @AnyThread
    fun setTaskbarInteractor(taskbarInteractor: TaskbarInteractor?) {
        launcher.taskbarInteractor = taskbarInteractor
    }

    @AnyThread
    fun adjustHotseatForBubbleBar(isBubbleBarVisible: Boolean) {
        executor.execute { launcher.hotseat?.adjustForBubbleBar(isBubbleBarVisible) }
    }

    @AnyThread
    fun postAdjustHotseatForBubbleBar(
        isBubbleBarVisible: Boolean,
        isTaskbarUiControllersSet: Boolean,
    ) {
        executor.execute {
            launcher.hotseat.let {
                if (isBubbleBarVisible && isTaskbarUiControllersSet) {
                    it.post { it.adjustForBubbleBar(true) }
                }
            }
        }
    }

    @AnyThread
    fun logAppLaunch(statsLogManager: StatsLogManager, info: ItemInfo, instanceId: InstanceId) {
        executor.execute { launcher.logAppLaunch(statsLogManager, info, instanceId) }
    }

    @AnyThread
    fun toggleAllApps(focusSearch: Boolean) {
        executor.execute { launcher.toggleAllApps(focusSearch) }
    }

    @AnyThread
    fun launchSplitTasks(splitTask: SplitTask, remoteTransition: RemoteTransition?) {
        executor.execute { launcher.launchSplitTasks(splitTask, remoteTransition) }
    }

    @AnyThread
    fun setBubbleBarLocation(location: BubbleBarLocation?) {
        executor.execute { launcher.setBubbleBarLocation(location) }
    }

    @AnyThread
    fun resetOverlayScroll() {
        executor.execute {
            if (launcher.workspace.isOverlayShown) {
                launcher.workspace.onOverlayScrollChanged(0f)
            }
        }
    }

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.isResumed()"),
    )
    @MainThread
    fun isResumed() = launcher.isResumed

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.isResumed()"),
    )
    @MainThread
    fun hasBeenResumed() = launcher.hasBeenResumed()

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.isTopResumedActivityRef.value()"),
    )
    @MainThread
    fun isTopResumedActivity() = launcher.isTopResumedActivity

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.deviceProfileRef.value()"),
    )
    @MainThread
    fun getDeviceProfile(): DeviceProfile = launcher.deviceProfile

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.isSplitSelectionActiveRef.value()"),
    )
    @MainThread
    fun isSplitSelectActive() = launcher.isSplitSelectionActive

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.isOverlayShownRef.value()"),
    )
    @MainThread
    fun isOverlayShown() = launcher.workspace.isOverlayShown

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.launcherStateRef.value()"),
    )
    @MainThread
    fun getState(): LauncherState = launcher.stateManager.state

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("LauncherUiState.taskbarAlignmentChannelAlphaRef.value()"),
    )
    fun getTaskbarAlignmentChannelAlpha() =
        launcher.hotseat.getIconsAlpha(ALPHA_CHANNEL_TASKBAR_ALIGNMENT).value
}
