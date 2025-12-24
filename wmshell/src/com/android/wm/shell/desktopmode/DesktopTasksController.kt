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

package com.android.wm.shell.desktopmode

import android.annotation.UserIdInt
import android.app.ActivityManager
import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.TaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.app.WindowConfiguration.WindowingMode
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Region
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.os.Trace
import android.os.UserHandle
import android.os.UserManager
import android.util.Slog
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.DragEvent
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_PIP
import android.view.WindowManager.TRANSIT_START_LOCK_TASK_MODE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.WindowManager.transitTypeToString
import android.widget.Toast
import android.window.DesktopExperienceFlags
import android.window.DesktopExperienceFlags.DesktopExperienceFlag
import android.window.DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY
import android.window.DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT
import android.window.DesktopExperienceFlags.ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY
import android.window.DesktopModeFlags
import android.window.DesktopModeFlags.DISABLE_NON_RESIZABLE_APP_SNAP_RESIZE
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY
import android.window.DesktopModeFlags.ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS
import android.window.RemoteTransition
import android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON
import android.window.TaskSnapshotManager
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.annotation.BinderThread
//import com.android.app.tracing.traceSection
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MOVE_FROM_SPLIT_SCREEN
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightPx
import com.android.internal.protolog.ProtoLog
import com.android.internal.util.LatencyTracker
import com.android.window.flags2.Flags
import com.android.wm.shell.Flags.enableFlexibleSplit
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ExternalInterfaceBinder
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.MultiInstanceHelper.Companion.getComponent
import com.android.wm.shell.common.RemoteCallable
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.desktopmode.DesktopMixedTransitionHandler.PendingMixedTransition
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.getEnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.getExitReason
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.DragStartState
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.Companion.DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.DragToDesktopStateListener
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler.FULLSCREEN_ANIMATION_DURATION
import com.android.wm.shell.desktopmode.clientfullscreenrequest.ClientFullscreenRequestTransitionHandler
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.desktopmode.data.DesktopDisplay
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepository.Companion.INVALID_DESK_ID
import com.android.wm.shell.desktopmode.data.DesktopRepository.DeskChangeListener
import com.android.wm.shell.desktopmode.data.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer.DeskRecreationFactory
import com.android.wm.shell.desktopmode.data.persistence.DesktopTaskTilingState
import com.android.wm.shell.desktopmode.desktopfirst.DesktopFirstListenerManager
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.minimize.DesktopWindowLimitRemoteHandler
import com.android.wm.shell.desktopmode.multidesks.DeskSwitchTransitionHandler
import com.android.wm.shell.desktopmode.multidesks.DeskTransition
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.desktopmode.multidesks.PreserveDisplayRequestHandler
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.recents.RecentTasksController
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.RecentsTransitionState
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING
import com.android.wm.shell.shared.R as SharedR
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ExternalThread
import com.android.wm.shell.shared.annotations.ShellDesktopThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopFirstListener
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DESKTOP_MODE
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.OneShotRemoteHandler
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.windowdecor.DragPositioningCallbackUtility
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator
import com.android.wm.shell.windowdecor.OnTaskRepositionAnimationListener
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import com.android.wm.shell.windowdecor.extension.isFullscreen
import com.android.wm.shell.windowdecor.extension.isMultiWindow
import com.android.wm.shell.windowdecor.extension.requestingImmersive
import com.android.wm.shell.windowdecor.tiling.DesktopTilingWindowDecoration.Companion.getDividerBoundsForZombieSession
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import com.android.wm.shell.windowdecor.tiling.TilingDisplayReconnectEventHandler
import java.io.PrintWriter
import java.util.Optional
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.coroutines.suspendCoroutine
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A callback to be invoked when a transition is started via |Transitions.startTransition| with the
 * transition binder token that it produces.
 *
 * Useful when multiple components are appending WCT operations to a single transition that is
 * started outside of their control, and each of them wants to track the transition lifecycle
 * independently by cross-referencing the transition token with future ready-transitions.
 */
typealias RunOnTransitStart = (IBinder) -> Unit

