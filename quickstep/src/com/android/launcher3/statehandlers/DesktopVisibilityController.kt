/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.statehandlers

import android.content.Context
import android.os.Debug
import android.util.Log
import android.util.Slog
import android.util.SparseArray
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY
import androidx.core.util.forEach
import com.android.launcher3.LauncherState
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.window.WindowManagerProxy.DesktopVisibilityListener
import com.android.quickstep.GestureState.GestureEndTarget
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.fallback.RecentsState
import com.android.wm.shell.desktopmode.DisplayDeskState
import com.android.wm.shell.desktopmode.IDesktopTaskListener.Stub
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus.useRoundedCorners
import java.io.PrintWriter
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Controls the visibility of the workspace and the resumed / paused state when desktop mode is
 * enabled.
 */
@LauncherAppSingleton
class DesktopVisibilityController
@Inject
constructor(
    @ApplicationContext private val context: Context,
    systemUiProxy: SystemUiProxy,
    lifecycleTracker: DaggerSingletonTracker,
) {
    /**
     * Tracks the desks configurations on each display.
     *
     * (Used only when multiple desks are enabled).
     *
     * @property displayId The ID of the display this object represents.
     * @property activeDeskId The ID of the active desk on the associated display (if any). It has a
     *   value of `INACTIVE_DESK_ID` (-1) if there are no active desks. Note that there can only be
     *   at most one active desk on each display.
     * @property deskIds a set containing the IDs of the desks on the associated display.
     */
    private data class DisplayDeskConfig(
        val displayId: Int,
        var activeDeskId: Int = INACTIVE_DESK_ID,
        val deskIds: MutableSet<Int>,
    )

    /** True if it is possible to create new desks on current setup. */
    var canCreateDesks: Boolean = false
        private set(value) {
            if (field == value) return
            field = value
            desktopVisibilityListeners.forEach { it.onCanCreateDesksChanged(field) }
        }

    /** Maps each display by its ID to its desks configuration. */
    private val displaysDesksConfigsMap = SparseArray<DisplayDeskConfig>()

    private val desktopVisibilityListeners: MutableSet<DesktopVisibilityListener> = HashSet()
    private val taskbarDesktopModeListeners: MutableSet<TaskbarDesktopModeListener> = HashSet()

    // This simply indicates that user is currently in desktop mode or not.
    @Deprecated("Does not work with multi-desks") private var isInDesktopModeDeprecated = false

    // to track if any pending notification to be done.
    var isNotifyingDesktopVisibilityPending = false

    // to let launcher hold off on notifying desktop visibility listeners.
    var launcherAnimationRunning = false

    // TODO: b/394387739 - Deprecate this and replace it with something that tracks the count per
    //  desk.
    /**
     * Number of visible desktop windows in desktop mode. This can be > 0 when user goes to overview
     * from desktop window mode.
     */
    @Deprecated("Does not work with multi-desks")
    var visibleDesktopTasksCountDeprecated: Int = 0
        /**
         * Sets the number of desktop windows that are visible and updates launcher visibility based
         * on it.
         */
        set(visibleTasksCount) {
            if (enableMultipleDesktops(context)) {
                return
            }
            if (DEBUG) {
                Log.d(
                    TAG,
                    ("setVisibleDesktopTasksCount: visibleTasksCount=" +
                        visibleTasksCount +
                        " currentValue=" +
                        field),
                )
            }

            if (visibleTasksCount != field) {
                if (visibleDesktopTasksCountDeprecated == 0 && visibleTasksCount == 1) {
                    isInDesktopModeDeprecated = true
                }
                if (visibleDesktopTasksCountDeprecated == 1 && visibleTasksCount == 0) {
                    isInDesktopModeDeprecated = false
                }
                val wasVisible = field > 0
                val isVisible = visibleTasksCount > 0
                val wereDesktopTasksVisibleBefore = areDesktopTasksVisibleAndNotInOverview()
                field = visibleTasksCount
                val areDesktopTasksVisibleNow = areDesktopTasksVisibleAndNotInOverview()

                if (
                    wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow ||
                        wasVisible != isVisible
                ) {
                    if (!launcherAnimationRunning) {
                        notifyIsInDesktopModeChanged(DEFAULT_DISPLAY, areDesktopTasksVisibleNow)
                    } else {
                        isNotifyingDesktopVisibilityPending = true
                    }
                }

                if (
                    !ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue && wasVisible != isVisible
                ) {
                    // TODO: b/333533253 - Remove after flag rollout
                    if (field > 0) {
                        if (!inOverviewState) {
                            // When desktop tasks are visible & we're not in overview, we want
                            // launcher
                            // to appear paused, this ensures that taskbar displays.
                            markLauncherPaused()
                        }
                    } else {
                        // If desktop tasks aren't visible, ensure that launcher appears resumed to
                        // behave normally.
                        markLauncherResumed()
                    }
                }
            }
        }

    private var inOverviewState = false
    private var backgroundStateEnabled = false
    private var gestureInProgress = false

    private var desktopTaskListener: DesktopTaskListenerImpl?

    init {
        desktopTaskListener = DesktopTaskListenerImpl(this, context, context.displayId)
        systemUiProxy.setDesktopTaskListener(desktopTaskListener)

        lifecycleTracker.addCloseable {
            desktopTaskListener = null
            systemUiProxy.setDesktopTaskListener(null)
        }
    }

    /**
     * Returns the ID of the active desk (if any) on the display whose ID is [displayId], or
     * [INACTIVE_DESK_ID] if no desk is currently active or the multiple desks feature is disabled.
     */
    fun getActiveDeskId(displayId: Int): Int {
        if (!enableMultipleDesktops(context)) {
            // When the multiple desks feature is disabled, callers should not rely on the concept
            // of a desk ID.
            return INACTIVE_DESK_ID
        }

        return getDisplayDeskConfig(displayId)?.activeDeskId ?: INACTIVE_DESK_ID
    }

    /** Returns whether a desk is currently active on the display with the given [displayId]. */
    fun isInDesktopMode(displayId: Int): Boolean {
        if (!enableMultipleDesktops(context)) {
            return isInDesktopModeDeprecated
        }

        val activeDeskId = getDisplayDeskConfig(displayId)?.activeDeskId ?: INACTIVE_DESK_ID
        val isInDesktopMode = activeDeskId != INACTIVE_DESK_ID
        if (DEBUG) {
            Log.d(TAG, "isInDesktopMode: $isInDesktopMode")
        }
        return isInDesktopMode
    }

    /**
     * Returns whether a desk is currently active on the display with the given [displayId] and
     * Overview is not active.
     */
    fun isInDesktopModeAndNotInOverview(displayId: Int): Boolean {
        if (!enableMultipleDesktops(context)) {
            return areDesktopTasksVisibleAndNotInOverview()
        }

        if (DEBUG) {
            Log.d(TAG, "isInDesktopModeAndNotInOverview: overview=$inOverviewState")
        }
        return isInDesktopMode(displayId) && !inOverviewState
    }

    /** Whether desktop tasks are visible in desktop mode. */
    private fun areDesktopTasksVisibleAndNotInOverview(): Boolean {
        val desktopTasksVisible: Boolean = visibleDesktopTasksCountDeprecated > 0
        if (DEBUG) {
            Log.d(
                TAG,
                ("areDesktopTasksVisible: desktopVisible=" +
                    desktopTasksVisible +
                    " overview=" +
                    inOverviewState),
            )
        }
        return desktopTasksVisible && !inOverviewState
    }

    /** Registers a listener for Taskbar changes in Desktop Mode. */
    fun registerTaskbarDesktopModeListener(listener: TaskbarDesktopModeListener) {
        taskbarDesktopModeListeners.add(listener)
    }

    /** Removes a previously registered listener for Taskbar changes in Desktop Mode. */
    fun unregisterTaskbarDesktopModeListener(listener: TaskbarDesktopModeListener) {
        taskbarDesktopModeListeners.remove(listener)
    }

    fun onLauncherStateChanged(state: LauncherState) {
        onLauncherStateChanged(
            state,
            state === LauncherState.BACKGROUND_APP,
            state.isRecentsViewVisible,
        )
    }

    /**
     * Launcher Driven Desktop Mode changes. For example, swipe to home and quick switch from
     * Desktop Windowing Mode. if there is any pending notification please notify desktop visibility
     * listeners.
     */
    fun onLauncherAnimationFromDesktopEnd() {
        launcherAnimationRunning = false
        if (isNotifyingDesktopVisibilityPending) {
            isNotifyingDesktopVisibilityPending = false
            notifyIsInDesktopModeChanged(
                DEFAULT_DISPLAY,
                isInDesktopModeAndNotInOverview(DEFAULT_DISPLAY),
            )
        }
    }

    fun onLauncherStateChanged(state: RecentsState) {
        onLauncherStateChanged(
            state,
            state === RecentsState.BACKGROUND_APP,
            state.isRecentsViewVisible,
        )
    }

    /** Process launcher state change and update launcher view visibility based on desktop state */
    fun onLauncherStateChanged(
        state: BaseState<*>,
        isBackgroundAppState: Boolean,
        isRecentsViewVisible: Boolean,
    ) {
        if (DEBUG) {
            Log.d(TAG, "onLauncherStateChanged: newState=$state")
        }
        setBackgroundStateEnabled(isBackgroundAppState)
        // Desktop visibility tracks overview and background state separately
        setOverviewStateEnabled(!isBackgroundAppState && isRecentsViewVisible)
    }

    private fun setOverviewStateEnabled(overviewStateEnabled: Boolean) {
        if (DEBUG) {
            Log.d(
                TAG,
                ("setOverviewStateEnabled: enabled=" +
                    overviewStateEnabled +
                    " currentValue=" +
                    inOverviewState),
            )
        }
        if (overviewStateEnabled != inOverviewState) {
            val wereDesktopTasksVisibleBefore = areDesktopTasksVisibleAndNotInOverview()
            inOverviewState = overviewStateEnabled
            val areDesktopTasksVisibleNow = areDesktopTasksVisibleAndNotInOverview()

            if (!enableMultipleDesktops(context)) {
                if (wereDesktopTasksVisibleBefore != areDesktopTasksVisibleNow) {
                    notifyIsInDesktopModeChanged(DEFAULT_DISPLAY, areDesktopTasksVisibleNow)
                }
            } else {
                // When overview state changes, it changes together on all displays.
                displaysDesksConfigsMap.forEach { displayId, deskConfig ->
                    // Overview affects the state of desks only if desktop mode is active on this
                    // display.
                    if (isInDesktopMode(displayId)) {
                        notifyIsInDesktopModeChanged(
                            displayId,
                            isInDesktopModeAndNotInOverview(displayId),
                        )
                    }
                }
            }

            if (ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue) {
                return
            }

            // TODO: b/333533253 - Clean up after flag rollout
            if (inOverviewState) {
                markLauncherResumed()
            } else if (areDesktopTasksVisibleNow && !gestureInProgress) {
                // Switching out of overview state and gesture finished.
                // If desktop tasks are still visible, hide launcher again.
                markLauncherPaused()
            }
        }
    }

    /** Registers a listener for Taskbar changes in Desktop Mode. */
    fun registerDesktopVisibilityListener(listener: DesktopVisibilityListener) {
        desktopVisibilityListeners.add(listener)
    }

    /** Removes a previously registered listener for Taskbar changes in Desktop Mode. */
    fun unregisterDesktopVisibilityListener(listener: DesktopVisibilityListener) {
        desktopVisibilityListeners.remove(listener)
    }

    private fun notifyIsInDesktopModeChanged(
        displayId: Int,
        isInDesktopModeAndNotInOverview: Boolean,
    ) {
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyIsInDesktopModeChanged: displayId=$displayId, isInDesktopModeAndNotInOverview=$isInDesktopModeAndNotInOverview",
            )
        }

        for (listener in desktopVisibilityListeners) {
            listener.onIsInDesktopModeChanged(displayId, isInDesktopModeAndNotInOverview)
        }
    }

    private fun notifyTaskbarDesktopModeListeners(doesAnyTaskRequireTaskbarRounding: Boolean) {
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyTaskbarDesktopModeListeners: doesAnyTaskRequireTaskbarRounding=" +
                    doesAnyTaskRequireTaskbarRounding,
            )
        }
        for (listener in taskbarDesktopModeListeners) {
            listener.onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding)
        }
    }

    private fun notifyTaskbarDesktopModeListenersForEntry(duration: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyTaskbarDesktopModeListenersForEntry: duration=" + duration)
        }
        for (listener in taskbarDesktopModeListeners) {
            listener.onEnterDesktopMode(duration)
        }
        DisplayController.INSTANCE.get(context).notifyConfigChange()
    }

    private fun notifyTaskbarDesktopModeListenersForExit(duration: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyTaskbarDesktopModeListenersForExit: duration=" + duration)
        }
        for (listener in taskbarDesktopModeListeners) {
            listener.onExitDesktopMode(duration)
        }
        DisplayController.INSTANCE.get(context).notifyConfigChange()
    }

    private fun notifyOnDeskAdded(displayId: Int, deskId: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyOnDeskAdded: displayId=$displayId, deskId=$deskId")
        }

        for (listener in desktopVisibilityListeners) {
            listener.onDeskAdded(displayId, deskId)
        }
    }

    private fun notifyOnDeskRemoved(displayId: Int, deskId: Int) {
        if (DEBUG) {
            Log.d(TAG, "notifyOnDeskRemoved: displayId=$displayId, deskId=$deskId")
        }

        for (listener in desktopVisibilityListeners) {
            listener.onDeskRemoved(displayId, deskId)
        }
    }

    private fun notifyOnActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
        if (DEBUG) {
            Log.d(
                TAG,
                "notifyOnActiveDeskChanged: displayId=$displayId, newActiveDesk=$newActiveDesk, oldActiveDesk=$oldActiveDesk",
            )
        }

        for (listener in desktopVisibilityListeners) {
            listener.onActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
        }
    }

    /** TODO: b/333533253 - Remove after flag rollout */
    private fun setBackgroundStateEnabled(backgroundStateEnabled: Boolean) {
        if (DEBUG) {
            Log.d(
                TAG,
                ("setBackgroundStateEnabled: enabled=" +
                    backgroundStateEnabled +
                    " currentValue=" +
                    this.backgroundStateEnabled),
            )
        }
        if (backgroundStateEnabled != this.backgroundStateEnabled) {
            this.backgroundStateEnabled = backgroundStateEnabled
            if (this.backgroundStateEnabled) {
                markLauncherResumed()
            } else if (areDesktopTasksVisibleAndNotInOverview() && !gestureInProgress) {
                // Switching out of background state. If desktop tasks are visible, pause launcher.
                markLauncherPaused()
            }
        }
    }

    var isRecentsGestureInProgress: Boolean
        /**
         * Whether recents gesture is currently in progress.
         *
         * TODO: b/333533253 - Remove after flag rollout
         */
        get() = gestureInProgress
        /** TODO: b/333533253 - Remove after flag rollout */
        private set(gestureInProgress) {
            if (gestureInProgress != this.gestureInProgress) {
                this.gestureInProgress = gestureInProgress
            }
        }

    /**
     * Notify controller that recents gesture has started.
     *
     * TODO: b/333533253 - Remove after flag rollout
     */
    fun setRecentsGestureStart() {
        if (DEBUG) {
            Log.d(TAG, "setRecentsGestureStart")
        }
        isRecentsGestureInProgress = true
    }

    /**
     * Notify controller that recents gesture finished with the given
     * [com.android.quickstep.GestureState.GestureEndTarget]
     *
     * TODO: b/333533253 - Remove after flag rollout
     */
    fun setRecentsGestureEnd(endTarget: GestureEndTarget?) {
        if (DEBUG) {
            Log.d(TAG, "setRecentsGestureEnd: endTarget=$endTarget")
        }
        isRecentsGestureInProgress = false

        if (endTarget == null) {
            // Gesture did not result in a new end target. Ensure launchers gets paused again.
            markLauncherPaused()
        }
    }

    private fun onListenerConnected(
        displayDeskStates: Array<DisplayDeskState>,
        canCreateDesks: Boolean,
    ) {
        if (!enableMultipleDesktops(context)) {
            return
        }

        displaysDesksConfigsMap.clear()

        displayDeskStates.forEach { displayDeskState ->
            displaysDesksConfigsMap[displayDeskState.displayId] =
                DisplayDeskConfig(
                    displayId = displayDeskState.displayId,
                    activeDeskId = displayDeskState.activeDeskId,
                    deskIds = displayDeskState.deskIds.toMutableSet(),
                )
        }

        this.canCreateDesks = canCreateDesks
    }

    private fun getDisplayDeskConfig(displayId: Int) =
        displaysDesksConfigsMap[displayId]
            ?: null.also { Slog.e(TAG, "Expected non-null desk config for display: $displayId") }

    private fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
        if (!enableMultipleDesktops(context)) {
            return
        }

        this.canCreateDesks = canCreateDesks
    }

    private fun onDeskAdded(displayId: Int, deskId: Int) {
        if (!enableMultipleDesktops(context)) {
            return
        }

        getDisplayDeskConfig(displayId)?.also {
            check(it.deskIds.add(deskId)) {
                "Found a duplicate desk Id: $deskId on display: $displayId"
            }
        }

        notifyOnDeskAdded(displayId, deskId)
    }

    private fun onDeskRemoved(displayId: Int, deskId: Int) {
        if (!enableMultipleDesktops(context)) {
            return
        }

        getDisplayDeskConfig(displayId)?.also {
            check(it.deskIds.remove(deskId)) {
                "Removing non-existing desk Id: $deskId on display: $displayId"
            }
            if (it.activeDeskId == deskId) {
                it.activeDeskId = INACTIVE_DESK_ID
            }
        }

        notifyOnDeskRemoved(displayId, deskId)
    }

    private fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
        if (!enableMultipleDesktops(context)) {
            return
        }

        val wasInDesktopMode = isInDesktopModeAndNotInOverview(displayId)

        getDisplayDeskConfig(displayId)?.also {
            check(oldActiveDesk == it.activeDeskId) {
                "Mismatch between the Shell's oldActiveDesk: $oldActiveDesk, and Launcher's: ${it.activeDeskId}"
            }
            check(newActiveDesk == INACTIVE_DESK_ID || it.deskIds.contains(newActiveDesk)) {
                "newActiveDesk: $newActiveDesk was never added to display: $displayId"
            }
            it.activeDeskId = newActiveDesk
        }

        if (newActiveDesk != oldActiveDesk) {
            notifyOnActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
        }

        if (wasInDesktopMode != isInDesktopModeAndNotInOverview(displayId)) {
            notifyIsInDesktopModeChanged(displayId, !wasInDesktopMode)
        }
    }

    /** TODO: b/333533253 - Remove after flag rollout */
    private fun markLauncherPaused() {
        if (ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherPaused " + Debug.getCaller())
        }
        val activity: StatefulActivity<LauncherState>? =
            QuickstepLauncher.ACTIVITY_TRACKER.getCreatedContext()
        activity?.setPaused()
    }

    /** TODO: b/333533253 - Remove after flag rollout */
    private fun markLauncherResumed() {
        if (ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "markLauncherResumed " + Debug.getCaller())
        }
        val activity: StatefulActivity<LauncherState>? =
            QuickstepLauncher.ACTIVITY_TRACKER.getCreatedContext()
        // Check activity state before calling setResumed(). Launcher may have been actually
        // paused (eg fullscreen task moved to front).
        // In this case we should not mark the activity as resumed.
        if (activity != null && activity.isResumed) {
            activity.setResumed()
        }
    }

    fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "DesktopVisibilityController:")

        pw.println("$prefix\tdesktopVisibilityListeners=$desktopVisibilityListeners")
        pw.println("$prefix\tvisibleDesktopTasksCount=$visibleDesktopTasksCountDeprecated")
        pw.println("$prefix\tinOverviewState=$inOverviewState")
        pw.println("$prefix\tbackgroundStateEnabled=$backgroundStateEnabled")
        pw.println("$prefix\tgestureInProgress=$gestureInProgress")
        pw.println("$prefix\tdesktopTaskListener=$desktopTaskListener")
        pw.println("$prefix\tcontext=$context")
    }

    /**
     * Wrapper for the IDesktopTaskListener stub to prevent lingering references to the launcher
     * activity via the controller.
     */
    private class DesktopTaskListenerImpl(
        controller: DesktopVisibilityController,
        @ApplicationContext private val context: Context,
        private val displayId: Int,
    ) : Stub() {
        private val controller = WeakReference(controller)

        override fun onListenerConnected(
            displayDeskStates: Array<DisplayDeskState>,
            canCreateDesks: Boolean,
        ) {
            MAIN_EXECUTOR.execute {
                controller.get()?.onListenerConnected(displayDeskStates, canCreateDesks)
            }
        }

        override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {
            if (displayId != this.displayId) return
            MAIN_EXECUTOR.execute {
                controller.get()?.apply {
                    if (DEBUG) {
                        Log.d(TAG, "desktop visible tasks count changed=$visibleTasksCount")
                    }
                    visibleDesktopTasksCountDeprecated = visibleTasksCount
                }
            }
        }

        override fun onStashedChanged(displayId: Int, stashed: Boolean) {
            Log.w(TAG, "DesktopTaskListenerImpl: onStashedChanged is deprecated")
        }

        override fun onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding: Boolean) {
            if (!useRoundedCorners()) return
            MAIN_EXECUTOR.execute {
                controller.get()?.apply {
                    Log.d(
                        TAG,
                        "DesktopTaskListenerImpl: doesAnyTaskRequireTaskbarRounding= " +
                            doesAnyTaskRequireTaskbarRounding,
                    )
                    notifyTaskbarDesktopModeListeners(doesAnyTaskRequireTaskbarRounding)
                }
            }
        }

        // TODO: b/402496827 - The multi-desks backend needs to be updated to call this API only
        //  once, not between desk switches.
        override fun onEnterDesktopModeTransitionStarted(transitionDuration: Int) {
            val controller = controller.get() ?: return
            MAIN_EXECUTOR.execute {
                Log.d(
                    TAG,
                    ("DesktopTaskListenerImpl: onEnterDesktopModeTransitionStarted with " +
                        "duration= " +
                        transitionDuration),
                )
                if (enableMultipleDesktops(context)) {
                    controller.notifyTaskbarDesktopModeListenersForEntry(transitionDuration)
                } else if (!controller.isInDesktopModeDeprecated) {
                    controller.isInDesktopModeDeprecated = true
                    controller.notifyTaskbarDesktopModeListenersForEntry(transitionDuration)
                }
            }
        }

        // TODO: b/402496827 - The multi-desks backend needs to be updated to call this API only
        //  once, not between desk switches.
        override fun onExitDesktopModeTransitionStarted(transitionDuration: Int) {
            val controller = controller.get() ?: return
            MAIN_EXECUTOR.execute {
                Log.d(
                    TAG,
                    ("DesktopTaskListenerImpl: onExitDesktopModeTransitionStarted with " +
                        "duration= " +
                        transitionDuration),
                )
                if (enableMultipleDesktops(context)) {
                    controller.notifyTaskbarDesktopModeListenersForExit(transitionDuration)
                } else if (controller.isInDesktopModeDeprecated) {
                    controller.isInDesktopModeDeprecated = false
                    controller.notifyTaskbarDesktopModeListenersForExit(transitionDuration)
                }
            }
        }

        override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
            MAIN_EXECUTOR.execute { controller.get()?.onCanCreateDesksChanged(canCreateDesks) }
        }

        override fun onDeskAdded(displayId: Int, deskId: Int) {
            MAIN_EXECUTOR.execute { controller.get()?.onDeskAdded(displayId, deskId) }
        }

        override fun onDeskRemoved(displayId: Int, deskId: Int) {
            MAIN_EXECUTOR.execute { controller.get()?.onDeskRemoved(displayId, deskId) }
        }

        override fun onActiveDeskChanged(displayId: Int, newActiveDesk: Int, oldActiveDesk: Int) {
            MAIN_EXECUTOR.execute {
                controller.get()?.onActiveDeskChanged(displayId, newActiveDesk, oldActiveDesk)
            }
        }
    }

    /** A listener for Taskbar in Desktop Mode. */
    interface TaskbarDesktopModeListener {
        /**
         * Callback for when task is resized in desktop mode.
         *
         * @param doesAnyTaskRequireTaskbarRounding whether task requires taskbar corner roundness.
         */
        fun onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding: Boolean) {}

        /**
         * Callback for when user is exiting desktop mode.
         *
         * @param duration for exit transition
         */
        fun onExitDesktopMode(duration: Int) {}

        /**
         * Callback for when user is entering desktop mode.
         *
         * @param duration for enter transition
         */
        fun onEnterDesktopMode(duration: Int) {}
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDesktopVisibilityController)

        private const val TAG = "DesktopVisController"
        private const val DEBUG = false

        const val INACTIVE_DESK_ID = -1
    }
}
