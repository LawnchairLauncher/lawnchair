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

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.IBinder
import android.os.Trace
import android.os.UserHandle
import android.os.UserManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS
import android.window.DesktopModeFlags
import android.window.DisplayAreaInfo
import android.window.TransitionInfo
//import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer
import com.android.wm.shell.desktopmode.desktopfirst.DesktopDisplayModeController
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.desktopmode.multidesks.PreserveDisplayRequestHandler
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.KeyguardChangeListener
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.transition.Transitions
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
    private val transitions: Transitions,
    private val keyguardManager: KeyguardManager,
) :
    OnDisplaysChangedListener,
    OnDeskRemovedListener,
    PreserveDisplayRequestHandler,
    Transitions.TransitionObserver,
    KeyguardChangeListener {

    private val onDisplayAreaChangeListener = OnDisplayAreaChangeListener { displayId ->
        val keyguardLocked = keyguardManager.isKeyguardLocked
        logV(
            "displayAreaChanged in displayId=%d, keyguardLocked=%b",
            displayId,
            keyguardLocked
        )
        // Do not create default desk if keyguard is locked. It will be handled on unlock.
        if (!handlePotentialReconnect(displayId) && !keyguardLocked) {
            createDefaultDesksIfNeeded(displayIds = listOf(displayId), userId = null)
        }
    }

    // Mapping of display uniqueIds to displayId. Used to match a disconnected
    // displayId to its uniqueId since we will not be able to fetch it after disconnect.
    private val uniqueIdByDisplayId = mutableMapOf<Int, String>()

    private val oldDpiLayoutByDisplayId = mutableMapOf<Int, DisplayLayout>()
    private val boundsChangedByDisplayId = mutableSetOf<Int>()
    private val stableBoundsChangedByDisplayId = mutableSetOf<Int>()
    private val displayConfigById = mutableMapOf<Int, Configuration>()

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
            if (DesktopExperienceFlags.ENABLE_DISPLAY_RECONNECT_INTERACTION.isTrue) {
                desktopTasksController.preserveDisplayRequestHandler = this
                shellController.addKeyguardChangeListener(this)
            }
        }
    }

    override fun onDisplayConfigurationChanged(
        displayId: Int,
        newConfig: Configuration?,
        oldLayout: DisplayLayout?,
    ) {
        val newDisplayLayout = displayController.getDisplayLayout(displayId)
        val oldDisplayLayout = oldDpiLayoutByDisplayId[displayId] ?: oldLayout
        if (oldDisplayLayout == null || newDisplayLayout == null) return
        newConfig?.let { displayConfigById.put(displayId, it) }
        if (newDisplayLayout.densityDpi() == oldDisplayLayout.densityDpi()) {
            return
        }
        oldDpiLayoutByDisplayId.put(displayId, oldDisplayLayout)
        val oldStableBounds = Rect()
        val newStableBounds = Rect()
        oldDisplayLayout.getStableBounds(oldStableBounds)
        newDisplayLayout.getStableBounds(newStableBounds)
        when {
            oldStableBounds == newStableBounds -> {}
            // Width update means resolution is updated, and we should wait for TRANSIT_CHANGE
            // transition to apply new resolution logic.
            displayResolutionChanged(oldDisplayLayout, newDisplayLayout) -> {
                transitions.registerObserver(this)
                boundsChangedByDisplayId.add(displayId)
            }
            taskbarInsetsUpdated(oldStableBounds, newStableBounds) -> {
                stableBoundsChangedByDisplayId.add(displayId)
            }
        }
        resizeTasksIfPreconditionsSatisfied(displayId, newConfig)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        if (info.changes.isEmpty()) return
        val displayId = info.changes[0].endDisplayId
        if (displayId !in displayConfigById) return
        val config = displayConfigById[displayId]
        if (info.type == TRANSIT_CHANGE) {
            resizeTasksIfPreconditionsSatisfied(displayId, config, true)
        }
    }

    override fun onStableInsetsChanging(displayId: Int, oldLayout: DisplayLayout?) {
        val oldStableBounds = Rect()
        val newStableBounds = Rect()
        val oldestLayout = oldDpiLayoutByDisplayId[displayId] ?: oldLayout
        val newLayout = displayController.getDisplayLayout(displayId)
        val config = displayConfigById[displayId]
        if (oldestLayout == null || newLayout == null) return
        oldDpiLayoutByDisplayId.put(displayId, oldestLayout)
        oldestLayout.getStableBounds(oldStableBounds)
        newLayout.getStableBounds(newStableBounds)
        when {
            oldStableBounds == newStableBounds -> {
                // No change in stable bounds.
            }
            // Width or height updates mean the resolution has changed.
            displayResolutionChanged(oldestLayout, newLayout) -> {
                boundsChangedByDisplayId.add(displayId)
            }

            taskbarInsetsUpdated(oldStableBounds, newStableBounds) -> {
                stableBoundsChangedByDisplayId.add(displayId)
            }
        }
        resizeTasksIfPreconditionsSatisfied(displayId, config)
    }

    private fun displayResolutionChanged(
        oldestLayout: DisplayLayout,
        newLayout: DisplayLayout,
    ): Boolean =
        oldestLayout.width() != newLayout.width() || oldestLayout.height() != newLayout.height()

    private fun taskbarInsetsUpdated(oldStableBounds: Rect, newStableBounds: Rect): Boolean =
        oldStableBounds.bottom != newStableBounds.bottom

    private fun resizeTasksIfPreconditionsSatisfied(
        displayId: Int,
        config: Configuration?,
        boundsChangeReady: Boolean = false,
    ) {
        when {
            config == null -> {}
            dpiChangedAndInsetsReadyForDisplay(displayId) -> {
                desktopTasksController.onDisplayDpiChanging(
                    displayId,
                    config,
                    oldDpiLayoutByDisplayId[displayId],
                )
                oldDpiLayoutByDisplayId.remove(displayId)
                stableBoundsChangedByDisplayId.remove(displayId)
            }
            resolutionChangedAndInsetsReadyForDisplay(displayId, boundsChangeReady) -> {
                desktopTasksController.onDisplayDpiChanging(
                    displayId,
                    config,
                    oldDpiLayoutByDisplayId[displayId],
                )
                transitions.unregisterObserver(this)
                oldDpiLayoutByDisplayId.remove(displayId)
                boundsChangedByDisplayId.remove(displayId)
            }
        }
    }

    private fun dpiChangedAndInsetsReadyForDisplay(displayId: Int): Boolean =
        displayId in oldDpiLayoutByDisplayId && displayId in stableBoundsChangedByDisplayId

    private fun resolutionChangedAndInsetsReadyForDisplay(
        displayId: Int,
        transitionReady: Boolean,
    ): Boolean =
        displayId in oldDpiLayoutByDisplayId &&
            displayId in boundsChangedByDisplayId &&
            transitionReady

    override fun onDisplayAdded(displayId: Int) {
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                rootTaskDisplayAreaOrganizer.registerListener(
                    displayId,
                    onDisplayAreaChangeListener,
                )
            }
            if (displayId != DEFAULT_DISPLAY) {
                desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            }
            // The default display's windowing mode depends on the availability of the external
            // display. So updating the default display's windowing mode regardless of the type of
            // `displayId`.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
            if (DesktopExperienceFlags.ENABLE_DISPLAY_RECONNECT_INTERACTION.isTrue) {
                displayController.getDisplay(displayId)?.uniqueId?.let { uniqueId ->
                    uniqueIdByDisplayId[displayId] = uniqueId
                }
            }
        }

    override fun onDisplayRemoved(displayId: Int): Unit {
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                rootTaskDisplayAreaOrganizer.unregisterListener(
                    displayId,
                    onDisplayAreaChangeListener,
                )
            }
            if (displayId != DEFAULT_DISPLAY) {
                desktopDisplayModeController.updateDefaultDisplayWindowingMode()
            }
            val uniqueDisplayId = uniqueIdByDisplayId[displayId]
            uniqueIdByDisplayId.remove(displayId)
        }

    override fun requestPreserveDisplay(displayId: Int) {
        logV("requestPreserveDisplay displayId=%d", displayId)
        val uniqueId = uniqueIdByDisplayId[displayId] ?: return
        // TODO: b/365873835 - Preserve/restore bounds for other repositories.
        desktopUserRepositories.current.preserveDisplay(displayId, uniqueId)
    }

    override fun onDesktopModeEligibleChanged(displayId: Int) {
        if (displayId == DEFAULT_DISPLAY) return
        if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) {
            desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            // The default display's windowing mode depends on the desktop eligibility of the
            // external display. So updating the default display's windowing mode here.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
        if (DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()) {
            handlePotentialDeskDisplayChange(displayId)
        }
    }

    private fun handlePotentialDeskDisplayChange(displayId: Int) {
        if (desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
            // A display has become desktop eligible. Treat this as a potential reconnect.
            val keyguardLocked = keyguardManager.isKeyguardLocked
            logV(
                "onDesktopModeEligibleChanged: keyguardLocked=%b, " +
                        "displayId=%d has become desktop eligible",
                displayId,
                keyguardLocked
            )
            // Do not create default desk if keyguard is locked. It will be handled on unlock.
            if (!handlePotentialReconnect(displayId) && !keyguardLocked) {
                createDefaultDesksIfNeeded(displayIds = listOf(displayId), userId = null)
            }
        } else {
            // A display has become desktop ineligible. Treat this as a potential disconnect.
            logV(
                "onDesktopModeEligibleChanged: displayId=%d has become desktop ineligible",
                displayId,
            )
            desktopTasksController.disconnectDisplay(displayId)
        }
    }

    override fun onKeyguardVisibilityChanged(
        visible: Boolean,
        occluded: Boolean,
        animatingDismiss: Boolean,
    ) {
        if (visible) return
        val displaysByUniqueId = displayController.allDisplaysByUniqueId ?: return
        val defaultDeskDisplayIds = mutableSetOf<Int>()
        for (displayIdByUniqueId in displaysByUniqueId) {
            val displayId = displayIdByUniqueId.value
            if (displayId != DEFAULT_DISPLAY && !handlePotentialReconnect(displayId)) {
                defaultDeskDisplayIds.add(displayIdByUniqueId.value)
            }
        }
        createDefaultDesksIfNeeded(defaultDeskDisplayIds, null)
    }

    private fun handlePotentialReconnect(displayId: Int): Boolean {
        // Do not handle restoration while locked; it will be handled when keyguard is gone.
        if (keyguardManager.isKeyguardLocked) return false
        val uniqueDisplayId = displayController.getDisplay(displayId)?.uniqueId ?: return false
        uniqueIdByDisplayId[displayId] = uniqueDisplayId
        val currentUserRepository = desktopUserRepositories.current
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_RECONNECT_INTERACTION.isTrue) {
            logV("handlePotentialReconnect: Reconnect not supported; aborting.")
            return false
        }
        // To ensure only one restoreDisplay is actually called, remove the preserved display.
        val preservedDisplay = currentUserRepository.removePreservedDisplay(uniqueDisplayId)
        if (preservedDisplay == null) {
            logV(
                "handlePotentialReconnect: No preserved display found for " +
                    "uniqueDisplayId=$uniqueDisplayId; aborting."
            )
            return false
        }
        val preservedTasks =
            currentUserRepository.getPreservedTasks(preservedDisplay).toMutableList()
        // Projected mode: Do not move anything focused on the internal display.
        if (!desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)) {
            val focusedDefaultDisplayTaskIds =
                desktopTasksController
                    .getFocusedNonDesktopTasks(DEFAULT_DISPLAY, currentUserRepository.userId)
                    .map { task -> task.taskId }
            preservedTasks.removeAll { taskId -> focusedDefaultDisplayTaskIds.contains(taskId) }
        }
        if (preservedTasks.isEmpty()) {
            // If we don't restore anything, skip the restoration and return false so we
            // create a default desk.
            return false
        }
        desktopTasksController.restoreDisplay(
            displayId = displayId,
            preservedDisplay = preservedDisplay,
            userId = desktopUserRepositories.current.userId,
        )
        return true
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
                                ENABLE_MULTIPLE_DESKTOPS_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS
                                    .isTrue,
                            enterReason = EnterReason.DISPLAY_CONNECT,
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
