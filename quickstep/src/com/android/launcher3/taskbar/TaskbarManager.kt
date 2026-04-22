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
import com.android.app.displaylib.DisplayDecorationListener
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.statemanager.StatefulActivity
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.shared.statusbar.phone.BarTransitions
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import java.io.PrintWriter

interface TaskbarManager : DisplayDecorationListener {

    fun createLauncherStartFromSuwAnim(duration: Int): AnimatorPlaybackController?

    fun onUserUnlocked()

    fun setActivity(activity: StatefulActivity<*>)

    fun setRecentsViewContainer(recentsViewContainer: RecentsViewContainer)

    fun recreateTaskbars()

    fun onSystemUiFlagsChanged(@SystemUiStateFlags systemUiStateFlags: Long, displayId: Int)

    fun onLongPressHomeEnabled(assistantLongPressEnabled: Boolean)

    fun setSetupUIVisible(isVisible: Boolean)

    fun setWallpaperVisible(displayId: Int, isVisible: Boolean)

    fun checkNavBarModes(displayId: Int)

    fun finishBarAnimations(displayId: Int)

    fun touchAutoDim(displayId: Int, reset: Boolean)

    fun transitionTo(displayId: Int, @BarTransitions.TransitionMode barMode: Int, animate: Boolean)

    fun appTransitionPending(pending: Boolean)

    fun onRotationProposal(rotation: Int, isValid: Boolean)

    fun disableNavBarElements(displayId: Int, state1: Int, state2: Int, animate: Boolean)

    fun onSystemBarAttributesChanged(displayId: Int, behavior: Int)

    fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean)

    fun onNavButtonsDarkIntensityChanged(darkIntensity: Float)

    fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean)

    fun destroy()

    fun getCurrentActivityContext(): TaskbarActivityContext?

    fun dumpLogs(prefix: String, pw: PrintWriter)

    fun getUIControllerForDisplay(displayId: Int): TaskbarUIController?

    fun getTaskbarForDisplay(displayId: Int): TaskbarActivityContext?

    fun createAllAppsPendingIntent(): PendingIntent

    fun getPrimaryDisplayId(): Int

    fun debugPrimaryTaskbar(debugReason: String, verbose: Boolean)
}
