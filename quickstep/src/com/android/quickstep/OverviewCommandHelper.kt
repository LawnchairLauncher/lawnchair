/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.View
import android.window.TransitionInfo
import androidx.annotation.BinderThread
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.android.app.displaylib.DisplayRepository
import com.android.app.displaylib.PerDisplayRepository
import com.android.internal.jank.Cuj
import com.android.launcher3.DeviceProfile
import com.android.launcher3.PagedView
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarUIController
import com.android.launcher3.util.OverviewCommandHelperProtoLogProxy
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.android.quickstep.OverviewCommandHelper.CommandInfo.CommandStatus
import com.android.quickstep.OverviewCommandHelper.CommandType.HIDE_ALT_TAB
import com.android.quickstep.OverviewCommandHelper.CommandType.HOME
import com.android.quickstep.OverviewCommandHelper.CommandType.SHOW_ALT_TAB
import com.android.quickstep.OverviewCommandHelper.CommandType.SHOW_WITH_FOCUS
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE_OVERVIEW_PREVIOUS
import com.android.quickstep.fallback.window.RecentsWindowManager
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.ActiveGestureProtoLogProxy
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.wm.shell.Flags.enableShellTopTaskTracking
import java.io.PrintWriter
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Helper class to handle various atomic commands for switching between Overview. */
class OverviewCommandHelper
@JvmOverloads
constructor(
    private val touchInteractionService: TouchInteractionService,
    private val overviewComponentObserver: OverviewComponentObserver,
    private val dispatcherProvider: DispatcherProvider = ProductionDispatchers,
    private val displayRepository: DisplayRepository,
    private val taskbarManager: TaskbarManager,
    private val taskAnimationManagerRepository: PerDisplayRepository<TaskAnimationManager>,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val coroutineScope =
        CoroutineScope(SupervisorJob() + dispatcherProvider.lightweightBackground)

    private val commandQueue = ConcurrentLinkedDeque<CommandInfo>()

    /**
     * Index of the TaskView that should be focused when launching Overview. Persisted so that we do
     * not lose the focus across multiple calls of [OverviewCommandHelper.executeCommand] for the
     * same command
     */
    private var keyboardTaskFocusIndex = -1

    private val lastToggleInfo = mutableMapOf<Int, ToggleInfo>()

    private fun getContainerInterface(displayId: Int) =
        overviewComponentObserver.getContainerInterface(displayId)

    private fun getVisibleRecentsView(displayId: Int) =
        getContainerInterface(displayId)?.getVisibleRecentsView<RecentsView<*, *>>()

    /**
     * Adds a command to be executed next, after all pending tasks are completed. Max commands that
     * can be queued is [.MAX_QUEUE_SIZE]. Requests after reaching that limit will be silently
     * dropped.
     *
     * @param type The type of the command
     * @param onDisplays The display to run the command on
     */
    @BinderThread
    @JvmOverloads
    fun addCommand(
        type: CommandType,
        displayId: Int = DEFAULT_DISPLAY,
        isLastOfBatch: Boolean = true,
    ): CommandInfo? {
        if (commandQueue.size >= MAX_QUEUE_SIZE) {
            OverviewCommandHelperProtoLogProxy.logCommandQueueFull(type, commandQueue)
            return null
        }

        val command =
            CommandInfo(
                type,
                displayId = displayId,
                createTime = elapsedRealtime(),
                isLastOfBatch = isLastOfBatch,
            )
        commandQueue.add(command)
        OverviewCommandHelperProtoLogProxy.logCommandAdded(command)

        if (commandQueue.size == 1) {
            OverviewCommandHelperProtoLogProxy.logCommandExecuted(command, commandQueue.size)
            coroutineScope.launch(dispatcherProvider.main) { processNextCommand() }
        } else {
            OverviewCommandHelperProtoLogProxy.logCommandNotExecuted(command, commandQueue.size)
        }

        return command
    }

    @BinderThread
    fun addCommandsForDisplays(type: CommandType, displayIds: IntArray): CommandInfo? {
        if (displayIds.isEmpty()) return null
        var lastCommand: CommandInfo? = null
        displayIds.forEachIndexed({ i, displayId ->
            lastCommand = addCommand(type, displayId, i == displayIds.size - 1)
        })
        return lastCommand
    }

    @BinderThread
    fun addCommandsForAllDisplays(type: CommandType) =
        addCommandsForDisplays(type, displayRepository.displayIds.value.toIntArray())

    @BinderThread
    fun addCommandsForDisplaysExcept(type: CommandType, excludedDisplayId: Int) =
        addCommandsForDisplays(
            type,
            displayRepository.displayIds.value
                .filter { displayId -> displayId != excludedDisplayId }
                .toIntArray(),
        )

    fun canStartHomeSafely(): Boolean =
        commandQueue.isEmpty() ||
            commandQueue.first().type == HOME ||
            commandQueue.first().type == TOGGLE_OVERVIEW_PREVIOUS

    /** Clear pending or completed commands from the queue */
    fun clearPendingCommands() {
        OverviewCommandHelperProtoLogProxy.logClearPendingCommands(commandQueue)
        commandQueue.removeAll { it.status != CommandStatus.PROCESSING }
    }

    /**
     * Executes the next command from the queue. If the command finishes immediately (returns true),
     * it continues to execute the next command, until the queue is empty of a command defer's its
     * completion (returns false).
     */
    @UiThread
    private fun processNextCommand() {
            val command: CommandInfo? = commandQueue.firstOrNull()
            if (command == null) {
                OverviewCommandHelperProtoLogProxy.logNoPendingCommands()
                return
            }

            command.status = CommandStatus.PROCESSING
            OverviewCommandHelperProtoLogProxy.logExecutingCommand(command)

            coroutineScope.launch(dispatcherProvider.main) {
                    withTimeout(QUEUE_WAIT_DURATION_IN_MS) {
                        executeCommandSuspended(command)
                        ensureActive()
                        onCommandFinished(command)
                    }
            }
        }

    /**
     * Executes the task and returns true if next task can be executed. If false, then the next task
     * is deferred until [.scheduleNextTask] is called
     */
    @VisibleForTesting
    fun executeCommand(command: CommandInfo, onCallbackResult: () -> Unit): Boolean {
        val recentsView = getVisibleRecentsView(command.displayId)
        OverviewCommandHelperProtoLogProxy.logExecutingCommand(command, recentsView)
        return if (recentsView != null) {
            executeWhenRecentsIsVisible(command, recentsView, onCallbackResult)
        } else {
            executeWhenRecentsIsNotVisible(command, onCallbackResult)
        }
    }

    /**
     * Executes the task and returns true if next task can be executed. If false, then the next task
     * is deferred until [.scheduleNextTask] is called
     */
    private suspend fun executeCommandSuspended(command: CommandInfo) =
        suspendCancellableCoroutine { continuation ->
            fun processResult(isCompleted: Boolean) {
                OverviewCommandHelperProtoLogProxy.logExecutedCommandWithResult(
                    command,
                    isCompleted,
                )
                if (isCompleted) {
                    continuation.resume(Unit)
                } else {
                    OverviewCommandHelperProtoLogProxy.logWaitingForCommandCallback(command)
                }
            }

            val result = executeCommand(command, onCallbackResult = { processResult(true) })
            processResult(result)

            continuation.invokeOnCancellation { cancelCommand(command, it) }
        }

    private fun executeWhenRecentsIsVisible(
        command: CommandInfo,
        recentsView: RecentsView<*, *>,
        onCallbackResult: () -> Unit,
    ): Boolean =
        when (command.type) {
            SHOW_WITH_FOCUS -> true // already visible
            SHOW_ALT_TAB,
            HIDE_ALT_TAB -> {
                if (recentsView.isHandlingTouch) {
                    true
                } else {
                    keyboardTaskFocusIndex = PagedView.INVALID_PAGE
                    val currentPage = recentsView.nextPage
                    val taskView = recentsView.getTaskViewAt(currentPage)
                    launchTask(recentsView, taskView, command, onCallbackResult)
                }
            }

            TOGGLE -> {
                val runningTaskId = recentsView.runningTaskView?.taskIdSet
                launchTask(
                    recentsView,
                    getNextToggledTaskView(recentsView, command.displayId),
                    command,
                ) {
                    if (enableGridOnlyOverview() && runningTaskId != null) {
                        lastToggleInfo[command.displayId] =
                            ToggleInfo(command.createTime, runningTaskId)
                    }
                    onCallbackResult()
                }
            }
            TOGGLE_OVERVIEW_PREVIOUS -> {
                val taskView = recentsView.runningTaskView
                if (taskView == null) {
                    recentsView.startHome()
                } else {
                    taskView.launchWithAnimation()
                }
                true
            }
            HOME -> {
                recentsView.startHome()
                true
            }
        }

    private fun getNextToggledTaskView(recentsView: RecentsView<*, *>, displayId: Int): TaskView? {
        val lastToggleInfo = lastToggleInfo[displayId]
        val lastToggleTaskView =
            if (
                enableGridOnlyOverview() &&
                    lastToggleInfo != null &&
                    elapsedRealtime() - lastToggleInfo.createTime < TOGGLE_PREVIOUS_TIMEOUT_MS
            ) {
                recentsView.getTaskViewByTaskIds(lastToggleInfo.taskIds.toIntArray())
            } else null
        val runningTaskView = recentsView.runningTaskView
        return when {
            runningTaskView == null && !enableGridOnlyOverview() ->
                // When running task view is null we return last large taskView - typically
                // focusView or last desktop task view.
                recentsView.lastLargeTaskView ?: recentsView.firstTaskView
            runningTaskView == null ->
                recentsView.firstNonDesktopTaskView ?: recentsView.lastDesktopTaskView
            lastToggleTaskView != null && lastToggleTaskView != runningTaskView ->
                lastToggleTaskView
            else -> recentsView.nextTaskView ?: recentsView.previousTaskView ?: runningTaskView
        }
    }

    private fun launchTask(
        recents: RecentsView<*, *>,
        taskView: TaskView?,
        command: CommandInfo,
        onCallbackResult: () -> Unit,
    ): Boolean {
        var callbackList: RunnableList? = null
        if (taskView != null) {
            taskView.isEndQuickSwitchCuj = true
            callbackList = taskView.launchWithAnimation()
        }

        if (callbackList != null) {
            callbackList.add {
                OverviewCommandHelperProtoLogProxy.logLaunchingTaskCallback(command)
                onCallbackResult()
            }
            OverviewCommandHelperProtoLogProxy.logLaunchingTaskWaitingForCallback(command)
            return false
        } else {
            recents.startHome()
            return true
        }
    }

    // Returns false if callbacks should be awaited, true otherwise.
    private fun executeWhenRecentsIsNotVisible(
        command: CommandInfo,
        onCallbackResult: () -> Unit,
    ): Boolean {
        val containerInterface = getContainerInterface(command.displayId) ?: return true
        val recentsViewContainer = containerInterface.getCreatedContainer()
        val recentsView: RecentsView<*, *>? = recentsViewContainer?.getOverviewPanel()
        val deviceProfile = recentsViewContainer?.getDeviceProfile()
        val taskbarUIController: TaskbarUIController? =
            if (
                command.displayId != DEFAULT_DISPLAY &&
                    recentsViewContainer !is RecentsWindowManager
            ) {
                // When recentsViewContainer is not RecentsWindowManager, get TaskbarUiController
                // from TaskbarManager as a workaround.
                taskbarManager.getUIControllerForDisplay(command.displayId)
            } else {
                containerInterface.getTaskbarController()
            }

        val taskAnimationManager = taskAnimationManagerRepository[command.displayId]
        if (taskAnimationManager == null) {
            Log.e(TAG, "No TaskAnimationManager found for display ${command.displayId}")
            ActiveGestureProtoLogProxy.logOnTaskAnimationManagerNotAvailable(command.displayId)
            return false
        }

        when (command.type) {
            HIDE_ALT_TAB -> {
                if (
                    taskbarUIController == null ||
                        !shouldShowAltTabKqs(deviceProfile, command.displayId)
                ) {
                    return true
                }
                keyboardTaskFocusIndex = taskbarUIController.launchFocusedTask()

                if (keyboardTaskFocusIndex == -1) return true
            }

            SHOW_ALT_TAB ->
                if (
                    taskbarUIController != null &&
                        shouldShowAltTabKqs(deviceProfile, command.displayId)
                ) {
                    taskbarUIController.openQuickSwitchView()
                    return true
                } else {
                    keyboardTaskFocusIndex = 0
                }

            HOME -> {
                taskAnimationManager.maybeStartHomeAction {
                    // Although IActivityTaskManager$Stub$Proxy.startActivity is a slow binder call,
                    // we should still call it on main thread because launcher is waiting for
                    // ActivityTaskManager to resume it. Also calling startActivity() on bg thread
                    // could potentially delay resuming launcher. See b/348668521 for more details.
                    touchInteractionService.startActivity(
                        overviewComponentObserver.getHomeIntent(command.displayId)
                    )
                }
                return true
            }

            SHOW_WITH_FOCUS ->
                // When Recents is not currently visible, the command's type is SHOW
                // when overview is triggered via the keyboard overview button or Action+Tab
                // keys (Not Alt+Tab which is KQS). The overview button on-screen in 3-button
                // nav is TYPE_TOGGLE.
                keyboardTaskFocusIndex = 0

            TOGGLE,
            TOGGLE_OVERVIEW_PREVIOUS -> {}
        }

        recentsView?.setKeyboardTaskFocusIndex(
            recentsView.indexOfChild(recentsView.taskViews.elementAtOrNull(keyboardTaskFocusIndex))
                ?: -1
        )

        // Handle recents view focus when launching from home
        val animatorListener: Animator.AnimatorListener =
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    OverviewCommandHelperProtoLogProxy.logSwitchingToOverviewStateStart(command)
                    super.onAnimationStart(animation)
                    updateRecentsViewFocus(command)
                    logShowOverviewFrom(command)
                }

                override fun onAnimationEnd(animation: Animator) {
                    OverviewCommandHelperProtoLogProxy.logSwitchingToOverviewStateEnd(command)
                    super.onAnimationEnd(animation)
                    onRecentsViewFocusUpdated(command)
                    onCallbackResult()
                }
            }
        if (containerInterface.switchToRecentsIfVisible(animatorListener)) {
            OverviewCommandHelperProtoLogProxy.logSwitchingToOverviewStateWaiting(command)
            // If successfully switched, wait until animation finishes
            return false
        }

        // If we get here then launcher is not the top visible task, so we should animate
        // that task.

        if (recentsViewContainer !is RecentsWindowManager) {
            recentsViewContainer?.rootView?.let { view ->
                InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_QUICK_SWITCH)
            }
        }

        val gestureState =
            touchInteractionService
                .createGestureState(
                    command.displayId,
                    GestureState.DEFAULT_STATE,
                    GestureState.TrackpadGestureType.NONE,
                )
                .apply {
                    isHandlingAtomicEvent = true
                    if (!enableShellTopTaskTracking()) {
                        val runningTask = runningTask
                        // In the case where we are in an excluded, translucent overlay, ignore it
                        // and treat the running activity as the task behind the overlay.
                        val otherVisibleTask = runningTask?.visibleNonExcludedTask
                        if (otherVisibleTask != null) {
                            ActiveGestureProtoLogProxy.logUpdateGestureStateRunningTask(
                                otherVisibleTask.packageName ?: "MISSING",
                                runningTask.packageName ?: "MISSING",
                            )
                            updateRunningTask(otherVisibleTask)
                        }
                    }
                }
        val interactionHandler =
            touchInteractionService
                .getSwipeUpHandlerFactory(command.displayId)
                .newHandler(gestureState, command.createTime)
        if (interactionHandler == null) {
            // Can happen e.g. when a display is disconnected, so try to handle gracefully.
            Log.d(TAG, "AbsSwipeUpHandler not available for displayId=${command.displayId})")
            ActiveGestureProtoLogProxy.logOnAbsSwipeUpHandlerNotAvailable(command.displayId)
            return true
        }
        interactionHandler.setGestureAnimationEndCallback {
            onTransitionComplete(command, interactionHandler, onCallbackResult)
        }
        interactionHandler.initWhenReady("OverviewCommandHelper: command.type=${command.type}")

        val recentAnimListener: RecentsAnimationCallbacks.RecentsAnimationListener =
            object : RecentsAnimationCallbacks.RecentsAnimationListener {
                override fun onRecentsAnimationStart(
                    controller: RecentsAnimationController,
                    targets: RecentsAnimationTargets,
                    transitionInfo: TransitionInfo?,
                ) {
                    OverviewCommandHelperProtoLogProxy.logRecentsAnimStarted(command)
                    if (recentsViewContainer is RecentsWindowManager) {
                        recentsViewContainer.rootView?.let { view ->
                            InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_QUICK_SWITCH)
                        }
                    }

                    updateRecentsViewFocus(command)
                    logShowOverviewFrom(command)
                    containerInterface.runOnInitBackgroundStateUI {
                        OverviewCommandHelperProtoLogProxy.logOnInitBackgroundStateUI(command)
                        interactionHandler.onGestureEnded(
                            0f,
                            PointF(),
                            /* horizontalTouchSlopPassed= */ false,
                        )
                    }
                    command.removeListener(this)
                }

                override fun onRecentsAnimationCanceled(
                    thumbnailDatas: HashMap<Int, ThumbnailData>
                ) {
                    OverviewCommandHelperProtoLogProxy.logRecentsAnimCanceled(command)
                    interactionHandler.onGestureCancelled()
                    command.removeListener(this)

                    containerInterface.getCreatedContainer() ?: return
                    recentsView?.onRecentsAnimationComplete()
                }
            }

        if (taskAnimationManager.isRecentsAnimationRunning) {
            command.setAnimationCallbacks(
                taskAnimationManager.continueRecentsAnimation(gestureState)
            )
            command.addListener(interactionHandler)
            taskAnimationManager.notifyRecentsAnimationState(interactionHandler)
            interactionHandler.onGestureStarted(true /*isLikelyToStartNewTask*/)

            command.addListener(recentAnimListener)
            taskAnimationManager.notifyRecentsAnimationState(recentAnimListener)
        } else {
            val intent =
                Intent(interactionHandler.getLaunchIntent())
                    .putExtra(ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID, gestureState.gestureId)
            command.setAnimationCallbacks(
                taskAnimationManager.startRecentsAnimation(gestureState, intent, interactionHandler)
            )
            interactionHandler.onGestureStarted(false /*isLikelyToStartNewTask*/)
            command.addListener(recentAnimListener)
        }
        OverviewCommandHelperProtoLogProxy.logSwitchingViaRecentsAnim(command)
        return false
    }

    private fun shouldShowAltTabKqs(deviceProfile: DeviceProfile?, displayId: Int): Boolean =
        // Alt+Tab KQS is always shown on tablets (large screen devices).
        deviceProfile?.deviceProperties?.isTablet == true ||
            // For small screen devices, it's only shown on connected displays.
            displayId != DEFAULT_DISPLAY

    private fun onTransitionComplete(
        command: CommandInfo,
        handler: AbsSwipeUpHandler<*, *, *>,
        onCommandResult: () -> Unit,
    ) {
        OverviewCommandHelperProtoLogProxy.logSwitchingViaRecentsAnimComplete(command)
        command.removeListener(handler)
        onRecentsViewFocusUpdated(command)
        onCommandResult()
    }

    /** Called when the command finishes execution. */
    private fun onCommandFinished(command: CommandInfo) {
        command.status = CommandStatus.COMPLETED
        if (commandQueue.firstOrNull() !== command) {
            OverviewCommandHelperProtoLogProxy.logCommandFinishedButNotScheduled(
                commandQueue.firstOrNull(),
                command,
            )
            return
        }

        OverviewCommandHelperProtoLogProxy.logCommandFinishedSuccessfully(command)
        commandQueue.remove(command)
        processNextCommand()
    }

    private fun cancelCommand(command: CommandInfo, throwable: Throwable?) {
        command.status = CommandStatus.CANCELED
        OverviewCommandHelperProtoLogProxy.logCommandCanceled(command, throwable)
        commandQueue.remove(command)
        processNextCommand()
    }

    private fun updateRecentsViewFocus(command: CommandInfo) {
        val recentsView: RecentsView<*, *> = getVisibleRecentsView(command.displayId) ?: return
        if (
            command.type != SHOW_ALT_TAB &&
                command.type != HIDE_ALT_TAB &&
                command.type != SHOW_WITH_FOCUS
        ) {
            return
        }
        // When the overview is launched via alt tab (command type is TYPE_KEYBOARD_INPUT),
        // the touch mode somehow is not change to false by the Android framework.
        // The subsequent tab to go through tasks in overview can only be dispatched to
        // focuses views, while focus can only be requested in
        // {@link View#requestFocusNoSearch(int, Rect)} when touch mode is false. To note,
        // here we launch overview with live tile.
        if (recentsView.isAttachedToWindow) {
            recentsView.viewRootImpl.touchModeChanged(false)
        } else {
            recentsView.post { recentsView.viewRootImpl.touchModeChanged(false) }
        }
        // Ensure that recents view has focus so that it receives the followup key inputs
        // Stops requesting focused after first view gets focused.
        recentsView
            .getTaskViewAt(
                recentsView.indexOfChild(
                    recentsView.taskViews.elementAtOrNull(keyboardTaskFocusIndex)
                )
            )
            .requestFocus() ||
            recentsView.nextTaskView.requestFocus() ||
            recentsView.firstTaskView.requestFocus() ||
            recentsView.requestFocus()
    }

    private fun onRecentsViewFocusUpdated(command: CommandInfo) {
        val recentsView: RecentsView<*, *> = getVisibleRecentsView(command.displayId) ?: return
        if (command.type != HIDE_ALT_TAB || keyboardTaskFocusIndex == PagedView.INVALID_PAGE) {
            return
        }
        recentsView.setKeyboardTaskFocusIndex(PagedView.INVALID_PAGE)
        recentsView.currentPage =
            recentsView.indexOfChild(recentsView.taskViews.elementAtOrNull(keyboardTaskFocusIndex))
        keyboardTaskFocusIndex = PagedView.INVALID_PAGE
    }

    private fun View?.requestFocus(): Boolean {
        if (this == null) return false
        post {
            requestFocus()
            requestAccessibilityFocus()
        }
        return true
    }

    private fun logShowOverviewFrom(command: CommandInfo) {
        val containerInterface = getContainerInterface(command.displayId) ?: return
        val container = containerInterface.getCreatedContainer() ?: return
        val event =
            when (command.type) {
                SHOW_WITH_FOCUS -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT
                HIDE_ALT_TAB -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH
                TOGGLE -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON
                else -> return
            }
        StatsLogManager.newInstance(container.asContext())
            .logger()
            .withContainerInfo(
                LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskSwitcherContainer(
                        LauncherAtom.TaskSwitcherContainer.getDefaultInstance()
                    )
                    .build()
            )
            .log(event)
    }

    fun dump(pw: PrintWriter) {
        pw.println("OverviewCommandHelper:")
        pw.println("  pendingCommands=${commandQueue.size}")
        if (commandQueue.isNotEmpty()) {
            pw.println("    pendingCommandType=${commandQueue.first().type}")
        }
        pw.println("  keyboardTaskFocusIndex=$keyboardTaskFocusIndex")
    }

    @VisibleForTesting
    data class CommandInfo(
        val type: CommandType,
        var status: CommandStatus = CommandStatus.IDLE,
        val createTime: Long,
        private var animationCallbacks: RecentsAnimationCallbacks? = null,
        val displayId: Int = DEFAULT_DISPLAY,
        val isLastOfBatch: Boolean = true,
    ) {
        fun setAnimationCallbacks(recentsAnimationCallbacks: RecentsAnimationCallbacks) {
            this.animationCallbacks = recentsAnimationCallbacks
        }

        fun addListener(listener: RecentsAnimationCallbacks.RecentsAnimationListener) {
            animationCallbacks?.addListener(listener)
        }

        fun removeListener(listener: RecentsAnimationCallbacks.RecentsAnimationListener?) {
            animationCallbacks?.removeListener(listener)
        }

        enum class CommandStatus {
            IDLE,
            PROCESSING,
            COMPLETED,
            CANCELED,
        }
    }

    enum class CommandType {
        SHOW_WITH_FOCUS,
        SHOW_ALT_TAB,
        HIDE_ALT_TAB,
        /** Toggle between overview and the next task */
        TOGGLE, // Navigate to Overview
        HOME, // Navigate to Home
        /**
         * Toggle between Overview and the previous screen before launching Overview, which can
         * either be a task or the home screen.
         */
        TOGGLE_OVERVIEW_PREVIOUS,
    }

    data class ToggleInfo(val createTime: Long, val taskIds: Set<Int>)

    companion object {
        private const val TAG = "OverviewCommandHelper"
        private const val TRANSITION_NAME = "Transition:toOverview"

        /**
         * Use case for needing a queue is double tapping recents button in 3 button nav. Size of 2
         * should be enough. We'll toss in one more because we're kind hearted.
         */
        private const val MAX_QUEUE_SIZE = 3
        private const val QUEUE_WAIT_DURATION_IN_MS = 5000L
        @VisibleForTesting val TOGGLE_PREVIOUS_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
