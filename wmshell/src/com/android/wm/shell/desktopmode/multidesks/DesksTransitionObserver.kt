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
package com.android.wm.shell.desktopmode.multidesks

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.os.IBinder
import android.view.Display.INVALID_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_END_RECENTS_TRANSITION
import com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Observer of desk-related transitions, such as adding, removing or activating a whole desk. It
 * tracks pending transitions and updates repository state once they finish.
 */
class DesksTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val transitions: Transitions,
    private val shellController: ShellController,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    @ShellMainThread private val mainScope: CoroutineScope,
) {
    private val deskTransitions = mutableMapOf<IBinder, MutableSet<DeskTransition>>()

    /** Adds a pending desk transition to be tracked. */
    fun addPendingTransition(transition: DeskTransition) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val transitions = deskTransitions[transition.token] ?: mutableSetOf()
        transitions += transition
        deskTransitions[transition.token] = transitions
        logD("Added pending desk transition: %s", transition)
    }

    /**
     * Called when any transition is ready, which may include transitions not tracked by this
     * observer.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) =
        traceSection("DesksTransitionObserver#onTransitionReady") {
            if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
            val deskTransitions = deskTransitions.remove(transition)
            deskTransitions?.forEach { deskTransition ->
                handleDeskTransition(info, deskTransition)
            }
            if (deskTransitions.isNullOrEmpty()) {
                // A desk transition could also occur without shell having started it or
                // intercepting it, check for that here in case launch roots need to be updated.
                handleIndependentDeskTransitionIfNeeded(info)
            }
        }

    /**
     * Called when a transition is merged with another transition, which may include transitions not
     * tracked by this observer.
     */
    fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val transitions = deskTransitions.remove(merged) ?: return
        deskTransitions[playing] =
            transitions
                .map { deskTransition -> deskTransition.copyWithToken(token = playing) }
                .toMutableSet()
    }

    /**
     * Called when any transition finishes, which may include transitions not tracked by this
     * observer.
     *
     * Most [DeskTransition]s are not handled here because [onTransitionReady] handles them and
     * removes them from the map. However, there can be cases where the transition was added after
     * [onTransitionReady] had already been called and they need to be handled here, such as the
     * swipe-to-home recents transition when there is no book-end transition.
     */
    fun onTransitionFinished(transition: IBinder) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        val deskTransitions = deskTransitions.remove(transition) ?: return
        deskTransitions.forEach { deskTransition ->
            if (deskTransition is DeskTransition.DeactivateDesk) {
                handleDeactivateDeskTransition(null, deskTransition)
            } else {
                logW(
                    "Unexpected desk transition finished without being handled: %s",
                    deskTransition,
                )
            }
        }
    }

    private fun handleDeskTransition(info: TransitionInfo, deskTransition: DeskTransition) {
        logD("Desk transition ready: %s", deskTransition)
        // TODO: b/415381304 - don't use |current|. It can point to the old user during user-switch
        //   transitions while transition info changes can be for the new user. Transitions can
        //   even contain changes for tasks of different users. Instead, add a |userId| argument
        //   in |DeskTransition|, since |TaskInfo#mUserId| is also unreliable on non leaf tasks.
        val desktopRepository = desktopUserRepositories.current
        when (deskTransition) {
            is DeskTransition.RemoveDesk -> {
                check(info.type == TRANSIT_CLOSE) { "Expected close transition for desk removal" }
                // TODO: b/362720497 - consider verifying the desk was actually removed through the
                //  DesksOrganizer. The transition info won't have changes if the desk was not
                //  visible, such as when dismissing from Overview.
                val deskId = deskTransition.deskId
                val displayId = deskTransition.displayId
                deskTransition.runOnTransitEnd?.invoke()
                desktopRepository.removeDesk(deskTransition.deskId)
                deskTransition.onDeskRemovedListener?.onDeskRemoved(displayId, deskId)
            }
            is DeskTransition.ActivateDesk -> {
                val activateDeskChange =
                    info.changes.find { change ->
                        desksOrganizer.isDeskActiveAtEnd(change, deskTransition.deskId)
                    }
                if (activateDeskChange == null) {
                    // Always activate even if there is no change in the transition for the
                    // activated desk. This is necessary because some activation requests, such as
                    // those involving empty desks, may not contain visibility changes that are
                    // reported in the transition change list.
                    logD("Activating desk without transition change")
                }
                desktopRepository.setActiveDesk(
                    displayId = deskTransition.displayId,
                    deskId = deskTransition.deskId,
                )
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.ActivateDeskWithTask -> {
                val deskId = deskTransition.deskId
                val deskChange =
                    info.changes.find { change -> desksOrganizer.isDeskChange(change, deskId) }
                if (deskChange != null) {
                    val deskChangeDisplayId = deskChange.taskInfo?.displayId ?: INVALID_DISPLAY
                    if (deskChangeDisplayId != deskTransition.displayId) {
                        logW(
                            "ActivateDeskWithTask: expected displayId=%d but got displayId=%d",
                            deskTransition.displayId,
                            deskChangeDisplayId,
                        )
                    }
                    desktopRepository.setActiveDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                    )
                } else {
                    logW("ActivateDeskWithTask: did not find desk change")
                }
                val taskChange =
                    info.changes.find { change ->
                        change.taskInfo?.taskId == deskTransition.enterTaskId &&
                            change.taskInfo?.isVisibleRequested == true &&
                            desksOrganizer.getDeskAtEnd(change) == deskTransition.deskId
                    }
                if (taskChange != null) {
                    desktopRepository.addTaskToDesk(
                        displayId = deskTransition.displayId,
                        deskId = deskTransition.deskId,
                        taskId = deskTransition.enterTaskId,
                        isVisible = true,
                        taskBounds = taskChange.taskInfo?.configuration?.windowConfiguration?.bounds,
                    )
                } else {
                    // This is possible in cases where the task that was originally launched is a
                    // trampoline and a new task ends up being the one that appeared. It's ok as
                    // desktop task updates to the repository are handled by
                    // [DesktopTaskChangeListener].
                    logW("ActivateDeskWithTask: did not find task change")
                }
                deskTransition.runOnTransitEnd?.invoke()
            }
            is DeskTransition.DeactivateDesk -> handleDeactivateDeskTransition(info, deskTransition)
            is DeskTransition.ChangeDeskDisplay -> handleChangeDeskDisplay(info, deskTransition)
            is DeskTransition.RemoveDisplay -> handleRemoveDisplay(deskTransition)
        }
    }

    private fun handleDeactivateDeskTransition(
        info: TransitionInfo?,
        deskTransition: DeskTransition.DeactivateDesk,
    ) {
        logD("handleDeactivateDeskTransition: %s", deskTransition)
        val desktopRepository = desktopUserRepositories.current
        var deskChangeFound = false

        deskTransition.runOnTransitEnd?.invoke()
        val changes = info?.changes ?: emptyList()
        for (change in changes) {
            val isDeskChange = desksOrganizer.isDeskChange(change, deskTransition.deskId)
            if (isDeskChange) {
                deskChangeFound = true
                continue
            }
        }
        // Always deactivate even if there's no change that confirms the desk was
        // deactivated. Some interactions, such as the desk deactivating because it's
        // occluded by a fullscreen task result in a transition change, but others, such
        // as transitioning from an empty desk to home may not.
        if (!deskChangeFound) {
            logD("Deactivating desk without transition change")
        }
        desktopRepository.setDeskInactive(deskId = deskTransition.deskId)
    }

    private fun handleChangeDeskDisplay(
        info: TransitionInfo,
        deskTransition: DeskTransition.ChangeDeskDisplay,
    ) {
        logD("handleChangeDeskDisplay: %s", deskTransition)
        val deskId = deskTransition.deskId
        val deskChange = info.changes.find { change -> desksOrganizer.isDeskChange(change, deskId) }
        if (deskChange != null) {
            desktopUserRepositories.getRepositoriesWithDeskId(deskId).forEach { desktopRepository ->
                desktopRepository.onDeskDisplayChanged(deskId, deskTransition.displayId)
            }
        }
    }

    private fun handleRemoveDisplay(deskTransition: DeskTransition.RemoveDisplay) {
        logD("handleRemoveDisplay: %s", deskTransition)
        desktopUserRepositories.forAllRepositories { desktopRepository ->
            desktopRepository.removeDisplay(deskTransition.displayId)
        }
    }

    private fun handleIndependentDeskTransitionIfNeeded(info: TransitionInfo) {
        val deskChanges = info.deskChanges()
        val desktopWallpaperChanges = info.desktopWallpaperChanges()
        if (deskChanges.isEmpty() && desktopWallpaperChanges.isEmpty()) return
        logD(
            "handleIndependentDeskTransitionIfNeeded %d desk related change(s) found with " +
                "%d desktop wallpaper change(s)",
            deskChanges.size,
            desktopWallpaperChanges.size,
        )
        if (info.isRecentsType()) {
            // Recents related changes are transient, so desk activation state should not change.
            logD("Desk changes in a recents transition, ignoring")
            return
        }
        val wct = WindowContainerTransaction()
        var hasSeenDesk = false
        var hasSeenOpeningTask = false
        val openingUserIds = mutableListOf<Int>()
        val desksToActivate = mutableListOf<Int>()
        // Visit all task changes, not just desk/wallpaper changes because we're interested in
        // capturing user ids of showing tasks (such as Home) to detect user switches.
        for (change in info.taskChanges()) {
            val taskInfo = checkNotNull(change.taskInfo) { "Expected non-null task info" }
            val taskId = taskInfo.taskId
            logD("Handle change for taskId=%d:", taskId)
            if (change.isOpeningOrToTop()) {
                logD("Opening/to-top change for userId=%d", taskInfo.userId)
                openingUserIds += taskInfo.userId
            }
            if (change in deskChanges) {
                hasSeenDesk = true
            } else if (change.isOpeningOrToTop()) {
                hasSeenOpeningTask = true
            }
            if (change !in deskChanges && change !in desktopWallpaperChanges) {
                logD("Not desk or wallpaper, skipping")
                continue
            }
            val changeUserId = taskInfo.userId
            val userSwitch = getUserSwitch(change, openingUserIds)
            logD(
                "Independent change userSwitch=%s changeUserId=%d openingUserIds=%s",
                userSwitch,
                changeUserId,
                openingUserIds,
            )

            if (change in desktopWallpaperChanges) {
                logD("Desktop wallpaper change")
                if (hasSeenDesk) {
                    logD("Saw desk change before desktop wallpaper change, skipping")
                    continue
                }
                when {
                    change.isToBack() -> {
                        // The desktop wallpaper is moving to back without seeing a desk first.
                        // This might mean an empty desk is moving to back, such as when having an
                        // empty desk active and switching users. Make sure the desk is deactivated.
                        // If this is a user switch, the userId of the desk change will have
                        // updated to the new user already, so use the old user id for reporting the
                        // deactivation to the going-away user's repository.
                        val userId = userSwitch?.oldUserId ?: changeUserId
                        val repository = desktopUserRepositories.getProfile(userId)
                        // When moving to back due to a user switch, keep the repository state as
                        // active for when the user session is restored.
                        val keepActiveInRepository = userSwitch != null
                        val activeDeskId = repository.getActiveDeskId(change.endDisplayId)
                        if (activeDeskId != null) {
                            logD(
                                "Desktop wallpaper of user=%d moved to back without visible desk" +
                                    ", will let the organizer deactivate " +
                                    "and the repository will %s",
                                userId,
                                if (keepActiveInRepository) "keep active" else "deactivate",
                            )
                            // Always let the organizer deactivate to clear the launch root.
                            desksOrganizer.deactivateDesk(wct, activeDeskId, skipReorder = true)
                            if (!keepActiveInRepository) {
                                // The desk was independently deactivated (such as when Home is
                                // brought to front during CTS), make sure the repository state
                                // reflects that too.
                                repository.setDeskInactive(activeDeskId)
                            }
                        } else {
                            logD(
                                "Desktop wallpaper of user=%d moved to back with no active desk" +
                                    ", skipping",
                                userId,
                            )
                        }
                    }
                    change.isToTop() -> {
                        // It is possible that the desktop wallpaper ends up on top of the desk.
                        // For example: when in an empty desktop, then switching users and back.
                        // WM restoration on user switches is only for visible root tasks, and an
                        // empty desk root task is technically invisible (even if top / "active"),
                        // so only the wallpaper is restored (in front of the desk).
                        // This isn't valid desktop state, so if a desk was supposed to be active
                        // activate it, otherwise dismiss the wallpaper.
                        val repository = desktopUserRepositories.getProfile(changeUserId)
                        val activeDeskId = repository.getActiveDeskId(change.endDisplayId)
                        logD(
                            "Found desktop wallpaper without a desk in front for userId=%d " +
                                "activeDeskId=%d",
                            changeUserId,
                            activeDeskId,
                        )
                        if (activeDeskId != null) {
                            logD("Reactivating desk=%d", activeDeskId)
                            desksToActivate.add(activeDeskId)
                            desksOrganizer.activateDesk(wct, activeDeskId, skipReorder = false)
                        } else {
                            logD("Dismissing desktop wallpaper")
                            val container =
                                checkNotNull(change.container) { "Expected non-null container" }
                            wct.reorder(container, /* onTop= */ false)
                        }
                    }
                    else -> {
                        logW(
                            "Unexpected change for desktop wallpaper with mode=%s",
                            TransitionInfo.modeToString(change.mode),
                        )
                    }
                }
                continue
            }

            val deskId = desksOrganizer.getDeskIdFromChange(change)
            if (deskId == null) {
                logW("No desk found in change")
                continue
            }
            val displayId = change.endDisplayId
            val usersWithDeskId =
                desktopUserRepositories.getRepositoriesWithDeskId(deskId).map { it.userId }
            logD(
                "Handle desk change for desk=%d in display=%d usersWithDeskId=%s",
                deskId,
                displayId,
                usersWithDeskId,
            )
            when {
                change.isToBack() -> {
                    if (
                        !hasSeenOpeningTask &&
                            DesktopExperienceFlags.SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT
                                .isTrue
                    ) {
                        // In cases such as minimizing the last app, where we want to remain
                        // in an empty desk, WM core may report BOTH the minimizing task and its
                        // (former) parent root as TO_BACK changes. However, the root will still
                        // remain in front (as it should), so do not interpret TO_BACK as it having
                        // to be deactivated unless something else actually showed up in front.
                        logD("desk=%d moved to back but nothing moved to front, skipping", deskId)
                        continue
                    }
                    if (
                        desksToActivate.contains(deskId) &&
                            DesktopExperienceFlags.ENABLE_EMPTY_DESK_ON_MINIMIZE.isTrue
                    ) {
                        // In cases such as back-navigating the last app, where we want to remain
                        // in an empty desk, WM core may forcefully move the desktop wallpaper to
                        // front and the desk root to back because the next focusable activity is
                        // the desktop wallpaper. This is another case of desktop wallpaper over
                        // desk that's already handled above so the desk is already scheduled to be
                        // reactivated. Just skip the deactivation here to avoid cancelling it out.
                        logD("desk=%d moved to back but is scheduled to activate, skipping", deskId)
                        continue
                    }
                    // If this is a user switch, the userId of the desk change will have updated to
                    // the new user already, so it can't be used for reporting the deactivation
                    // to the going-away user's repository.
                    val userId = userSwitch?.oldUserId ?: changeUserId
                    val repository = desktopUserRepositories.getProfile(userId)
                    // When moving to back due to a user switch, keep the repository state as
                    // active for when the user session is restored.
                    val keepActiveInRepository = userSwitch != null
                    logD(
                        "desk=%d of user=%d moved to back, will let the organizer deactivate and " +
                            "the repository will %s",
                        deskId,
                        userId,
                        if (keepActiveInRepository) "keep active" else "deactivate",
                    )
                    // Always let the organizer deactivate to clear the launch root.
                    desksOrganizer.deactivateDesk(wct, deskId, skipReorder = true)
                    if (!keepActiveInRepository) {
                        // The desk was independently deactivated (such as when Home is brought
                        // to front during CTS), make sure the repository state reflects that too.
                        repository.setDeskInactive(deskId)
                    }
                }
                change.isToTop() -> {
                    val repository = desktopUserRepositories.getProfile(changeUserId)
                    logD(
                        "desk=%d of user=%d moved to front, " +
                            "will let the organizer and repository activate",
                        deskId,
                        changeUserId,
                    )
                    desksOrganizer.activateDesk(wct, deskId, skipReorder = true)
                    repository.setActiveDesk(displayId, deskId)
                }
                else -> {
                    logW(
                        "Unexpected change for desk=%d with mode=%s",
                        deskId,
                        TransitionInfo.modeToString(change.mode),
                    )
                }
            }
        }
        if (wct.isEmpty) {
            logD("No changes, ignoring")
            return
        }
        logD("handleIndependentDeskTransitionIfNeeded starting transition")
        mainScope.launch { transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null) }
    }

    private fun getUserSwitch(
        change: TransitionInfo.Change,
        openingUserIds: List<Int>,
    ): UserSwitch? {
        val taskInfo = checkNotNull(change.taskInfo) { "Expected non-null task info" }
        val currentUserId = shellController.currentUserId
        val changeUserId = taskInfo.userId
        // This isn't a reliable way to identify user switches. It makes two assumptions:
        //  1) When switching from user A to user B with a desk active, the transition
        //  change for the desk is reported with the id of user B - that is because root
        //  tasks are not tied to a single user, so WM always sets the ID of the "current"
        //  (post-switch) user.
        //  2) The transition is handled by Shell before Shell knows about the user switch
        //  so |ShellController.userId| still reports user A.
        // TODO: b/418821815 - make desk ids unique even across users, or include user
        //  change info in the transition.
        val isUserSwitch =
            currentUserId != changeUserId ||
                // In some cases, like when switching users from one with an empty desk:
                //   1) there is no desk change because it was empty and thus already invisible
                //   2) the desktop wallpaper going to back has the userId of the new user (because
                //      it uses |showForAllUsers|.
                // So the only way to identify the user switch is by looking at other tasks (of
                // the new user) that are opening/to-top, such as Home if say the new user is
                // is restoring to Home.
                openingUserIds.filterNot { it == currentUserId }.isNotEmpty()
        if (!isUserSwitch) return null
        val newUserId =
            if (currentUserId != changeUserId) {
                changeUserId
            } else {
                openingUserIds.filterNot { it == currentUserId }.first()
            }
        return UserSwitch(oldUserId = currentUserId, newUserId = newUserId)
    }

    data class UserSwitch(val oldUserId: Int, val newUserId: Int)

    private fun TransitionInfo.isRecentsType() =
        type == TRANSIT_START_RECENTS_TRANSITION || type == TRANSIT_END_RECENTS_TRANSITION

    private fun TransitionInfo.Change.isToTop(): Boolean =
        (mode == TRANSIT_TO_FRONT) || hasFlags(FLAG_MOVED_TO_TOP)

    private fun TransitionInfo.Change.isToBack(): Boolean = mode == TRANSIT_TO_BACK

    private fun TransitionInfo.Change.isOpeningOrToTop(): Boolean =
        TransitionUtil.isOpeningMode(mode) || isToTop()

    private fun TransitionInfo.taskChanges(): List<TransitionInfo.Change> =
        changes.filter { c ->
            val taskId = c.taskInfo?.taskId
            taskId != null && taskId != INVALID_TASK_ID
        }

    private fun TransitionInfo.deskChanges(): List<TransitionInfo.Change> =
        changes.filter { c -> desksOrganizer.isDeskChange(c) }

    private fun TransitionInfo.desktopWallpaperChanges(): List<TransitionInfo.Change> =
        changes.filter { c ->
            c.container == desktopWallpaperActivityTokenProvider.getToken(c.endDisplayId)
        }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesksTransitionObserver"
    }
}