/** Handles moving tasks in and out of desktop */
class DesktopTasksController(
    private val context: Context,
    shellInit: ShellInit,
    private val shellCommandHandler: ShellCommandHandler,
    private val shellController: ShellController,
    private val displayController: DisplayController,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val syncQueue: SyncTransactionQueue,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val dragAndDropController: DragAndDropController,
    private val transitions: Transitions,
    private val keyguardManager: KeyguardManager,
    private val returnToDragStartAnimator: ReturnToDragStartAnimator,
    private val desktopMixedTransitionHandler: DesktopMixedTransitionHandler,
    private val enterDesktopTaskTransitionHandler: EnterDesktopTaskTransitionHandler,
    private val exitDesktopTaskTransitionHandler: ExitDesktopTaskTransitionHandler,
    private val desktopModeDragAndDropTransitionHandler: DesktopModeDragAndDropTransitionHandler,
    private val toggleResizeDesktopTaskTransitionHandler: ToggleResizeDesktopTaskTransitionHandler,
    private val dragToDesktopTransitionHandler: DragToDesktopTransitionHandler,
    private val desktopImmersiveController: DesktopImmersiveController,
    private val clientFullscreenRequestTransitionHandler: ClientFullscreenRequestTransitionHandler,
    private val userRepositories: DesktopUserRepositories,
    desktopRepositoryInitializer: DesktopRepositoryInitializer,
    private val recentsTransitionHandler: RecentsTransitionHandler,
    private val multiInstanceHelper: MultiInstanceHelper,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    @ShellMainThread private val mainScope: CoroutineScope,
    @ShellDesktopThread private val desktopExecutor: ShellExecutor,
    private val desktopTasksLimiter: Optional<DesktopTasksLimiter>,
    private val recentTasksController: RecentTasksController?,
    private val interactionJankMonitor: InteractionJankMonitor,
    @ShellMainThread private val handler: Handler,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val desktopModeEventLogger: DesktopModeEventLogger,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val bubbleController: Optional<BubbleController>,
    private val overviewToDesktopTransitionObserver: OverviewToDesktopTransitionObserver,
    private val desksOrganizer: DesksOrganizer,
    private val desksTransitionObserver: DesksTransitionObserver,
    private val userProfileContexts: UserProfileContexts,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
    private val windowDragTransitionHandler: WindowDragTransitionHandler,
    private val deskSwitchTransitionHandler: DeskSwitchTransitionHandler,
    private val moveToDisplayTransitionHandler: DesktopModeMoveToDisplayTransitionHandler,
    private val homeIntentProvider: HomeIntentProvider,
    private val desktopState: DesktopState,
    private val desktopConfig: DesktopConfig,
    private val visualIndicatorUpdateScheduler: VisualIndicatorUpdateScheduler,
    private val desktopFirstListenerManager: Optional<DesktopFirstListenerManager>,
    private val taskSnapshotManager: TaskSnapshotManager,
) :
    RemoteCallable<DesktopTasksController>,
    Transitions.TransitionHandler,
    DragAndDropController.DragAndDropListener,
    UserChangeListener {

    private val desktopMode: DesktopModeImpl
    private var visualIndicator: DesktopModeVisualIndicator? = null
    private val desktopModeShellCommandHandler: DesktopModeShellCommandHandler =
        DesktopModeShellCommandHandler(this, focusTransitionObserver)
    private val latencyTracker: LatencyTracker

    private val mOnAnimationFinishedCallback = { releaseVisualIndicator() }
    private lateinit var snapEventHandler: SnapEventHandler
    private val dragToDesktopStateListener =
        object : DragToDesktopStateListener {
            override fun onCommitToDesktopAnimationStart() {
                removeVisualIndicator()
            }

            override fun onCancelToDesktopAnimationEnd() {
                removeVisualIndicator()
            }

            override fun onTransitionInterrupted() {
                removeVisualIndicator()
            }

            private fun removeVisualIndicator() {
                visualIndicator?.fadeOutIndicator { releaseVisualIndicator() }
            }
        }

    @VisibleForTesting var taskbarDesktopTaskListener: TaskbarDesktopTaskListener? = null

    @VisibleForTesting
    var desktopModeEnterExitTransitionListener: DesktopModeEntryExitTransitionListener? = null

    /** Task id of the task currently being dragged from fullscreen/split. */
    val draggingTaskId
        get() = dragToDesktopTransitionHandler.draggingTaskId

    @RecentsTransitionState private var recentsTransitionState = TRANSITION_STATE_NOT_RUNNING

    private lateinit var splitScreenController: SplitScreenController
    lateinit var freeformTaskTransitionStarter: FreeformTaskTransitionStarter
    // Launch cookie used to identify a drag and drop transition to fullscreen after it has begun.
    // Used to prevent handleRequest from moving the new fullscreen task to freeform.
    private var dragAndDropFullscreenCookie: Binder? = null

    // A listener that is invoked after a desk has been remove from the system. */
    var onDeskRemovedListener: OnDeskRemovedListener? = null

    // A handler for requests to preserve a disconnected display to potentially restore later.
    var preserveDisplayRequestHandler: PreserveDisplayRequestHandler? = null

    private val toDesktopAnimationDurationMs =
        context.resources.getInteger(SharedR.integer.to_desktop_animation_duration_ms)

    init {
        desktopMode = DesktopModeImpl()
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback({ onInit() }, this)
        }
        latencyTracker = LatencyTracker.getInstance(context)

        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desktopRepositoryInitializer.deskRecreationFactory =
                DeskRecreationFactory { deskUserId, destinationDisplayId, _ ->
                    // TODO: b/393978539 - One of the recreated desks may need to be activated by
                    //  default in desktop-first.
                    createDeskRootSuspending(displayId = destinationDisplayId, userId = deskUserId)
                }
        }
    }

    private fun onInit() {
        logD("onInit")
        shellCommandHandler.addDumpCallback(this::dump, this)
        shellCommandHandler.addCommandCallback("desktopmode", desktopModeShellCommandHandler, this)
        shellController.addExternalInterface(
            IDesktopMode.DESCRIPTOR,
            { createExternalInterface() },
            this,
        )
        shellController.addUserChangeListener(this)
        // Update the current user id again because it might be updated between init and onInit().
        updateCurrentUser(ActivityManager.getCurrentUser())
        transitions.addHandler(this)
        clientFullscreenRequestTransitionHandler.desktopTasksController = this
        dragToDesktopTransitionHandler.dragToDesktopStateListener = dragToDesktopStateListener
        recentsTransitionHandler.addTransitionStateListener(
            object : RecentsTransitionStateListener {
                override fun onTransitionStateChanged(@RecentsTransitionState state: Int) {
                    logV(
                        "Recents transition state changed: %s",
                        RecentsTransitionStateListener.stateToString(state),
                    )
                    recentsTransitionState = state
                }
            }
        )
        dragAndDropController.addListener(this)
        desksOrganizer.addOnDesktopTaskInfoChangedListener { taskInfo ->
            onTaskInfoChanged(taskInfo)
        }
    }

    @VisibleForTesting
    fun getVisualIndicator(): DesktopModeVisualIndicator? {
        return visualIndicator
    }

    fun setOnTaskResizeAnimationListener(listener: OnTaskResizeAnimationListener) {
        toggleResizeDesktopTaskTransitionHandler.setOnTaskResizeAnimationListener(listener)
        enterDesktopTaskTransitionHandler.setOnTaskResizeAnimationListener(listener)
        dragToDesktopTransitionHandler.onTaskResizeAnimationListener = listener
        desktopImmersiveController.onTaskResizeAnimationListener = listener
        clientFullscreenRequestTransitionHandler.onTaskResizeAnimationListener = listener
    }

    fun setOnTaskRepositionAnimationListener(listener: OnTaskRepositionAnimationListener) {
        returnToDragStartAnimator.setTaskRepositionAnimationListener(listener)
    }

    /** Setter needed to avoid cyclic dependency. */
    fun setSplitScreenController(controller: SplitScreenController) {
        splitScreenController = controller
        dragToDesktopTransitionHandler.setSplitScreenController(controller)
    }

    /** Setter to handle snap events */
    fun setSnapEventHandler(handler: SnapEventHandler) {
        snapEventHandler = handler
        desktopTasksLimiter.ifPresent { it.snapEventHandler = snapEventHandler }
    }

    /** Returns the transition type for the given remote transition. */
    private fun transitionType(remoteTransition: RemoteTransition?): Int {
        if (remoteTransition == null) {
            logV("RemoteTransition is null")
            return TRANSIT_NONE
        }
        return TRANSIT_TO_FRONT
    }

    /**
     * Shows all tasks, that are part of the desktop, on top of launcher. Brings the task with id
     * [taskIdToReorderToFront] to front if provided and is already on the default desk on the given
     * display.
     */
    @Deprecated("Use activateDesk() instead.", ReplaceWith("activateDesk()"))
    fun showDesktopApps(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
        transitionSource: DesktopModeTransitionSource,
    ) {
        logV("showDesktopApps")
        activateDefaultDeskInDisplay(
            displayId,
            userId,
            remoteTransition,
            taskIdToReorderToFront,
            transitionSource,
        )
    }

    /** Returns whether the given display has an active desk. */
    @JvmOverloads
    fun isAnyDeskActive(displayId: Int, userId: Int = shellController.currentUserId): Boolean =
        userRepositories.getProfile(userId).isAnyDeskActive(displayId)

    /** Returns the id of the active desk in [displayId]. */
    @JvmOverloads
    fun getActiveDeskId(displayId: Int, userId: Int = shellController.currentUserId): Int? =
        userRepositories.getProfile(userId).getActiveDeskId(displayId)

    /**
     * Moves focused task to desktop mode for given [displayId].
     *
     * TODO(b/405381458): use focusTransitionObserver to get the focused task on a certain display
     */
    fun moveFocusedTaskToDesktop(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        transitionSource: DesktopModeTransitionSource,
    ) {
        val allFocusedTasks = getFocusedNonDesktopTasks(displayId = displayId, userId = userId)
        when (allFocusedTasks.size) {
            0 -> return
            // Full screen case
            1 -> moveFullscreenTaskToDesktop(allFocusedTasks.single(), transitionSource)
            // Split-screen case where there are two focused tasks, then we find the child
            // task to move to desktop.
            2 -> {
                val focusedTask = getSplitFocusedTask(allFocusedTasks[0], allFocusedTasks[1])
                if (desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(focusedTask)) {
                    return
                }
                moveTaskToDefaultDeskAndActivate(
                    focusedTask.taskId,
                    transitionSource = transitionSource,
                )
            }
            else ->
                logW(
                    "DesktopTasksController: Cannot enter desktop, expected less " +
                        "than 3 focused tasks but found %d",
                    allFocusedTasks.size,
                )
        }
    }

    /**
     * Toggles the focused task's fullscreen state. A desktop task or a split screen task is moved
     * into fullscreen mode, while a fullscreen task is moved into desktop mode.
     */
    fun toggleFocusedTaskFullscreenState(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        transitionSource: DesktopModeTransitionSource,
    ) {
        getFocusedDesktopTask(displayId = displayId, userId = userId)?.let {
            // Desktop -> Fullscreen.
            moveDesktopTaskToFullscreen(it, displayId, transitionSource)
            return
        }
        val allFocusedTasks = getFocusedNonDesktopTasks(displayId = displayId, userId = userId)
        when (allFocusedTasks.size) {
            0 -> {}
            // Fullscreen -> Desktop.
            1 -> {
                val focusedTask = allFocusedTasks.single()
                // No-op toggling to Desktop via keyboard shortcut if we are mid-dragging the same
                // task to Desktop
                if (
                    transitionSource == DesktopModeTransitionSource.KEYBOARD_SHORTCUT &&
                        focusedTask.taskId == draggingTaskId
                ) {
                    logW(
                        "DesktopTasksController: Abandon keyboard shortcut attempt to toggle " +
                            "from fullscreen to Desktop because task with id=%d is mid-drag",
                        focusedTask.taskId,
                    )
                    return
                }
                moveFullscreenTaskToDesktop(focusedTask, transitionSource)
            }
            // Split screen -> Fullscreen (the active split screen app is moved into fullscreen).
            2 -> splitScreenController.goToFullscreenFromSplit()
            else ->
                logW(
                    "DesktopTasksController: Cannot toggle fullscreen state, expected less " +
                        "than 3 focused tasks but found %d",
                    allFocusedTasks.size,
                )
        }
    }

    /**
     * Returns all focused tasks in full screen or split screen mode in [displayId] excluding home
     * activity and desk roots.
     */
    fun getFocusedNonDesktopTasks(displayId: Int, userId: Int): List<RunningTaskInfo> {
        val repository = userRepositories.getProfile(userId)
        return shellTaskOrganizer.getRunningTasks(displayId).filter { taskInfo ->
            val focused = taskInfo.isFocused
            val isNotDesktop =
                if (DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue) {
                    !repository.isActiveTask(taskInfo.taskId) &&
                        !repository.getAllDeskIds().contains(taskInfo.taskId)
                } else {
                    taskInfo.windowingMode == WINDOWING_MODE_FULLSCREEN ||
                        taskInfo.windowingMode == WINDOWING_MODE_MULTI_WINDOW
                }
            val isHome = taskInfo.activityType == ACTIVITY_TYPE_HOME
            return@filter focused && isNotDesktop && !isHome
        }
    }

    /** Moves a desktop task into fullscreen mode. */
    private fun moveDesktopTaskToFullscreen(
        task: RunningTaskInfo,
        displayId: Int,
        transitionSource: DesktopModeTransitionSource,
    ) {
        snapEventHandler.removeTaskIfTiled(displayId, task.taskId)
        moveToFullscreenWithAnimation(task, task.positionInParent, transitionSource)
    }

    /** Moves a fullscreen task into desktop mode. */
    private fun moveFullscreenTaskToDesktop(
        task: RunningTaskInfo,
        transitionSource: DesktopModeTransitionSource,
    ) {
        if (desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(task)) return
        moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = transitionSource)
    }

    @Deprecated("Use isDisplayDesktopFirst() instead.", ReplaceWith("isDisplayDesktopFirst()"))
    private fun isDesktopFirstLegacy(displayId: Int): Boolean {
        if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX.isTrue) {
            return rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
        }

        if (!desktopState.enterDesktopByDefaultOnFreeformDisplay) {
            return false
        }

        // Secondary displays are always desktop-first
        if (displayId != DEFAULT_DISPLAY) {
            return true
        }

        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        // A non-organized display (e.g., non-trusted virtual displays used in CTS) doesn't have
        // TDA.
        if (tdaInfo == null) {
            logW(
                "isDesktopFirstLegacy cannot find DisplayAreaInfo for displayId=%d. This could happen" +
                    " when the display is a non-trusted virtual display.",
                displayId,
            )
            return false
        }
        val tdaWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
        val isFreeformDisplay = tdaWindowingMode == WINDOWING_MODE_FREEFORM
        return isFreeformDisplay
    }

    /** Called when the recents transition that started while in desktop is finishing. */
    @JvmOverloads
    fun onRecentsInDesktopAnimationFinishing(
        transition: IBinder,
        finishWct: WindowContainerTransaction,
        returnToApp: Boolean,
        userId: Int = shellController.currentUserId,
        activeDeskIdOnRecentsStart: Int?,
    ) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        logV(
            "onRecentsInDesktopAnimationFinishing returnToApp=%b activeDeskIdOnRecentsStart=%d",
            returnToApp,
            activeDeskIdOnRecentsStart,
        )

        if (returnToApp) {
            // Returning to the same desk, notify the snap event handler of recents animation
            // ending to the same desk.
            snapEventHandler.onRecentsAnimationEndedToSameDesk()
            return
        }
        val repository = userRepositories.getProfile(userId)
        if (
            activeDeskIdOnRecentsStart == null ||
                !repository.isDeskActive(activeDeskIdOnRecentsStart)
        ) {
            // No desk was active or it is already inactive.
            return
        }
        // At this point the recents transition is either finishing to home, to another non-desktop
        // task or to a different desk than the one that was active when recents started. For all
        // of those the desk that was active needs to be deactivated.
        val runOnTransitStart =
            performDesktopExitCleanUp(
                wct = finishWct,
                deskId = activeDeskIdOnRecentsStart,
                displayId = DEFAULT_DISPLAY,
                userId = userId,
                willExitDesktop = true,
                removingLastTaskId = null,
                // No need to clean up the wallpaper / home when coming from a recents transition.
                skipWallpaperAndHomeOrdering = true,
                // This is a recents-finish, so taskbar animation on transit start does not apply.
                skipUpdatingExitDesktopListener = true,
                exitReason = ExitReason.RETURN_HOME_OR_OVERVIEW,
            )
        runOnTransitStart?.invoke(transition)
    }

    /** Returns whether a new desk can be created. */
    fun canCreateDesks(userId: Int = shellController.currentUserId): Boolean {
        val deskLimit = desktopConfig.maxDeskLimit
        val repository = userRepositories.getProfile(userId)
        return deskLimit == 0 || repository.getNumberOfDesks() < deskLimit
    }

    /**
     * Adds a new desk to the given display for the given user and invokes [onResult] once the desk
     * is created, but necessarily activated.
     */
    fun createDesk(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        enforceDeskLimit: Boolean = true,
        activateDesk: Boolean = false,
        enterReason: EnterReason = EnterReason.UNKNOWN_ENTER,
        onResult: ((Int) -> Unit) = {},
    ) {
        logV(
            "createDesk displayId=%d, userId=%d enforceDeskLimit=%b",
            displayId,
            userId,
            enforceDeskLimit,
        )
        if (!desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
            // Display does not support desktops, no-op.
            logW("createDesk displayId $displayId does not support desktops, ignoring request")
            return
        }
        val repository = userRepositories.getProfile(userId)
        if (enforceDeskLimit && !canCreateDesks(userId)) {
            // At the limit, no-op.
            logW("createDesk already at desk-limit, ignoring request")
            return
        }
        createDeskRoot(displayId, userId) { deskId ->
            if (deskId == null) {
                logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
            } else {
                repository.addDesk(
                    displayId = displayId,
                    deskId = deskId,
                    uniqueDisplayId = displayController.getDisplayUniqueId(displayId),
                )
                onResult(deskId)
                if (activateDesk) {
                    activateDesk(deskId = deskId, userId = userId, enterReason = enterReason)
                }
            }
        }
    }

    @Deprecated("Use createDeskSuspending() instead.", ReplaceWith("createDeskSuspending()"))
    private fun createDeskImmediate(displayId: Int, userId: Int): Int? {
        logV("createDeskImmediate displayId=%d, userId=%d", displayId, userId)
        val repository = userRepositories.getProfile(userId)
        val deskId = createDeskRootImmediate(displayId, userId)
        if (deskId == null) {
            logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
            return null
        }
        repository.addDesk(
            displayId = displayId,
            deskId = deskId,
            uniqueDisplayId = displayController.getDisplayUniqueId(displayId),
        )
        return deskId
    }

    private suspend fun createDeskSuspending(
        displayId: Int,
        userId: Int,
        enforceDeskLimit: Boolean,
    ): Int = suspendCoroutine { cont ->
        createDesk(displayId = displayId, userId = userId, enforceDeskLimit = enforceDeskLimit) {
            deskId ->
            cont.resumeWith(Result.success(deskId))
        }
    }

    private fun createDeskRoot(displayId: Int, userId: Int, onResult: (Int?) -> Unit) {
        if (displayId == Display.INVALID_DISPLAY) {
            logW("createDesk attempt with invalid displayId", displayId)
            onResult(null)
            return
        }
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // In single-desk, the desk reuses the display id.
            logD("createDesk reusing displayId=%d for single-desk", displayId)
            onResult(displayId)
            return
        }
        if (
            DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_HSUM.isTrue &&
                UserManager.isHeadlessSystemUserMode() &&
                UserHandle.USER_SYSTEM == userId
        ) {
            logW("createDesk ignoring attempt for system user")
            onResult(null)
            return
        }
        desksOrganizer.createDesk(displayId, userId) { deskId ->
            logD(
                "createDesk obtained deskId=%d for displayId=%d and userId=%d",
                deskId,
                displayId,
                userId,
            )
            onResult(deskId)
        }
    }

    @Deprecated(
        "Use createDeskRootSuspending() instead.",
        ReplaceWith("createDeskRootSuspending()"),
    )
    private fun createDeskRootImmediate(displayId: Int, userId: Int): Int? {
        if (displayId == Display.INVALID_DISPLAY) {
            logW("createDeskRootImmediate attempt with invalid displayId", displayId)
            return null
        }
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // In single-desk, the desk reuses the display id.
            logD("createDeskRootImmediate reusing displayId=%d for single-desk", displayId)
            return displayId
        }
        if (
            DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_HSUM.isTrue &&
                UserManager.isHeadlessSystemUserMode() &&
                UserHandle.USER_SYSTEM == userId
        ) {
            logW("createDeskRootImmediate ignoring attempt for system user")
            return null
        }
        return desksOrganizer.createDeskImmediate(displayId, userId)
    }

    private suspend fun createDeskRootSuspending(displayId: Int, userId: Int): Int? =
        suspendCoroutine { cont ->
            createDeskRoot(displayId, userId) { deskId -> cont.resumeWith(Result.success(deskId)) }
        }

    /** Start a disconnect transition directly in Shell. */
    fun disconnectDisplay(disconnectedDisplayId: Int) {
        logD("disconnectDisplay: disconnectedDisplayId=$disconnectedDisplayId")
        var disconnectReparentDisplay =
            UserManager.get(userProfileContexts.userContext).mainDisplayIdAssignedToUser
        // If user has no default display configured, fall back to DEFAULT_DISPLAY
        if (disconnectReparentDisplay == INVALID_DISPLAY) {
            disconnectReparentDisplay = DEFAULT_DISPLAY
        }
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addOnDisplayDisconnectChanges(wct, disconnectedDisplayId, disconnectReparentDisplay)
        val transition = transitions.startTransition(TRANSIT_CLOSE, wct, null)
        runOnTransitStart(transition)
    }

    private fun onDisplayDisconnect(
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
        transition: IBinder,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        addOnDisplayDisconnectChanges(wct, disconnectedDisplayId, destinationDisplayId)
            .invoke(transition)
        try {
            userRepositories.current.getExpandedTasksOrdered(disconnectedDisplayId).forEach {
                logD("addOnDisplayDisconnect: taking a snapshot of=$it before disconnect")
                if (Flags.reduceTaskSnapshotMemoryUsage()) {
                    taskSnapshotManager.takeTaskSnapshot(it, true)
                } else {
                    ActivityTaskManager.getService().getTaskSnapshot(it, false)
                }
            }
        } catch (e: RemoteException) {
            logE("addOnDisplayDisconnect: failed to take task snapshot", e)
        }

        return wct
    }

    private fun addOnDisplayDisconnectChanges(
        wct: WindowContainerTransaction,
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
    ): RunOnTransitStart {
        logD(
            "addOnDisplayDisconnectChanges: disconnectedDisplayId=$disconnectedDisplayId, " +
                "destinationDisplayId=$destinationDisplayId"
        )
        val runOnTransitStartList = mutableListOf<RunOnTransitStart>()
        preserveDisplayRequestHandler?.requestPreserveDisplay(disconnectedDisplayId)
        // TODO: b/406320371 - Verify this works with non-system users once the underlying bug is
        //  resolved.
        // TODO: b/391652399 - Investigate why sometimes disconnect results in a black background.
        //  Additionally, investigate why wallpaper goes to front for inactive users.
        val desktopModeSupportedOnDisplay =
            desktopState.isDesktopModeSupportedOnDisplay(destinationDisplayId)
        val destDisplayLayout = displayController.getDisplayLayout(destinationDisplayId)
        if (destDisplayLayout == null) {
            logE(
                "addOnDisplayDisconnectChanges: no display layout found for " +
                    "destinationDisplayId=$destinationDisplayId"
            )
        }
        snapEventHandler.onDisplayDisconnected(disconnectedDisplayId)
        removeHomeTask(wct, disconnectedDisplayId)
        userRepositories.forAllRepositories { desktopRepository ->
            val userId = desktopRepository.userId
            val deskIds = desktopRepository.getDeskIds(disconnectedDisplayId).toList()
            if (desktopModeSupportedOnDisplay) {
                handleExtendedModeDisconnect(
                    desktopRepository,
                    wct,
                    runOnTransitStartList,
                    deskIds,
                    disconnectedDisplayId,
                    destinationDisplayId,
                    destDisplayLayout,
                    userId,
                )
            } else {
                handleProjectedModeDisconnect(
                    desktopRepository,
                    wct,
                    runOnTransitStartList,
                    deskIds,
                    disconnectedDisplayId,
                    destinationDisplayId,
                    userId,
                )
            }
        }
        return { transition ->
            for (runOnTransitStart in runOnTransitStartList) {
                runOnTransitStart(transition)
            }
        }
    }

    private fun handleExtendedModeDisconnect(
        desktopRepository: DesktopRepository,
        wct: WindowContainerTransaction,
        runOnTransitStartList: MutableList<RunOnTransitStart>,
        deskIds: List<Int>,
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
        destDisplayLayout: DisplayLayout?,
        userId: Int,
    ) {
        // Desktop supported on display; reparent desks, focused desk on top.
        for (deskId in deskIds) {
            val deskTasks = desktopRepository.getActiveTaskIdsInDesk(deskId)
            // Remove desk if it's empty.
            if (deskTasks.isEmpty()) {
                logD(
                    "handleExtendedModeDisconnect: removing empty desk=%d of user=%d",
                    deskId,
                    userId,
                )
                desksOrganizer.removeDesk(wct, deskId, userId)
                runOnTransitStartList.add { transition ->
                    desksTransitionObserver.addPendingTransition(
                        DeskTransition.RemoveDesk(
                            token = transition,
                            userId = userId,
                            displayId = disconnectedDisplayId,
                            deskId = deskId,
                            tasks = emptySet(),
                            onDeskRemovedListener = onDeskRemovedListener,
                            exitReason = ExitReason.DISPLAY_DISCONNECTED,
                            runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                        )
                    )
                }
            } else {
                logD(
                    "handleExtendedModeDisconnect: " +
                        "reparenting desk=%d to display=%d for user=%d",
                    deskId,
                    destinationDisplayId,
                    userId,
                )
                // Otherwise, reparent it to the destination display.
                val toTop = deskTasks.contains(focusTransitionObserver.globallyFocusedTaskId)
                desksOrganizer.moveDeskToDisplay(wct, deskId, destinationDisplayId, toTop)
                val taskIds = desktopRepository.getActiveTaskIdsInDesk(deskId)
                for (taskId in taskIds) {
                    val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: continue
                    val taskTilingState =
                        when (taskId) {
                            desktopRepository.getLeftTiledTask(deskId) ->
                                DesktopTaskTilingState.LEFT
                            desktopRepository.getRightTiledTask(deskId) ->
                                DesktopTaskTilingState.RIGHT
                            else -> DesktopTaskTilingState.NONE
                        }
                    val newStableBounds = Rect()
                    val oldStableBounds = Rect()
                    val sourceLayout = displayController.getDisplayLayout(task.displayId) ?: return
                    val destLayout = destDisplayLayout ?: return
                    destLayout.getStableBounds(newStableBounds)
                    sourceLayout.getStableBounds(oldStableBounds)
                    val newDisplayContext =
                        displayController.getDisplayContext(destinationDisplayId) ?: return
                    val newToOldDpiRatio =
                        destLayout.densityDpi().toDouble() / sourceLayout.densityDpi().toDouble()
                    val dividerBounds: Rect? =
                        when (taskTilingState) {
                            DesktopTaskTilingState.LEFT ->
                                getDividerBoundsForZombieSession(
                                    task.configuration.windowConfiguration.bounds,
                                    null,
                                    newStableBounds,
                                    oldStableBounds,
                                    newToOldDpiRatio,
                                    newDisplayContext,
                                )
                            DesktopTaskTilingState.RIGHT ->
                                getDividerBoundsForZombieSession(
                                    null,
                                    task.configuration.windowConfiguration.bounds,
                                    newStableBounds,
                                    oldStableBounds,
                                    newToOldDpiRatio,
                                    newDisplayContext,
                                )
                            else -> null
                        }
                    applyFreeformDisplayChange(
                        wct,
                        task,
                        destDisplayLayout,
                        displayController.getDisplayLayout(task.displayId),
                        taskTilingState,
                        deskId,
                        dividerBounds,
                    )
                }
                runOnTransitStartList.add { transition ->
                    desksTransitionObserver.addPendingTransition(
                        DeskTransition.ChangeDeskDisplay(
                            token = transition,
                            userId = userId,
                            deskId = deskId,
                            displayId = destinationDisplayId,
                            uniqueDisplayId =
                                displayController.getDisplayUniqueId(destinationDisplayId),
                        )
                    )
                }
                updateDesksActivationOnDisconnection(
                        disconnectedDisplayActiveDesk = deskId,
                        destinationDisplayId = destinationDisplayId,
                        userId = userId,
                        wct = wct,
                        toTop = toTop,
                    )
                    ?.let { runOnTransitStartList.add(it) }
            }
        }
    }

    fun onDisplayDpiChanging(
        displayId: Int,
        newConfig: Configuration,
        oldDisplayLayout: DisplayLayout?,
    ) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue) return
        val newDisplayLayout = displayController.getDisplayLayout(displayId) ?: return
        if (oldDisplayLayout == null) return
        val oldStableBounds = Rect()
        oldDisplayLayout.getStableBounds(oldStableBounds)
        val newToOldDpiRatio =
            newDisplayLayout.densityDpi().toDouble() / oldDisplayLayout.densityDpi()
        snapEventHandler.onDisplayLayoutChange(
            displayId,
            newConfig,
            oldStableBounds,
            newToOldDpiRatio,
        )
        val stableBounds = Rect()
        newDisplayLayout?.getStableBounds(stableBounds)

        val wct = WindowContainerTransaction()
        val userId = userRepositories.current.userId
        userRepositories.forAllRepositories { userRepo ->
            if (userId == userRepo.userId) {
                val deskIds = userRepo.getDeskIds(displayId).toList()
                for (deskId in deskIds) {
                    val deskTasks = userRepo.getActiveTaskIdsInDesk(deskId)
                    if (deskTasks.isEmpty()) continue
                    for (taskId in deskTasks) {
                        val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: continue
                        val taskTilingState =
                            when (taskId) {
                                userRepo.getLeftTiledTask(deskId) -> DesktopTaskTilingState.LEFT
                                userRepo.getRightTiledTask(deskId) -> DesktopTaskTilingState.RIGHT
                                else -> DesktopTaskTilingState.NONE
                            }
                        applyFreeformDisplayChange(
                            wct,
                            task,
                            newDisplayLayout,
                            oldDisplayLayout,
                            taskTilingState,
                            deskId,
                        )
                    }
                }
            }
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, null)
    }

    private fun handleProjectedModeDisconnect(
        desktopRepository: DesktopRepository,
        wct: WindowContainerTransaction,
        runOnTransitStartList: MutableList<RunOnTransitStart>,
        deskIds: List<Int>,
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
        userId: Int,
    ) {
        logD("handleProjectedModeDisconnect: moving tasks to non-desktop display")
        // Desktop not supported on display; reparent tasks to display area, remove desk.
        val tdaInfo =
            checkNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(destinationDisplayId)) {
                "Expected to find displayAreaInfo for displayId=$destinationDisplayId"
            }
        for (deskId in deskIds) {
            val taskIds = desktopRepository.getActiveTaskIdsInDesk(deskId)
            for (taskId in taskIds) {
                val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: continue
                wct.reparent(task.token, tdaInfo.token, /* onTop= */ false)
            }
            desksOrganizer.removeDesk(wct, deskId, userId)
            runOnTransitStartList.add { transition ->
                desksTransitionObserver.addPendingTransition(
                    DeskTransition.RemoveDesk(
                        token = transition,
                        userId = userId,
                        displayId = disconnectedDisplayId,
                        deskId = deskId,
                        tasks = emptySet(),
                        onDeskRemovedListener = onDeskRemovedListener,
                        exitReason = ExitReason.DISPLAY_DISCONNECTED,
                        runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                    )
                )
                desksTransitionObserver.addPendingTransition(
                    DeskTransition.RemoveDisplay(
                        token = transition,
                        userId = userId,
                        displayId = disconnectedDisplayId,
                    )
                )
            }
        }
    }

    /**
     * Restore a display based on info that was stored on disconnect.
     *
     * TODO: b/365873835 - Restore for all users, not just current.
     */
    fun restoreDisplay(displayId: Int, preservedDisplay: DesktopDisplay, userId: Int) {
        logD(
            "restoreDisplay: displayId=%d former displayId=%d userId=%d",
            displayId,
            preservedDisplay.displayId,
            userId,
        )
        // TODO: b/365873835 - Utilize DesktopTask data class once it is
        //  implemented in DesktopRepository.
        val repository = userRepositories.getProfile(userId)
        val preservedTaskIdsByDeskId =
            repository.getPreservedTasksByDeskIdInZOrder(preservedDisplay)
        val boundsByTaskId = repository.getPreservedTaskBounds(preservedDisplay)
        val activeDeskId = preservedDisplay.activeDeskId
        val wct = WindowContainerTransaction()
        var runOnTransitStartList = mutableListOf<RunOnTransitStart>()
        val tilingReconnectHandler =
            TilingDisplayReconnectEventHandler(repository, snapEventHandler, transitions, displayId)
        val excludedTasks =
            getFocusedNonDesktopTasks(DEFAULT_DISPLAY, userId).map { task -> task.taskId }
        // Preserve focus state on reconnect, regardless if focused task is restored or not.
        val globallyFocusedTask =
            shellTaskOrganizer.getRunningTaskInfo(focusTransitionObserver.globallyFocusedTaskId)
        mainScope.launch {
            preservedTaskIdsByDeskId.forEach { (preservedDeskId, preservedTaskIds) ->
                val newDeskId =
                    createDeskSuspending(
                        displayId = displayId,
                        userId = userId,
                        enforceDeskLimit = true,
                    )
                logD(
                    "restoreDisplay: created new desk deskId=$newDeskId " +
                        "from preserved deskId=$preservedDeskId"
                )
                val isActiveDesk = preservedDeskId == activeDeskId
                if (isActiveDesk) {
                    runOnTransitStartList.add(
                        addDeskActivationChanges(
                            deskId = newDeskId,
                            wct = wct,
                            userId = userId,
                            enterReason = EnterReason.DISPLAY_CONNECT,
                        )
                    )
                }

                preservedTaskIds.asReversed().forEach { taskId ->
                    if (!excludedTasks.contains(taskId)) {
                        addRestoreTaskToDeskChanges(
                                wct = wct,
                                deskId = newDeskId,
                                taskId = taskId,
                                userId = userId,
                                displayId = displayId,
                                preservedDisplay = preservedDisplay,
                                taskBounds = boundsByTaskId[taskId],
                            )
                            ?.let { runOnTransitStartList.add(it) }
                    }
                }

                val preservedTilingData =
                    repository.getPreservedTilingData(preservedDisplay, preservedDeskId)
                if (preservedTilingData != null) {
                    tilingReconnectHandler.addTilingDisplayReconnectSession(
                        TilingDisplayReconnectEventHandler.TilingDisplayReconnectSession(
                            preservedTilingData.leftTiledTask,
                            preservedTilingData.rightTiledTask,
                            newDeskId,
                            isActiveDesk,
                        )
                    )
                }
            }
            globallyFocusedTask?.let {
                wct.reorder(it.token, /* onTop= */ true, /* includingParents= */ true)
            }
            val transition = transitions.startTransition(TRANSIT_CHANGE, wct, null)
            tilingReconnectHandler.activationBinder = transition
            runOnTransitStartList.forEach { it.invoke(transition) }
        }
    }

    private fun addRestoreTaskToDeskChanges(
        wct: WindowContainerTransaction,
        deskId: Int,
        taskId: Int,
        userId: Int,
        displayId: Int,
        preservedDisplay: DesktopDisplay,
        taskBounds: Rect?,
    ): RunOnTransitStart? {
        logD(
            "addRestoreTaskToDeskChanges: taskId=$taskId; deskId=$deskId; userId=$userId; " +
                "taskBounds=$taskBounds."
        )

        val minimized =
            preservedDisplay.orderedDesks.any { desk ->
                taskId in desk.minimizedTasks
            }
        val task =
            shellTaskOrganizer.getRunningTaskInfo(taskId)
                ?: recentTasksController?.findTaskInBackground(taskId)
        if (task == null) {
            logE("restoreDisplay: Could not find running task info for taskId=$taskId.")
            return null
        }
        desksOrganizer.moveTaskToDesk(wct, deskId, task, minimized = minimized)
        taskBounds?.let { wct.setBounds(task.token, it) }

        return { transition ->
            desksTransitionObserver.addPendingTransition(
                DeskTransition.AddTaskToDesk(
                    token = transition,
                    userId = userId,
                    displayId = displayId,
                    deskId = deskId,
                    taskId = taskId,
                    taskBounds = taskBounds,
                    minimized = minimized,
                )
            )
        }
    }

    private fun handleUserChangeTransitionRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val userChange = request.userChange ?: return null
        val previousRepo = userRepositories.getProfile(userChange.previousUserId)
        val newRepo = userRepositories.getProfile(userChange.newUserId)
        val wct = WindowContainerTransaction()
        // Deactivate desks of old user.
        rootTaskDisplayAreaOrganizer.displayIds
            .toList()
            .mapNotNull { displayId ->
                val deskId = previousRepo.getActiveDeskId(displayId) ?: return@mapNotNull null
                addDeskDeactivationChanges(
                    wct = wct,
                    deskId = deskId,
                    userId = userChange.previousUserId,
                    displayId = displayId,
                    switchingUser = true,
                    exitReason = ExitReason.UNKNOWN_EXIT,
                )
            }
            .forEach { runOnTransitStart -> runOnTransitStart(transition) }
        // Activate desks of new user.
        rootTaskDisplayAreaOrganizer.displayIds
            .toList()
            .mapNotNull { displayId -> newRepo.getActiveDeskId(displayId) }
            .map { deskId ->
                addDeskActivationChanges(
                    deskId = deskId,
                    wct = wct,
                    userId = userChange.newUserId,
                    switchingUser = true,
                    enterReason = EnterReason.UNKNOWN_ENTER,
                )
            }
            .forEach { runOnTransitStart -> runOnTransitStart(transition) }

        return wct
    }

    /**
     * Handle desk operations when disconnecting a display and all desks on that display are moving
     * to a display that supports desks. The previously focused display will determine which desk
     * will move to the front.
     *
     * @param disconnectedDisplayActiveDesk the id of the active desk on the disconnected display
     * @param toTop whether this desk was reordered to the top
     */
    @VisibleForTesting
    fun updateDesksActivationOnDisconnection(
        disconnectedDisplayActiveDesk: Int,
        destinationDisplayId: Int,
        userId: Int,
        wct: WindowContainerTransaction,
        toTop: Boolean,
    ): RunOnTransitStart? {
        val runOnTransitStart =
            if (toTop) {
                // The disconnected display's active desk was reparented to the top, activate it
                // here.
                addDeskActivationChanges(
                    deskId = disconnectedDisplayActiveDesk,
                    wct = wct,
                    displayId = destinationDisplayId,
                    userId = userId,
                    enterReason = EnterReason.DESK_REPARENT,
                )
            } else {
                // The disconnected display's active desk was reparented to the back, ensure it is
                // no longer an active launch root.
                addDeskDeactivationChanges(
                    wct = wct,
                    deskId = disconnectedDisplayActiveDesk,
                    userId = userId,
                    displayId = destinationDisplayId,
                    exitReason = ExitReason.DISPLAY_DISCONNECTED,
                )
            }
        return runOnTransitStart
    }

    private fun getDisplayIdForTaskOrDefault(task: TaskInfo?): Int {
        // First, try to get the display already associated with the task.
        if (
            task != null &&
                task.displayId != INVALID_DISPLAY &&
                desktopState.isDesktopModeSupportedOnDisplay(displayId = task.displayId)
        ) {
            return task.displayId
        }
        // Second, try to use the globally focused display.
        val globallyFocusedDisplayId = focusTransitionObserver.globallyFocusedDisplayId
        if (
            globallyFocusedDisplayId != INVALID_DISPLAY &&
                desktopState.isDesktopModeSupportedOnDisplay(displayId = globallyFocusedDisplayId)
        ) {
            return globallyFocusedDisplayId
        }
        // Fallback to any display that supports desktop.
        val supportedDisplayId =
            rootTaskDisplayAreaOrganizer.displayIds.firstOrNull { displayId ->
                desktopState.isDesktopModeSupportedOnDisplay(displayId)
            }
        if (supportedDisplayId != null) {
            return supportedDisplayId
        }
        // Use the default display as the last option even if it does not support desktop. Callers
        // should handle this case.
        return DEFAULT_DISPLAY
    }

    /** Moves task to desktop mode if task is running, else launches it in desktop mode. */
    @JvmOverloads
    fun moveTaskToDefaultDeskAndActivate(
        taskId: Int,
        wct: WindowContainerTransaction = WindowContainerTransaction(),
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
        callback: IMoveToDesktopCallback? = null,
    ): Boolean {
        val task =
            shellTaskOrganizer.getRunningTaskInfo(taskId)
                ?: recentTasksController?.findTaskInBackground(taskId)
        if (task == null) {
            logW("moveTaskToDefaultDeskAndActivate taskId=%d not found", taskId)
            return false
        }
        val displayId = getDisplayIdForTaskOrDefault(task)
        val userId = task.userId
        if (
            DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue &&
                !desktopState.isDesktopModeSupportedOnDisplay(displayId) &&
                transitionSource != DesktopModeTransitionSource.ADB_COMMAND &&
                transitionSource != DesktopModeTransitionSource.OVERVIEW_TASK_MENU
        ) {
            logW("moveTaskToDefaultDeskAndActivate display=$displayId does not support desk")
            return false
        }
        if (
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                !DesktopExperienceFlags.ENABLE_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION.isTrue
        ) {
            val deskId = getOrCreateDefaultDeskId(displayId, userId) ?: return false
            return moveTaskToDesk(
                taskId = taskId,
                deskId = deskId,
                userId = userId,
                wct = wct,
                transitionSource = transitionSource,
                remoteTransition = remoteTransition,
                callback = callback,
            )
        }
        mainScope.launch {
            try {
                moveTaskToDesk(
                    taskId = taskId,
                    deskId = getOrCreateDefaultDeskIdSuspending(displayId, userId),
                    userId = userId,
                    wct = wct,
                    transitionSource = transitionSource,
                    remoteTransition = remoteTransition,
                    callback = callback,
                )
            } catch (t: Throwable) {
                logE("Failed to move task to default desk: %s", t.message)
            }
        }
        return true
    }

    /** Moves task to desktop mode if task is running, else launches it in desktop mode. */
    fun moveTaskToDesk(
        taskId: Int,
        deskId: Int,
        userId: Int = shellController.currentUserId,
        wct: WindowContainerTransaction = WindowContainerTransaction(),
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
        callback: IMoveToDesktopCallback? = null,
    ): Boolean {
        logV("moveTaskToDesk taskId=%d deskId=%d source=%s", taskId, deskId, transitionSource)
        val runningTask = shellTaskOrganizer.getRunningTaskInfo(taskId)
        if (runningTask != null) {
            return moveRunningTaskToDesk(
                task = runningTask,
                deskId = deskId,
                wct = wct,
                transitionSource = transitionSource,
                remoteTransition = remoteTransition,
                callback = callback,
            )
        }
        val backgroundTask = recentTasksController?.findTaskInBackground(taskId)
        if (backgroundTask != null) {
            return moveBackgroundTaskToDesktop(
                taskId = taskId,
                deskId = deskId,
                userId = userId,
                wct = wct,
                transitionSource = transitionSource,
                remoteTransition = remoteTransition,
                callback = callback,
            )
        }
        logW("moveTaskToDesk taskId=%d not found", taskId)
        return false
    }

    private fun moveBackgroundTaskToDesktop(
        taskId: Int,
        deskId: Int,
        userId: Int,
        wct: WindowContainerTransaction,
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
        callback: IMoveToDesktopCallback? = null,
    ): Boolean {
        val repository = userRepositories.getProfile(userId)
        val targetDisplayId = repository.getDisplayForDesk(deskId)
        val displayLayout = displayController.getDisplayLayout(targetDisplayId) ?: return false
        val task = recentTasksController?.findTaskInBackground(taskId)
        if (task == null) {
            logW("moveBackgroundTaskToDesktop taskId=%d not found", taskId)
            return false
        }
        logV("moveBackgroundTaskToDesktop with taskId=%d to deskId=%d", taskId, deskId)

        val runOnTransitStart =
            addDeskActivationChanges(
                deskId = deskId,
                wct = wct,
                newTask = task,
                userId = userId,
                enterReason = transitionSource.getEnterReason(),
            )
        val exitResult =
            desktopImmersiveController.exitImmersiveIfApplicable(
                wct = wct,
                displayId = DEFAULT_DISPLAY,
                excludeTaskId = taskId,
                reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
            )
        wct.startTask(
            taskId,
            ActivityOptions.makeBasic()
                .apply {
                    launchWindowingMode = WINDOWING_MODE_FREEFORM
                    launchBounds = getInitialBounds(displayLayout, task, deskId)
                    launchDisplayId = targetDisplayId
                }
                .toBundle(),
        )

        val transition: IBinder
        if (remoteTransition != null) {
            val transitionType = transitionType(remoteTransition)
            val remoteTransitionHandler = OneShotRemoteHandler(mainExecutor, remoteTransition)
            transition = transitions.startTransition(transitionType, wct, remoteTransitionHandler)
            remoteTransitionHandler.setTransition(transition)
        } else {
            // TODO(343149901): Add DPI changes for task launch
            transition = enterDesktopTaskTransitionHandler.moveToDesktop(wct, transitionSource)
            invokeCallbackToOverview(transition, callback)
        }
        // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
        if (!desktopState.enableMultipleDesktops) {
            desktopModeEnterExitTransitionListener?.onEnterDesktopModeTransitionStarted(
                toDesktopAnimationDurationMs
            )
        }
        runOnTransitStart?.invoke(transition)
        exitResult.asExit()?.runOnTransitionStart?.invoke(transition)
        return true
    }

    /** Moves a running task to desktop. */
    private fun moveRunningTaskToDesk(
        task: RunningTaskInfo,
        deskId: Int,
        wct: WindowContainerTransaction = WindowContainerTransaction(),
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
        callback: IMoveToDesktopCallback? = null,
    ): Boolean {
        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        val displayId = repository.getDisplayForDesk(deskId)
        logV(
            "moveRunningTaskToDesk taskId=%d deskId=%d displayId=%d",
            task.taskId,
            deskId,
            displayId,
        )
        exitSplitIfApplicable(wct, task)
        val exitResult =
            desktopImmersiveController.exitImmersiveIfApplicable(
                wct = wct,
                displayId = displayId,
                excludeTaskId = task.taskId,
                reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
            )

        val runOnTransitStart =
            addDeskActivationWithMovingTaskChanges(deskId, wct, task, transitionSource)

        val transition: IBinder
        if (remoteTransition != null) {
            val transitionType = transitionType(remoteTransition)
            val remoteTransitionHandler = OneShotRemoteHandler(mainExecutor, remoteTransition)
            transition = transitions.startTransition(transitionType, wct, remoteTransitionHandler)
            remoteTransitionHandler.setTransition(transition)
        } else {
            transition = enterDesktopTaskTransitionHandler.moveToDesktop(wct, transitionSource)
            invokeCallbackToOverview(transition, callback)
        }
        // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
        if (!desktopState.enableMultipleDesktops) {
            desktopModeEnterExitTransitionListener?.onEnterDesktopModeTransitionStarted(
                toDesktopAnimationDurationMs
            )
        }
        runOnTransitStart?.invoke(transition)
        exitResult.asExit()?.runOnTransitionStart?.invoke(transition)
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            repository.setActiveDesk(displayId = displayId, deskId = deskId)
        }
        return true
    }

    private fun invokeCallbackToOverview(transition: IBinder, callback: IMoveToDesktopCallback?) {
        // TODO: b/333524374 - Remove this later.
        // This is a temporary implementation for adding CUJ end and
        // should be removed when animation is moved to launcher through remote transition.
        if (callback != null) {
            overviewToDesktopTransitionObserver.addPendingOverviewTransition(transition, callback)
        }
    }

    /**
     * The first part of the animated drag to desktop transition. This is followed with a call to
     * [finalizeDragToDesktop] or [cancelDragToDesktop].
     */
    fun startDragToDesktop(
        taskInfo: RunningTaskInfo,
        dragToDesktopValueAnimator: MoveToDesktopAnimator,
        taskSurface: SurfaceControl,
        dragInterruptedCallback: Runnable,
    ) {
        logV("startDragToDesktop taskId=%d", taskInfo.taskId)
        val jankConfigBuilder =
            InteractionJankMonitor.Configuration.Builder.withSurface(
                    CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD,
                    context,
                    taskSurface,
                    handler,
                )
                .setTimeout(APP_HANDLE_DRAG_HOLD_CUJ_TIMEOUT_MS)
        interactionJankMonitor.begin(jankConfigBuilder)
        dragToDesktopTransitionHandler.startDragToDesktopTransition(
            taskInfo,
            dragToDesktopValueAnimator,
            visualIndicator,
            dragInterruptedCallback,
        )
    }

    /**
     * The second part of the animated drag to desktop transition, called after
     * [startDragToDesktop].
     */
    private fun finalizeDragToDesktop(taskInfo: RunningTaskInfo) {
        val deskId = getOrCreateDefaultDeskId(taskInfo.displayId, taskInfo.userId) ?: return
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTasksController: finalizeDragToDesktop taskId=%d deskId=%d",
            taskInfo.taskId,
            deskId,
        )
        val repository = userRepositories.getProfile(taskInfo.userId)
        val wct = WindowContainerTransaction()
        exitSplitIfApplicable(wct, taskInfo)
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // |moveHomeTask| is also called in |bringDesktopAppsToFrontBeforeShowingNewTask|, so
            // this shouldn't be necessary at all.
            if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
                moveHomeTaskToTop(taskInfo.displayId, wct)
            } else {
                moveHomeTaskToTop(context.displayId, wct)
            }
        }
        val runOnTransitStart =
            addDeskActivationWithMovingTaskChanges(
                deskId,
                wct,
                taskInfo,
                transitionSource = DesktopModeTransitionSource.TASK_DRAG,
            )
        val exitResult =
            desktopImmersiveController.exitImmersiveIfApplicable(
                wct = wct,
                displayId = taskInfo.displayId,
                excludeTaskId = null,
                reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
            )
        val transition = dragToDesktopTransitionHandler.finishDragToDesktopTransition(wct)
        // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
        if (!desktopState.enableMultipleDesktops) {
            desktopModeEnterExitTransitionListener?.onEnterDesktopModeTransitionStarted(
                DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS.toInt()
            )
        }
        if (transition != null) {
            runOnTransitStart?.invoke(transition)
            exitResult.asExit()?.runOnTransitionStart?.invoke(transition)
            if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                repository.setActiveDesk(displayId = taskInfo.displayId, deskId = deskId)
            }
        } else {
            latencyTracker.onActionCancel(LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG)
        }
    }

    /**
     * Perform needed cleanup transaction once animation is complete. Bounds need to be set here
     * instead of initial wct to both avoid flicker and to have task bounds to use for the staging
     * animation.
     *
     * @param taskInfo task entering split that requires a bounds update
     */
    fun onDesktopSplitSelectChoice(taskInfo: RunningTaskInfo) {
        val wct = WindowContainerTransaction()
        wct.setBounds(taskInfo.token, Rect())
        if (!DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue) {
            wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED)
        }
        shellTaskOrganizer.applyTransaction(wct)
    }

    /**
     * Perform clean up of the desktop wallpaper activity if the closed window task is the last
     * active task.
     *
     * @param wct transaction to modify if the last active task is closed
     * @param displayId display id of the window that's being closed
     * @param taskId task id of the window that's being closed
     */
    fun onDesktopWindowClose(
        wct: WindowContainerTransaction,
        displayId: Int,
        taskInfo: RunningTaskInfo,
    ): ((IBinder) -> Unit)? {
        val taskId = taskInfo.taskId
        val userId = taskInfo.userId
        val repository = userRepositories.getProfile(userId)
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        if (deskId == null && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            logW("onDesktopWindowClose: desk not found for task: $taskId")
            return null
        }
        snapEventHandler.removeTaskIfTiled(displayId, taskId)
        val shouldExitDesktop =
            willExitDesktop(
                triggerTaskId = taskId,
                displayId = displayId,
                userId = userId,
                forceExitDesktop = false,
            )
        val desktopExitRunnable =
            if (shouldExitDesktop) {
                val isLastTask =
                    deskId?.let { repository.isOnlyTaskInDesk(taskInfo.taskId, it) } ?: false
                performDesktopExitCleanUp(
                    wct = wct,
                    deskId = deskId,
                    displayId = displayId,
                    userId = userId,
                    willExitDesktop = true,
                    removingLastTaskId = if (isLastTask) taskInfo.taskId else null,
                    shouldEndUpAtHome = true,
                    exitReason = ExitReason.TASK_FINISHED,
                )
            } else {
                null
            }

        repository.addClosingTask(displayId = displayId, deskId = deskId, taskId = taskId)
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(
                displayId = displayId,
                userId = userId,
                excludeTaskId = taskId,
            )
        )

        val immersiveRunnable =
            desktopImmersiveController
                .exitImmersiveIfApplicable(
                    wct = wct,
                    taskInfo = taskInfo,
                    reason = DesktopImmersiveController.ExitReason.CLOSED,
                )
                .asExit()
                ?.runOnTransitionStart
        return { transitionToken ->
            immersiveRunnable?.invoke(transitionToken)
            desktopExitRunnable?.invoke(transitionToken)
        }
    }

    /**
     * Returns the task that will be focused next after the current task (the given [taskInfo]) is
     * removed, due to being minimized or closed.
     *
     * @param taskInfo the task that is being removed.
     * @return the taskId of the next focused task, or [INVALID_TASK_ID] if no task is found.
     */
    fun getNextFocusedTask(taskInfo: RunningTaskInfo): Int {
        val deskId =
            getOrCreateDefaultDeskId(taskInfo.displayId, taskInfo.userId) ?: return INVALID_TASK_ID
        val repository = userRepositories.getProfile(taskInfo.userId)
        return repository
            .getExpandedTasksIdsInDeskOrdered(deskId)
            // exclude current task since maximize/restore transition has not taken place yet.
            .filterNot { it == taskInfo.taskId }
            .firstOrNull { !repository.isClosingTask(it) } ?: INVALID_TASK_ID
    }

    fun minimizeTask(taskInfo: RunningTaskInfo, minimizeReason: MinimizeReason) {
        val wct = WindowContainerTransaction()
        val taskId = taskInfo.taskId
        val displayId = taskInfo.displayId
        val userId = taskInfo.userId
        val repository = userRepositories.getProfile(userId)
        val deskId =
            repository.getDeskIdForTask(taskId)
                ?: if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                    logW("minimizeTask: desk not found for task: $taskId")
                    return
                } else {
                    getOrCreateDefaultDeskId(displayId, userId)
                }
        val isLastTask =
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                repository.isOnlyVisibleNonClosingTaskInDesk(
                    taskId = taskId,
                    deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                    displayId = displayId,
                )
            } else {
                repository.isOnlyVisibleNonClosingTask(taskId = taskId, displayId = displayId)
            }
        snapEventHandler.removeTaskIfTiled(displayId, taskId)
        val isMinimizingToPip =
            DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue &&
                (taskInfo.pictureInPictureParams?.isAutoEnterEnabled ?: false) &&
                isPipAllowedInAppOps(taskInfo)
        logD(
            "minimizeTask isMinimizingToPip=%b isAutoEnterEnabled=%b isPipAllowedInAppOps=%b",
            isMinimizingToPip,
            (taskInfo.pictureInPictureParams?.isAutoEnterEnabled ?: false),
            isPipAllowedInAppOps(taskInfo),
        )
        // If task is going to PiP, start a PiP transition instead of a minimize transition
        if (isMinimizingToPip) {
            val requestInfo =
                TransitionRequestInfo(
                    TRANSIT_PIP,
                    /* triggerTask= */ null,
                    taskInfo,
                    /* remoteTransition= */ null,
                    /* displayChange= */ null,
                    /* flags= */ 0,
                )
            val requestRes =
                transitions.dispatchRequest(SYNTHETIC_TRANSITION, requestInfo, /* skip= */ null)
            wct.merge(requestRes.second, true)

            // In multi-activity case, we either explicitly minimize the parent task, or reorder the
            // parent task to the back so that it is not brought to the front and shown when the
            // child task breaks off into PiP.
            val isMultiActivityPip = taskInfo.numActivities > 1
            var minimizeMultiActivityRunnable: RunOnTransitStart? = null
            if (isMultiActivityPip) {
                minimizeMultiActivityRunnable =
                    minimizeMultiActivityPipTask(wct = wct, deskId = deskId, task = taskInfo)
            }

            // If the task minimizing to PiP is the last task, modify wct to perform Desktop cleanup
            var desktopExitRunnable: RunOnTransitStart? = null
            if (isLastTask) {
                desktopExitRunnable =
                    performDesktopExitCleanUp(
                        wct = wct,
                        deskId = deskId,
                        displayId = displayId,
                        userId = userId,
                        willExitDesktop = true,
                        removingLastTaskId = null,
                        exitReason = ExitReason.TASK_MINIMIZED,
                    )
            }
            val transition = freeformTaskTransitionStarter.startPipTransition(wct)
            minimizeMultiActivityRunnable?.invoke(transition)
            desktopExitRunnable?.invoke(transition)
        } else {
            val willExitDesktop =
                if (
                    DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                        DesktopExperienceFlags.ENABLE_EMPTY_DESK_ON_MINIMIZE.isTrue
                )
                    false
                else
                    willExitDesktop(
                        triggerTaskId = taskId,
                        displayId = displayId,
                        userId = userId,
                        forceExitDesktop = false,
                    )
            val desktopExitRunnable =
                performDesktopExitCleanUp(
                    wct = wct,
                    deskId = deskId,
                    displayId = displayId,
                    userId = userId,
                    willExitDesktop = willExitDesktop,
                    removingLastTaskId = null,
                    exitReason = ExitReason.TASK_MINIMIZED,
                )
            // Notify immersive handler as it might need to exit immersive state.
            val exitResult =
                desktopImmersiveController.exitImmersiveIfApplicable(
                    wct = wct,
                    taskInfo = taskInfo,
                    reason = DesktopImmersiveController.ExitReason.MINIMIZED,
                )
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                desksOrganizer.minimizeTask(
                    wct = wct,
                    deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                    task = taskInfo,
                )
            } else {
                wct.reorder(taskInfo.token, /* onTop= */ false)
            }
            val transition =
                freeformTaskTransitionStarter.startMinimizedModeTransition(wct, taskId, isLastTask)
            desktopTasksLimiter.ifPresent {
                it.addPendingMinimizeChange(
                    transition = transition,
                    displayId = displayId,
                    taskId = taskId,
                    minimizeReason = minimizeReason,
                )
            }
            exitResult.asExit()?.runOnTransitionStart?.invoke(transition)
            desktopExitRunnable?.invoke(transition)
        }
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(
                displayId = displayId,
                userId = userId,
                excludeTaskId = taskId,
            )
        )
    }

    /**
     * Called when a multi-activity PiP task needs to be minimized.
     *
     * @param wct WindowContainerTransaction that will apply minimization changes
     * @param deskId desk id that the multi-activity PiP is in
     * @param taskInfo of the task that is entering PiP
     * @return RunOnTransitStart runnable to be invoked after the transition has been started
     */
    fun minimizeMultiActivityPipTask(
        wct: WindowContainerTransaction,
        deskId: Int?,
        task: RunningTaskInfo,
    ): RunOnTransitStart {
        logD(
            "minimizeMultiActivityPipTask taskId=%d deskId=%d displayId=%d",
            task.taskId,
            deskId,
            task.displayId,
        )
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desksOrganizer.minimizeTask(
                wct = wct,
                deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                task = task,
            )
        } else {
            wct.reorder(task.token, /* onTop= */ false)
        }
        return { transition ->
            desktopTasksLimiter.ifPresent {
                it.addPendingMinimizeChange(
                    transition = transition,
                    displayId = task.displayId,
                    taskId = task.taskId,
                    minimizeReason = MinimizeReason.MULTI_ACTIVITY_PIP,
                )
            }
        }
    }

    /** Checks whether the given [taskInfo] is allowed to enter PiP in AppOps. */
    private fun isPipAllowedInAppOps(taskInfo: RunningTaskInfo): Boolean {
        val packageName =
            taskInfo.baseActivity?.packageName
                ?: taskInfo.topActivity?.packageName
                ?: taskInfo.origActivity?.packageName
                ?: taskInfo.realActivity?.packageName
                ?: return false

        val appOpsManager =
            checkNotNull(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        try {
            val appInfo =
                context.packageManager.getApplicationInfoAsUser(
                    packageName,
                    /* flags= */ 0,
                    taskInfo.userId,
                )
            return appOpsManager.checkOpNoThrow(
                AppOpsManager.OP_PICTURE_IN_PICTURE,
                appInfo.uid,
                packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: PackageManager.NameNotFoundException) {
            logW(
                "isPipAllowedInAppOps: Failed to find applicationInfo for packageName=%s " +
                    "and userId=%d",
                packageName,
                taskInfo.userId,
            )
        }
        return false
    }

    /** Move or launch a task with given [taskId] to fullscreen */
    @JvmOverloads
    fun moveToFullscreen(
        taskId: Int,
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
    ) {
        logV(
            "moveToFullscreen taskId=%d transitionSource=%s remoteTransition=%s",
            taskId,
            transitionSource,
            remoteTransition,
        )
        val taskInfo: TaskInfo? =
            shellTaskOrganizer.getRunningTaskInfo(taskId)
                ?: if (enableAltTabKqsFlatenning.isTrue) {
                    recentTasksController?.findTaskInBackground(taskId)
                } else {
                    null
                }
        taskInfo?.let { task ->
            snapEventHandler.removeTaskIfTiled(task.displayId, taskId)
            moveToFullscreenWithAnimation(
                task,
                task.positionInParent,
                transitionSource,
                remoteTransition,
            )
        }
    }

    /** Enter fullscreen by moving the focused freeform task in given `displayId` to fullscreen. */
    fun enterFullscreen(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        transitionSource: DesktopModeTransitionSource,
    ) {
        getFocusedDesktopTask(displayId = displayId, userId = userId)?.let {
            moveDesktopTaskToFullscreen(it, displayId, transitionSource)
        }
    }

    private fun exitSplitIfApplicable(wct: WindowContainerTransaction, taskInfo: RunningTaskInfo) {
        if (splitScreenController.isTaskInSplitScreen(taskInfo.taskId)) {
            splitScreenController.prepareExitSplitScreen(
                wct,
                splitScreenController.getStageOfTask(taskInfo.taskId),
                EXIT_REASON_DESKTOP_MODE,
            )
            splitScreenController.transitionHandler?.onSplitToDesktop()
        }
    }

    /**
     * The second part of the animated drag to desktop transition, called after
     * [startDragToDesktop].
     */
    fun cancelDragToDesktop(task: RunningTaskInfo) {
        logV("cancelDragToDesktop taskId=%d", task.taskId)
        dragToDesktopTransitionHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )
    }

    private fun moveToFullscreenWithAnimation(
        task: TaskInfo,
        position: Point,
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
    ) {
        val displayId =
            when {
                // May be invalid when the task info of a recent (non-running task).
                task.displayId != INVALID_DISPLAY -> task.displayId
                focusTransitionObserver.globallyFocusedDisplayId != INVALID_DISPLAY ->
                    focusTransitionObserver.globallyFocusedDisplayId
                else -> DEFAULT_DISPLAY
            }
        logV(
            "moveToFullscreenWithAnimation taskId=%d displayId=%d source=%s remoteTransition=%s",
            task.taskId,
            displayId,
            transitionSource,
            remoteTransition,
        )
        val wct = WindowContainerTransaction()

        // When a task is background, update wct to start task.
        if (
            enableAltTabKqsFlatenning.isTrue &&
                shellTaskOrganizer.getRunningTaskInfo(task.taskId) == null &&
                task is RecentTaskInfo
        ) {
            logV(
                "moveToFullscreenWithAnimation taskId=%d is in background, adding start WCT",
                task.taskId,
            )
            wct.startTask(
                task.taskId,
                // TODO(b/400817258): Use ActivityOptions Utils when available.
                ActivityOptions.makeBasic()
                    .apply {
                        launchWindowingMode = WINDOWING_MODE_FULLSCREEN
                        launchDisplayId = displayId
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    }
                    .toBundle(),
            )
        }
        val willExitDesktop =
            willExitDesktop(
                triggerTaskId = task.taskId,
                displayId = displayId,
                userId = task.userId,
                forceExitDesktop = true,
            )
        val deactivationRunnable = addMoveToFullscreenChanges(wct, task, willExitDesktop, displayId)

        // We are moving a freeform task to fullscreen, put the home task under the fullscreen task.
        if (!isDesktopFirstLegacy(displayId)) {
            moveHomeTaskToTop(displayId, wct)
            wct.reorder(task.token, /* onTop= */ true)
        }

        val transition =
            if (remoteTransition != null) {
                val transitionType = transitionType(remoteTransition)
                val remoteTransitionHandler = OneShotRemoteHandler(mainExecutor, remoteTransition)
                transitions.startTransition(transitionType, wct, remoteTransitionHandler).also {
                    remoteTransitionHandler.setTransition(it)
                }
            } else {
                exitDesktopTaskTransitionHandler.startTransition(
                    transitionSource,
                    wct,
                    position,
                    mOnAnimationFinishedCallback,
                )
            }
        deactivationRunnable?.invoke(transition)

        // handles case where we are moving to full screen without closing all DW tasks.
        if (
            !isOnlyVisibleNonClosingTask(
                taskId = task.taskId,
                displayId = displayId,
                userId = task.userId,
            )
            // This callback is already invoked by |addMoveToFullscreenChanges| when this flag is
            // enabled.
            &&
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
                // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
                &&
                !desktopState.enableMultipleDesktops
        ) {
            desktopModeEnterExitTransitionListener?.onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        }
    }

    /**
     * Move a task to the front, using [remoteTransition].
     *
     * Note: beyond moving a task to the front, this method will minimize a task if we reach the
     * Desktop task limit, so [remoteTransition] should also handle any such minimize change.
     */
    @JvmOverloads
    fun moveTaskToFront(
        taskId: Int,
        userId: Int = shellController.currentUserId,
        remoteTransition: RemoteTransition? = null,
        unminimizeReason: UnminimizeReason,
    ) {
        val task = shellTaskOrganizer.getRunningTaskInfo(taskId)
        if (task == null) {
            moveBackgroundTaskToFront(
                taskId = taskId,
                userId = userId,
                remoteTransition = remoteTransition,
                unminimizeReason = unminimizeReason,
            )
        } else {
            moveTaskToFront(task, remoteTransition, unminimizeReason)
        }
    }

    /**
     * Launch a background task in desktop. Note that this should be used when we are already in
     * desktop. If outside of desktop and want to launch a background task in desktop, use
     * [moveBackgroundTaskToDesktop] instead.
     */
    private fun moveBackgroundTaskToFront(
        taskId: Int,
        userId: Int,
        remoteTransition: RemoteTransition?,
        unminimizeReason: UnminimizeReason,
    ) {
        logV("moveBackgroundTaskToFront taskId=%s unminimizeReason=%s", taskId, unminimizeReason)
        val repository = userRepositories.getProfile(userId)
        val wct = WindowContainerTransaction()
        val deskIdForTask = repository.getDeskIdForTask(taskId)
        val deskId =
            if (deskIdForTask != null) {
                deskIdForTask
            } else {
                val task = recentTasksController?.findTaskInBackground(taskId)
                val displayId = getDisplayIdForTaskOrDefault(task)
                logV(
                    "background taskId=%s did not have desk associated, " +
                        "using default desk of displayId=%d",
                    taskId,
                    displayId,
                )
                getOrCreateDefaultDeskId(displayId, userId) ?: return
            }
        val displayId =
            if (ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) repository.getDisplayForDesk(deskId)
            else DEFAULT_DISPLAY
        wct.startTask(
            taskId,
            ActivityOptions.makeBasic()
                .apply {
                    launchWindowingMode = WINDOWING_MODE_FREEFORM
                    launchDisplayId = displayId
                }
                .toBundle(),
        )
        startLaunchTransition(
            TRANSIT_OPEN,
            wct,
            taskId,
            deskId = deskId,
            displayId = displayId,
            userId = userId,
            remoteTransition = remoteTransition,
            unminimizeReason = unminimizeReason,
        )
    }

    /**
     * Move a task to the front, using [remoteTransition].
     *
     * Note: beyond moving a task to the front, this method will minimize a task if we reach the
     * Desktop task limit, so [remoteTransition] should also handle any such minimize change.
     */
    @JvmOverloads
    fun moveTaskToFront(
        taskInfo: RunningTaskInfo,
        remoteTransition: RemoteTransition? = null,
        unminimizeReason: UnminimizeReason = UnminimizeReason.UNKNOWN,
    ) {
        val repository = userRepositories.getProfile(taskInfo.userId)
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        logV("moveTaskToFront taskId=%s deskId=%s", taskInfo.taskId, deskId)
        val wct = WindowContainerTransaction()
        addMoveTaskToFrontChanges(wct = wct, deskId = deskId, taskInfo = taskInfo)
        startLaunchTransition(
            transitionType = TRANSIT_TO_FRONT,
            wct = wct,
            launchingTaskId = taskInfo.taskId,
            remoteTransition = remoteTransition,
            deskId = deskId,
            displayId = taskInfo.displayId,
            userId = taskInfo.userId,
            unminimizeReason = unminimizeReason,
        )
    }

    /** Applies the necessary [wct] changes to move [taskInfo] to front. */
    fun addMoveTaskToFrontChanges(
        wct: WindowContainerTransaction,
        deskId: Int?,
        taskInfo: RunningTaskInfo,
    ) {
        logV("addMoveTaskToFrontChanges taskId=%s deskId=%s", taskInfo.taskId, deskId)
        // If a task is tiled, another task should be brought to foreground with it so let
        // tiling controller handle the request.
        if (snapEventHandler.moveTaskToFrontIfTiled(taskInfo)) {
            return
        }
        if (deskId == null || !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // Not a desktop task, just move to the front.
            wct.reorder(taskInfo.token, /* onTop= */ true, /* includingParents= */ true)
        } else {
            // A desktop task with multiple desks enabled, reorder it within its desk.
            desksOrganizer.reorderTaskToFront(wct, deskId, taskInfo)
        }
    }

    /**
     * Starts a launch transition with [transitionType] using [wct].
     *
     * @param transitionType the type of transition to start.
     * @param wct the wct to use in the transition, which may already container changes.
     * @param launchingTaskId the id of task launching, may be null if starting the task through an
     *   intent in the [wct].
     * @param remoteTransition the remote transition associated with this transition start.
     * @param deskId may be null if the launching task isn't launching into a desk, such as when
     *   fullscreen or split tasks are just moved to front.
     * @param displayId the display in which the launch is happening.
     * @param unminimizeReason the reason to unminimize.
     */
    @VisibleForTesting
    fun startLaunchTransition(
        transitionType: Int,
        wct: WindowContainerTransaction,
        launchingTaskId: Int?,
        remoteTransition: RemoteTransition? = null,
        deskId: Int?,
        displayId: Int,
        userId: Int,
        unminimizeReason: UnminimizeReason = UnminimizeReason.UNKNOWN,
        dragEvent: DragEvent? = null,
    ): IBinder {
        logV(
            "startLaunchTransition type=%s launchingTaskId=%d deskId=%d displayId=%d",
            transitTypeToString(transitionType),
            launchingTaskId,
            deskId,
            displayId,
        )
        val repository = userRepositories.getProfile(userId)
        // TODO: b/397619806 - Consolidate sharable logic with [handleFreeformTaskLaunch].
        var launchTransaction = wct
        // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition flag
        val taskIdToMinimize =
            deskId?.let {
                addAndGetMinimizeChanges(
                    deskId = it,
                    wct = launchTransaction,
                    newTaskId = launchingTaskId,
                    launchingNewIntent = launchingTaskId == null,
                )
            }
        val exitImmersiveResult =
            desktopImmersiveController.exitImmersiveIfApplicable(
                wct = launchTransaction,
                displayId = displayId,
                excludeTaskId = launchingTaskId,
                reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
            )
        var activationRunOnTransitStart: RunOnTransitStart? = null
        val shouldActivateDesk =
            when {
                deskId == null -> false
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ->
                    !repository.isDeskActive(deskId)
                DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue ->
                    !repository.isAnyDeskActive(displayId)
                else -> false
            }
        if (shouldActivateDesk) {
            val activateDeskWct = WindowContainerTransaction()
            // TODO: b/391485148 - pass in the launching task here to apply task-limit policy,
            //  but make sure to not do it twice since it is also done at the start of this
            //  function.
            activationRunOnTransitStart =
                addDeskActivationChanges(
                    deskId = checkNotNull(deskId) { "Desk id must be non-null when activating" },
                    wct = activateDeskWct,
                    userId = userId,
                    enterReason = EnterReason.APP_FREEFORM_INTENT,
                    isDeskSwitch = repository.isAnyDeskActive(displayId),
                )
            // Desk activation must be handled before app launch-related transactions.
            activateDeskWct.merge(launchTransaction, /* transfer= */ true)
            launchTransaction = activateDeskWct
            // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
            if (!desktopState.enableMultipleDesktops) {
                desktopModeEnterExitTransitionListener?.onEnterDesktopModeTransitionStarted(
                    toDesktopAnimationDurationMs
                )
            }
        }
        // Remove top transparent fullscreen task if needed.
        val closingTopTransparentTaskId =
            deskId?.let {
                closeTopTransparentFullscreenTask(
                    wct = launchTransaction,
                    deskId = it,
                    launchingTaskId = launchingTaskId,
                    userId = userId,
                )
            }
        val t =
            // If a desk is active and we are activating a new desk, start switch desk transition.
            if (repository.isAnyDeskActive(displayId) && shouldActivateDesk) {
                desktopMixedTransitionHandler.startSwitchDeskTransition(
                    transitionType = transitionType,
                    wct = launchTransaction,
                )
            } else if (remoteTransition == null) {
                logV("startLaunchTransition -- no remoteTransition -- wct = $launchTransaction")
                desktopMixedTransitionHandler.startLaunchTransition(
                    transitionType = transitionType,
                    wct = launchTransaction,
                    taskId = launchingTaskId,
                    minimizingTaskId = taskIdToMinimize,
                    closingTopTransparentTaskId = closingTopTransparentTaskId,
                    exitingImmersiveTask = exitImmersiveResult.asExit()?.exitingTask,
                    dragEvent = dragEvent,
                )
            } else if (taskIdToMinimize == null) {
                // TODO(b/412761429): Move OneShotRemoteHandler call to within
                //  DesktopMixedTransitionHandler.
                val remoteTransitionHandler = OneShotRemoteHandler(mainExecutor, remoteTransition)
                transitions
                    .startTransition(transitionType, launchTransaction, remoteTransitionHandler)
                    .also { remoteTransitionHandler.setTransition(it) }
            } else {
                val remoteTransitionHandler =
                    DesktopWindowLimitRemoteHandler(
                        mainExecutor,
                        rootTaskDisplayAreaOrganizer,
                        remoteTransition,
                        taskIdToMinimize,
                    )
                transitions
                    .startTransition(transitionType, launchTransaction, remoteTransitionHandler)
                    .also { remoteTransitionHandler.setTransition(it) }
            }
        if (taskIdToMinimize != null) {
            addPendingMinimizeTransition(t, taskIdToMinimize, MinimizeReason.TASK_LIMIT)
        }
        if (deskId != null) {
            addPendingTaskLimitTransition(t, deskId, launchingTaskId)
        }
        if (launchingTaskId != null && repository.isMinimizedTask(launchingTaskId)) {
            addPendingUnminimizeTransition(t, displayId, launchingTaskId, unminimizeReason)
        }
        activationRunOnTransitStart?.invoke(t)
        exitImmersiveResult.asExit()?.runOnTransitionStart?.invoke(t)
        return t
    }

    /**
     * Move task to the next display.
     *
     * Queries all currently known display IDs and checks if they match the predicate. The check is
     * performed in an order such that display IDs greater than the passed task's displayId are
     * considered before display IDs less than or equal to the passed task's displayID. Within each
     * of these two groups, the check is performed in ascending order.
     *
     * If a display ID matches predicate is found, re-parents the task to that display. No-op if no
     * such display is found.
     */
    fun moveToNextDisplay(
        taskId: Int,
        enterReason: EnterReason,
        predicate: (Int) -> Boolean = { true },
    ) {
        val task = shellTaskOrganizer.getRunningTaskInfo(taskId)
        if (task == null) {
            logW("moveToNextDisplay: taskId=%d not found", taskId)
            return
        }

        val newDisplayId =
            rootTaskDisplayAreaOrganizer.displayIds
                .sortedBy { (it - task.displayId - 1).mod(Int.MAX_VALUE) }
                .find(predicate)
        if (newDisplayId == null) {
            logW("moveToNextDisplay: next display not found")
            return
        }
        // moveToDisplay is no-op if newDisplayId is same with task.displayId.
        moveToDisplay(task, newDisplayId, enterReason = enterReason)
    }

    /** Move task to the next display which can host desktop tasks. */
    fun moveToNextDesktopDisplay(taskId: Int, userId: Int, enterReason: EnterReason) =
        moveToNextDisplay(taskId, enterReason) { displayId ->
            if (
                DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE.isTrue &&
                    desktopState.isProjectedMode() &&
                    displayId == DEFAULT_DISPLAY
            ) {
                logD("moveToNextDesktopDisplay: Moving to default display during projected mode.")
                return@moveToNextDisplay true
            }
            if (!desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
                logD(
                    "moveToNextDesktopDisplay: Skip displayId=$displayId as desktop mode " +
                        "is not supported."
                )
                return@moveToNextDisplay false
            }
            val focusedNonDesktopTasks =
                getFocusedNonDesktopTasks(displayId, userId).filter {
                    !DesktopWallpaperActivity.isWallpaperTask(it)
                }
            if (!focusedNonDesktopTasks.isEmpty()) {
                logD(
                    "moveToNextDesktopDisplay: Skip displayId=$displayId as it has focused " +
                        "non desktop tasks ${focusedNonDesktopTasks.joinToString()}"
                )
                return@moveToNextDisplay false
            }
            logD("moveToNextDesktopDisplay: Choose displayId=$displayId for the next display.")
            return@moveToNextDisplay true
        }

    /**
     * Start an intent through a launch transition for starting tasks whose transition does not get
     * handled by [handleRequest]
     */
    fun startLaunchIntentTransition(
        intent: Intent,
        options: Bundle,
        displayId: Int,
        userId: Int = shellController.currentUserId,
    ) {
        val repository = userRepositories.getProfile(userId)
        val wct = WindowContainerTransaction()
        val displayLayout = displayController.getDisplayLayout(displayId) ?: return
        val bounds = calculateDefaultDesktopTaskBounds(displayLayout)
        val deskId = getOrCreateDefaultDeskId(displayId, userId) ?: return
        if (DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue) {
            val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
            cascadeWindow(
                context,
                recentTasksController,
                repository,
                shellTaskOrganizer,
                bounds,
                displayLayout,
                deskId,
                stableBounds,
            )
        }
        val pendingIntent =
            PendingIntent.getActivityAsUser(
                context,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null,
                UserHandle.of(userId),
            )
        val ops =
            ActivityOptions.fromBundle(options).apply {
                launchWindowingMode = WINDOWING_MODE_FREEFORM
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                launchBounds = bounds
                launchDisplayId = displayId
                if (DesktopModeFlags.ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX.isTrue) {
                    // Sets launch bounds size as flexible so core can recalculate.
                    flexibleLaunchSize = true
                }
            }

        wct.sendPendingIntent(pendingIntent, intent, ops.toBundle())
        startLaunchTransition(
            TRANSIT_OPEN,
            wct,
            launchingTaskId = null,
            deskId = deskId,
            displayId = displayId,
            userId = userId,
        )
    }

    /**
     * Move [task] to display with [displayId]. When [bounds] is not null, it will be used as the
     * bounds on the new display. When [transitionHandler] is not null, it will be used instead of
     * the default [DesktopModeMoveToDisplayTransitionHandler].
     *
     * No-op if task is already on that display per [RunningTaskInfo.displayId].
     *
     * TODO: b/399411604 - split this up into smaller functions.
     */
    private fun moveToDisplay(
        task: RunningTaskInfo,
        displayId: Int,
        bounds: Rect? = null,
        transitionHandler: TransitionHandler? = null,
        enterReason: EnterReason,
        captionInsets: Int = 0,
    ) {
        logV("moveToDisplay: taskId=%d displayId=%d", task.taskId, displayId)
        if (task.displayId == displayId) {
            logD("moveToDisplay: task already on display %d", displayId)
            return
        }

        if (splitScreenController.isTaskInSplitScreen(task.taskId)) {
            moveSplitPairToDisplay(task, displayId)
            return
        }

        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        val wct = WindowContainerTransaction()
        val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        if (displayAreaInfo == null) {
            logW("moveToDisplay: display not found")
            return
        }

        val activationRunnable: RunOnTransitStart?
        val deactivationRunnable: RunOnTransitStart?

        if (
            DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE.isTrue &&
                desktopState.isProjectedMode() &&
                displayId == DEFAULT_DISPLAY
        ) {
            logV("moveToDisplay: moving task to default display during projected mode")
            activationRunnable = null
            deactivationRunnable =
                addMoveToFullscreenChanges(
                    wct,
                    task,
                    willExitDesktop(
                        triggerTaskId = task.taskId,
                        displayId = task.displayId,
                        userId = userId,
                        forceExitDesktop = false,
                    ),
                    displayId,
                )
        } else {
            val destinationDeskId = repository.getDefaultDeskId(displayId)
            if (destinationDeskId == null) {
                logW("moveToDisplay: desk not found for display: $displayId")
                return
            }
            snapEventHandler.removeTaskIfTiled(task.displayId, task.taskId)
            // TODO: b/393977830 and b/397437641 - do not assume that freeform==desktop.
            if (!task.isFreeform) {
                if (desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(task)) {
                    logW(
                        "moveToDisplay: do nothing because the desktop entry points should be " +
                            "disabled for the focused task"
                    )
                    return
                }
                addMoveToDeskTaskChanges(wct = wct, task = task, deskId = destinationDeskId)
            } else {
                if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                    desksOrganizer.moveTaskToDesk(wct, destinationDeskId, task)
                }
                if (bounds != null) {
                    wct.setBounds(task.token, bounds)

                    val prevCaptionInsets = task.freeformCaptionInsets
                    if (prevCaptionInsets != 0) {
                        val appBounds =
                            Rect(bounds).apply {
                                if (captionInsets != 0) {
                                    this.top += captionInsets
                                } else {
                                    val captionInsetsDp =
                                        displayController
                                            .getDisplayLayout(task.getDisplayId())
                                            ?.pxToDp(prevCaptionInsets)
                                            ?.toInt() ?: 0
                                    val newDisplayLayout =
                                        displayController.getDisplayLayout(displayId)
                                    val insets =
                                        newDisplayLayout?.dpToPx(captionInsetsDp)?.toInt() ?: 0
                                    this.top += insets
                                }
                            }
                        wct.setAppBounds(task.token, appBounds)
                    }
                } else if (DesktopExperienceFlags.ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT.isTrue) {
                    applyFreeformDisplayChange(
                        wct,
                        task,
                        displayController.getDisplayLayout(displayId),
                        displayController.getDisplayLayout(task.displayId),
                        // Tiling state will be broken if it exists when a task is moved to next
                        // display.
                        DesktopTaskTilingState.NONE,
                        destinationDeskId,
                    )
                }
            }

            if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                wct.reparent(task.token, displayAreaInfo.token, /* onTop= */ true)
            }

            activationRunnable =
                addDeskActivationChanges(
                    deskId = destinationDeskId,
                    wct = wct,
                    newTask = task,
                    userId = userId,
                    enterReason = enterReason,
                )
            val sourceDisplayId = task.displayId
            val sourceDeskId = repository.getDeskIdForTask(task.taskId)
            val shouldExitDesktopIfNeeded =
                ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue ||
                    DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
            val isLastTask =
                sourceDeskId?.let { repository.isOnlyTaskInDesk(task.taskId, it) } ?: false
            deactivationRunnable =
                if (shouldExitDesktopIfNeeded) {
                    performDesktopExitCleanupIfNeeded(
                        taskId = task.taskId,
                        deskId = sourceDeskId,
                        displayId = sourceDisplayId,
                        userId = userId,
                        wct = wct,
                        removingLastTaskId = if (isLastTask) task.taskId else null,
                        forceToFullscreen = false,
                        exitReason = ExitReason.TASK_MOVED_FROM_DESK,
                    )
                } else {
                    null
                }
        }
        if (DesktopExperienceFlags.ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue) {
            // Bring the destination display to top with includingParents=true, so that the
            // destination display gains the display focus, which makes the top task in the display
            // gains the global focus. This must be done after performDesktopExitCleanupIfNeeded.
            // The method launches Launcher on the source display when the last task is moved, which
            // brings the source display to the top. Calling reorder after
            // performDesktopExitCleanupIfNeeded ensures that the destination display becomes the
            // top (focused) display.
            wct.reorder(task.token, /* onTop= */ true, /* includingParents= */ true)
        }
        val transition =
            transitions.startTransition(
                TRANSIT_CHANGE,
                wct,
                transitionHandler ?: moveToDisplayTransitionHandler,
            )
        deactivationRunnable?.invoke(transition)
        activationRunnable?.invoke(transition)
    }

    /**
     * Move split pair associated with the [task] to display with [displayId].
     *
     * No-op if task is already on that display per [RunningTaskInfo.displayId].
     */
    private fun moveSplitPairToDisplay(task: RunningTaskInfo, displayId: Int) {
        if (!splitScreenController.isTaskInSplitScreen(task.taskId)) {
            return
        }

        if (
            !ENABLE_NON_DEFAULT_DISPLAY_SPLIT.isTrue ||
                !DesktopExperienceFlags.ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT.isTrue
        ) {
            return
        }

        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        val wct = WindowContainerTransaction()
        val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        if (displayAreaInfo == null) {
            logW("moveSplitPairToDisplay: display not found")
            return
        }

        val activeDeskId = repository.getActiveDeskId(displayId)
        logV("moveSplitPairToDisplay: moving split root to displayId=%d", displayId)

        val stageCoordinatorRootTaskToken =
            splitScreenController.multiDisplayProvider.getDisplayRootForDisplayId(DEFAULT_DISPLAY)
        if (stageCoordinatorRootTaskToken == null) {
            return
        }
        wct.reparent(stageCoordinatorRootTaskToken, displayAreaInfo.token, true /* onTop */)

        val deactivationRunnable =
            if (activeDeskId != null) {
                // Split is being placed on top of an existing desk in the target display. Make
                // sure it is cleaned up.
                performDesktopExitCleanUp(
                    wct = wct,
                    deskId = activeDeskId,
                    displayId = displayId,
                    userId = userId,
                    willExitDesktop = true,
                    removingLastTaskId = null,
                    shouldEndUpAtHome = false,
                    exitReason = ExitReason.TASK_MOVED_FROM_DESK,
                )
            } else {
                null
            }
        val transition = transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
        deactivationRunnable?.invoke(transition)
        return
    }

    /**
     * Quick-resizes a desktop task, toggling between a fullscreen state (represented by the stable
     * bounds) and a free floating state (either the last saved bounds if available or the default
     * bounds otherwise).
     */
    fun toggleDesktopTaskSize(taskInfo: RunningTaskInfo, interaction: ToggleTaskSizeInteraction) {
        val repository = userRepositories.getProfile(taskInfo.userId)
        val currentTaskBounds = taskInfo.configuration.windowConfiguration.bounds
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        desktopModeEventLogger.logTaskResizingStarted(
            interaction.resizeTrigger,
            interaction.inputMethod,
            taskInfo,
            currentTaskBounds.width(),
            currentTaskBounds.height(),
            displayController,
            deskId,
        )
        val displayLayout = displayController.getDisplayLayout(taskInfo.displayId) ?: return
        val destinationBounds = Rect()
        val isMaximized = interaction.direction == ToggleTaskSizeInteraction.Direction.RESTORE
        // If the task is currently maximized, we will toggle it not to be and vice versa. This is
        // helpful to eliminate the current task from logic to calculate taskbar corner rounding.
        val willMaximize = interaction.direction == ToggleTaskSizeInteraction.Direction.MAXIMIZE
        if (isMaximized) {
            // The desktop task is at the maximized width and/or height of the stable bounds.
            // If the task's pre-maximize stable bounds were saved, toggle the task to those bounds.
            // Otherwise, toggle to the default bounds.
            val taskBoundsBeforeMaximize = repository.removeBoundsBeforeMaximize(taskInfo.taskId)
            if (taskBoundsBeforeMaximize != null) {
                destinationBounds.set(taskBoundsBeforeMaximize)
            } else {
                if (ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS.isTrue()) {
                    destinationBounds.set(calculateInitialBounds(displayLayout, taskInfo))
                } else {
                    destinationBounds.set(calculateDefaultDesktopTaskBounds(displayLayout))
                }
            }
        } else {
            // Save current bounds so that task can be restored back to original bounds if necessary
            // and toggle to the stable bounds.
            snapEventHandler.removeTaskIfTiled(taskInfo.displayId, taskInfo.taskId)
            repository.saveBoundsBeforeMaximize(taskInfo.taskId, currentTaskBounds)
            destinationBounds.set(calculateMaximizeBounds(displayLayout, taskInfo))
        }

        val shouldRestoreToSnap =
            isMaximized && isTaskSnappedToHalfScreen(taskInfo.displayId, destinationBounds)

        logD("willMaximize = %s", willMaximize)
        logD("shouldRestoreToSnap = %s", shouldRestoreToSnap)

        val doesAnyTaskRequireTaskbarRounding =
            willMaximize ||
                shouldRestoreToSnap ||
                doesAnyTaskRequireTaskbarRounding(
                    displayId = taskInfo.displayId,
                    userId = taskInfo.userId,
                    excludeTaskId = taskInfo.taskId,
                )

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding)
        val wct = WindowContainerTransaction().setBounds(taskInfo.token, destinationBounds)
        interaction.uiEvent?.let { uiEvent -> desktopModeUiEventLogger.log(taskInfo, uiEvent) }
        desktopModeEventLogger.logTaskResizingEnded(
            interaction.resizeTrigger,
            interaction.inputMethod,
            taskInfo,
            destinationBounds.width(),
            destinationBounds.height(),
            displayController,
            deskId,
        )
        toggleResizeDesktopTaskTransitionHandler.startTransition(
            wct,
            interaction.animationStartBounds,
        )
    }

    private fun dragToMaximizeDesktopTask(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl,
        currentDragBounds: Rect,
        motionEvent: MotionEvent,
    ) {
        val displayId = taskInfo.displayId
        val displayLayout = displayController.getDisplayLayout(displayId)
        if (displayLayout == null) {
            logW("Display %d is not found, task displayId might be stale", displayId)
            return
        }
        if (isTaskMaximized(taskInfo, displayLayout)) {
            // Handle the case where we attempt to drag-to-maximize when already maximized: the task
            // position won't need to change but we want to animate the surface going back to the
            // maximized position.
            val containerBounds = taskInfo.configuration.windowConfiguration.bounds
            if (containerBounds != currentDragBounds) {
                returnToDragStartAnimator.start(
                    taskInfo.taskId,
                    taskSurface,
                    startBounds = currentDragBounds,
                    endBounds = containerBounds,
                )
            }
            return
        }

        toggleDesktopTaskSize(
            taskInfo,
            ToggleTaskSizeInteraction(
                direction = ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                source = ToggleTaskSizeInteraction.Source.HEADER_DRAG_TO_TOP,
                inputMethod = DesktopModeEventLogger.getInputMethodFromMotionEvent(motionEvent),
                animationStartBounds = currentDragBounds,
            ),
        )
    }

    private fun isMaximizedToStableBoundsEdges(displayId: Int, taskBounds: Rect): Boolean {
        val displayLayout = displayController.getDisplayLayout(displayId) ?: return false
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        return isTaskBoundsEqual(taskBounds, stableBounds)
    }

    /** Returns if current task bound is snapped to half screen */
    private fun isTaskSnappedToHalfScreen(displayId: Int, taskBounds: Rect): Boolean =
        getSnapBounds(displayId, SnapPosition.LEFT) == taskBounds ||
            getSnapBounds(displayId, SnapPosition.RIGHT) == taskBounds

    /**
     * Update the rounding state of the taskbar on the given display, based on the task with ID
     * [taskId] having bounds [newBounds].
     */
    fun updateTaskbarRoundingOnTaskResize(displayId: Int, taskId: Int, newBounds: Rect) {
        val otherTasksRequireTaskbarRounding =
            doesAnyTaskRequireTaskbarRounding(
                displayId,
                shellController.currentUserId,
                excludeTaskId = taskId,
            )
        val resizedTaskRequiresTaskbarRounding =
            doesTaskRequireTaskbarRounding(displayId, newBounds)
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            otherTasksRequireTaskbarRounding || resizedTaskRequiresTaskbarRounding
        )
    }

    private fun doesTaskRequireTaskbarRounding(displayId: Int, taskBounds: Rect): Boolean {
        val isSnappedToHalfScreen = isTaskSnappedToHalfScreen(displayId, taskBounds)
        val isMaximizedToBothEdges = isMaximizedToStableBoundsEdges(displayId, taskBounds)
        logD("isTaskSnappedToHalfScreen(taskInfo) = %s", isSnappedToHalfScreen)
        logD("isMaximizedToStableBoundsEdges(taskInfo, stableBounds) = %s", isMaximizedToBothEdges)
        return isSnappedToHalfScreen || isMaximizedToBothEdges
    }

    @VisibleForTesting
    fun doesAnyTaskRequireTaskbarRounding(
        displayId: Int,
        userId: Int,
        excludeTaskId: Int? = null,
    ): Boolean {
        val doesAnyTaskRequireTaskbarRounding =
            userRepositories
                .getProfile(userId)
                .getExpandedTasksOrdered(displayId)
                // exclude current task since maximize/restore transition has not taken place yet.
                .filterNot { taskId -> taskId == excludeTaskId }
                .any { taskId ->
                    val taskInfo = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: return false
                    logD("taskInfo = %s", taskInfo)
                    val taskBounds = taskInfo.configuration.windowConfiguration.bounds
                    doesTaskRequireTaskbarRounding(displayId, taskBounds)
                }
        logD("doesAnyTaskRequireTaskbarRounding = %s", doesAnyTaskRequireTaskbarRounding)
        return doesAnyTaskRequireTaskbarRounding
    }

    /**
     * Quick-resize to the right or left half of the stable bounds.
     *
     * @param taskInfo current task that is being snap-resized via dragging or maximize menu button
     * @param taskSurface the leash of the task being dragged
     * @param currentDragBounds current position of the task leash being dragged (or current task
     *   bounds if being snapped resize via maximize menu button)
     * @param position the portion of the screen (RIGHT or LEFT) we want to snap the task to.
     */
    fun snapToHalfScreen(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl?,
        currentDragBounds: Rect,
        position: SnapPosition,
        resizeTrigger: ResizeTrigger,
        inputMethod: InputMethod,
    ) {
        val repository = userRepositories.getProfile(taskInfo.userId)
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        desktopModeEventLogger.logTaskResizingStarted(
            resizeTrigger,
            inputMethod,
            taskInfo,
            currentDragBounds.width(),
            currentDragBounds.height(),
            displayController,
            deskId,
        )

        val destinationBounds = getSnapBounds(taskInfo.displayId, position)
        desktopModeEventLogger.logTaskResizingEnded(
            resizeTrigger,
            inputMethod,
            taskInfo,
            destinationBounds.width(),
            destinationBounds.height(),
            displayController,
            deskId,
        )

        if (DesktopExperienceFlags.ENABLE_TILE_RESIZING.isTrue()) {
            val isTiled = snapEventHandler.snapToHalfScreen(taskInfo, currentDragBounds, position)
            if (isTiled) {
                taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(true)
            }
            return
        }

        if (destinationBounds == taskInfo.configuration.windowConfiguration.bounds) {
            // Handle the case where we attempt to snap resize when already snap resized: the task
            // position won't need to change but we want to animate the surface going back to the
            // snapped position from the "dragged-to-the-edge" position.
            if (destinationBounds != currentDragBounds && taskSurface != null) {
                returnToDragStartAnimator.start(
                    taskInfo.taskId,
                    taskSurface,
                    startBounds = currentDragBounds,
                    endBounds = destinationBounds,
                )
            }
            return
        }

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(true)
        val wct = WindowContainerTransaction().setBounds(taskInfo.token, destinationBounds)

        toggleResizeDesktopTaskTransitionHandler.startTransition(wct, currentDragBounds)
    }

    /**
     * Handles snap resizing a [taskInfo] to [position] instantaneously, for example when the
     * [resizeTrigger] is the snap resize menu using any [motionEvent] or a keyboard shortcut.
     */
    fun handleInstantSnapResizingTask(
        taskInfo: RunningTaskInfo,
        position: SnapPosition,
        resizeTrigger: ResizeTrigger,
        inputMethod: InputMethod,
    ) {
        if (!isSnapResizingAllowed(taskInfo)) {
            val displayContext = displayController.getDisplayContext(taskInfo.displayId) ?: context
            Toast.makeText(
                    displayContext,
                    R.string.desktop_mode_non_resizable_snap_text,
                    Toast.LENGTH_SHORT,
                )
                .show()
            return
        }

        snapToHalfScreen(
            taskInfo,
            null,
            taskInfo.configuration.windowConfiguration.bounds,
            position,
            resizeTrigger,
            inputMethod,
        )
    }

    @VisibleForTesting
    fun handleSnapResizingTaskOnDrag(
        taskInfo: RunningTaskInfo,
        position: SnapPosition,
        taskSurface: SurfaceControl,
        currentDragBounds: Rect,
        dragStartBounds: Rect,
        motionEvent: MotionEvent,
    ) {
        releaseVisualIndicator()
        if (!isSnapResizingAllowed(taskInfo)) {
            interactionJankMonitor.begin(
                taskSurface,
                context,
                handler,
                CUJ_DESKTOP_MODE_SNAP_RESIZE,
                "drag_non_resizable",
            )

            // reposition non-resizable app back to its original position before being dragged
            returnToDragStartAnimator.start(
                taskInfo.taskId,
                taskSurface,
                startBounds = currentDragBounds,
                endBounds = dragStartBounds,
                doOnEnd = {
                    val displayContext =
                        displayController.getDisplayContext(taskInfo.displayId) ?: context
                    Toast.makeText(
                            displayContext,
                            R.string.desktop_mode_non_resizable_snap_text,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                },
            )
        } else {
            val resizeTrigger =
                if (position == SnapPosition.LEFT) {
                    ResizeTrigger.DRAG_LEFT
                } else {
                    ResizeTrigger.DRAG_RIGHT
                }
            interactionJankMonitor.begin(
                taskSurface,
                context,
                handler,
                CUJ_DESKTOP_MODE_SNAP_RESIZE,
                "drag_resizable",
            )
            snapToHalfScreen(
                taskInfo,
                taskSurface,
                currentDragBounds,
                position,
                resizeTrigger,
                DesktopModeEventLogger.getInputMethodFromMotionEvent(motionEvent),
            )
        }
    }

    private fun isSnapResizingAllowed(taskInfo: RunningTaskInfo) =
        taskInfo.isResizeable || !DISABLE_NON_RESIZABLE_APP_SNAP_RESIZE.isTrue()

    private fun getSnapBounds(displayId: Int, position: SnapPosition): Rect {
        val displayLayout = displayController.getDisplayLayout(displayId) ?: return Rect()

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }

        val destinationWidth = stableBounds.width() / 2
        return when (position) {
            SnapPosition.LEFT -> {
                Rect(
                    stableBounds.left,
                    stableBounds.top,
                    stableBounds.left + destinationWidth,
                    stableBounds.bottom,
                )
            }
            SnapPosition.RIGHT -> {
                Rect(
                    stableBounds.right - destinationWidth,
                    stableBounds.top,
                    stableBounds.right,
                    stableBounds.bottom,
                )
            }
        }
    }

    /**
     * Get windowing move for a given `taskId`
     *
     * @return [WindowingMode] for the task or [WINDOWING_MODE_UNDEFINED] if task is not found
     */
    @WindowingMode
    fun getTaskWindowingMode(taskId: Int): Int {
        return shellTaskOrganizer.getRunningTaskInfo(taskId)?.windowingMode
            ?: WINDOWING_MODE_UNDEFINED
    }

    private fun prepareForDeskActivation(displayId: Int, wct: WindowContainerTransaction) {
        logD(
            "prepareForDeskActivation displayId=%d shouldShowHomeBehindDesktop=%b",
            displayId,
            desktopState.shouldShowHomeBehindDesktop,
        )
        // Move home to front, ensures that we go back home when all desktop windows are closed
        val useParamDisplayId =
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue
        moveHomeTaskToTop(
            displayId = if (useParamDisplayId) displayId else context.displayId,
            wct = wct,
        )
        // Currently, we only handle the desktop on the default display really.
        if (
            (displayId == DEFAULT_DISPLAY ||
                ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) &&
                ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue() &&
                !desktopState.shouldShowHomeBehindDesktop
        ) {
            // Add translucent wallpaper activity to show the wallpaper underneath.
            addWallpaperActivity(displayId, wct)
        }
    }

    @Deprecated(
        "Use addDeskActivationChanges() instead.",
        ReplaceWith("addDeskActivationChanges()"),
    )
    private fun bringDesktopAppsToFront(
        displayId: Int,
        userId: Int,
        wct: WindowContainerTransaction,
        newTaskIdInFront: Int? = null,
    ): Int? {
        logV("bringDesktopAppsToFront, newTaskId=%d", newTaskIdInFront)
        val repository = userRepositories.getProfile(userId)
        prepareForDeskActivation(displayId, wct)

        val expandedTasksOrderedFrontToBack = repository.getExpandedTasksOrdered(displayId)
        // If we're adding a new Task we might need to minimize an old one
        // TODO(b/365725441): Handle non running task minimization
        // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition flag
        val taskIdToMinimize: Int? =
            if (newTaskIdInFront != null) {
                getTaskIdToMinimize(expandedTasksOrderedFrontToBack, newTaskIdInFront)
            } else {
                null
            }

        expandedTasksOrderedFrontToBack
            // If there is a Task to minimize, let it stay behind the Home Task
            .filter { taskId -> taskId != taskIdToMinimize }
            .reversed() // Start from the back so the front task is brought forward last
            .forEach { taskId ->
                val runningTaskInfo = shellTaskOrganizer.getRunningTaskInfo(taskId)
                if (runningTaskInfo != null) {
                    // Task is already running, reorder it to the front
                    wct.reorder(runningTaskInfo.token, /* onTop= */ true)
                } else if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
                    // Task is not running, start it
                    val startDesk = repository.getDefaultDeskId(displayId) ?: INVALID_DESK_ID
                    wct.startTask(
                        taskId,
                        createActivityOptionsForStartTask(startDesk, desksOrganizer).toBundle(),
                    )
                }
            }

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(displayId = displayId, userId = userId)
        )

        return taskIdToMinimize
    }

    private fun moveHomeTaskToTop(displayId: Int, wct: WindowContainerTransaction) {
        logV("moveHomeTaskToTop in displayId=%d", displayId)
        getHomeTask(displayId)?.let { homeTask ->
            wct.reorder(homeTask.getToken(), /* onTop= */ true)
        }
    }

    private fun removeHomeTask(wct: WindowContainerTransaction, displayId: Int) {
        logV("removeHomeTask in displayId=%d", displayId)
        getHomeTask(displayId)?.let { homeTask -> wct.removeRootTask(homeTask.getToken()) }
    }

    private fun getHomeTask(displayId: Int): RunningTaskInfo? {
        return shellTaskOrganizer.getRunningTasks(displayId).firstOrNull { task ->
            task.activityType == ACTIVITY_TYPE_HOME
        }
    }

    private fun addLaunchHomePendingIntent(
        wct: WindowContainerTransaction,
        displayId: Int,
        userId: Int,
    ) {
        logV("addLaunchHomePendingIntent displayId=%d userId=%d", displayId, userId)
        homeIntentProvider.addLaunchHomePendingIntent(wct, displayId, userId)
    }

    private fun addWallpaperActivity(displayId: Int, wct: WindowContainerTransaction) {
        logV("addWallpaperActivity")
        if (ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER.isTrue) {

            // If the wallpaper activity for this display already exists, let's reorder it to top.
            val wallpaperActivityToken = desktopWallpaperActivityTokenProvider.getToken(displayId)
            if (wallpaperActivityToken != null) {
                wct.reorder(wallpaperActivityToken, /* onTop= */ true)
                return
            }
            val intent = Intent(context, DesktopWallpaperActivity::class.java)
            if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options =
                ActivityOptions.makeBasic().apply {
                    launchWindowingMode = WINDOWING_MODE_FULLSCREEN
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
                        launchDisplayId = displayId
                    }
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    /* requestCode = */ 0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE,
                )
            wct.sendPendingIntent(pendingIntent, intent, options.toBundle())
        } else {
            val userId = shellController.currentUserId
            val userHandle = UserHandle.of(userId)
            val userContext = context.createContextAsUser(userHandle, /* flags= */ 0)
            val intent = Intent(userContext, DesktopWallpaperActivity::class.java)
            if (
                desktopWallpaperActivityTokenProvider.getToken(displayId) == null &&
                    ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue
            ) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            intent.putExtra(Intent.EXTRA_USER_HANDLE, userId)
            val options =
                ActivityOptions.makeBasic().apply {
                    launchWindowingMode = WINDOWING_MODE_FULLSCREEN
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
                        launchDisplayId = displayId
                    }
                }
            val pendingIntent =
                PendingIntent.getActivityAsUser(
                    userContext,
                    /* requestCode= */ 0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE,
                    /* options= */ null,
                    userHandle,
                )
            wct.sendPendingIntent(pendingIntent, intent, options.toBundle())
        }
    }

    private fun moveWallpaperActivityToBack(wct: WindowContainerTransaction, displayId: Int) {
        desktopWallpaperActivityTokenProvider.getToken(displayId)?.let { token ->
            logV("moveWallpaperActivityToBack")
            wct.reorder(token, /* onTop= */ false)
        }
    }

    private fun removeWallpaperTask(wct: WindowContainerTransaction, displayId: Int) {
        desktopWallpaperActivityTokenProvider.getToken(displayId)?.let { token ->
            logV("removeWallpaperTask")
            wct.removeTask(token)
        }
    }

    private fun willExitDesktop(
        triggerTaskId: Int,
        displayId: Int,
        userId: Int,
        forceExitDesktop: Boolean,
    ): Boolean {
        if (forceExitDesktop && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // |forceExitDesktop| is true when the callers knows we'll exit desktop, such as when
            // explicitly going fullscreen, so there's no point in checking the desktop state.
            return true
        }
        return isOnlyVisibleNonClosingTask(
            taskId = triggerTaskId,
            displayId = displayId,
            userId = userId,
        )
    }

    private fun isOnlyVisibleNonClosingTask(taskId: Int, displayId: Int, userId: Int): Boolean {
        val repository = userRepositories.getProfile(userId)
        return if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
            repository.isOnlyVisibleNonClosingTask(taskId, displayId)
        } else {
            repository.isOnlyVisibleNonClosingTask(taskId)
        }
    }

    private fun performDesktopExitCleanupIfNeeded(
        taskId: Int,
        deskId: Int? = null,
        displayId: Int,
        userId: Int,
        wct: WindowContainerTransaction,
        removingLastTaskId: Int?,
        forceToFullscreen: Boolean,
        exitReason: ExitReason,
    ): RunOnTransitStart? {
        if (
            !willExitDesktop(
                triggerTaskId = taskId,
                displayId = displayId,
                userId = userId,
                forceExitDesktop = forceToFullscreen,
            )
        ) {
            return null
        }
        // TODO: b/394268248 - update remaining callers to pass in a |deskId| and apply the
        //  |RunOnTransitStart| when the transition is started.
        return performDesktopExitCleanUp(
            wct = wct,
            deskId = deskId,
            displayId = displayId,
            userId = userId,
            willExitDesktop = true,
            removingLastTaskId = removingLastTaskId,
            shouldEndUpAtHome = true,
            exitReason = exitReason,
        )
    }

    /** TODO: b/394268248 - update [deskId] to be non-null. */
    fun performDesktopExitCleanUp(
        wct: WindowContainerTransaction,
        deskId: Int?,
        displayId: Int,
        userId: Int,
        willExitDesktop: Boolean,
        removingLastTaskId: Int?,
        shouldEndUpAtHome: Boolean = true,
        skipWallpaperAndHomeOrdering: Boolean = false,
        skipUpdatingExitDesktopListener: Boolean = false,
        exitReason: ExitReason,
    ): RunOnTransitStart? {
        logV(
            "performDesktopExitCleanUp deskId=%d displayId=%d userId=%d willExitDesktop=%b " +
                "removingLastTaskId=%d shouldEndUpAtHome=%b skipWallpaperAndHomeOrdering=%b " +
                "skipUpdatingExitDesktopListener=%b exitReason=%s",
            deskId,
            displayId,
            userId,
            willExitDesktop,
            removingLastTaskId,
            shouldEndUpAtHome,
            skipWallpaperAndHomeOrdering,
            skipUpdatingExitDesktopListener,
            exitReason,
        )
        if (!willExitDesktop) return null
        if (
            !skipUpdatingExitDesktopListener &&
                // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
                !desktopState.enableMultipleDesktops
        ) {
            desktopModeEnterExitTransitionListener?.onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome,
            )
        }
        val shouldHandleWallpaperAndHome =
            (!skipWallpaperAndHomeOrdering ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) &&
                !desktopState.shouldShowHomeBehindDesktop
        if (shouldHandleWallpaperAndHome) {
            if (ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER.isTrue) {
                moveWallpaperActivityToBack(wct, displayId)
            } else {
                removeWallpaperTask(wct, displayId)
            }
            if (shouldEndUpAtHome) {
                // If the transition should end up with user going to home, launch home with a
                // pending intent.
                addLaunchHomePendingIntent(wct, displayId, userId)
            }
        }
        val shouldRemoveDesk =
            DesktopExperienceFlags.ENABLE_REMOVE_DESK_ON_LAST_TASK_REMOVAL.isTrue &&
                removingLastTaskId != null &&
                !rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
        return if (shouldRemoveDesk) {
            addDeskRemovalChanges(
                wct = wct,
                deskId = deskId,
                displayId = displayId,
                userId = userId,
                excludingTaskId = removingLastTaskId,
                exitReason = exitReason,
            )
        } else {
            addDeskDeactivationChanges(
                wct = wct,
                deskId = deskId,
                userId = userId,
                displayId = displayId,
                exitReason = exitReason,
            )
        }
    }

    fun releaseVisualIndicator() {
        visualIndicator?.releaseVisualIndicator()
        visualIndicator = null
    }

    override fun getContext(): Context = context

    override fun getRemoteCallExecutor(): ShellExecutor = mainExecutor

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        // This handler should never be the sole handler, so should not animate anything.
        return false
    }

    private fun taskDisplaySupportDesktopMode(triggerTask: RunningTaskInfo) =
        desktopState.isDesktopModeSupportedOnDisplay(triggerTask.displayId)

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        logV("handleRequest request=%s", request)
        // First, check if this is a display disconnect request.
        val displayChange = request.displayChange
        if (
            DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue &&
                displayChange != null &&
                displayChange.disconnectReparentDisplay != INVALID_DISPLAY
        ) {
            return onDisplayDisconnect(
                displayChange.displayId,
                displayChange.disconnectReparentDisplay,
                transition,
            )
        }
        val userChange = request.userChange
        if (
            DesktopExperienceFlags.ENABLE_APPLY_DESK_ACTIVATION_ON_USER_SWITCH.isTrue &&
                userChange != null
        ) {
            return handleUserChangeTransitionRequest(transition, request)
        }
        // Check if we should skip handling this transition
        var reason = ""
        val triggerTask = request.triggerTask
        // Skipping early if the trigger task is null
        if (triggerTask == null) {
            logV("skipping handleRequest reason=%s", "triggerTask is null")
            return null
        }
        val recentsAnimationRunning =
            RecentsTransitionStateListener.isAnimating(recentsTransitionState)
        val shouldHandleMidRecentsFreeformLaunch =
            recentsAnimationRunning && isFreeformRelaunch(triggerTask, request)
        val isDragAndDropFullscreenTransition = taskContainsDragAndDropCookie(triggerTask)
        val shouldHandleRequest =
            when {
                !taskDisplaySupportDesktopMode(triggerTask) -> {
                    reason = "triggerTask's display doesn't support desktop mode"
                    false
                }
                // Handle freeform relaunch during recents animation
                shouldHandleMidRecentsFreeformLaunch -> true
                recentsAnimationRunning -> {
                    reason = "recents animation is running"
                    false
                }
                // Don't handle request if this was a tear to fullscreen transition.
                // handleFullscreenTaskLaunch moves fullscreen intents to freeform;
                // this is an exception to the rule
                isDragAndDropFullscreenTransition -> {
                    dragAndDropFullscreenCookie = null
                    false
                }
                // Handle task closing for the last window if wallpaper is available
                shouldHandleTaskClosing(request) -> true
                // Handle task moving requests
                request.requestedLocation != null -> true
                // Handle client requests to enter/exit fullscreen mode.
                clientFullscreenRequestTransitionHandler.shouldHandleRequest(request) -> true
                // Only handle open or to front transitions
                request.type != TRANSIT_OPEN &&
                    request.type != TRANSIT_TO_FRONT &&
                    request.type != TRANSIT_START_LOCK_TASK_MODE -> {
                    reason = "transition type not handled (${request.type})"
                    false
                }
                // Home launches are only handled with multiple desktops enabled.
                triggerTask.activityType == ACTIVITY_TYPE_HOME &&
                    !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue -> {
                    reason = "ACTIVITY_TYPE_HOME not handled"
                    false
                }
                // Only handle standard and home tasks types.
                triggerTask.activityType != ACTIVITY_TYPE_STANDARD &&
                    triggerTask.activityType != ACTIVITY_TYPE_HOME -> {
                    reason = "activityType not handled (${triggerTask.activityType})"
                    false
                }
                // Only handle fullscreen or freeform tasks
                !triggerTask.isFullscreen && !triggerTask.isFreeform -> {
                    reason = "windowingMode not handled (${triggerTask.windowingMode})"
                    false
                }
                // Otherwise process it
                else -> true
            }

        if (!shouldHandleRequest) {
            logV("skipping handleRequest reason=%s", reason)
            return null
        }

        val result =
            when {
                triggerTask.activityType == ACTIVITY_TYPE_HOME ->
                    handleHomeTaskLaunch(triggerTask, transition)
                // Check if freeform task launch during recents should be handled
                shouldHandleMidRecentsFreeformLaunch ->
                    handleMidRecentsFreeformTaskLaunch(triggerTask, transition)
                request.type == TRANSIT_START_LOCK_TASK_MODE ->
                    handleLockTask(triggerTask, transition)
                // Check if the closing task needs to be handled
                TransitionUtil.isClosingType(request.type) ->
                    handleTaskClosing(triggerTask, transition, request.type)
                // Check if the top task shouldn't be allowed to enter desktop mode
                isIncompatibleTask(triggerTask) ->
                    handleIncompatibleTaskLaunch(triggerTask, transition)
                // Check if a desktop task was requested to enter/exit fullscreen from the client.
                clientFullscreenRequestTransitionHandler.shouldHandleRequest(request) ->
                    clientFullscreenRequestTransitionHandler.handleRequest(transition, request)
                // Check if fullscreen task should be updated
                triggerTask.isFullscreen ->
                    handleFullscreenTaskLaunch(triggerTask, transition, request.type)
                // Check if the request is a task move request
                request.requestedLocation != null ->
                    handleTaskLocationRequest(
                        triggerTask,
                        transition,
                        request.requestedLocation!!.displayId,
                        request.requestedLocation!!.bounds,
                    )
                // Check if freeform task should be updated
                triggerTask.isFreeform ->
                    handleFreeformTaskLaunch(triggerTask, transition, request.type)
                else -> {
                    null
                }
            }
        logV("handleRequest result=%s", result)
        return result
    }

    /** Whether the given [change] in the [transition] is a known desktop change. */
    fun isDesktopChange(transition: IBinder, change: TransitionInfo.Change): Boolean {
        // Only the immersive controller is currently involved in mixed transitions.
        return DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue &&
            desktopImmersiveController.isImmersiveChange(transition, change)
    }

    /**
     * Whether the given transition [info] will potentially include a desktop change, in which case
     * the transition should be treated as mixed so that the change is in part animated by one of
     * the desktop transition handlers.
     */
    fun shouldPlayDesktopAnimation(info: TransitionRequestInfo): Boolean {
        // Only immersive mixed transition are currently supported.
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return false
        val triggerTask = info.triggerTask ?: return false
        val userId = triggerTask.userId
        val repository = userRepositories.getProfile(userId)
        if (!repository.isAnyDeskActive(triggerTask.displayId)) {
            return false
        }
        if (!TransitionUtil.isOpeningType(info.type)) {
            return false
        }
        repository.getTaskInFullImmersiveState(displayId = triggerTask.displayId) ?: return false
        return when {
            triggerTask.isFullscreen -> {
                // Trigger fullscreen task will enter desktop, so any existing immersive task
                // should exit.
                shouldFullscreenTaskLaunchSwitchToDesktop(triggerTask, info.type)
            }
            triggerTask.isFreeform -> {
                // Trigger freeform task will enter desktop, so any existing immersive task should
                // exit.
                !shouldFreeformTaskLaunchSwitchToFullscreen(triggerTask)
            }
            else -> false
        }
    }

    /** Animate a desktop change found in a mixed transitions. */
    fun animateDesktopChange(
        transition: IBinder,
        change: Change,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback,
    ) {
        if (!desktopImmersiveController.isImmersiveChange(transition, change)) {
            throw IllegalStateException("Only immersive changes support desktop mixed transitions")
        }
        desktopImmersiveController.animateResizeChange(
            change,
            startTransaction,
            finishTransaction,
            finishCallback,
        )
    }

    private fun taskContainsDragAndDropCookie(taskInfo: RunningTaskInfo) =
        taskInfo.launchCookies?.any { it == dragAndDropFullscreenCookie } ?: false

    /**
     * Applies the proper surface states (rounded corners) to tasks when desktop mode is active.
     * This is intended to be used when desktop mode is part of another animation but isn't, itself,
     * animating.
     */
    fun syncSurfaceState(info: TransitionInfo, finishTransaction: SurfaceControl.Transaction) {
        // Add rounded corners to freeform windows
        if (!desktopConfig.useRoundedCorners) {
            return
        }
        val cornerRadius =
            context.resources
                .getDimensionPixelSize(
                    SharedR.dimen.desktop_windowing_freeform_rounded_corner_radius
                )
                .toFloat()
        info.changes
            .filter { it.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM }
            .forEach { finishTransaction.setCornerRadius(it.leash, cornerRadius) }
    }

    /** Returns whether an existing desktop task is being relaunched in freeform or not. */
    private fun isFreeformRelaunch(
        triggerTask: RunningTaskInfo,
        request: TransitionRequestInfo,
    ): Boolean {
        val repository = userRepositories.getProfile(triggerTask.userId)
        return (triggerTask.windowingMode == WINDOWING_MODE_FREEFORM &&
            TransitionUtil.isOpeningType(request.type) &&
            repository.isActiveTask(triggerTask.taskId))
    }

    /** Returns whether a fullscreen task is being relaunched on the same display or not. */
    private fun isFullscreenRelaunch(
        triggerTask: RunningTaskInfo,
        @WindowManager.TransitionType requestType: Int,
    ): Boolean {
        val repository = userRepositories.getProfile(triggerTask.userId)
        // Do not treat fullscreen-in-desktop as fullscreen.
        if (repository.isActiveTask(triggerTask.taskId)) return false

        val existingTask = shellTaskOrganizer.getRunningTaskInfo(triggerTask.taskId) ?: return false
        return triggerTask.isFullscreen &&
            TransitionUtil.isOpeningType(requestType) &&
            existingTask.isFullscreen &&
            existingTask.displayId == triggerTask.displayId
    }

    private fun isIncompatibleTask(task: RunningTaskInfo) =
        desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(task)

    private fun shouldHandleTaskClosing(request: TransitionRequestInfo): Boolean =
        ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY.isTrue() &&
            TransitionUtil.isClosingType(request.type) &&
            request.triggerTask != null &&
            request.triggerTask?.activityType != ACTIVITY_TYPE_HOME

    /** Open an existing instance of an app. */
    fun openInstance(callingTask: RunningTaskInfo, requestedTaskId: Int) {
        val deskId = getOrCreateDefaultDeskId(callingTask.displayId, callingTask.userId) ?: return
        if (callingTask.isFreeform) {
            val requestedTaskInfo = shellTaskOrganizer.getRunningTaskInfo(requestedTaskId)
            if (requestedTaskInfo?.isFreeform == true) {
                // If requested task is an already open freeform task, just move it to front.
                moveTaskToFront(
                    taskId = requestedTaskId,
                    userId = callingTask.userId,
                    unminimizeReason = UnminimizeReason.APP_HANDLE_MENU_BUTTON,
                )
            } else {
                moveTaskToDesk(
                    taskId = requestedTaskId,
                    deskId = deskId,
                    userId = callingTask.userId,
                    wct = WindowContainerTransaction(),
                    transitionSource = DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
                )
            }
        } else {
            val options = createNewWindowOptions(callingTask, deskId)
            val splitPosition = splitScreenController.determineNewInstancePosition(callingTask)
            splitScreenController.startTask(
                requestedTaskId,
                splitPosition,
                options.toBundle(),
                /* hideTaskToken= */ null,
                if (enableFlexibleSplit())
                    splitScreenController.determineNewInstanceIndex(callingTask)
                else SPLIT_INDEX_UNDEFINED,
            )
        }
    }

    /** Create an Intent to open a new window of a task. */
    fun openNewWindow(callingTaskInfo: RunningTaskInfo) {
        // TODO(b/337915660): Add a transition handler for these; animations
        //  need updates in some cases.
        val userId = callingTaskInfo.userId
        val baseActivity = callingTaskInfo.baseActivity ?: return
        val userHandle = UserHandle.of(userId)
        val fillIn: Intent =
            userProfileContexts
                .getOrCreate(userId)
                .packageManager
                .getLaunchIntentForPackage(baseActivity.packageName) ?: return
        fillIn.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val launchIntent =
            PendingIntent.getActivityAsUser(
                context,
                /* requestCode= */ 0,
                fillIn,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null,
                userHandle,
            )
        val repository = userRepositories.getProfile(userId)
        val deskId =
            repository.getDeskIdForTask(callingTaskInfo.taskId)
                ?: getOrCreateDefaultDeskId(callingTaskInfo.displayId, userId)
                ?: return
        val options = createNewWindowOptions(callingTaskInfo, deskId)
        when (options.launchWindowingMode) {
            WINDOWING_MODE_MULTI_WINDOW -> {
                val wct = WindowContainerTransaction()
                wct.setWindowingMode(callingTaskInfo.token, WINDOWING_MODE_UNDEFINED)
                    .setBounds(callingTaskInfo.token, Rect())
                val splitPosition =
                    splitScreenController.determineNewInstancePosition(callingTaskInfo)
                // TODO(b/349828130) currently pass in index_undefined until we can revisit these
                //  specific cases in the future.
                val splitIndex =
                    if (enableFlexibleSplit())
                        splitScreenController.determineNewInstanceIndex(callingTaskInfo)
                    else SPLIT_INDEX_UNDEFINED
                splitScreenController.startIntent(
                    launchIntent,
                    context.userId,
                    fillIn,
                    splitPosition,
                    options.toBundle(),
                    /* hideTaskToken= */ null,
                    wct,
                    /* forceLaunchNewTask= */ true,
                    splitIndex,
                    if (ENABLE_NON_DEFAULT_DISPLAY_SPLIT.isTrue) callingTaskInfo.displayId
                    else DEFAULT_DISPLAY,
                )
            }
            WINDOWING_MODE_FREEFORM -> {
                val wct = WindowContainerTransaction()
                wct.sendPendingIntent(launchIntent, fillIn, options.toBundle())
                startLaunchTransition(
                    transitionType = TRANSIT_OPEN,
                    wct = wct,
                    launchingTaskId = null,
                    deskId = deskId,
                    displayId = callingTaskInfo.displayId,
                    userId = userId,
                )
            }
        }
    }

    private fun createNewWindowOptions(callingTask: RunningTaskInfo, deskId: Int): ActivityOptions {
        val repository = userRepositories.getProfile(callingTask.userId)
        val newTaskWindowingMode =
            when {
                callingTask.isFreeform -> {
                    WINDOWING_MODE_FREEFORM
                }
                callingTask.isFullscreen || callingTask.isMultiWindow -> {
                    WINDOWING_MODE_MULTI_WINDOW
                }
                else -> {
                    error("Invalid windowing mode: ${callingTask.windowingMode}")
                }
            }
        val bounds =
            when (newTaskWindowingMode) {
                WINDOWING_MODE_FREEFORM -> {
                    displayController.getDisplayLayout(callingTask.displayId)?.let {
                        getInitialBounds(it, callingTask, deskId)
                    }
                }
                WINDOWING_MODE_MULTI_WINDOW -> {
                    Rect()
                }
                else -> {
                    error("Invalid windowing mode: $newTaskWindowingMode")
                }
            }
        val displayId =
            if (ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) repository.getDisplayForDesk(deskId)
            else DEFAULT_DISPLAY
        return ActivityOptions.makeBasic().apply {
            launchWindowingMode = newTaskWindowingMode
            pendingIntentBackgroundActivityStartMode =
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            launchBounds = bounds
            launchDisplayId = displayId
        }
    }

    private fun handleLockTask(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        logV("handleLockTask taskId=%d", task.taskId)
        if (
            !DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX.isTrue() ||
                !task.isFreeform
        )
            return null

        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addMoveToFullscreenChanges(
                wct = wct,
                taskInfo = task,
                willExitDesktop =
                    willExitDesktop(
                        triggerTaskId = task.taskId,
                        displayId = task.displayId,
                        userId = task.userId,
                        forceExitDesktop = true,
                    ),
            )
        moveHomeTaskToTop(DEFAULT_DISPLAY, wct)
        wct.reorder(task.token, /* onTop= */ true)

        runOnTransitStart?.invoke(transition)
        return wct
    }

    private fun handleHomeTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        logV(
            "DesktopTasksController: handleHomeTaskLaunch taskId=%s userId=%s currentUserId=%d",
            task.taskId,
            task.userId,
            shellController.currentUserId,
        )
        // On user-switches, the home task is launched and the request is dispatched before the
        // user-switch is known by SysUI/Shell, so don't use the "current" repository.
        val repository = userRepositories.getProfile(task.userId)
        val activeDeskId = repository.getActiveDeskId(task.displayId) ?: return null
        val wct = WindowContainerTransaction()
        // TODO: b/393978539 - desktop-first displays may need to keep the desk active.
        // TODO: b/415381304 - pass in the correct |userId| to |performDesktopExitCleanUp| to
        //  ensure desk deactivation updates are applied to the right repository.
        val runOnTransitStart =
            performDesktopExitCleanUp(
                wct = wct,
                deskId = activeDeskId,
                displayId = task.displayId,
                userId = task.userId,
                willExitDesktop = true,
                removingLastTaskId = null,
                shouldEndUpAtHome = true,
                // No need to clean up the wallpaper / home order if Home is launching directly.
                skipWallpaperAndHomeOrdering = true,
                exitReason = ExitReason.RETURN_HOME_OR_OVERVIEW,
            )
        runOnTransitStart?.invoke(transition)
        return wct
    }

    /**
     * Handles the case where a freeform task is launched from recents.
     *
     * This is a special case where we want to launch the task in fullscreen instead of freeform.
     */
    private fun handleMidRecentsFreeformTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        logV("DesktopTasksController: handleMidRecentsFreeformTaskLaunch")
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addMoveToFullscreenChanges(
                wct = wct,
                taskInfo = task,
                willExitDesktop =
                    willExitDesktop(
                        triggerTaskId = task.taskId,
                        displayId = task.displayId,
                        userId = task.userId,
                        forceExitDesktop = true,
                    ),
            )
        runOnTransitStart?.invoke(transition)
        wct.reorder(task.token, true)
        return wct
    }

    private fun handleTaskLocationRequest(
        task: RunningTaskInfo,
        transition: IBinder,
        requestedDisplayId: Int,
        requestedTaskBounds: Rect,
    ): WindowContainerTransaction? {
        logV(
            "handleTaskLocationRequest taskId=%d requestedDisplayId=%d requestedTaskBounds=%s",
            task.taskId,
            requestedDisplayId,
            requestedTaskBounds,
        )

        val repository = userRepositories.getProfile(task.userId)
        val deskId = repository.getDeskIdForTask(task.taskId)
        if (
            deskId == null ||
                !repository.isDeskActive(deskId) ||
                !repository.isVisibleTask(task.taskId)
        ) {
            logV(
                "handleTaskLocationRequest taskId=%d is not visible in an active desk, dropping " +
                    "the request",
                task.taskId,
            )
            return null
        }

        return handleFreeformTaskPlacement(
            task = task,
            transition = transition,
            targetDisplayId = requestedDisplayId,
            requestedTaskBounds = requestedTaskBounds,
            requestType = TRANSIT_CHANGE,
            enterReason = EnterReason.APP_SELF_REPOSITION,
        )
    }

    private fun handleFreeformTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
        @WindowManager.TransitionType requestType: Int,
    ): WindowContainerTransaction? {
        logV("handleFreeformTaskLaunch taskId=%d displayId=%d", task.taskId, task.displayId)
        if (keyguardManager.isKeyguardLocked) {
            // Do NOT handle freeform task launch when locked.
            // It will be launched in fullscreen windowing mode (Details: b/160925539)
            logV("skip keyguard is locked")
            return null
        }
        return handleFreeformTaskPlacement(
            task = task,
            transition = transition,
            targetDisplayId = task.displayId,
            requestedTaskBounds = null,
            requestType = requestType,
            enterReason = EnterReason.TASK_LAUNCH,
        )
    }

    /**
     * Handle the placement of a freeform task that is either launching as freeform or moving to
     * freeform.
     *
     * @param task the task to place in freeform
     * @param transition the transition requesting this placement
     * @param targetDisplayId the display in which to place this task
     * @param suggestedTargetDeskId the desk in which to place this task if valid, otherwise place
     *   in a default desk
     * @param requestType the transition request type
     */
    fun handleFreeformTaskPlacement(
        task: RunningTaskInfo,
        transition: IBinder,
        targetDisplayId: Int,
        suggestedTargetDeskId: Int? = null,
        requestedTaskBounds: Rect?,
        @WindowManager.TransitionType requestType: Int,
        enterReason: EnterReason,
    ): WindowContainerTransaction? {
        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        val anyDeskActive = repository.isAnyDeskActive(targetDisplayId)
        val sourceDisplayId = task.displayId
        val sourceDeskId =
            repository.getDeskIdForTask(task.taskId)
                ?: if (enableAltTabKqsFlatenning.isTrue) {
                    repository.getActiveDeskId(sourceDisplayId)
                } else {
                    null
                }
        val targetDeskId =
            if (suggestedTargetDeskId in repository.getDeskIds(targetDisplayId)) {
                suggestedTargetDeskId
            } else {
                getOrCreateDefaultDeskId(displayId = targetDisplayId, userId = userId)
            }

        val isKnownDesktopTask = repository.isActiveTask(task.taskId)
        val bringTaskToFront = sourceDisplayId != targetDisplayId || requestedTaskBounds == null
        val shouldForceEnterDesktop =
            shouldForceEnterDesktopByDesktopFirstPolicy(task, requestType, targetDisplayId)

        logV(
            "handleFreeformTaskPlacement taskId=%d sourceDisplayId=%d targetDisplayId=%d" +
                " sourceDeskId=%d targetDeskId=%d anyDeskActive=%b isKnownDesktopTask=%b" +
                " bringTaskToFront=%b shouldForceEnterDesktop=%b requestedTaskBounds=%s" +
                " suggestedTargetDeskId=%d requestType=%s",
            task.taskId,
            sourceDisplayId,
            targetDisplayId,
            sourceDeskId,
            targetDeskId,
            anyDeskActive,
            isKnownDesktopTask,
            bringTaskToFront,
            shouldForceEnterDesktop,
            requestedTaskBounds,
            suggestedTargetDeskId,
            Transitions.transitTypeToString(requestType),
        )

        val placeAsDesktopTask =
            when {
                // If there is no desk on the target display, then it is not capable of handling
                // desktop tasks.
                targetDeskId == null -> false
                // If there is an active desk on the target display, then it is already in desktop
                // windowing so the new task should also be placed in desktop windowing.
                anyDeskActive -> true
                DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX.isTrue ||
                    DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_POLICY_IN_LPM.isTrue ->
                    // Here we have no desk activated, but check if we really want to force a task
                    // into desktop.
                    if (rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(targetDisplayId)) {
                        if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_POLICY_IN_LPM.isTrue) {
                            // Fully trust the LPM's decision under desktop-first mode.
                            true
                        } else {
                            // In desktop-first mode, we force to activate desk only when the
                            // desktop-first policy can be applied.
                            shouldForceEnterDesktop
                        }
                    } else {
                        // In touch-first mode, new tasks should be forced into desktop, while known
                        // desktop tasks should be moved outside of desktop.
                        !isKnownDesktopTask
                    }
                // If there is some desk on target display and it's been marked as a "desktop-first"
                // display, activate the desk and place the task in desktop windowing.
                shouldForceEnterDesktop -> true
                // New tasks should be forced into desktop, while known desktop tasks should be
                // moved outside of desktop.
                else -> !isKnownDesktopTask
            }

        val placeAsFullscreenTask = !placeAsDesktopTask

        val departFromCurrentDesk =
            when {
                // If the task is not a desktop task, then it could not have been on a desk before
                // the request.
                !isKnownDesktopTask -> false
                placeAsFullscreenTask -> true
                else -> sourceDeskId != targetDeskId
            }

        logV(
            "handleFreeformTaskPlacement taskId=%d placeAsFullscreenTask=%b placeAsDesktopTask=%b" +
                " departFromCurrentDesk=%b",
            task.taskId,
            placeAsFullscreenTask,
            placeAsDesktopTask,
            departFromCurrentDesk,
        )

        if (!placeAsFullscreenTask && !placeAsDesktopTask) {
            logE(
                "handleFreeformTaskPlacement taskId=%d no available destination to handle the" +
                    " request, bailing out",
                task.taskId,
            )
            return null
        }

        // The overall departure and arrival strategy is decided at this point so we can start
        // constructing the |WindowContainerTransaction|.
        val wct = WindowContainerTransaction()

        if (departFromCurrentDesk) {
            // If the task in question is the last task of its original desk, then we may need to
            // clean up after this desk.
            val isLastTask =
                sourceDeskId?.let { repository.isOnlyTaskInDesk(task.taskId, it) } ?: false
            performDesktopExitCleanupIfNeeded(
                    taskId = task.taskId,
                    deskId = sourceDeskId,
                    displayId = sourceDisplayId,
                    userId = userId,
                    wct = wct,
                    removingLastTaskId = if (isLastTask) task.taskId else null,
                    forceToFullscreen = false,
                    exitReason = ExitReason.TASK_MOVED_FROM_DESK,
                )
                ?.invoke(transition)
        }

        if (placeAsFullscreenTask) {
            // Even though we may want to exit desktop mode on the source display throughout
            // execution of this method, we don't want [addMoveToFullscreenChanges] to clean up the
            // desk for us, hence |willExitDesktop| parameter is |false|.
            addMoveToFullscreenChanges(wct, task, willExitDesktop = false, targetDisplayId)
            return wct
        }

        // From now on we can assume that we are placing the task on some desk.
        checkNotNull(targetDeskId) {
            "handleFreeformTaskPlacement decided to place task in desktop mode but the target" +
                " desk ID is null."
        }
        if (
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                sourceDeskId != targetDeskId
        ) {
            desksOrganizer.moveTaskToDesk(wct, targetDeskId, task)
        }

        if (!anyDeskActive) {
            val runOnTransitStart =
                addDeskActivationChanges(
                    deskId = targetDeskId,
                    wct = wct,
                    newTask = task,
                    userId = userId,
                    enterReason = enterReason,
                )
            runOnTransitStart?.invoke(transition)
            wct.reorder(task.token, true)
        }

        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(task.token, desktopConfig.desktopDensityOverride)
        }

        val bounds =
            decideDesktopTaskPlacementBounds(
                context,
                recentTasksController,
                repository,
                shellTaskOrganizer,
                displayController,
                task,
                targetDisplayId,
                targetDeskId,
                requestedTaskBounds,
            )
        if (bounds != null) {
            logV("handleFreeformTaskPlacement decided placement bounds to be %s", bounds)
            wct.setBounds(task.token, bounds)
        }

        if (bringTaskToFront) {
            // The task that is being focused might have been minimized before - in which case this
            // is an unminimize action.
            if (repository.isMinimizedTask(task.taskId)) {
                addPendingUnminimizeTransition(
                    transition,
                    targetDisplayId,
                    task.taskId,
                    UnminimizeReason.TASK_LAUNCH,
                )
            }
            desktopImmersiveController.exitImmersiveIfApplicable(
                transition = transition,
                wct = wct,
                displayId = targetDisplayId,
                reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
            )
            // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition
            // flag
            val taskIdToMinimize = addAndGetMinimizeChanges(targetDeskId, wct, task.taskId)
            val closingTopTransparentTaskId =
                closeTopTransparentFullscreenTask(
                    wct,
                    targetDeskId,
                    launchingTaskId = task.taskId,
                    userId,
                )
            if (enterReason == EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN) {
                addPendingClientExitFullscreenToDesktopTransition(
                    transition = transition,
                    taskId = task.taskId,
                    minimizeTaskId = taskIdToMinimize,
                    closingTopTransparentTaskId = closingTopTransparentTaskId,
                )
            } else {
                addPendingAppLaunchTransition(
                    transition = transition,
                    launchTaskId = task.taskId,
                    minimizeTaskId = taskIdToMinimize,
                    closingTopTransparentTaskId = closingTopTransparentTaskId,
                )
            }
            if (taskIdToMinimize != null) {
                addPendingMinimizeTransition(
                    transition,
                    taskIdToMinimize,
                    MinimizeReason.TASK_LIMIT,
                )
                snapEventHandler.removeTaskIfTiled(targetDisplayId, taskIdToMinimize)
                return wct
            }
            addPendingTaskLimitTransition(transition, targetDeskId, task.taskId)
        }

        if (!wct.isEmpty) {
            return wct
        }
        return null
    }

    private fun handleFullscreenTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
        @WindowManager.TransitionType requestType: Int,
    ): WindowContainerTransaction? {
        logV("handleFullscreenTaskLaunch")
        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        if (shouldFullscreenTaskLaunchSwitchToDesktop(task, requestType)) {
            logD("Switch fullscreen task to freeform on transition: taskId=%d", task.taskId)
            return WindowContainerTransaction().also { wct ->
                val deskId = getOrCreateDefaultDeskId(task.displayId, userId) ?: return@also
                addMoveToDeskTaskChanges(wct = wct, task = task, deskId = deskId)
                val runOnTransitStart: RunOnTransitStart? =
                    if (
                        task.baseIntent.flags.and(Intent.FLAG_ACTIVITY_TASK_ON_HOME) != 0 ||
                            !repository.isAnyDeskActive(task.displayId)
                    ) {
                        // In some launches home task is moved behind new task being launched. Make
                        // sure that's not the case for launches in desktop. Also, if this launch is
                        // the first one to trigger the desktop mode (e.g., when
                        // [shouldForceEnterDesktopByDesktopFirstPolicy()]), activate the desk here.
                        val activationRunnable =
                            addDeskActivationChanges(
                                deskId = deskId,
                                wct = wct,
                                newTask = task,
                                addPendingLaunchTransition = true,
                                userId = userId,
                                enterReason = EnterReason.APP_FREEFORM_INTENT,
                            )
                        wct.reorder(task.token, true)
                        activationRunnable
                    } else {
                        { transition: IBinder ->
                            // The desk was already showing and we're launching a new Task - we
                            // might need to minimize another Task.
                            // TODO: b/32994943 - remove dead code when cleaning up
                            //  task_limit_separate_transition flag
                            val taskIdToMinimize =
                                addAndGetMinimizeChanges(deskId, wct, task.taskId)
                            taskIdToMinimize?.let { minimizingTaskId ->
                                addPendingMinimizeTransition(
                                    transition,
                                    minimizingTaskId,
                                    MinimizeReason.TASK_LIMIT,
                                )
                            }
                            addPendingTaskLimitTransition(transition, deskId, task.taskId)
                            // Remove top transparent fullscreen task if needed.
                            val closingTopTransparentTaskId =
                                closeTopTransparentFullscreenTask(
                                    wct,
                                    deskId,
                                    launchingTaskId = task.taskId,
                                    userId,
                                )
                            // Also track the pending launching task.
                            addPendingAppLaunchTransition(
                                transition,
                                task.taskId,
                                taskIdToMinimize,
                                closingTopTransparentTaskId,
                            )
                        }
                    }
                runOnTransitStart?.invoke(transition)
                desktopImmersiveController.exitImmersiveIfApplicable(
                    transition,
                    wct,
                    task.displayId,
                    reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
                )
            }
        } else if (repository.isActiveTask(task.taskId)) {
            // If a freeform task receives a request for a fullscreen launch, apply the same
            // changes we do for similar transitions. The task not having WINDOWING_MODE_UNDEFINED
            // set when needed can interfere with future split / multi-instance transitions.
            val wct = WindowContainerTransaction()
            val runOnTransitStart =
                addMoveToFullscreenChanges(
                    wct = wct,
                    taskInfo = task,
                    willExitDesktop =
                        willExitDesktop(
                            triggerTaskId = task.taskId,
                            displayId = task.displayId,
                            userId = userId,
                            forceExitDesktop = true,
                        ),
                )
            runOnTransitStart?.invoke(transition)
            return wct
        }
        return null
    }

    private fun shouldFreeformTaskLaunchSwitchToFullscreen(task: RunningTaskInfo): Boolean =
        !isAnyDeskActive(task.displayId, task.userId)

    private fun shouldFullscreenTaskLaunchSwitchToDesktop(
        task: RunningTaskInfo,
        @WindowManager.TransitionType requestType: Int,
    ): Boolean {
        if (isDesktopFirstLegacy(task.displayId)) {
            // We're in desktop-first mode.
            val forceEnterDesktop = shouldForceEnterDesktopByDesktopFirstPolicy(task, requestType)
            logV(
                "shouldFullscreenTaskLaunchSwitchToDesktop, forceEnterDesktop=%s",
                forceEnterDesktop,
            )
            return forceEnterDesktop
        }

        // We're in touch-first mode.
        val isAnyDeskActive = isAnyDeskActive(task.displayId, task.userId)
        logV("shouldFullscreenTaskLaunchSwitchToDesktop, isAnyDeskActive=%s", isAnyDeskActive)
        return isAnyDeskActive
    }

    /**
     * Returns `true` if `openingTask` should enter desktop mode because of desktop-first policy.
     * Note that this may return `false` even if the display is in desktop-first mode. To check the
     * state of the desktop-first mode use `isDisplayDesktopFirst()` instead.
     *
     * It is possible to explicitly specify the target display on which the task is to be placed.
     * Consequently the desktop-first policies will be checked on the display provided. By default
     * the target display is `openingTask.displayId`.
     */
    private fun shouldForceEnterDesktopByDesktopFirstPolicy(
        openingTask: RunningTaskInfo,
        @WindowManager.TransitionType requestType: Int,
        targetDisplayId: Int = openingTask.displayId,
    ): Boolean {
        if (!DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX.isTrue) {
            return isDesktopFirstLegacy(targetDisplayId)
        }

        val repository = userRepositories.getProfile(openingTask.userId)
        val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(targetDisplayId)
        if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_POLICY_IN_LPM.isTrue && isDesktopFirst) {
            // The desktop-first policy has to be considered already in
            // `DesktopModeLaunchParamsModifier`.
            return false
        }

        if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX.isTrue) {
            val anyDeskActive = repository.isAnyDeskActive(targetDisplayId)
            val focusedTask =
                shellTaskOrganizer.getRunningTaskInfo(
                    focusTransitionObserver.getFocusedTaskIdOnDisplay(targetDisplayId)
                )
            val isFullscreenFocused = focusedTask?.isFullscreen == true
            val isNonHomeFocused = focusedTask?.activityType != ACTIVITY_TYPE_HOME
            logV(
                "shouldForceEnterDesktopByDesktopFirstPolicy: anyDeskActive=%s " +
                    "isFullscreenFocused=%s isNonHomeFocused=%s",
                anyDeskActive,
                isFullscreenFocused,
                isNonHomeFocused,
            )
            if (isDesktopFirst && !anyDeskActive && isFullscreenFocused && isNonHomeFocused) {
                logV(
                    "shouldForceEnterDesktopByDesktopFirstPolicy: no switch as the other " +
                        "fullscreen task is focused on desktop-first display#%s",
                    targetDisplayId,
                )
                return false
            }
        }

        if (
            DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX.isTrue &&
                isDesktopFirst &&
                isFullscreenRelaunch(openingTask, requestType)
        ) {
            logV(
                "shouldForceEnterDesktopByDesktopFirstPolicy: no switch as fullscreen relaunch on" +
                    " desktop-first display#%s",
                targetDisplayId,
            )
            return false
        }
        return isDesktopFirst
    }

    /**
     * If a task is not compatible with desktop mode freeform, it should always be launched in
     * fullscreen.
     */
    private fun handleIncompatibleTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        val taskId = task.taskId
        val displayId = task.displayId
        val userId = task.userId
        val repository = userRepositories.getProfile(userId)
        val inDesktop = repository.isAnyDeskActive(displayId)
        val isTransparentTask = desktopModeCompatPolicy.isTransparentTask(task)
        val isFreeform = task.isFreeform
        logV(
            "handleIncompatibleTaskLaunch taskId=%d displayId=%d isTransparent=%b inDesktop=%b" +
                " isFreeform=%b",
            taskId,
            displayId,
            isTransparentTask,
            inDesktop,
            isFreeform,
        )
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            if (!inDesktop && !isDesktopFirstLegacy(displayId)) return null
            if (
                isTransparentTask &&
                    (DesktopExperienceFlags.FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK.isTrue ||
                        DesktopModeFlags
                            .INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC
                            .isTrue)
            ) {
                // Only update task repository for transparent task.
                val deskId = repository.getActiveDeskId(displayId)
                deskId?.let { repository.setTopTransparentFullscreenTaskData(it, task) }
            }
            // Already fullscreen, no-op.
            if (task.isFullscreen) return null
            val wct = WindowContainerTransaction()
            val runOnTransitStart =
                addMoveToFullscreenChanges(
                    wct = wct,
                    taskInfo = task,
                    willExitDesktop =
                        willExitDesktop(
                            triggerTaskId = taskId,
                            displayId = displayId,
                            userId = userId,
                            forceExitDesktop = true,
                        ),
                )
            runOnTransitStart?.invoke(transition)
            return wct
        }
        if (!inDesktop && !isFreeform) {
            logD("handleIncompatibleTaskLaunch not in desktop, not a freeform task, nothing to do")
            return null
        }
        if (
            isTransparentTask &&
                (DesktopExperienceFlags.FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK.isTrue ||
                    DesktopModeFlags.INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC
                        .isTrue)
        ) {
            // Only update task repository for transparent task.
            val deskId = repository.getActiveDeskId(displayId)
            deskId?.let { repository.setTopTransparentFullscreenTaskData(it, task) }
        }
        // Both opaque and transparent incompatible tasks need to be forced to fullscreen, but
        // opaque ones force-exit the desktop while transparent ones are just shown on top of the
        // desktop while keeping it active.
        val willExitDesktop = inDesktop && !isTransparentTask
        if (willExitDesktop) {
            logD("handleIncompatibleTaskLaunch forcing task to fullscreen and exiting desktop")
        } else {
            logD("handleIncompatibleTaskLaunch forcing task to fullscreen and staying in desktop")
        }
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addMoveToFullscreenChanges(
                wct = wct,
                taskInfo = task,
                willExitDesktop = willExitDesktop,
            )
        runOnTransitStart?.invoke(transition)
        return wct
    }

    /**
     * Handles a closing task. This usually means deactivating and cleaning up the desk if it was
     * the last task in it. It also handles to-back transitions of the last desktop task as a
     * minimize operation.
     */
    private fun handleTaskClosing(
        task: RunningTaskInfo,
        transition: IBinder,
        @WindowManager.TransitionType requestType: Int,
    ): WindowContainerTransaction? {
        logV(
            "handleTaskClosing taskId=%d closingType=%s",
            task.taskId,
            transitTypeToString(requestType),
        )
        val repository = userRepositories.getProfile(task.userId)
        if (!repository.isAnyDeskActive(task.displayId)) return null
        val deskId = repository.getDeskIdForTask(task.taskId)
        if (deskId == null && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            return null
        }
        val wct = WindowContainerTransaction()
        val isLastTask = repository.isOnlyTaskInDesk(task.taskId, task.displayId)
        if (
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                requestType == TRANSIT_TO_BACK
        ) {
            logV(
                "Handling to-back of taskId=%d (isLast=%b) as minimize in deskId=%d",
                task.taskId,
                isLastTask,
                deskId,
            )
            desksOrganizer.minimizeTask(
                wct = wct,
                deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                task = task,
            )
        }

        // TODO(b/416014060): Check if task is really receiving a back gesture
        if (
            !(DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                DesktopExperienceFlags.ENABLE_EMPTY_DESK_ON_MINIMIZE.isTrue)
        ) {
            val deactivationRunnable =
                performDesktopExitCleanupIfNeeded(
                    taskId = task.taskId,
                    deskId = deskId,
                    displayId = task.displayId,
                    userId = task.userId,
                    wct = wct,
                    removingLastTaskId = if (isLastTask) task.taskId else null,
                    forceToFullscreen = false,
                    exitReason = ExitReason.TASK_FINISHED,
                )
            deactivationRunnable?.invoke(transition)
        }

        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) {
            repository.addClosingTask(
                displayId = task.displayId,
                deskId = deskId,
                taskId = task.taskId,
            )
            snapEventHandler.removeTaskIfTiled(task.displayId, task.taskId)
        }

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(
                displayId = task.displayId,
                userId = task.userId,
                excludeTaskId = task.taskId,
            )
        )
        return if (wct.isEmpty) null else wct
    }

    /**
     * Applies the [wct] changes need when a task is first moving to a desk and the desk needs to be
     * activated.
     */
    private fun addDeskActivationWithMovingTaskChanges(
        deskId: Int,
        wct: WindowContainerTransaction,
        task: RunningTaskInfo,
        transitionSource: DesktopModeTransitionSource,
    ): RunOnTransitStart? {
        val runOnTransitStart =
            addDeskActivationChanges(
                deskId = deskId,
                wct = wct,
                newTask = task,
                userId = task.userId,
                enterReason = transitionSource.getEnterReason(),
            )
        addMoveToDeskTaskChanges(wct = wct, task = task, deskId = deskId)
        return runOnTransitStart
    }

    /**
     * Applies the [wct] changes needed when a task is first moving to a desk.
     *
     * Note that this recalculates the initial bounds of the task, so it should not be used when
     * transferring a task between desks.
     *
     * TODO: b/362720497 - this should be improved to be reusable by desk-to-desk CUJs where
     *   [DesksOrganizer.moveTaskToDesk] needs to be called and even cross-display CUJs where
     *   [applyFreeformDisplayChange] needs to be called. Potentially by comparing source vs
     *   destination desk ids and display ids, or adding extra arguments to the function.
     */
    fun addMoveToDeskTaskChanges(
        wct: WindowContainerTransaction,
        task: RunningTaskInfo,
        deskId: Int,
    ) {
        val repository = userRepositories.getProfile(task.userId)
        val targetDisplayId = repository.getDisplayForDesk(deskId)
        val displayLayout = displayController.getDisplayLayout(targetDisplayId) ?: return
        logV(
            "addMoveToDeskTaskChanges taskId=%d deskId=%d displayId=%d",
            task.taskId,
            deskId,
            targetDisplayId,
        )
        val inheritedTaskBounds =
            getInheritedExistingTaskBounds(repository, shellTaskOrganizer, task, deskId)
        if (inheritedTaskBounds != null) {
            // Inherit bounds from closing task instance to prevent application jumping different
            // cascading positions.
            wct.setBounds(task.token, inheritedTaskBounds)
        } else {
            val initialBounds = getInitialBounds(displayLayout, task, deskId)
            wct.setBounds(task.token, initialBounds)
        }
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desksOrganizer.moveTaskToDesk(wct = wct, deskId = deskId, task = task)
        } else {
            val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(targetDisplayId)!!
            val tdaWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
            val targetWindowingMode =
                if (tdaWindowingMode == WINDOWING_MODE_FREEFORM) {
                    // Display windowing is freeform, set to undefined and inherit it
                    WINDOWING_MODE_UNDEFINED
                } else {
                    WINDOWING_MODE_FREEFORM
                }
            wct.setWindowingMode(task.token, targetWindowingMode)
            wct.reorder(task.token, /* onTop= */ true)
        }
        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(task.token, desktopConfig.desktopDensityOverride)
        }
    }

    /**
     * Apply changes to move a freeform task on display or it's setting changing, which includes
     * handling density changes between displays or on the same display.
     */
    private fun applyFreeformDisplayChange(
        wct: WindowContainerTransaction,
        taskInfo: RunningTaskInfo,
        destLayout: DisplayLayout?,
        sourceLayout: DisplayLayout?,
        taskTilingState: DesktopTaskTilingState,
        deskId: Int,
        updatedDividerBounds: Rect? = null,
    ) {
        if (sourceLayout == null || destLayout == null) return
        val bounds = taskInfo.configuration.windowConfiguration.bounds
        val newToOldDpiRatio =
            destLayout.densityDpi().toDouble() / sourceLayout.densityDpi().toDouble()
        val scaledWidth = bounds.width() * newToOldDpiRatio
        val scaledHeight = bounds.height() * newToOldDpiRatio
        val sourceWidthMargin = sourceLayout.width() - bounds.width()
        val sourceHeightMargin = sourceLayout.height() - bounds.height()
        val destWidthMargin = destLayout.width() - scaledWidth
        val destHeightMargin = destLayout.height() - scaledHeight
        val scaledLeft =
            if (sourceWidthMargin != 0) {
                bounds.left * destWidthMargin / sourceWidthMargin
            } else {
                destWidthMargin / 2
            }
        val scaledTop =
            if (sourceHeightMargin != 0) {
                bounds.top * destHeightMargin / sourceHeightMargin
            } else {
                destHeightMargin / 2
            }
        val sourceStableBounds = Rect()
        val destStableBounds = Rect()
        sourceLayout.getStableBounds(sourceStableBounds)
        destLayout.getStableBounds(destStableBounds)
        val boundsWithinDisplay =
            if (taskTilingState == DesktopTaskTilingState.LEFT) {
                val dividerBounds =
                    updatedDividerBounds ?: snapEventHandler.getDividerBounds(deskId)
                Rect(
                    destStableBounds.left,
                    destStableBounds.top,
                    dividerBounds.left,
                    destStableBounds.bottom,
                )
            } else if (taskTilingState == DesktopTaskTilingState.RIGHT) {
                val dividerBounds =
                    updatedDividerBounds ?: snapEventHandler.getDividerBounds(deskId)
                Rect(
                    dividerBounds.right,
                    destStableBounds.top,
                    destStableBounds.right,
                    destStableBounds.bottom,
                )
            } else if (
                destWidthMargin >= 0 &&
                    destHeightMargin >= 0 &&
                    (bounds.width() < sourceStableBounds.width() ||
                        bounds.height() < sourceStableBounds.height())
            ) {
                Rect(0, 0, scaledWidth.toInt(), scaledHeight.toInt()).apply {
                    offsetTo(
                        scaledLeft.coerceIn(0.0, destWidthMargin).toInt(),
                        scaledTop.coerceIn(0.0, destHeightMargin).toInt(),
                    )
                }
            } else {
                calculateMaximizeBounds(destLayout, taskInfo)
            }
        wct.setBounds(taskInfo.token, boundsWithinDisplay)
    }

    private fun getInitialBounds(
        displayLayout: DisplayLayout,
        taskInfo: TaskInfo,
        deskId: Int,
    ): Rect {
        val repository = userRepositories.getProfile(taskInfo.userId)
        val bounds =
            if (ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS.isTrue) {
                // If caption insets should be excluded from app bounds, ensure caption insets
                // are excluded from the ideal initial bounds when scaling non-resizeable apps.
                // Caption insets stay fixed and don't scale with bounds.
                val displayId = repository.getDisplayForDesk(deskId)
                val displayContext = displayController.getDisplayContext(displayId) ?: context
                val captionInsets =
                    if (desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(taskInfo)) {
                        getDesktopViewAppHeaderHeightPx(displayContext)
                    } else {
                        0
                    }
                calculateInitialBounds(displayLayout, taskInfo, captionInsets = captionInsets)
            } else {
                calculateDefaultDesktopTaskBounds(displayLayout)
            }
        var hasLayoutGravityApplied = false
        if (!repository.isActiveTask(taskInfo.taskId)) {
            // Only apply layout gravity to new tasks in desk.
            val stableBounds = Rect()
            displayLayout.getStableBoundsForDesktopMode(stableBounds)
            hasLayoutGravityApplied = applyLayoutGravityIfNeeded(taskInfo, bounds, stableBounds)
        }
        if (DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue && !hasLayoutGravityApplied) {
            cascadeWindow(
                context,
                recentTasksController,
                repository,
                shellTaskOrganizer,
                bounds,
                displayLayout,
                deskId,
            )
        }
        return bounds
    }

    /**
     * Applies the changes needed to enter fullscreen and clean up the desktop if needed.
     *
     * @param destinationDisplayId the destination display id for the task, which may be different
     *   from the task info's displayId when the task is also being moved across displays or when
     *   the task is a recents (non-running) task that was previously in [destinationDisplayId] but
     *   its info reports INVALID_DISPLAY.
     */
    fun addMoveToFullscreenChanges(
        wct: WindowContainerTransaction,
        taskInfo: TaskInfo,
        willExitDesktop: Boolean,
        destinationDisplayId: Int = taskInfo.displayId,
        skipSetWindowingMode: Boolean = false,
        exitReason: ExitReason = ExitReason.FULLSCREEN_LAUNCH,
    ): RunOnTransitStart? {
        val sourceDisplayId =
            if (taskInfo.displayId != INVALID_DISPLAY) {
                taskInfo.displayId
            } else {
                // This is possible when the task is a recent (non-running) task.
                destinationDisplayId
            }
        logV(
            "addMoveToFullscreenChanges taskId=%d sourceDisplayId=%d destinationDisplayId=%d " +
                "willExitDesktop=%b",
            taskInfo.taskId,
            sourceDisplayId,
            destinationDisplayId,
            willExitDesktop,
        )
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(destinationDisplayId)!!
        if (!skipSetWindowingMode) {
            val tdaWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
            val targetWindowingMode =
                if (tdaWindowingMode == WINDOWING_MODE_FULLSCREEN) {
                    // Display windowing is fullscreen, set to undefined and inherit it
                    WINDOWING_MODE_UNDEFINED
                } else {
                    WINDOWING_MODE_FULLSCREEN
                }
            wct.setWindowingMode(taskInfo.token, targetWindowingMode)
        }
        wct.setBounds(taskInfo.token, Rect())

        if (Flags.appCompatRefactoringSetAppboundsToNullWhenEmpty()) {
            wct.setAppBounds(taskInfo.token, null)
        }

        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(taskInfo.token, getDefaultDensityDpi())
        }
        if (
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                taskInfo.displayId != destinationDisplayId
        ) {
            wct.reparent(taskInfo.token, tdaInfo.token, /* onTop= */ true)
        } else if (enableAltTabKqsFlatenning.isTrue) {
            // Until multiple desktops is enabled, we still want to reorder the task to top so that
            // if the task is not on top we can still switch to it using Alt+Tab.
            wct.reorder(taskInfo.token, /* onTop= */ true)
        }
        val userId = taskInfo.userId
        val repository = userRepositories.getProfile(userId)
        val sourceDeskId =
            // If the task moving to fullscreen was in a desk, then that's the desk to deactivate.
            repository.getDeskIdForTask(taskInfo.taskId)
                ?: if (enableAltTabKqsFlatenning.isTrue) {
                    // The task we're moving to might not be in a desk (for example, we might be
                    // refocusing an already fullscreen task with Alt+Tab), so check if there's a
                    // desk active and deactivate that one.
                    repository.getActiveDeskId(destinationDisplayId)
                } else {
                    null
                }
        val deskCleanUpRunOnTransit =
            if (willExitDesktop) {
                val cleanupDisplayId =
                    // A desk is getting deactivated, so use that desk's display id.
                    sourceDeskId?.let { repository.getDisplayForDesk(it) }
                        ?: if (
                            DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE
                                .isTrue
                        ) {
                            // Use the source display ID for clean up when the bug fix flag is
                            // enabled.
                            sourceDisplayId
                        } else {
                            // Before the bug fix, display move is not considered.
                            destinationDisplayId
                        }
                val isLastTask =
                    sourceDeskId?.let { repository.isOnlyTaskInDesk(taskInfo.taskId, it) } ?: false
                performDesktopExitCleanUp(
                    wct = wct,
                    deskId = sourceDeskId,
                    displayId = cleanupDisplayId,
                    userId = userId,
                    willExitDesktop = true,
                    removingLastTaskId = if (isLastTask) taskInfo.taskId else null,
                    shouldEndUpAtHome =
                        if (
                            DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE
                                .isTrue
                        ) {
                            // If the last task is moved from the display, it should go to home.
                            destinationDisplayId != taskInfo.displayId
                        } else {
                            // Before the bug fix, display move is not considered.
                            false
                        },
                    exitReason = exitReason,
                )
            } else {
                null
            }
        return { transition ->
            deskCleanUpRunOnTransit?.invoke(transition)
            if (exitReason == ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN) {
                addPendingClientEnterFullscreenFromDesktopTransition(
                    transition = transition,
                    taskId = taskInfo.taskId,
                )
            }
        }
    }

    /**
     * Adds split screen changes to a transaction. Note that bounds are not reset here due to
     * animation; see {@link onDesktopSplitSelectChoice}
     */
    private fun addMoveToSplitChanges(wct: WindowContainerTransaction, taskInfo: RunningTaskInfo) {
        if (!DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue) {
            // This windowing mode is to get the transition animation started; once we complete
            // split select, we will change windowing mode to undefined and inherit from split
            // stage.
            // Going to undefined here causes task to flicker to the top left.
            // Cancelling the split select flow will revert it to fullscreen.
            wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_MULTI_WINDOW)
        }
        // The task's density may have been overridden in freeform; revert it here as we don't
        // want it overridden in multi-window.
        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(taskInfo.token, getDefaultDensityDpi())
        }
    }

    /** Returns the ID of the Task that will be minimized, or null if no task will be minimized. */
    private fun addAndGetMinimizeChanges(
        deskId: Int,
        wct: WindowContainerTransaction,
        newTaskId: Int?,
        launchingNewIntent: Boolean = false,
    ): Int? {
        if (DesktopExperienceFlags.ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION.isTrue) return null
        val limiter = desktopTasksLimiter.getOrNull() ?: return null
        require(newTaskId == null || !launchingNewIntent)
        return limiter.addAndGetMinimizeTaskChanges(deskId, wct, newTaskId, launchingNewIntent)
    }

    private fun getTaskIdToMinimize(
        expandedTasksOrderedFrontToBack: List<Int>,
        newTaskIdInFront: Int?,
        isDeskSwitch: Boolean = false,
    ): Int? {
        // If it's a desk switch, include the minimized task in the same transition. So when the
        // user switches the desks, the new desk has the correct task minimized already.
        if (
            DesktopExperienceFlags.ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION.isTrue &&
                !isDeskSwitch
        )
            return null
        val limiter = desktopTasksLimiter.getOrNull() ?: return null
        return limiter.getTaskIdToMinimize(expandedTasksOrderedFrontToBack, newTaskIdInFront)
    }

    private fun addPendingMinimizeTransition(
        transition: IBinder,
        taskIdToMinimize: Int,
        minimizeReason: MinimizeReason,
    ) {
        val taskToMinimize = shellTaskOrganizer.getRunningTaskInfo(taskIdToMinimize)
        desktopTasksLimiter.ifPresent {
            it.addPendingMinimizeChange(
                transition = transition,
                displayId = taskToMinimize?.displayId ?: DEFAULT_DISPLAY,
                taskId = taskIdToMinimize,
                minimizeReason = minimizeReason,
            )
        }
    }

    private fun addPendingTaskLimitTransition(
        transition: IBinder,
        deskId: Int,
        launchTaskId: Int?,
    ) {
        if (!DesktopExperienceFlags.ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION.isTrue) return
        desktopTasksLimiter.ifPresent {
            it.addPendingTaskLimitTransition(
                transition = transition,
                deskId = deskId,
                taskId = launchTaskId,
            )
        }
    }

    private fun addPendingUnminimizeTransition(
        transition: IBinder,
        displayId: Int,
        taskIdToUnminimize: Int,
        unminimizeReason: UnminimizeReason,
    ) {
        desktopTasksLimiter.ifPresent {
            it.addPendingUnminimizeChange(
                transition,
                displayId = displayId,
                taskId = taskIdToUnminimize,
                unminimizeReason,
            )
        }
    }

    private fun addPendingAppLaunchTransition(
        transition: IBinder,
        launchTaskId: Int,
        minimizeTaskId: Int?,
        closingTopTransparentTaskId: Int?,
    ) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX.isTrue) {
            return
        }
        // TODO: b/446995362 - pass immersive task here?
        desktopMixedTransitionHandler.addPendingMixedTransition(
            DesktopMixedTransitionHandler.PendingMixedTransition.Launch(
                transition,
                launchTaskId,
                minimizeTaskId,
                closingTopTransparentTaskId,
                /* exitingImmersiveTask= */ null,
            )
        )
    }

    private fun addPendingClientEnterFullscreenFromDesktopTransition(
        transition: IBinder,
        taskId: Int,
    ) {
        // TODO: b/446995362 - pass immersive and/or top transparent task here?
        desktopMixedTransitionHandler.addPendingMixedTransition(
            PendingMixedTransition.ClientEnterFullscreenFromDesktop(
                transition = transition,
                fromDesktopTask = taskId,
            )
        )
    }

    private fun addPendingClientExitFullscreenToDesktopTransition(
        transition: IBinder,
        taskId: Int,
        minimizeTaskId: Int?,
        closingTopTransparentTaskId: Int?,
    ) {
        // TODO: b/446995362 - pass immersive task here?
        desktopMixedTransitionHandler.addPendingMixedTransition(
            PendingMixedTransition.ClientExitFullscreenToDesktop(
                transition,
                taskId,
                minimizeTaskId,
                closingTopTransparentTaskId,
            )
        )
    }

    private fun activateDefaultDeskInDisplay(
        displayId: Int,
        userId: Int,
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
        transitionSource: DesktopModeTransitionSource,
    ) {
        val deskId = getOrCreateDefaultDeskId(displayId, userId) ?: return
        activateDesk(
            deskId = deskId,
            userId = userId,
            remoteTransition = remoteTransition,
            taskIdToReorderToFront = taskIdToReorderToFront,
            enterReason = transitionSource.getEnterReason(),
        )
    }

    /**
     * Applies the necessary [wct] changes to activate the given desk.
     *
     * When a task is being brought into a desk together with the activation, then [newTask] is not
     * null and may be used to run other desktop policies, such as minimizing another task if the
     * task limit has been exceeded.
     */
    fun addDeskActivationChanges(
        deskId: Int,
        wct: WindowContainerTransaction,
        newTask: TaskInfo? = null,
        // TODO: b/362720497 - should this be true in other places? Can it be calculated locally
        //  without having to specify the value?
        addPendingLaunchTransition: Boolean = false,
        userId: Int,
        switchingUser: Boolean = false,
        displayId: Int = userRepositories.getProfile(userId).getDisplayForDesk(deskId),
        enterReason: EnterReason,
        isDeskSwitch: Boolean = false,
    ): RunOnTransitStart {
        val repository = userRepositories.getProfile(userId)
        val newTaskIdInFront = newTask?.taskId
        logV(
            "addDeskActivationChanges newTaskId=%d deskId=%d displayId=%d userId=%d",
            newTask?.taskId,
            deskId,
            displayId,
            userId,
        )
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            val taskIdToMinimize = bringDesktopAppsToFront(displayId, userId, wct, newTask?.taskId)
            return { transition ->
                // TODO: b/32994943 - remove dead code when cleaning up
                //  task_limit_separate_transition flag
                taskIdToMinimize?.let { minimizingTaskId ->
                    addPendingMinimizeTransition(
                        transition = transition,
                        taskIdToMinimize = minimizingTaskId,
                        minimizeReason = MinimizeReason.TASK_LIMIT,
                    )
                }
                addPendingTaskLimitTransition(
                    transition = transition,
                    deskId = deskId,
                    launchTaskId = newTask?.taskId,
                )
                if (newTask != null && addPendingLaunchTransition) {
                    // Remove top transparent fullscreen task if needed.
                    val closingTopTransparentTaskId =
                        closeTopTransparentFullscreenTask(
                            wct,
                            deskId,
                            launchingTaskId = newTask.taskId,
                            userId,
                        )
                    addPendingAppLaunchTransition(
                        transition,
                        newTask.taskId,
                        taskIdToMinimize,
                        closingTopTransparentTaskId,
                    )
                }
            }
        }
        prepareForDeskActivation(displayId, wct)
        desksOrganizer.activateDesk(wct, deskId)
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(displayId = displayId, userId = userId)
        )
        val expandedTasksOrderedFrontToBack =
            repository.getExpandedTasksIdsInDeskOrdered(deskId = deskId)
        // If we're adding a new Task we might need to minimize an old one
        // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition flag
        val taskIdToMinimize =
            getTaskIdToMinimize(expandedTasksOrderedFrontToBack, newTaskIdInFront, isDeskSwitch)
        if (taskIdToMinimize != null) {
            val taskToMinimize = shellTaskOrganizer.getRunningTaskInfo(taskIdToMinimize)
            // TODO(b/365725441): Handle non running task minimization
            if (taskToMinimize != null) {
                desksOrganizer.minimizeTask(wct, deskId, taskToMinimize)
            }
        }
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue) {
            expandedTasksOrderedFrontToBack
                .filter { taskId -> taskId != taskIdToMinimize }
                .reversed()
                .forEach { taskId ->
                    val runningTaskInfo = shellTaskOrganizer.getRunningTaskInfo(taskId)
                    if (runningTaskInfo == null) {
                        wct.startTask(
                            taskId,
                            createActivityOptionsForStartTask(deskId, desksOrganizer).toBundle(),
                        )
                    } else {
                        desksOrganizer.reorderTaskToFront(wct, deskId, runningTaskInfo)
                    }
                }
        }
        val deactivatingDesk = repository.getActiveDeskId(displayId)?.takeIf { it != deskId }
        val deactivationRunnable =
            addDeskDeactivationChanges(
                wct = wct,
                deskId = deactivatingDesk,
                userId = userId,
                displayId = displayId,
                switchingUser = switchingUser,
                ExitReason.RETURN_HOME_OR_OVERVIEW,
            )
        return { transition ->
            val activateDeskTransition =
                if (newTaskIdInFront != null) {
                    DeskTransition.ActivateDeskWithTask(
                        token = transition,
                        userId = userId,
                        displayId = displayId,
                        deskId = deskId,
                        enterTaskId = newTaskIdInFront,
                        enterReason = enterReason,
                        runOnTransitEnd = { snapEventHandler.onDeskActivated(deskId, displayId) },
                    )
                } else {
                    DeskTransition.ActivateDesk(
                        token = transition,
                        userId = userId,
                        displayId = displayId,
                        deskId = deskId,
                        enterReason = enterReason,
                        runOnTransitEnd = { snapEventHandler.onDeskActivated(deskId, displayId) },
                    )
                }
            desksTransitionObserver.addPendingTransition(activateDeskTransition)
            taskIdToMinimize?.let { minimizingTask ->
                addPendingMinimizeTransition(transition, minimizingTask, MinimizeReason.TASK_LIMIT)
            }
            addPendingTaskLimitTransition(transition, deskId, newTask?.taskId)
            deactivationRunnable?.invoke(transition)
        }
    }

    // returns the id of the task that was closed (if any)
    private fun closeTopTransparentFullscreenTask(
        wct: WindowContainerTransaction,
        deskId: Int,
        launchingTaskId: Int?,
        userId: Int,
    ): Int? {
        if (!DesktopExperienceFlags.FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK.isTrue) return null
        val repository = userRepositories.getProfile(userId)
        val data = repository.getTopTransparentFullscreenTaskData(deskId) ?: return null

        if (launchingTaskId != null && data.taskId == launchingTaskId) {
            logD(
                "closeTopTransparentFullscreenTask: task relaunching as freeform, not closing" +
                    ", taskId=%d, deskId=%d",
                data.taskId,
                deskId,
            )
            repository.clearTopTransparentFullscreenTaskData(deskId)
            return null
        }
        logD("closeTopTransparentFullscreenTask: taskId=%d, deskId=%d", data.taskId, deskId)
        wct.removeTask(data.token)
        return data.taskId
    }

    /** Activates the desk at the given index if it exists. */
    fun activatePreviousDesk(displayId: Int, userId: Int, enterReason: EnterReason) {
        if (
            !DesktopExperienceFlags.ENABLE_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS.isTrue ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            return
        }
        val validDisplay =
            when {
                displayId != INVALID_DISPLAY -> displayId
                focusTransitionObserver.globallyFocusedDisplayId != INVALID_DISPLAY ->
                    focusTransitionObserver.globallyFocusedDisplayId
                else -> {
                    logW("activatePreviousDesk no valid display found")
                    return
                }
            }
        val repository = userRepositories.getProfile(userId)
        val activeDeskId = repository.getActiveDeskId(validDisplay)
        if (activeDeskId == null) {
            logV("activatePreviousDesk no active desk in display=%d", validDisplay)
            return
        }
        val destinationDeskId = repository.getPreviousDeskId(activeDeskId)
        if (destinationDeskId == null) {
            logV(
                "activatePreviousDesk no previous desk before deskId=%d in display=%d",
                activeDeskId,
                validDisplay,
            )
            return
        }
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addDeskActivationChanges(
                deskId = destinationDeskId,
                wct = wct,
                userId = userId,
                enterReason = enterReason,
            )
        logV("activatePreviousDesk from deskId=%d to deskId=%d", activeDeskId, destinationDeskId)
        val transition =
            deskSwitchTransitionHandler.startTransition(
                wct = wct,
                userId = userId,
                displayId = displayId,
                fromDeskId = activeDeskId,
                toDeskId = destinationDeskId,
            )
        runOnTransitStart(transition)
    }

    /** Activates the desk at the given index if it exists. */
    fun activateNextDesk(displayId: Int, userId: Int, enterReason: EnterReason) {
        if (
            !DesktopExperienceFlags.ENABLE_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS.isTrue ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            return
        }
        val validDisplay =
            when {
                displayId != INVALID_DISPLAY -> displayId
                focusTransitionObserver.globallyFocusedDisplayId != INVALID_DISPLAY ->
                    focusTransitionObserver.globallyFocusedDisplayId
                else -> {
                    logW("activateNextDesk no valid display found")
                    return
                }
            }
        val repository = userRepositories.getProfile(userId)
        val activeDeskId = repository.getActiveDeskId(validDisplay)
        if (activeDeskId == null) {
            logV("activateNextDesk no active desk in display=%d", validDisplay)
            return
        }
        val destinationDeskId = repository.getNextDeskId(activeDeskId)
        if (destinationDeskId == null) {
            logV(
                "activateNextDesk no next desk before deskId=%d in display=%d",
                activeDeskId,
                validDisplay,
            )
            return
        }
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addDeskActivationChanges(
                deskId = destinationDeskId,
                wct = wct,
                userId = userId,
                enterReason = enterReason,
            )
        logV("activateNextDesk from deskId=%d to deskId=%d", activeDeskId, destinationDeskId)
        val transition =
            deskSwitchTransitionHandler.startTransition(
                wct = wct,
                userId = userId,
                displayId = displayId,
                fromDeskId = activeDeskId,
                toDeskId = destinationDeskId,
            )
        runOnTransitStart(transition)
    }

    /**
     * Activates the given desk and brings [taskIdToReorderToFront] to front if provided and is
     * already on the given desk.
     */
    fun activateDesk(
        deskId: Int,
        userId: Int = shellController.currentUserId,
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
        enterReason: EnterReason,
    ) {
            logD(
                "activateDesk deskId=%d userId=%d taskIdToReorderToFront=%d remoteTransition=%s",
                deskId,
                userId,
                taskIdToReorderToFront,
                remoteTransition,
            )
            val repository = userRepositories.getProfile(userId)
            if (!repository.getAllDeskIds().contains(deskId)) {
                logW("Request to activate desk=%d but desk not found for user=%d", deskId, userId)
                return
            }
            if (
                taskIdToReorderToFront != null &&
                    repository.getDeskIdForTask(taskIdToReorderToFront) != deskId
            ) {
                logW(
                    "activeDesk taskIdToReorderToFront=%d not on the desk %d",
                    taskIdToReorderToFront,
                    deskId,
                )
                return
            }

            val newTaskInFront =
                taskIdToReorderToFront?.let { taskId ->
                    shellTaskOrganizer.getRunningTaskInfo(taskId)
                        ?: recentTasksController?.findTaskInBackground(taskId)
                }

            val wct = WindowContainerTransaction()
            val runOnTransitStart =
                addDeskActivationChanges(
                    deskId = deskId,
                    wct = wct,
                    newTask = newTaskInFront,
                    userId = userId,
                    enterReason = enterReason,
                )
            // Put task with [taskIdToReorderToFront] to front.
            when (newTaskInFront) {
                is RunningTaskInfo -> {
                    // Task is running, reorder it.
                    if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                        desksOrganizer.reorderTaskToFront(wct, deskId, newTaskInFront)
                    } else {
                        wct.reorder(newTaskInFront.token, /* onTop= */ true)
                    }
                }
                is RecentTaskInfo -> {
                    // Task is not running, start it.
                    wct.startTask(
                        taskIdToReorderToFront,
                        createActivityOptionsForStartTask(deskId, desksOrganizer).toBundle(),
                    )
                }
                else -> {
                    logW("activateDesk taskIdToReorderToFront=%d not found", taskIdToReorderToFront)
                }
            }

            // Exploded view is where tasks are exploded within a desktop on entering recents,
            // so if the user selects a tiled task to be in front, tiling should handle this
            // to bring all tiled tasks to front.
            if (taskIdToReorderToFront != INVALID_TASK_ID && taskIdToReorderToFront != null) {
                snapEventHandler.notifyTilingOfExplodedViewReorder(deskId, taskIdToReorderToFront)
            }
            val transitionType = transitionType(remoteTransition)
            val handler =
                remoteTransition?.let {
                    OneShotRemoteHandler(transitions.mainExecutor, remoteTransition)
                }

            val transition = transitions.startTransition(transitionType, wct, handler)
            handler?.setTransition(transition)
            runOnTransitStart?.invoke(transition)
            // Replaced by |IDesktopTaskListener#onActiveDeskChanged|.
            if (!desktopState.enableMultipleDesktops) {
                desktopModeEnterExitTransitionListener?.onEnterDesktopModeTransitionStarted(
                    toDesktopAnimationDurationMs
                )
            }
        }

    private fun addDeskRemovalChanges(
        wct: WindowContainerTransaction,
        deskId: Int?,
        displayId: Int,
        userId: Int,
        excludingTaskId: Int? = null,
        exitReason: ExitReason,
    ): RunOnTransitStart? {
        if (deskId == null) return null
        val repository = userRepositories.getProfile(userId)
        val tasksToRemove =
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                val activeTaskIdsInDesk = repository.getActiveTaskIdsInDesk(deskId)
                if (DesktopExperienceFlags.ENABLE_REMOVE_DESK_ON_LAST_TASK_REMOVAL.isTrue) {
                    activeTaskIdsInDesk.filterNot { taskId -> taskId == excludingTaskId }.toSet()
                } else {
                    activeTaskIdsInDesk
                }
            } else {
                repository.removeDesk(deskId)
            }
        tasksToRemove.forEach {
            // TODO: b/404595635 - consider moving this block into [DesksOrganizer].
            val task = shellTaskOrganizer.getRunningTaskInfo(it)
            if (task != null) {
                wct.removeTask(task.token)
            } else {
                recentTasksController?.removeBackgroundTask(it)
            }
        }
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desksOrganizer.removeDesk(wct, deskId, userId)
            return { transition ->
                desksTransitionObserver.addPendingTransition(
                    DeskTransition.RemoveDesk(
                        token = transition,
                        userId = userId,
                        displayId = displayId,
                        deskId = deskId,
                        tasks = tasksToRemove,
                        onDeskRemovedListener = onDeskRemovedListener,
                        exitReason = exitReason,
                        runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                    )
                )
            }
        }
        return null
    }

    /**
     * TODO: b/393978539 - Deactivation should not happen in desktop-first devices when going home.
     */
    private fun addDeskDeactivationChanges(
        wct: WindowContainerTransaction,
        deskId: Int?,
        userId: Int,
        displayId: Int,
        switchingUser: Boolean = false,
        exitReason: ExitReason,
    ): RunOnTransitStart? {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return null
        if (deskId == null) return null
        desksOrganizer.deactivateDesk(wct, deskId)
        return { transition ->
            desksTransitionObserver.addPendingTransition(
                DeskTransition.DeactivateDesk(
                    token = transition,
                    userId = userId,
                    deskId = deskId,
                    displayId = displayId,
                    switchingUser = switchingUser,
                    exitReason = exitReason,
                    runOnTransitEnd = { snapEventHandler.onDeskDeactivated(deskId) },
                )
            )
        }
    }

    /** Removes the default desk in the given display. */
    @Deprecated("Deprecated with multi-desks.", ReplaceWith("removeDesk()"))
    fun removeDefaultDeskInDisplay(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        exitReason: ExitReason,
    ) {
        val deskId = getOrCreateDefaultDeskId(displayId, userId) ?: return
        removeDesk(displayId = displayId, deskId = deskId, userId = userId, exitReason = exitReason)
    }

    /**
     * Returns the default desk if it exists, or creates it if needed.
     *
     * Note: [DesktopDisplayEventHandler] is responsible for creating a default desk in
     * desktop-first displays or warming up a desk-root in touch-first displays. This guarantees
     * that a non-null desk can be returned by this function because even if one does not exist yet,
     * [createDeskImmediate] should succeed.
     *
     * TODO: b/406890311 - replace callers with [getOrCreateDefaultDeskIdSuspending], which is safer
     *   because it does not depend on pre-creating desk roots.
     */
    @Deprecated(
        "Use getOrCreateDefaultDeskIdSuspending() instead",
        ReplaceWith("getOrCreateDefaultDeskIdSuspending()"),
    )
    private fun getOrCreateDefaultDeskId(displayId: Int, userId: Int): Int? {
        val repository = userRepositories.getProfile(userId)
        val existingDefaultDeskId = repository.getDefaultDeskId(displayId)
        if (existingDefaultDeskId != null) {
            return existingDefaultDeskId
        }
        val immediateDeskId = createDeskImmediate(displayId, userId)
        if (immediateDeskId == null) {
            logE(
                "Failed to create immediate desk in displayId=%s for userId=%s:\n%s",
                displayId,
                userId,
                Throwable().stackTraceToString(),
            )
        }
        return immediateDeskId
    }

    private suspend fun getOrCreateDefaultDeskIdSuspending(displayId: Int, userId: Int): Int {
        val repository = userRepositories.getProfile(userId)
        return repository.getDefaultDeskId(displayId)
            ?: createDeskSuspending(displayId, userId, enforceDeskLimit = false)
    }

    /** Removes the given desk. */
    fun removeDesk(
        deskId: Int,
        userId: Int = shellController.currentUserId,
        exitReason: ExitReason,
    ) {
        val repository = userRepositories.getProfile(userId)
        if (!repository.getAllDeskIds().contains(deskId)) {
            logW("Request to remove desk=%d but desk not found for user=%d", deskId, userId)
            return
        }
        val displayId = repository.getDisplayForDesk(deskId)
        removeDesk(displayId = displayId, deskId = deskId, userId = userId, exitReason = exitReason)
    }

    /** Removes all the available desks on all displays. */
    fun removeAllDesks(userId: Int = shellController.currentUserId, exitReason: ExitReason) {
        val repository = userRepositories.getProfile(userId)
        val deskIds = repository.getAllDeskIds()
        logV("removeAllDesks userId=%d reason=%s deskIds=%s", userId, exitReason, deskIds)
        val wct = WindowContainerTransaction()
        val runOnTransitStartList =
            deskIds.mapNotNull { deskId ->
                val displayId = repository.getDisplayForDesk(deskId)
                addDeskRemovalChanges(
                    wct = wct,
                    deskId = deskId,
                    displayId = displayId,
                    userId = userId,
                    exitReason = exitReason,
                )
            }
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue && wct.isEmpty) return
        val transition = transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        runOnTransitStartList.forEach { runOnTransitStart -> runOnTransitStart(transition) }
    }

    private fun removeDesk(displayId: Int, deskId: Int, userId: Int, exitReason: ExitReason) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) return
        logV("removeDesk deskId=%d from displayId=%d of userId=%d", deskId, displayId, userId)
        val wct = WindowContainerTransaction()
        val runOnTransitStart =
            addDeskRemovalChanges(
                wct = wct,
                deskId = deskId,
                displayId = displayId,
                userId = userId,
                exitReason = exitReason,
            )
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue && wct.isEmpty) return
        val transition = transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        runOnTransitStart?.invoke(transition)
    }

    /** Enter split by using the focused desktop task in given `displayId`. */
    fun enterSplit(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        leftOrTop: Boolean,
    ) {
        getFocusedDesktopTask(displayId, userId)?.let { requestSplit(it, leftOrTop) }
    }

    private fun getFocusedDesktopTask(displayId: Int, userId: Int): RunningTaskInfo? {
        if (
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                !DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue
        ) {
            return shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo ->
                taskInfo.isFocused && taskInfo.windowingMode == WINDOWING_MODE_FREEFORM
            }
        }
        val repository = userRepositories.getProfile(userId)
        val deskId = repository.getActiveDeskId(displayId) ?: return null
        return shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo ->
            taskInfo.isFocused && repository.isActiveTaskInDesk(taskInfo.taskId, deskId)
        }
    }

    /**
     * Requests a task be transitioned from desktop to split select. Applies needed windowing
     * changes if this transition is enabled.
     */
    @JvmOverloads
    fun requestSplit(taskInfo: RunningTaskInfo, leftOrTop: Boolean = false) {
        val repository = userRepositories.getProfile(taskInfo.userId)
        // If a drag to desktop is in progress, we want to enter split select
        // even if the requesting task is already in split.
        val isDragging = dragToDesktopTransitionHandler.inProgress
        val shouldRequestSplit = taskInfo.isFullscreen || taskInfo.isFreeform || isDragging
        if (shouldRequestSplit) {
            if (isDragging) {
                releaseVisualIndicator()
                val cancelState =
                    if (leftOrTop) {
                        DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_LEFT
                    } else {
                        DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_RIGHT
                    }
                dragToDesktopTransitionHandler.cancelDragToDesktopTransition(cancelState)
            } else {
                val deskId = repository.getDeskIdForTask(taskInfo.taskId)
                logV("Split requested for task=%d in desk=%d", taskInfo.taskId, deskId)
                val wct = WindowContainerTransaction()
                addMoveToSplitChanges(wct, taskInfo)
                splitScreenController.requestEnterSplitSelect(
                    taskInfo,
                    if (leftOrTop) SPLIT_POSITION_TOP_OR_LEFT else SPLIT_POSITION_BOTTOM_OR_RIGHT,
                    taskInfo.configuration.windowConfiguration.bounds,
                    /* startRecents = */ true,
                    /* withRecentsWct = */ wct,
                )
            }
        }
    }

    /** Requests a task be transitioned from whatever mode it's in to a bubble. */
    @JvmOverloads
    fun requestFloat(taskInfo: RunningTaskInfo, left: Boolean? = null) {
        val isDragging = dragToDesktopTransitionHandler.inProgress
        val shouldRequestFloat =
            taskInfo.isFullscreen || taskInfo.isFreeform || isDragging || taskInfo.isMultiWindow
        if (!shouldRequestFloat) return
        if (isDragging) {
            releaseVisualIndicator()
            val cancelState =
                if (left == true) DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_LEFT
                else DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_RIGHT
            dragToDesktopTransitionHandler.cancelDragToDesktopTransition(cancelState)
        } else {
            bubbleController.ifPresent {
                it.expandStackAndSelectBubble(taskInfo, /* dragData= */ null)
            }
        }
    }

    private fun getDefaultDensityDpi(): Int = context.resources.displayMetrics.densityDpi

    /** Creates a new instance of the external interface to pass to another process. */
    private fun createExternalInterface(): ExternalInterfaceBinder = IDesktopModeImpl(this)

    /** Get connection interface between sysui and shell */
    fun asDesktopMode(): DesktopMode {
        return desktopMode
    }

    /**
     * Perform checks required on drag move. Create/release fullscreen indicator as needed.
     * Different sources for x and y coordinates are used due to different needs for each: We want
     * split transitions to be based on input coordinates but fullscreen transition to be based on
     * task edge coordinate.
     *
     * @param taskInfo the task being dragged.
     * @param taskSurface SurfaceControl of dragged task.
     * @param displayId displayId of the input event.
     * @param inputX x coordinate of input. Used for checks against left/right edge of screen.
     * @param inputY y coordinate of input. Used for checks about cross display drag.
     * @param taskBounds bounds of dragged task. Used for checks against status bar height.
     */
    fun onDragPositioningMove(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl,
        displayId: Int,
        inputX: Float,
        inputY: Float,
        taskBounds: Rect,
    ) {
        if (taskInfo.windowingMode != WINDOWING_MODE_FREEFORM) return
        snapEventHandler.removeTaskIfTiled(taskInfo.displayId, taskInfo.taskId)
        if (!DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue()) {
            updateVisualIndicator(
                taskInfo,
                taskSurface,
                displayId,
                inputX,
                taskBounds.top.toFloat(),
                DragStartState.FROM_FREEFORM,
            )
            return
        }

        val indicator =
            getOrCreateVisualIndicator(taskInfo, taskSurface, DragStartState.FROM_FREEFORM)
        val indicatorType =
            indicator.calculateIndicatorType(displayId, PointF(inputX, taskBounds.top.toFloat()))
        visualIndicatorUpdateScheduler.schedule(
            taskInfo.displayId,
            indicatorType,
            inputX,
            inputY,
            taskBounds,
            indicator,
        )
    }

    fun updateVisualIndicator(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl?,
        displayId: Int,
        inputX: Float,
        taskTop: Float,
        dragStartState: DragStartState,
    ): IndicatorType {
        return getOrCreateVisualIndicator(taskInfo, taskSurface, dragStartState)
            .updateIndicatorType(displayId, PointF(inputX, taskTop))
    }

    @VisibleForTesting
    fun getOrCreateVisualIndicator(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl?,
        dragStartState: DragStartState,
    ): DesktopModeVisualIndicator {
        // If the visual indicator has the wrong start state, it was never cleared from a previous
        // drag event and needs to be cleared
        if (visualIndicator != null && visualIndicator?.dragStartState != dragStartState) {
            Slog.e(TAG, "Visual indicator from previous motion event was never released")
            releaseVisualIndicator()
        }
        // If the visual indicator does not exist, create it.
        val indicator =
            visualIndicator
                ?: DesktopModeVisualIndicator(
                    desktopExecutor,
                    mainExecutor,
                    syncQueue,
                    taskInfo,
                    displayController,
                    if (ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) {
                        displayController.getDisplayContext(taskInfo.displayId)
                    } else {
                        context
                    },
                    taskSurface,
                    rootTaskDisplayAreaOrganizer,
                    dragStartState,
                    bubbleController.getOrNull()?.bubbleDropTargetBoundsProvider,
                    snapEventHandler,
                )
        if (visualIndicator == null) visualIndicator = indicator
        return indicator
    }

    /**
     * Perform checks required on drag end. If indicator indicates a windowing mode change, perform
     * that change. Otherwise, ensure bounds are up to date.
     *
     * @param taskInfo the task being dragged.
     * @param taskSurface the leash of the task being dragged.
     * @param displayId the displayId of the input event.
     * @param inputCoordinate the coordinates of the motion event
     * @param currentDragBounds the current bounds of where the visible task is (might be actual
     *   task bounds or just task leash)
     * @param validDragArea the bounds of where the task can be dragged within the display.
     * @param dragStartBounds the bounds of the task before starting dragging.
     * @return true if caller needs to clear the window drag indicators by itself.
     */
    fun onDragPositioningEnd(
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl,
        displayId: Int,
        inputCoordinate: PointF,
        currentDragBounds: Rect,
        validDragArea: Rect,
        dragStartBounds: Rect,
        motionEvent: MotionEvent,
    ): Boolean {
        if (taskInfo.configuration.windowConfiguration.windowingMode != WINDOWING_MODE_FREEFORM) {
            return true
        }

        val indicator = getVisualIndicator() ?: return true
        val indicatorType =
            indicator.updateIndicatorType(
                displayId,
                PointF(inputCoordinate.x, currentDragBounds.top.toFloat()),
            )

        var needDragIndicatorCleanup = true
        when (indicatorType) {
            IndicatorType.TO_FULLSCREEN_INDICATOR -> {
                val shouldMaximizeWhenDragToTopEdge =
                    if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE.isTrue)
                        rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(
                            motionEvent.getDisplayId()
                        )
                    else desktopConfig.shouldMaximizeWhenDragToTopEdge
                if (shouldMaximizeWhenDragToTopEdge) {
                    dragToMaximizeDesktopTask(taskInfo, taskSurface, currentDragBounds, motionEvent)
                } else {
                    desktopModeUiEventLogger.log(
                        taskInfo,
                        DesktopUiEventEnum.DESKTOP_WINDOW_APP_HEADER_DRAG_TO_FULL_SCREEN,
                    )
                    moveToFullscreenWithAnimation(
                        taskInfo,
                        Point(currentDragBounds.left, currentDragBounds.top),
                        DesktopModeTransitionSource.TASK_DRAG,
                    )
                }
            }
            IndicatorType.TO_SPLIT_LEFT_INDICATOR -> {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HEADER_DRAG_TO_TILE_TO_LEFT,
                )
                handleSnapResizingTaskOnDrag(
                    taskInfo,
                    SnapPosition.LEFT,
                    taskSurface,
                    currentDragBounds,
                    dragStartBounds,
                    motionEvent,
                )
            }
            IndicatorType.TO_SPLIT_RIGHT_INDICATOR -> {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HEADER_DRAG_TO_TILE_TO_RIGHT,
                )
                handleSnapResizingTaskOnDrag(
                    taskInfo,
                    SnapPosition.RIGHT,
                    taskSurface,
                    currentDragBounds,
                    dragStartBounds,
                    motionEvent,
                )
            }
            IndicatorType.NO_INDICATOR,
            IndicatorType.TO_BUBBLE_LEFT_INDICATOR,
            IndicatorType.TO_BUBBLE_RIGHT_INDICATOR -> {
                // TODO(b/391928049): add support fof dragging desktop apps to a bubble

                // Create a copy so that we can animate from the current bounds if we end up having
                // to snap the surface back without a WCT change.
                val destinationBounds = Rect(currentDragBounds)
                // If task bounds are outside valid drag area, snap them inward
                DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(
                    destinationBounds,
                    validDragArea,
                )

                val newDisplayId = motionEvent.displayId
                val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(newDisplayId)
                val isCrossDisplayDrag =
                    DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue() &&
                        newDisplayId != taskInfo.getDisplayId() &&
                        displayAreaInfo != null

                if (!isCrossDisplayDrag && destinationBounds == dragStartBounds) {
                    // There's no actual difference between the start and end bounds, so while a
                    // WCT change isn't needed, the dragged surface still needs to be snapped back
                    // to its original location.
                    if (currentDragBounds != dragStartBounds) {
                        // If task's position needs to be changed from the current bounds, animate
                        // the bounds change
                        returnToDragStartAnimator.start(
                            taskInfo.taskId,
                            taskSurface,
                            startBounds = currentDragBounds,
                            endBounds = dragStartBounds,
                        )
                    } else {
                        // Set the task bounds even if there is no difference between the current
                        // bounds and destination bounds in case that task surface was moved off
                        // screen during the drag
                        val t = SurfaceControl.Transaction()
                        t.setPosition(
                            taskSurface,
                            destinationBounds.left.toFloat(),
                            destinationBounds.top.toFloat(),
                        )
                        t.apply()
                    }
                    releaseVisualIndicator()
                    return true
                }

                val prevCaptionInsets = taskInfo.freeformCaptionInsets
                if (isCrossDisplayDrag) {
                    val captionInsetsDp =
                        displayController
                            .getDisplayLayout(taskInfo.getDisplayId())
                            ?.pxToDp(prevCaptionInsets)
                            ?.toInt() ?: 0
                    val destDisplayLayout = displayController.getDisplayLayout(newDisplayId)
                    val captionInsets = destDisplayLayout?.dpToPx(captionInsetsDp)?.toInt() ?: 0
                    val constrainedBounds =
                        if (
                            DesktopExperienceFlags.ENABLE_SHRINK_WINDOW_BOUNDS_AFTER_DRAG.isTrue()
                        ) {
                            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                                destinationBounds,
                                destDisplayLayout,
                                taskInfo.isResizeable,
                                inputCoordinate.x,
                                captionInsets,
                            )
                        } else {
                            Rect(destinationBounds)
                        }

                    moveToDisplay(
                        taskInfo,
                        newDisplayId,
                        constrainedBounds,
                        windowDragTransitionHandler,
                        enterReason = EnterReason.APP_HANDLE_DRAG,
                        captionInsets = captionInsets,
                    )
                } else {
                    // Update task bounds so that the task position will match the position of its
                    // leash
                    val wct = WindowContainerTransaction()
                    wct.setBounds(taskInfo.token, destinationBounds)
                    if (prevCaptionInsets != 0) {
                        val appBounds =
                            Rect(destinationBounds).apply { this.top += prevCaptionInsets }
                        wct.setAppBounds(taskInfo.token, appBounds)
                    }
                    transitions.startTransition(TRANSIT_CHANGE, wct, windowDragTransitionHandler)
                }
                releaseVisualIndicator()
                needDragIndicatorCleanup = false
            }
            IndicatorType.TO_DESKTOP_INDICATOR -> {
                throw IllegalArgumentException(
                    "Should not be receiving TO_DESKTOP_INDICATOR for " + "a freeform task."
                )
            }
        }
        // A freeform drag-move ended, remove the indicator immediately.
        releaseVisualIndicator()
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(
                displayId = taskInfo.displayId,
                userId = taskInfo.userId,
            )
        )
        return needDragIndicatorCleanup
    }

    /**
     * Cancel the drag-to-desktop transition.
     *
     * @param taskInfo the task being dragged.
     */
    fun onDragPositioningCancelThroughStatusBar(taskInfo: RunningTaskInfo) {
        interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD)
        cancelDragToDesktop(taskInfo)
    }

    /**
     * Perform checks required when drag ends under status bar area.
     *
     * @param displayId the displayId of the input event.
     * @param taskInfo the task being dragged.
     * @param y height of drag, to be checked against status bar height.
     * @return the [IndicatorType] used for the resulting transition
     */
    fun onDragPositioningEndThroughStatusBar(
        displayId: Int,
        inputCoordinates: PointF,
        taskInfo: RunningTaskInfo,
        taskSurface: SurfaceControl,
    ): IndicatorType {
        // End the drag_hold CUJ interaction.
        interactionJankMonitor.end(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD)
        val indicator = getVisualIndicator() ?: return IndicatorType.NO_INDICATOR
        val indicatorType = indicator.updateIndicatorType(displayId, inputCoordinates)
        when (indicatorType) {
            IndicatorType.TO_DESKTOP_INDICATOR -> {
                latencyTracker.onActionStart(
                    LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG
                )
                if (
                    taskInfo.configuration.windowConfiguration.getWindowingMode() ===
                        WINDOWING_MODE_MULTI_WINDOW
                ) {
                    interactionJankMonitor.begin(
                        taskSurface,
                        context,
                        handler,
                        CUJ_DESKTOP_MODE_MOVE_FROM_SPLIT_SCREEN,
                    )
                } else {
                    // Start a new jank interaction for the drag release to desktop window
                    // animation.
                    interactionJankMonitor.begin(
                        taskSurface,
                        context,
                        handler,
                        CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE,
                        "to_desktop",
                    )
                }
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_DESKTOP_MODE,
                )
                finalizeDragToDesktop(taskInfo)
            }
            IndicatorType.NO_INDICATOR,
            IndicatorType.TO_FULLSCREEN_INDICATOR -> {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_FULL_SCREEN,
                )
                cancelDragToDesktop(taskInfo)
            }
            IndicatorType.TO_SPLIT_LEFT_INDICATOR -> {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_SPLIT_SCREEN,
                )
                requestSplit(taskInfo, leftOrTop = true)
            }
            IndicatorType.TO_SPLIT_RIGHT_INDICATOR -> {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_SPLIT_SCREEN,
                )
                requestSplit(taskInfo, leftOrTop = false)
            }
            IndicatorType.TO_BUBBLE_LEFT_INDICATOR -> {
                requestFloat(taskInfo, left = true)
            }
            IndicatorType.TO_BUBBLE_RIGHT_INDICATOR -> {
                requestFloat(taskInfo, left = false)
            }
        }
        return indicatorType
    }

    /** Update the exclusion region for a specified task */
    fun onExclusionRegionChanged(taskId: Int, exclusionRegion: Region) {
        userRepositories.current.updateTaskExclusionRegions(taskId, exclusionRegion)
    }

    /** Remove a previously tracked exclusion region for a specified task. */
    fun removeExclusionRegionForTask(taskId: Int) {
        userRepositories.current.removeExclusionRegion(taskId)
    }

    /**
     * Adds a listener to find out about changes in the visibility of freeform tasks.
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun addVisibleTasksListener(listener: VisibleTasksListener, callbackExecutor: Executor) {
        userRepositories.current.addVisibleTasksListener(listener, callbackExecutor)
    }

    /**
     * Adds a listener to find out about desk changes.
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun addDeskChangeListener(listener: DeskChangeListener, callbackExecutor: Executor) {
        userRepositories.current.addDeskChangeListener(listener, callbackExecutor)
    }

    /**
     * Adds a listener to track changes to desktop task gesture exclusion regions
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun setTaskRegionListener(listener: Consumer<Region>, callbackExecutor: Executor) {
        userRepositories.current.setExclusionRegionListener(listener, callbackExecutor)
    }

    // TODO(b/358114479): Move this implementation into a separate class.
    override fun onUnhandledDrag(
        launchIntent: PendingIntent,
        @UserIdInt userId: Int,
        dragEvent: DragEvent,
        onFinishCallback: Consumer<Boolean>,
    ): Boolean {
        val destinationDisplay = dragEvent.displayId
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(destinationDisplay) ||
                getFocusedNonDesktopTasks(destinationDisplay, userId).isNotEmpty()
        ) {
            // Destination display does not support desktop or has focused
            // non-freeform task; ignore the drop.
            return false
        }

        val launchComponent = getComponent(launchIntent)
        if (!multiInstanceHelper.supportsMultiInstanceSplit(launchComponent, userId)) {
            // TODO(b/320797628): Should only return early if there is an existing running task, and
            //                    notify the user as well. But for now, just ignore the drop.
            logV("Dropped intent does not support multi-instance")
            return false
        }
        val taskInfo =
            shellTaskOrganizer.getRunningTaskInfo(focusTransitionObserver.globallyFocusedTaskId)
                ?: return false
        // TODO(b/358114479): Update drag and drop handling to give us visibility into when another
        //  window will accept a drag event. This way, we can hide the indicator when we won't
        //  be handling the transition here, allowing us to display the indicator accurately.
        //  For now, we create the indicator only on drag end and immediately dispose it.
        val indicatorType =
            updateVisualIndicator(
                taskInfo,
                dragEvent.dragSurface,
                destinationDisplay,
                dragEvent.x,
                dragEvent.y,
                DragStartState.DRAGGED_INTENT,
            )
        releaseVisualIndicator()
        val windowingMode =
            when (indicatorType) {
                IndicatorType.TO_FULLSCREEN_INDICATOR -> {
                    WINDOWING_MODE_FULLSCREEN
                }
                // NO_INDICATOR can result from a cross-display drag.
                IndicatorType.TO_SPLIT_LEFT_INDICATOR,
                IndicatorType.TO_SPLIT_RIGHT_INDICATOR,
                IndicatorType.TO_DESKTOP_INDICATOR,
                IndicatorType.NO_INDICATOR -> {
                    WINDOWING_MODE_FREEFORM
                }
                else -> error("Invalid indicator type: $indicatorType")
            }
        val displayLayout = displayController.getDisplayLayout(destinationDisplay) ?: return false

        val newWindowBounds = Rect()
        when (indicatorType) {
            IndicatorType.TO_DESKTOP_INDICATOR,
            IndicatorType.NO_INDICATOR -> {
                if (
                    DesktopExperienceFlags.ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS.isTrue()
                ) {
                    // Inherit parent's bounds.
                    newWindowBounds.set(taskInfo.configuration.windowConfiguration.bounds)
                } else {
                    newWindowBounds.set(calculateDefaultDesktopTaskBounds(displayLayout))
                }
                positionDragAndDropBounds(newWindowBounds, dragEvent)
            }
            IndicatorType.TO_SPLIT_RIGHT_INDICATOR -> {
                newWindowBounds.set(getSnapBounds(destinationDisplay, SnapPosition.RIGHT))
            }
            IndicatorType.TO_SPLIT_LEFT_INDICATOR -> {
                newWindowBounds.set(getSnapBounds(destinationDisplay, SnapPosition.LEFT))
            }
            else -> {
                // Use empty bounds for the fullscreen case.
            }
        }
        // Start a new transition to launch the app
        val opts =
            ActivityOptions.makeBasic().apply {
                launchWindowingMode = windowingMode
                launchBounds = newWindowBounds
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                pendingIntentLaunchFlags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                splashScreenStyle = SPLASH_SCREEN_STYLE_ICON
                launchDisplayId = destinationDisplay
            }
        if (windowingMode == WINDOWING_MODE_FULLSCREEN) {
            dragAndDropFullscreenCookie = Binder()
            opts.launchCookie = dragAndDropFullscreenCookie
        }
        val wct = WindowContainerTransaction()
        wct.sendPendingIntent(launchIntent, null, opts.toBundle())
        if (windowingMode == WINDOWING_MODE_FREEFORM) {
            if (
                DesktopModeFlags.ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX.isTrue ||
                    DesktopExperienceFlags.ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION.isTrue
            ) {
                val deskId = getOrCreateDefaultDeskId(destinationDisplay, userId) ?: return false
                startLaunchTransition(
                    TRANSIT_OPEN,
                    wct,
                    launchingTaskId = null,
                    deskId = deskId,
                    displayId = destinationDisplay,
                    userId = userId,
                    dragEvent = dragEvent,
                )
            } else {
                desktopModeDragAndDropTransitionHandler.handleDropEvent(wct, dragEvent)
            }
        } else {
            transitions.startTransition(TRANSIT_OPEN, wct, null)
        }

        // Report that this is handled by the listener
        onFinishCallback.accept(true)

        // We've assumed responsibility of cleaning up the drag surface, so do that now
        // TODO(b/320797628): Do an actual animation here for the drag surface
        val t = SurfaceControl.Transaction()
        t.remove(dragEvent.dragSurface)
        t.apply()
        return true
    }

    // TODO(b/366397912): Support full multi-user mode in Windowing.
    override fun onUserChanged(newUserId: Int, userContext: Context) {
        logV(
            "onUserChanged previousUserId=%d, newUserId=%d",
            shellController.currentUserId,
            newUserId,
        )
        updateCurrentUser(newUserId)
    }

    private fun updateCurrentUser(newUserId: Int) {
        if (this::snapEventHandler.isInitialized) {
            snapEventHandler.onUserChange(newUserId)
        }
    }

    /** Called when a task's info changes. */
    fun onTaskInfoChanged(taskInfo: RunningTaskInfo) {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return
        val repository = userRepositories.getProfile(taskInfo.userId)
        val inImmersive = repository.isTaskInFullImmersiveState(taskInfo.taskId)
        val requestingImmersive = taskInfo.requestingImmersive
        if (
            inImmersive &&
                !requestingImmersive &&
                !RecentsTransitionStateListener.isRunning(recentsTransitionState)
        ) {
            logV(
                "onTaskInfoChanged taskId=%d stopped requesting immersive," +
                    " breaking out of desktop-immersive mode",
                taskInfo.taskId,
            )
            // Exit immersive if the app is no longer requesting it.
            desktopImmersiveController.moveTaskToNonImmersive(
                taskInfo,
                DesktopImmersiveController.ExitReason.APP_NOT_IMMERSIVE,
            )
        }
    }

    private fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}DesktopTasksController")
        desktopConfig.dump(pw, innerPrefix)
        userRepositories.dump(pw, innerPrefix)
        if (Flags.showDesktopExperienceDevOption()) {
            dumpFlags(pw, prefix)
        }
    }

    private fun dumpFlags(pw: PrintWriter, prefix: String) {
        val flagPrefix = "$prefix  "
        fun dumpFlag(
            name: String,
            flagNameWidth: Int,
            value: Boolean,
            flagValue: Boolean,
            overridable: Boolean,
        ) {
            val spaces = " ".repeat(flagNameWidth - name.length)
            pw.println(
                "${flagPrefix}Flag $name$spaces - $value (default: $flagValue, overridable: $overridable)"
            )
        }

        fun dumpFlag(flag: DesktopExperienceFlags, flagNameWidth: Int) {
            dumpFlag(flag.flagName, flagNameWidth, flag.isTrue, flag.flagValue, flag.isOverridable)
        }

        fun dumpFlag(flag: DesktopExperienceFlag, flagNameWidth: Int) {
            dumpFlag(flag.flagName, flagNameWidth, flag.isTrue, flag.flagValue, flag.isOverridable)
        }
        pw.println("${prefix}DesktopExperienceFlags")
        pw.println(
            "$prefix  Status: ${if (DesktopExperienceFlags.getToggleOverride()) "enabled" else "disabled"}"
        )
        val maxEnumFlagName = DesktopExperienceFlags.entries.maxOf { it.flagName.length }
        for (flag in DesktopExperienceFlags.entries) {
            dumpFlag(flag, maxEnumFlagName + 1)
        }
        val registeredFlags = DesktopExperienceFlags.getRegisteredFlags()
        val maxRegisteredFlagName = registeredFlags.maxOf { it.flagName.length }
        pw.println("${prefix}DesktopExperienceFlags.DesktopExperienceFlag")
        for (flag in registeredFlags) {
            dumpFlag(flag, maxRegisteredFlagName + 1)
        }
    }

    /** The interface for calls from outside the shell, within the host process. */
    @ExternalThread
    private inner class DesktopModeImpl : DesktopMode {
        override fun addVisibleTasksListener(
            listener: VisibleTasksListener,
            callbackExecutor: Executor,
        ) {
            mainExecutor.execute {
                this@DesktopTasksController.addVisibleTasksListener(listener, callbackExecutor)
            }
        }

        override fun addDeskChangeListener(
            listener: DeskChangeListener,
            callbackExecutor: Executor,
        ) {
            mainExecutor.execute {
                this@DesktopTasksController.addDeskChangeListener(listener, callbackExecutor)
            }
        }

        override fun addDesktopGestureExclusionRegionListener(
            listener: Consumer<Region>,
            callbackExecutor: Executor,
        ) {
            mainExecutor.execute {
                this@DesktopTasksController.setTaskRegionListener(listener, callbackExecutor)
            }
        }

        override fun moveFocusedTaskToDesktop(
            displayId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            logV("moveFocusedTaskToDesktop")
            mainExecutor.execute {
                this@DesktopTasksController.moveFocusedTaskToDesktop(
                    displayId = displayId,
                    transitionSource = transitionSource,
                )
            }
        }

        override fun moveFocusedTaskToFullscreen(
            displayId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            logV("moveFocusedTaskToFullscreen")
            mainExecutor.execute {
                this@DesktopTasksController.enterFullscreen(
                    displayId = displayId,
                    transitionSource = transitionSource,
                )
            }
        }

        override fun moveFocusedTaskToStageSplit(displayId: Int, leftOrTop: Boolean) {
            logV("moveFocusedTaskToStageSplit")
            mainExecutor.execute {
                this@DesktopTasksController.enterSplit(displayId = displayId, leftOrTop = leftOrTop)
            }
        }

        override fun toggleFocusedTaskFullscreenState(
            displayId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            logV("toggleFocusedTaskFullscreenState")
            mainExecutor.execute {
                this@DesktopTasksController.toggleFocusedTaskFullscreenState(
                    displayId = displayId,
                    transitionSource = transitionSource,
                )
            }
        }

        override fun registerDesktopFirstListener(listener: DesktopFirstListener) {
            logV("registerDesktopFirstListener")
            if (desktopFirstListenerManager.isEmpty) {
                throw UnsupportedOperationException(
                    "DesktopFirstListenerManager is not available on this device"
                )
            }
            mainExecutor.execute { desktopFirstListenerManager.get().registerListener(listener) }
        }

        override fun unregisterDesktopFirstListener(listener: DesktopFirstListener) {
            logV("unregisterDesktopFirstListener")
            if (desktopFirstListenerManager.isEmpty) {
                throw UnsupportedOperationException(
                    "DesktopFirstListenerManager is not available on this device"
                )
            }
            mainExecutor.execute { desktopFirstListenerManager.get().unregisterListener(listener) }
        }

        override fun isDisplayInDesktopMode(displayId: Int) =
            with(this@DesktopTasksController) {
                desktopState.isDesktopModeSupportedOnDisplay(displayId) &&
                    // TODO: b/440645027 - Simplify this call.
                    userRepositories.current
                        .getDeskDisplayStateForRemote()
                        .filter { it.displayId == displayId }
                        .filterNot { it.activeDeskId == INVALID_DISPLAY }
                        .isNotEmpty()
            }
    }

    /** The interface for calls from outside the host process. */
    @BinderThread
    private class IDesktopModeImpl(private var controller: DesktopTasksController?) :
        IDesktopMode.Stub(), ExternalInterfaceBinder {

        private lateinit var remoteListener:
            SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>

        private val deskChangeListener: DeskChangeListener =
            object : DeskChangeListener {
                override fun onDeskAdded(displayId: Int, deskId: Int) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onDeskAdded display=%d deskId=%d",
                        displayId,
                        deskId,
                    )
                    remoteListener.call { l -> l.onDeskAdded(displayId, deskId) }
                }

                override fun onDeskRemoved(displayId: Int, deskId: Int) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onDeskRemoved display=%d deskId=%d",
                        displayId,
                        deskId,
                    )
                    remoteListener.call { l -> l.onDeskRemoved(displayId, deskId) }
                }

                override fun onActiveDeskChanged(
                    displayId: Int,
                    newActiveDeskId: Int,
                    oldActiveDeskId: Int,
                ) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onActiveDeskChanged display=%d new=%d old=%d",
                        displayId,
                        newActiveDeskId,
                        oldActiveDeskId,
                    )
                    remoteListener.call { l ->
                        l.onActiveDeskChanged(displayId, newActiveDeskId, oldActiveDeskId)
                    }
                }

                override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onCanCreateDesksChanged canCreateDesks=%b",
                        canCreateDesks,
                    )
                    remoteListener.call { l -> l.onCanCreateDesksChanged(canCreateDesks) }
                }
            }

        private val visibleTasksListener: VisibleTasksListener =
            object : VisibleTasksListener {
                override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onVisibilityChanged display=%d visible=%d",
                        displayId,
                        visibleTasksCount,
                    )
                    remoteListener.call { l ->
                        l.onTasksVisibilityChanged(displayId, visibleTasksCount)
                    }
                }
            }

        private val taskbarDesktopTaskListener: TaskbarDesktopTaskListener =
            object : TaskbarDesktopTaskListener {
                override fun onTaskbarCornerRoundingUpdate(
                    hasTasksRequiringTaskbarRounding: Boolean
                ) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onTaskbarCornerRoundingUpdate " +
                            "doesAnyTaskRequireTaskbarRounding=%s",
                        hasTasksRequiringTaskbarRounding,
                    )

                    remoteListener.call { l ->
                        l.onTaskbarCornerRoundingUpdate(hasTasksRequiringTaskbarRounding)
                    }
                }
            }

        private val desktopModeEntryExitTransitionListener: DesktopModeEntryExitTransitionListener =
            object : DesktopModeEntryExitTransitionListener {
                override fun onEnterDesktopModeTransitionStarted(transitionDuration: Int) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onEnterDesktopModeTransitionStarted transitionTime=%s",
                        transitionDuration,
                    )
                    remoteListener.call { l ->
                        l.onEnterDesktopModeTransitionStarted(transitionDuration)
                    }
                }

                override fun onExitDesktopModeTransitionStarted(
                    transitionDuration: Int,
                    shouldEndUpAtHome: Boolean,
                ) {
                    ProtoLog.v(
                        WM_SHELL_DESKTOP_MODE,
                        "IDesktopModeImpl: onExitDesktopModeTransitionStarted transitionTime=%s shouldEndUpAtHome=%b",
                        transitionDuration,
                        shouldEndUpAtHome,
                    )
                    remoteListener.call { l ->
                        l.onExitDesktopModeTransitionStarted(transitionDuration, shouldEndUpAtHome)
                    }
                }
            }

        init {
            remoteListener =
                SingleInstanceRemoteListener<DesktopTasksController, IDesktopTaskListener>(
                    controller,
                    { c ->
                        run {
                            syncInitialState(c)
                            registerListeners(c)
                        }
                    },
                    { c -> run { unregisterListeners(c) } },
                )
        }

        /** Invalidates this instance, preventing future calls from updating the controller. */
        override fun invalidate() {
            remoteListener.unregister()
            controller = null
        }

        override fun createDesk(displayId: Int) {
            executeRemoteCallWithTaskPermission(controller, "createDesk") { c ->
                c.createDesk(displayId)
            }
        }

        override fun removeDesk(deskId: Int, transitionSource: DesktopModeTransitionSource) {
            executeRemoteCallWithTaskPermission(controller, "removeDesk") { c ->
                c.removeDesk(deskId, exitReason = transitionSource.getExitReason())
            }
        }

        override fun removeAllDesks(transitionSource: DesktopModeTransitionSource) {
            executeRemoteCallWithTaskPermission(controller, "removeAllDesks") { c ->
                c.removeAllDesks(exitReason = transitionSource.getExitReason())
            }
        }

        override fun activateDesk(
            deskId: Int,
            remoteTransition: RemoteTransition?,
            taskIdInFront: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            executeRemoteCallWithTaskPermission(controller, "activateDesk") { c ->
                c.activateDesk(
                    deskId = deskId,
                    remoteTransition = remoteTransition,
                    taskIdToReorderToFront =
                        if (taskIdInFront != INVALID_TASK_ID) taskIdInFront else null,
                    enterReason = transitionSource.getEnterReason(),
                )
            }
        }

        override fun showDesktopApps(
            displayId: Int,
            remoteTransition: RemoteTransition?,
            taskIdInFront: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            executeRemoteCallWithTaskPermission(controller, "showDesktopApps") { c ->
                c.showDesktopApps(
                    displayId = displayId,
                    remoteTransition = remoteTransition,
                    taskIdToReorderToFront =
                        if (taskIdInFront != INVALID_TASK_ID) taskIdInFront else null,
                    transitionSource = transitionSource,
                )
            }
        }

        override fun showDesktopApp(
            taskId: Int,
            remoteTransition: RemoteTransition?,
            toFrontReason: DesktopTaskToFrontReason,
        ) {
            executeRemoteCallWithTaskPermission(controller, "showDesktopApp") { c ->
                c.moveTaskToFront(
                    taskId = taskId,
                    remoteTransition = remoteTransition,
                    unminimizeReason = toFrontReason.toUnminimizeReason(),
                )
            }
        }

        override fun moveToFullscreen(
            taskId: Int,
            transitionSource: DesktopModeTransitionSource,
            remoteTransition: RemoteTransition?,
        ) {
            executeRemoteCallWithTaskPermission(controller, "moveToFullscreen") { c ->
                c.moveToFullscreen(taskId, transitionSource, remoteTransition)
            }
        }

        override fun stashDesktopApps(displayId: Int) {
            ProtoLog.w(WM_SHELL_DESKTOP_MODE, "IDesktopModeImpl: stashDesktopApps is deprecated")
        }

        override fun hideStashedDesktopApps(displayId: Int) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "IDesktopModeImpl: hideStashedDesktopApps is deprecated",
            )
        }

        override fun onDesktopSplitSelectChoice(taskInfo: RunningTaskInfo) {
            executeRemoteCallWithTaskPermission(controller, "onDesktopSplitSelectChoice") { c ->
                c.onDesktopSplitSelectChoice(taskInfo)
            }
        }

        override fun setTaskListener(listener: IDesktopTaskListener?) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "IDesktopModeImpl: set task listener=%s", listener)
            executeRemoteCallWithTaskPermission(controller, "setTaskListener") { _ ->
                listener?.let { remoteListener.register(it) } ?: remoteListener.unregister()
            }
        }

        override fun moveToDesktop(
            taskId: Int,
            transitionSource: DesktopModeTransitionSource,
            remoteTransition: RemoteTransition?,
            callback: IMoveToDesktopCallback?,
        ) {
            executeRemoteCallWithTaskPermission(controller, "moveTaskToDesktop") { c ->
                c.moveTaskToDefaultDeskAndActivate(
                    taskId,
                    transitionSource = transitionSource,
                    remoteTransition = remoteTransition,
                    callback = callback,
                )
            }
        }

        override fun removeDefaultDeskInDisplay(
            displayId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            executeRemoteCallWithTaskPermission(controller, "removeDefaultDeskInDisplay") { c ->
                c.removeDefaultDeskInDisplay(
                    displayId = displayId,
                    exitReason = transitionSource.getExitReason(),
                )
            }
        }

        override fun moveToExternalDisplay(
            taskId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            executeRemoteCallWithTaskPermission(controller, "moveTaskToExternalDisplay") { c ->
                c.moveToNextDisplay(taskId, enterReason = transitionSource.getEnterReason())
            }
        }

        override fun startLaunchIntentTransition(intent: Intent, options: Bundle, displayId: Int) {
            executeRemoteCallWithTaskPermission(controller, "startLaunchIntentTransition") { c ->
                c.startLaunchIntentTransition(intent, options, displayId)
            }
        }

        private fun syncInitialState(c: DesktopTasksController) {
            remoteListener.call { l ->
                l.onListenerConnected(
                    c.userRepositories.current.getDeskDisplayStateForRemote(),
                    c.canCreateDesks(),
                )
            }
        }

        private fun registerListeners(c: DesktopTasksController) {
            if (c.desktopState.enableMultipleDesktops) {
                c.userRepositories.current.addDeskChangeListener(deskChangeListener, c.mainExecutor)
            }
            c.userRepositories.current.addVisibleTasksListener(visibleTasksListener, c.mainExecutor)
            c.taskbarDesktopTaskListener = taskbarDesktopTaskListener
            c.desktopModeEnterExitTransitionListener = desktopModeEntryExitTransitionListener
        }

        private fun unregisterListeners(c: DesktopTasksController) {
            if (c.desktopState.enableMultipleDesktops) {
                c.userRepositories.current.removeDeskChangeListener(deskChangeListener)
            }
            c.userRepositories.current.removeVisibleTasksListener(visibleTasksListener)
            c.taskbarDesktopTaskListener = null
            c.desktopModeEnterExitTransitionListener = null
        }
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        // Timeout used for CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD, this is longer than the
        // default timeout to avoid timing out in the middle of a drag action.
        private val APP_HANDLE_DRAG_HOLD_CUJ_TIMEOUT_MS: Long = TimeUnit.SECONDS.toMillis(10L)

        private const val TAG = "DesktopTasksController"

        private fun DesktopTaskToFrontReason.toUnminimizeReason(): UnminimizeReason =
            when (this) {
                DesktopTaskToFrontReason.UNKNOWN -> UnminimizeReason.UNKNOWN
                DesktopTaskToFrontReason.TASKBAR_TAP -> UnminimizeReason.TASKBAR_TAP
                DesktopTaskToFrontReason.ALT_TAB -> UnminimizeReason.ALT_TAB
                DesktopTaskToFrontReason.TASKBAR_MANAGE_WINDOW ->
                    UnminimizeReason.TASKBAR_MANAGE_WINDOW
            }

        /** Returns child task from two focused tasks in split screen mode. */
        fun getSplitFocusedTask(task1: RunningTaskInfo, task2: RunningTaskInfo) =
            if (task1.taskId == task2.parentTaskId) task2 else task1

        @JvmField
        /**
         * A placeholder for a synthetic transition that isn't backed by a true system transition.
         */
        val SYNTHETIC_TRANSITION: IBinder = Binder()

        private val enableAltTabKqsFlatenning: DesktopExperienceFlag =
            DesktopExperienceFlag(
                com.android.launcher3.Flags::enableAltTabKqsFlatenning,
                /* shouldOverrideByDevOption= */ true,
                com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
            )
    }

    /** Defines interface for classes that can listen to changes for task resize. */
    // TODO(b/343931111): Migrate to using TransitionObservers when ready
    interface TaskbarDesktopTaskListener {
        /**
         * [hasTasksRequiringTaskbarRounding] is true when a task is either maximized or snapped
         * left/right and rounded corners are enabled.
         */
        fun onTaskbarCornerRoundingUpdate(hasTasksRequiringTaskbarRounding: Boolean)
    }

    /** Defines interface for entering and exiting desktop windowing mode. */
    interface DesktopModeEntryExitTransitionListener {
        /** [transitionDuration] time it takes to run enter desktop mode transition */
        fun onEnterDesktopModeTransitionStarted(transitionDuration: Int)

        /** [transitionDuration] time it takes to run exit desktop mode transition */
        fun onExitDesktopModeTransitionStarted(transitionDuration: Int, shouldEndUpAtHome: Boolean)
    }

    /** The positions on a screen that a task can snap to. */
    enum class SnapPosition {
        RIGHT,
        LEFT,
    }
}
