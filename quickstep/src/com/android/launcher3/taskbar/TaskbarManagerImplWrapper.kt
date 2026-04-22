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

import android.app.PendingIntent
import android.os.Looper
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.Executors
import com.android.quickstep.views.RecentsViewContainer
import java.io.PrintWriter

/**
 * Wrapper of [TaskbarManagerImpl], this class controls which thread the invocation happens. The
 * goal of this class is to minimize the changes to [TaskbarManagerImpl] during migration of
 * rendering taskbar in per-window ui thread.
 */
class TaskbarManagerImplWrapper(private val impl: TaskbarManagerImpl) : TaskbarManager {

    override fun onUserUnlocked() {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute(impl::onUserUnlocked)
        } else {
            impl.onUserUnlocked()
        }
    }

    override fun setActivity(activity: StatefulActivity<*>) {
        // TODO(b/404636836) internal invocation on activity should be posted back to main thread
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.setActivity(activity) }
        } else {
            impl.setActivity(activity)
        }
    }

    override fun setRecentsViewContainer(recentsViewContainer: RecentsViewContainer) {
        // TODO(b/404636836) internal invocation on recentsViewContainer should be posted back to
        //  main thread
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.setRecentsViewContainer(recentsViewContainer) }
        } else {
            impl.setRecentsViewContainer(recentsViewContainer)
        }
    }

    override fun recreateTaskbars() {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute(impl::recreateTaskbars)
        } else {
            impl.recreateTaskbars()
        }
    }

    override fun onSystemUiFlagsChanged(systemUiStateFlags: Long, displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onSystemUiFlagsChanged(systemUiStateFlags, displayId)
            }
        } else {
            impl.onSystemUiFlagsChanged(systemUiStateFlags, displayId)
        }
    }

    override fun onLongPressHomeEnabled(assistantLongPressEnabled: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onLongPressHomeEnabled(assistantLongPressEnabled)
            }
        } else {
            impl.onLongPressHomeEnabled(assistantLongPressEnabled)
        }
    }

    override fun setSetupUIVisible(isVisible: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.setSetupUIVisible(isVisible) }
        } else {
            impl.setSetupUIVisible(isVisible)
        }
    }

    override fun setWallpaperVisible(displayId: Int, isVisible: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.setWallpaperVisible(displayId, isVisible) }
        } else {
            impl.setWallpaperVisible(displayId, isVisible)
        }
    }

    override fun checkNavBarModes(displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.checkNavBarModes(displayId) }
        } else {
            impl.checkNavBarModes(displayId)
        }
    }

    override fun finishBarAnimations(displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.finishBarAnimations(displayId) }
        } else {
            impl.finishBarAnimations(displayId)
        }
    }

    override fun touchAutoDim(displayId: Int, reset: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.touchAutoDim(displayId, reset) }
        } else {
            impl.touchAutoDim(displayId, reset)
        }
    }

    override fun transitionTo(displayId: Int, barMode: Int, animate: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.transitionTo(displayId, barMode, animate) }
        } else {
            impl.transitionTo(displayId, barMode, animate)
        }
    }

    override fun appTransitionPending(pending: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.appTransitionPending(pending) }
        } else {
            impl.appTransitionPending(pending)
        }
    }

    override fun onRotationProposal(rotation: Int, isValid: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.onRotationProposal(rotation, isValid) }
        } else {
            impl.onRotationProposal(rotation, isValid)
        }
    }

    override fun disableNavBarElements(displayId: Int, state1: Int, state2: Int, animate: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.disableNavBarElements(displayId, state1, state2, animate)
            }
        } else {
            impl.disableNavBarElements(displayId, state1, state2, animate)
        }
    }

    override fun onSystemBarAttributesChanged(displayId: Int, behavior: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onSystemBarAttributesChanged(displayId, behavior)
            }
        } else {
            impl.onSystemBarAttributesChanged(displayId, behavior)
        }
    }

    override fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onTransitionModeUpdated(barMode, checkBarModes)
            }
        } else {
            impl.onTransitionModeUpdated(barMode, checkBarModes)
        }
    }

    override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onNavButtonsDarkIntensityChanged(darkIntensity)
            }
        } else {
            impl.onNavButtonsDarkIntensityChanged(darkIntensity)
        }
    }

    override fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute {
                impl.onNavigationBarLumaSamplingEnabled(displayId, enable)
            }
        } else {
            impl.onNavigationBarLumaSamplingEnabled(displayId, enable)
        }
    }

    override fun onDisplayAddSystemDecorations(displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.onDisplayAddSystemDecorations(displayId) }
        } else {
            impl.onDisplayAddSystemDecorations(displayId)
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.onDisplayRemoved(displayId) }
        } else {
            impl.onDisplayRemoved(displayId)
        }
    }

    override fun onDisplayRemoveSystemDecorations(displayId: Int) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.onDisplayRemoveSystemDecorations(displayId) }
        } else {
            impl.onDisplayRemoveSystemDecorations(displayId)
        }
    }

    override fun destroy() {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.destroy() }
        } else {
            impl.destroy()
        }
    }

    override fun createLauncherStartFromSuwAnim(duration: Int): AnimatorPlaybackController? {
        // TODO(b/404636836): Evaluate if internal impl taskbar.createLauncherStartFromSuwAnim() is
        //  thread safe
        return impl.createLauncherStartFromSuwAnim(duration)
    }

    override fun getCurrentActivityContext(): TaskbarActivityContext? {
        // Thread safe
        return impl.currentActivityContext
    }

    override fun getUIControllerForDisplay(displayId: Int): TaskbarUIController? {
        // Thread safe
        return impl.getUIControllerForDisplay(displayId)
    }

    override fun getTaskbarForDisplay(displayId: Int): TaskbarActivityContext? {
        // Thread safe
        return impl.getTaskbarForDisplay(displayId)
    }

    override fun createAllAppsPendingIntent(): PendingIntent {
        // Thread safe
        return impl.createAllAppsPendingIntent(
            if (enableTaskbarUiThread()) impl.perWindowUiExecutor else Executors.MAIN_EXECUTOR
        )
    }

    override fun getPrimaryDisplayId(): Int {
        // Thread safe
        return impl.getPrimaryDisplayId()
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        // Stay on caller thread because PrinterWriter is not thread safe.
        impl.dumpLogs(prefix, pw)
    }

    override fun debugPrimaryTaskbar(debugReason: String, verbose: Boolean) {
        if (shouldPostToTaskbarUiThread()) {
            impl.perWindowUiExecutor.execute { impl.debugPrimaryTaskbar(debugReason, verbose) }
        } else {
            impl.debugPrimaryTaskbar(debugReason, verbose)
        }
    }

    private fun shouldPostToTaskbarUiThread(): Boolean {
        return enableTaskbarUiThread() && Looper.getMainLooper() == Looper.myLooper()
    }
}
