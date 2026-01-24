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

package com.android.wm.shell.desktopmode

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopExperienceFlags
import android.window.DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS
import android.window.DesktopModeFlags
import android.window.DisplayAreaInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.desktopmode.desktopfirst.DesktopDisplayModeController
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.desktopmode.multidesks.PreserveDisplayRequestHandler
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Handles display events in desktop mode */
class DesktopDisplayEventHandler(
    shellInit: ShellInit,
    private val mainScope: CoroutineScope,
    private val shellController: ShellController,
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val desksOrganizer: DesksOrganizer,
    private val desktopRepositoryInitializer: DesktopRepositoryInitializer,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController,
    private val desktopDisplayModeController: DesktopDisplayModeController,
    private val desksTransitionObserver: DesksTransitionObserver,
    private val desktopState: DesktopState,
) : OnDisplaysChangedListener, OnDeskRemovedListener, PreserveDisplayRequestHandler {

    private val onDisplayAreaChangeListener = OnDisplayAreaChangeListener { displayId ->
        logV("displayAreaChanged in displayId=%d", displayId)
        createDefaultDesksIfNeeded(displayIds = listOf(displayId), userId = null)
    }

    // Mapping of display uniqueIds to displayId. Used to match a disconnected
    // displayId to its uniqueId since we will not be able to fetch it after disconnect.
    private val uniqueIdByDisplayId = mutableMapOf<Int, String>()

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(this)

        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desktopTasksController.onDeskRemovedListener = this
            shellController.addUserChangeListener(
                object : UserChangeListener {
                    override fun onUserChanged(newUserId: Int, userContext: Context) {
                        val displayIds = rootTaskDisplayAreaOrganizer.displayIds.toSet()
                        logV("onUserChanged newUserId=%d displays=%s", newUserId, displayIds)
                        createDefaultDesksIfNeeded(displayIds, newUserId)
                    }
                }
            )
            desktopTasksController.preserveDisplayRequestHandler = this
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            rootTaskDisplayAreaOrganizer.registerListener(displayId, onDisplayAreaChangeListener)
        }
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            // The default display's windowing mode depends on the availability of the external
            // display. So updating the default display's windowing mode here.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
        if (DesktopExperienceFlags.ENABLE_DISPLAY_RECONNECT_INTERACTION.isTrue) {
            // TODO - b/365873835: Restore a display if a uniqueId match is found in
            //  the desktop repository.
            displayController.getDisplay(displayId)?.uniqueId?.let { uniqueId ->
                uniqueIdByDisplayId[displayId] = uniqueId
            }
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            rootTaskDisplayAreaOrganizer.unregisterListener(displayId, onDisplayAreaChangeListener)
        }
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
        uniqueIdByDisplayId.remove(displayId)
    }

    override fun requestPreserveDisplay(displayId: Int) {
        logV("requestPreserveDisplay displayId=%d", displayId)
        val uniqueId = uniqueIdByDisplayId.remove(displayId) ?: return
        // TODO: b/365873835 - Preserve/restore bounds for other repositories.
        desktopUserRepositories.current.preserveDisplay(displayId, uniqueId)
    }

    override fun onDesktopModeEligibleChanged(displayId: Int) {
        if (
            DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue &&
                displayId != DEFAULT_DISPLAY
        ) {
            desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            // The default display's windowing mode depends on the desktop eligibility of the
            // external display. So updating the default display's windowing mode here.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
    }

    override fun onDeskRemoved(lastDisplayId: Int, deskId: Int) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        logV("onDeskRemoved deskId=%d displayId=%d", deskId, lastDisplayId)
        createDefaultDesksIfNeeded(listOf(lastDisplayId), userId = null)
    }

    private fun createDefaultDesksIfNeeded(displayIds: Collection<Int>, userId: Int?) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        logV("createDefaultDesksIfNeeded displays=%s userId=%d", displayIds, userId)
        if (userId != null && !isUserDesktopEligible(userId)) {
            logW("createDefaultDesksIfNeeded ignoring attempt for ineligible user")
            return
        }
        mainScope.launch {
            desktopRepositoryInitializer.isInitialized.collect { initialized ->
                if (!initialized) return@collect
                val repository =
                    userId?.let { desktopUserRepositories.getProfile(userId) }
                        ?: desktopUserRepositories.current
                if (!isUserDesktopEligible(repository.userId)) {
                    logW("createDefaultDesksIfNeeded ignoring attempt for ineligible user")
                    cancel()
                    return@collect
                }
                for (displayId in displayIds) {
                    if (!shouldCreateOrWarmUpDesk(displayId, repository)) continue
                    if (rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)) {
                        logV("Display %d is desktop-first and needs a default desk", displayId)
                        desktopTasksController.createDesk(
                            displayId = displayId,
                            userId = repository.userId,
                            enforceDeskLimit = false,
                            // TODO: b/393978539 - do not activate as a result of removing the
                            //  last desk from Overview. Let overview activate it once it is
                            //  selected or when the user goes home.
                            activateDesk =
                                ENABLE_MULTIPLE_DESKTOPS_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS.isTrue,
                        )
                    } else {
                        logV("Display %d is touch-first and needs to warm up a desk", displayId)
                        desksOrganizer.warmUpDefaultDesk(displayId, repository.userId)
                    }
                }
                cancel()
            }
        }
    }

    private fun shouldCreateOrWarmUpDesk(displayId: Int, repository: DesktopRepository): Boolean {
        if (displayId == Display.INVALID_DISPLAY) {
            logV("shouldCreateOrWarmUpDesk skipping reason: invalid display")
            return false
        }
        if (!desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
            logV(
                "shouldCreateOrWarmUpDesk skipping displayId=%d reason: desktop ineligible",
                displayId,
            )
            return false
        }
        if (repository.getNumberOfDesks(displayId) > 0) {
            logV("shouldCreateOrWarmUpDesk skipping displayId=%d reason: has desk(s)", displayId)
            return false
        }
        return true
    }

    private fun isUserDesktopEligible(userId: Int): Boolean =
        !(DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_HSUM.isTrue &&
            UserManager.isHeadlessSystemUserMode() &&
            UserHandle.USER_SYSTEM == userId)

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private class OnDisplayAreaChangeListener(
        private val onDisplayAreaChanged: (displayId: Int) -> Unit
    ) : RootTaskDisplayAreaListener {

        override fun onDisplayAreaAppeared(displayAreaInfo: DisplayAreaInfo) {
            onDisplayAreaChanged(displayAreaInfo.displayId)
        }

        override fun onDisplayAreaInfoChanged(displayAreaInfo: DisplayAreaInfo) {
            onDisplayAreaChanged(displayAreaInfo.displayId)
        }
    }

    companion object {
        private const val TAG = "DesktopDisplayEventHandler"
    }
}
