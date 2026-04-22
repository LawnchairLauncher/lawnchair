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
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Region
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.SystemProperties
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
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.annotation.BinderThread
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE
import com.android.internal.jank.InteractionJankMonitor
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
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.MultiInstanceHelper.Companion.getComponent
import com.android.wm.shell.common.RemoteCallable
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SingleInstanceRemoteListener
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.DragStartState
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType
import com.android.wm.shell.desktopmode.DesktopRepository.DeskChangeListener
import com.android.wm.shell.desktopmode.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.Companion.DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.DragToDesktopStateListener
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler.FULLSCREEN_ANIMATION_DURATION
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.desktopmode.desktopfirst.DesktopFirstListenerManager
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.minimize.DesktopWindowLimitRemoteHandler
import com.android.wm.shell.desktopmode.multidesks.DeskTransition
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.desktopmode.multidesks.PreserveDisplayRequestHandler
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer.DeskRecreationFactory
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
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy
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
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
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
    private val dragToDisplayTransitionHandler: DragToDisplayTransitionHandler,
    private val moveToDisplayTransitionHandler: DesktopModeMoveToDisplayTransitionHandler,
    private val homeIntentProvider: HomeIntentProvider,
    private val desktopState: DesktopState,
    private val desktopConfig: DesktopConfig,
    private val visualIndicatorUpdateScheduler: VisualIndicatorUpdateScheduler,
    private val desktopFirstListenerManager: Optional<DesktopFirstListenerManager>,
) :
    RemoteCallable<DesktopTasksController>,
    Transitions.TransitionHandler,
    DragAndDropController.DragAndDropListener,
    UserChangeListener {

    private val desktopMode: DesktopModeImpl
    private var taskRepository: DesktopRepository
    private var visualIndicator: DesktopModeVisualIndicator? = null
    private var userId: Int
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
        userId = ActivityManager.getCurrentUser()
        taskRepository = userRepositories.getProfile(userId)
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
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
    ) {
        logV("showDesktopApps")
        activateDefaultDeskInDisplay(displayId, remoteTransition, taskIdToReorderToFront)
    }

    /** Returns whether the given display has an active desk. */
    fun isAnyDeskActive(displayId: Int): Boolean = taskRepository.isAnyDeskActive(displayId)

    /** Returns the id of the active desk in [displayId]. */
    fun getActiveDeskId(displayId: Int): Int? = taskRepository.getActiveDeskId(displayId)

    /**
     * Moves focused task to desktop mode for given [displayId].
     *
     * TODO(b/405381458): use focusTransitionObserver to get the focused task on a certain display
     */
    fun moveFocusedTaskToDesktop(displayId: Int, transitionSource: DesktopModeTransitionSource) {
        val allFocusedTasks = getFocusedNonDesktopTasks(displayId)
        when (allFocusedTasks.size) {
            0 -> return
            // Full screen case
            1 -> {
                if (
                    desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
                        allFocusedTasks.single()
                    )
                ) {
                    return
                }
                moveTaskToDefaultDeskAndActivate(
                    allFocusedTasks.single().taskId,
                    transitionSource = transitionSource,
                )
            }
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
     * Returns all focused tasks in full screen or split screen mode in [displayId] when it is not
     * the home activity.
     */
    private fun getFocusedNonDesktopTasks(displayId: Int): List<RunningTaskInfo> =
        shellTaskOrganizer.getRunningTasks(displayId).filter { taskInfo ->
            val focused = taskInfo.isFocused
            val isNotDesktop =
                if (DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue) {
                    !taskRepository.isActiveTask(taskInfo.taskId)
                } else {
                    taskInfo.windowingMode == WINDOWING_MODE_FULLSCREEN ||
                        taskInfo.windowingMode == WINDOWING_MODE_MULTI_WINDOW
                }
            val isHome = taskInfo.activityType == ACTIVITY_TYPE_HOME
            return@filter focused && isNotDesktop && !isHome
        }

    /** Returns child task from two focused tasks in split screen mode. */
    private fun getSplitFocusedTask(task1: RunningTaskInfo, task2: RunningTaskInfo) =
        if (task1.taskId == task2.parentTaskId) task2 else task1

    @Deprecated("Use isDisplayDesktopFirst() instead.", ReplaceWith("isDisplayDesktopFirst()"))
    private fun forceEnterDesktop(displayId: Int): Boolean {
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
                "forceEnterDesktop cannot find DisplayAreaInfo for displayId=%d. This could happen" +
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
    fun onRecentsInDesktopAnimationFinishing(
        transition: IBinder,
        finishWct: WindowContainerTransaction,
        returnToApp: Boolean,
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
        if (
            activeDeskIdOnRecentsStart == null ||
                !taskRepository.isDeskActive(activeDeskIdOnRecentsStart)
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
                willExitDesktop = true,
                // No need to clean up the wallpaper / home when coming from a recents transition.
                skipWallpaperAndHomeOrdering = true,
                // This is a recents-finish, so taskbar animation on transit start does not apply.
                skipUpdatingExitDesktopListener = true,
            )
        runOnTransitStart?.invoke(transition)
    }

    /** Returns whether a new desk can be created. */
    fun canCreateDesks(repository: DesktopRepository = this.taskRepository): Boolean {
        val deskLimit = desktopConfig.maxDeskLimit
        return deskLimit == 0 || repository.getNumberOfDesks() < deskLimit
    }

    /**
     * Adds a new desk to the given display for the given user and invokes [onResult] once the desk
     * is created, but necessarily activated.
     */
    fun createDesk(
        displayId: Int,
        userId: Int = this.userId,
        enforceDeskLimit: Boolean = true,
        activateDesk: Boolean = false,
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
        if (enforceDeskLimit && !canCreateDesks(repository)) {
            // At the limit, no-op.
            logW("createDesk already at desk-limit, ignoring request")
            return
        }
        createDeskRoot(displayId, userId) { deskId ->
            if (deskId == null) {
                logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
            } else {
                repository.addDesk(displayId = displayId, deskId = deskId)
                onResult(deskId)
                if (activateDesk) {
                    activateDesk(deskId)
                }
            }
        }
    }

    @Deprecated("Use createDeskSuspending() instead.", ReplaceWith("createDeskSuspending()"))
    private fun createDeskImmediate(displayId: Int, userId: Int = this.userId): Int? {
        logV("createDeskImmediate displayId=%d, userId=%d", displayId, userId)
        val repository = userRepositories.getProfile(userId)
        val deskId = createDeskRootImmediate(displayId, userId)
        if (deskId == null) {
            logW("Failed to add desk in displayId=%d for userId=%d", displayId, userId)
            return null
        }
        repository.addDesk(displayId = displayId, deskId = deskId)
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

    private fun createDeskRoot(
        displayId: Int,
        userId: Int = this.userId,
        onResult: (Int?) -> Unit,
    ) {
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

    private suspend fun createDeskRootSuspending(displayId: Int, userId: Int = this.userId): Int? =
        suspendCoroutine { cont ->
            createDeskRoot(displayId, userId) { deskId -> cont.resumeWith(Result.success(deskId)) }
        }

    private fun onDisplayDisconnect(
        disconnectedDisplayId: Int,
        destinationDisplayId: Int,
        transition: IBinder,
    ): WindowContainerTransaction {
        preserveDisplayRequestHandler?.requestPreserveDisplay(disconnectedDisplayId)
        // TODO: b/406320371 - Verify this works with non-system users once the underlying bug is
        //  resolved.
        val wct = WindowContainerTransaction()
        // TODO: b/391652399 - Investigate why sometimes disconnect results in a black background.
        //  Additionally, investigate why wallpaper goes to front for inactive users.
        val desktopModeSupportedOnDisplay =
            desktopState.isDesktopModeSupportedOnDisplay(destinationDisplayId)
        snapEventHandler.onDisplayDisconnected(disconnectedDisplayId, desktopModeSupportedOnDisplay)
        removeWallpaperTask(wct, disconnectedDisplayId)
        removeHomeTask(wct, disconnectedDisplayId)
        userRepositories.forAllRepositories { desktopRepository ->
            val deskIds = desktopRepository.getDeskIds(disconnectedDisplayId).toList()
            if (desktopModeSupportedOnDisplay) {
                // Desktop supported on display; reparent desks, focused desk on top.
                for (deskId in deskIds) {
                    val deskTasks = desktopRepository.getActiveTaskIdsInDesk(deskId)
                    // Remove desk if it's empty.
                    if (deskTasks.isEmpty()) {
                        desksOrganizer.removeDesk(wct, deskId, desktopRepository.userId)
                        desksTransitionObserver.addPendingTransition(
                            DeskTransition.RemoveDesk(
                                token = transition,
                                displayId = disconnectedDisplayId,
                                deskId = deskId,
                                tasks = emptySet(),
                                onDeskRemovedListener = onDeskRemovedListener,
                                runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                            )
                        )
                    } else {
                        // Otherwise, reparent it to the destination display.
                        val toTop =
                            deskTasks.contains(focusTransitionObserver.globallyFocusedTaskId)
                        desksOrganizer.moveDeskToDisplay(wct, deskId, destinationDisplayId, toTop)
                        desksTransitionObserver.addPendingTransition(
                            DeskTransition.ChangeDeskDisplay(
                                transition,
                                deskId,
                                destinationDisplayId,
                            )
                        )
                        updateDesksActivationOnDisconnection(
                                deskId,
                                destinationDisplayId,
                                wct,
                                toTop,
                            )
                            ?.invoke(transition)
                    }
                }
            } else {
                // Desktop not supported on display; reparent tasks to display area, remove desk.
                val tdaInfo =
                    checkNotNull(
                        rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(destinationDisplayId)
                    ) {
                        "Expected to find displayAreaInfo for displayId=$destinationDisplayId"
                    }
                for (deskId in deskIds) {
                    val taskIds = desktopRepository.getActiveTaskIdsInDesk(deskId)
                    for (taskId in taskIds) {
                        val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: continue
                        wct.reparent(
                            task.token,
                            tdaInfo.token,
                            focusTransitionObserver.globallyFocusedTaskId == task.taskId,
                        )
                    }
                    desksOrganizer.removeDesk(wct, deskId, userId)
                    desksTransitionObserver.addPendingTransition(
                        DeskTransition.RemoveDesk(
                            token = transition,
                            displayId = disconnectedDisplayId,
                            deskId = deskId,
                            tasks = emptySet(),
                            onDeskRemovedListener = onDeskRemovedListener,
                            runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                        )
                    )
                    desksTransitionObserver.addPendingTransition(
                        DeskTransition.RemoveDisplay(transition, disconnectedDisplayId)
                    )
                }
            }
        }
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
                )
            } else {
                // The disconnected display's active desk was reparented to the back, ensure it is
                // no longer an active launch root.
                prepareDeskDeactivationIfNeeded(wct, disconnectedDisplayActiveDesk)
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
        if (
            DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue &&
                !desktopState.isDesktopModeSupportedOnDisplay(displayId) &&
                transitionSource != DesktopModeTransitionSource.ADB_COMMAND &&
                transitionSource != DesktopModeTransitionSource.APP_FROM_OVERVIEW
        ) {
            logW("moveTaskToDefaultDeskAndActivate display=$displayId does not support desk")
            return false
        }
        if (
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                !DesktopExperienceFlags.ENABLE_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION.isTrue
        ) {
            val deskId = getOrCreateDefaultDeskId(displayId) ?: return false
            return moveTaskToDesk(
                taskId = taskId,
                deskId = deskId,
                wct = wct,
                transitionSource = transitionSource,
                remoteTransition = remoteTransition,
            )
        }
        mainScope.launch {
            try {
                moveTaskToDesk(
                    taskId = taskId,
                    deskId = getOrCreateDefaultDeskIdSuspending(displayId),
                    wct = wct,
                    transitionSource = transitionSource,
                    remoteTransition = remoteTransition,
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
                taskId,
                deskId,
                wct,
                transitionSource,
                remoteTransition,
                callback,
            )
        }
        logW("moveTaskToDesk taskId=%d not found", taskId)
        return false
    }

    private fun moveBackgroundTaskToDesktop(
        taskId: Int,
        deskId: Int,
        wct: WindowContainerTransaction,
        transitionSource: DesktopModeTransitionSource,
        remoteTransition: RemoteTransition? = null,
        callback: IMoveToDesktopCallback? = null,
    ): Boolean {
        val task = recentTasksController?.findTaskInBackground(taskId)
        if (task == null) {
            logW("moveBackgroundTaskToDesktop taskId=%d not found", taskId)
            return false
        }
        logV("moveBackgroundTaskToDesktop with taskId=%d to deskId=%d", taskId, deskId)

        val runOnTransitStart = addDeskActivationChanges(deskId, wct, task)
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
                .apply { launchWindowingMode = WINDOWING_MODE_FREEFORM }
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
        val displayId = taskRepository.getDisplayForDesk(deskId)
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

        val runOnTransitStart = addDeskActivationWithMovingTaskChanges(deskId, wct, task)

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
            taskRepository.setActiveDesk(displayId = displayId, deskId = deskId)
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
        val deskId = getOrCreateDefaultDeskId(taskInfo.displayId) ?: return
        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTasksController: finalizeDragToDesktop taskId=%d deskId=%d",
            taskInfo.taskId,
            deskId,
        )
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
        val runOnTransitStart = addDeskActivationWithMovingTaskChanges(deskId, wct, taskInfo)
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
                taskRepository.setActiveDesk(displayId = taskInfo.displayId, deskId = deskId)
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
    fun onDesktopSplitSelectAnimComplete(taskInfo: RunningTaskInfo) {
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
    ): ((IBinder) -> Unit) {
        val taskId = taskInfo.taskId
        val deskId = taskRepository.getDeskIdForTask(taskInfo.taskId)
        if (deskId == null && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            error("Did not find desk for task: $taskId")
        }
        snapEventHandler.removeTaskIfTiled(displayId, taskId)
        val shouldExitDesktop =
            willExitDesktop(
                triggerTaskId = taskInfo.taskId,
                displayId = displayId,
                forceExitDesktop = false,
            )
        val desktopExitRunnable =
            performDesktopExitCleanUp(
                wct = wct,
                deskId = deskId,
                displayId = displayId,
                willExitDesktop = shouldExitDesktop,
                shouldEndUpAtHome = true,
            )

        taskRepository.addClosingTask(displayId = displayId, deskId = deskId, taskId = taskId)
        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(displayId, taskId)
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
        val deskId = getOrCreateDefaultDeskId(taskInfo.displayId) ?: return INVALID_TASK_ID
        return taskRepository
            .getExpandedTasksIdsInDeskOrdered(deskId)
            // exclude current task since maximize/restore transition has not taken place yet.
            .filterNot { it == taskInfo.taskId }
            .firstOrNull { !taskRepository.isClosingTask(it) } ?: INVALID_TASK_ID
    }

    fun minimizeTask(taskInfo: RunningTaskInfo, minimizeReason: MinimizeReason) {
        val wct = WindowContainerTransaction()
        val taskId = taskInfo.taskId
        val displayId = taskInfo.displayId
        val deskId =
            taskRepository.getDeskIdForTask(taskInfo.taskId)
                ?: if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                    logW("minimizeTask: desk not found for task: ${taskInfo.taskId}")
                    return
                } else {
                    getOrCreateDefaultDeskId(taskInfo.displayId)
                }
        val isLastTask =
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                taskRepository.isOnlyVisibleNonClosingTaskInDesk(
                    taskId = taskId,
                    deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                    displayId = displayId,
                )
            } else {
                taskRepository.isOnlyVisibleNonClosingTask(taskId = taskId, displayId = displayId)
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
            if (isMultiActivityPip) {
                if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                    desksOrganizer.minimizeTask(
                        wct = wct,
                        deskId = checkNotNull(deskId) { "Expected non-null deskId" },
                        task = taskInfo,
                    )
                } else {
                    wct.reorder(taskInfo.token, /* onTop= */ false)
                }
            }

            // If the task minimizing to PiP is the last task, modify wct to perform Desktop cleanup
            var desktopExitRunnable: RunOnTransitStart? = null
            if (isLastTask) {
                desktopExitRunnable =
                    performDesktopExitCleanUp(
                        wct = wct,
                        deskId = deskId,
                        displayId = displayId,
                        willExitDesktop = true,
                    )
            }
            val transition = freeformTaskTransitionStarter.startPipTransition(wct)
            if (isMultiActivityPip) {
                desktopTasksLimiter.ifPresent {
                    it.addPendingMinimizeChange(
                        transition = transition,
                        displayId = displayId,
                        taskId = taskId,
                        minimizeReason = minimizeReason,
                    )
                }
            }
            desktopExitRunnable?.invoke(transition)
        } else {
            val willExitDesktop =
                if (
                    DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                        DesktopExperienceFlags.ENABLE_EMPTY_DESK_ON_MINIMIZE.isTrue
                )
                    false
                else willExitDesktop(taskId, displayId, forceExitDesktop = false)
            val desktopExitRunnable =
                performDesktopExitCleanUp(
                    wct = wct,
                    deskId = deskId,
                    displayId = displayId,
                    willExitDesktop = willExitDesktop,
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
            doesAnyTaskRequireTaskbarRounding(displayId, taskId)
        )
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
                context.packageManager.getApplicationInfoAsUser(packageName, /* flags= */ 0, userId)
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
                userId,
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
    fun enterFullscreen(displayId: Int, transitionSource: DesktopModeTransitionSource) {
        getFocusedDesktopTask(displayId)?.let {
            snapEventHandler.removeTaskIfTiled(displayId, it.taskId)
            moveToFullscreenWithAnimation(it, it.positionInParent, transitionSource)
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
        logV("moveToFullscreenWithAnimation taskId=%d", task.taskId)
        val displayId =
            when {
                task.displayId != INVALID_DISPLAY -> task.displayId
                focusTransitionObserver.globallyFocusedDisplayId != INVALID_DISPLAY ->
                    focusTransitionObserver.globallyFocusedDisplayId
                else -> DEFAULT_DISPLAY
            }
        val wct = WindowContainerTransaction()

        // When a task is background, update wct to start task.
        if (
            enableAltTabKqsFlatenning.isTrue &&
                shellTaskOrganizer.getRunningTaskInfo(task.taskId) == null &&
                task is RecentTaskInfo
        ) {
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
        val willExitDesktop = willExitDesktop(task.taskId, displayId, forceExitDesktop = true)
        val deactivationRunnable = addMoveToFullscreenChanges(wct, task, willExitDesktop, displayId)

        // We are moving a freeform task to fullscreen, put the home task under the fullscreen task.
        if (!forceEnterDesktop(displayId)) {
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
            !taskRepository.isOnlyVisibleNonClosingTask(task.taskId)
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
        remoteTransition: RemoteTransition? = null,
        unminimizeReason: UnminimizeReason,
    ) {
        val task = shellTaskOrganizer.getRunningTaskInfo(taskId)
        if (task == null) {
            moveBackgroundTaskToFront(taskId, remoteTransition, unminimizeReason)
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
        remoteTransition: RemoteTransition?,
        unminimizeReason: UnminimizeReason,
    ) {
        logV("moveBackgroundTaskToFront taskId=%s unminimizeReason=%s", taskId, unminimizeReason)
        val wct = WindowContainerTransaction()
        val deskIdForTask = taskRepository.getDeskIdForTask(taskId)
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
                getOrCreateDefaultDeskId(displayId) ?: return
            }
        val displayId =
            if (ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue)
                taskRepository.getDisplayForDesk(deskId)
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
        val deskId = taskRepository.getDeskIdForTask(taskInfo.taskId)
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
        val closingTopTransparentTaskId =
            deskId?.let { taskRepository.getTopTransparentFullscreenTaskData(it)?.taskId }
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
                    !taskRepository.isDeskActive(deskId)
                DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue ->
                    !isAnyDeskActive(displayId)
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
        deskId?.let { closeTopTransparentFullscreenTask(launchTransaction, it) }
        val t =
            if (remoteTransition == null) {
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
        if (launchingTaskId != null && taskRepository.isMinimizedTask(launchingTaskId)) {
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
    fun moveToNextDisplay(taskId: Int, predicate: (Int) -> Boolean = { true }) {
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
        moveToDisplay(task, newDisplayId)
    }

    /** Move task to the next display which can host desktop tasks. */
    fun moveToNextDesktopDisplay(taskId: Int) =
        moveToNextDisplay(taskId) { displayId ->
            desktopState.isDesktopModeSupportedOnDisplay(displayId)
        }

    /**
     * Start an intent through a launch transition for starting tasks whose transition does not get
     * handled by [handleRequest]
     */
    fun startLaunchIntentTransition(intent: Intent, options: Bundle, displayId: Int) {
        val wct = WindowContainerTransaction()
        val displayLayout = displayController.getDisplayLayout(displayId) ?: return
        val bounds = calculateDefaultDesktopTaskBounds(displayLayout)
        val deskId = getOrCreateDefaultDeskId(displayId) ?: return
        if (DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue) {
            val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
            cascadeWindow(bounds, displayLayout, deskId, stableBounds)
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

        val wct = WindowContainerTransaction()
        val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        if (displayAreaInfo == null) {
            logW("moveToDisplay: display not found")
            return
        }

        val destinationDeskId = taskRepository.getDefaultDeskId(displayId)
        if (destinationDeskId == null) {
            logW("moveToDisplay: desk not found for display: $displayId")
            return
        }
        snapEventHandler.removeTaskIfTiled(task.displayId, task.taskId)
        // TODO: b/393977830 and b/397437641 - do not assume that freeform==desktop.
        if (!task.isFreeform) {
            addMoveToDeskTaskChanges(wct = wct, task = task, deskId = destinationDeskId)
        } else {
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                desksOrganizer.moveTaskToDesk(wct, destinationDeskId, task)
            }
            if (bounds != null) {
                wct.setBounds(task.token, bounds)
            } else if (DesktopExperienceFlags.ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT.isTrue) {
                applyFreeformDisplayChange(wct, task, displayId, destinationDeskId)
            }
        }

        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            wct.reparent(task.token, displayAreaInfo.token, /* onTop= */ true)
        }

        val activationRunnable = addDeskActivationChanges(destinationDeskId, wct, task)

        val sourceDisplayId = task.displayId
        val sourceDeskId = taskRepository.getDeskIdForTask(task.taskId)
        val shouldExitDesktopIfNeeded =
            ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue ||
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        val deactivationRunnable =
            if (shouldExitDesktopIfNeeded) {
                performDesktopExitCleanupIfNeeded(
                    taskId = task.taskId,
                    deskId = sourceDeskId,
                    displayId = sourceDisplayId,
                    wct = wct,
                    forceToFullscreen = false,
                )
            } else {
                null
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

        val wct = WindowContainerTransaction()
        val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        if (displayAreaInfo == null) {
            logW("moveSplitPairToDisplay: display not found")
            return
        }

        val activeDeskId = taskRepository.getActiveDeskId(displayId)
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
                    willExitDesktop = true,
                    shouldEndUpAtHome = false,
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
        val currentTaskBounds = taskInfo.configuration.windowConfiguration.bounds
        desktopModeEventLogger.logTaskResizingStarted(
            interaction.resizeTrigger,
            interaction.inputMethod,
            taskInfo,
            currentTaskBounds.width(),
            currentTaskBounds.height(),
            displayController,
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
            val taskBoundsBeforeMaximize =
                taskRepository.removeBoundsBeforeMaximize(taskInfo.taskId)
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
            taskRepository.saveBoundsBeforeMaximize(taskInfo.taskId, currentTaskBounds)
            destinationBounds.set(calculateMaximizeBounds(displayLayout, taskInfo))
        }

        val shouldRestoreToSnap =
            isMaximized && isTaskSnappedToHalfScreen(taskInfo, destinationBounds)

        logD("willMaximize = %s", willMaximize)
        logD("shouldRestoreToSnap = %s", shouldRestoreToSnap)

        val doesAnyTaskRequireTaskbarRounding =
            willMaximize ||
                shouldRestoreToSnap ||
                doesAnyTaskRequireTaskbarRounding(taskInfo.displayId, taskInfo.taskId)

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
        if (isTaskMaximized(taskInfo, displayController)) {
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

    private fun isMaximizedToStableBoundsEdges(
        taskInfo: RunningTaskInfo,
        stableBounds: Rect,
    ): Boolean {
        val currentTaskBounds = taskInfo.configuration.windowConfiguration.bounds
        return isTaskBoundsEqual(currentTaskBounds, stableBounds)
    }

    /** Returns if current task bound is snapped to half screen */
    private fun isTaskSnappedToHalfScreen(
        taskInfo: RunningTaskInfo,
        taskBounds: Rect = taskInfo.configuration.windowConfiguration.bounds,
    ): Boolean =
        getSnapBounds(taskInfo.displayId, SnapPosition.LEFT) == taskBounds ||
            getSnapBounds(taskInfo.displayId, SnapPosition.RIGHT) == taskBounds

    @VisibleForTesting
    fun doesAnyTaskRequireTaskbarRounding(displayId: Int, excludeTaskId: Int? = null): Boolean {
        val doesAnyTaskRequireTaskbarRounding =
            taskRepository
                .getExpandedTasksOrdered(displayId)
                // exclude current task since maximize/restore transition has not taken place yet.
                .filterNot { taskId -> taskId == excludeTaskId }
                .any { taskId ->
                    val taskInfo = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: return false
                    val displayLayout = displayController.getDisplayLayout(taskInfo.displayId)
                    val stableBounds = Rect().also { displayLayout?.getStableBounds(it) }
                    logD("taskInfo = %s", taskInfo)
                    logD(
                        "isTaskSnappedToHalfScreen(taskInfo) = %s",
                        isTaskSnappedToHalfScreen(taskInfo),
                    )
                    logD(
                        "isMaximizedToStableBoundsEdges(taskInfo, stableBounds) = %s",
                        isMaximizedToStableBoundsEdges(taskInfo, stableBounds),
                    )
                    isTaskSnappedToHalfScreen(taskInfo) ||
                        isMaximizedToStableBoundsEdges(taskInfo, stableBounds)
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
        desktopModeEventLogger.logTaskResizingStarted(
            resizeTrigger,
            inputMethod,
            taskInfo,
            currentDragBounds.width(),
            currentDragBounds.height(),
            displayController,
        )

        val destinationBounds = getSnapBounds(taskInfo.displayId, position)
        desktopModeEventLogger.logTaskResizingEnded(
            resizeTrigger,
            inputMethod,
            taskInfo,
            destinationBounds.width(),
            destinationBounds.height(),
            displayController,
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
            Toast.makeText(
                    getContext(),
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
                    Toast.makeText(
                            context,
                            com.android.wm.shell.R.string.desktop_mode_non_resizable_snap_text,
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
        wct: WindowContainerTransaction,
        newTaskIdInFront: Int? = null,
    ): Int? {
        logV("bringDesktopAppsToFront, newTaskId=%d", newTaskIdInFront)
        prepareForDeskActivation(displayId, wct)

        val expandedTasksOrderedFrontToBack = taskRepository.getExpandedTasksOrdered(displayId)
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
                    wct.startTask(taskId, createActivityOptionsForStartTask().toBundle())
                }
            }

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(displayId)
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

    private fun addLaunchHomePendingIntent(wct: WindowContainerTransaction, displayId: Int) {
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
        forceExitDesktop: Boolean,
    ): Boolean {
        if (forceExitDesktop && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // |forceExitDesktop| is true when the callers knows we'll exit desktop, such as when
            // explicitly going fullscreen, so there's no point in checking the desktop state.
            return true
        }
        if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
            if (!taskRepository.isOnlyVisibleNonClosingTask(triggerTaskId, displayId)) {
                return false
            }
        } else {
            if (!taskRepository.isOnlyVisibleNonClosingTask(triggerTaskId)) {
                return false
            }
        }
        return true
    }

    private fun performDesktopExitCleanupIfNeeded(
        taskId: Int,
        deskId: Int? = null,
        displayId: Int,
        wct: WindowContainerTransaction,
        forceToFullscreen: Boolean,
    ): RunOnTransitStart? {
        if (!willExitDesktop(taskId, displayId, forceToFullscreen)) {
            return null
        }
        // TODO: b/394268248 - update remaining callers to pass in a |deskId| and apply the
        //  |RunOnTransitStart| when the transition is started.
        return performDesktopExitCleanUp(
            wct = wct,
            deskId = deskId,
            displayId = displayId,
            willExitDesktop = true,
            shouldEndUpAtHome = true,
        )
    }

    /** TODO: b/394268248 - update [deskId] to be non-null. */
    fun performDesktopExitCleanUp(
        wct: WindowContainerTransaction,
        deskId: Int?,
        displayId: Int,
        willExitDesktop: Boolean,
        shouldEndUpAtHome: Boolean = true,
        skipWallpaperAndHomeOrdering: Boolean = false,
        skipUpdatingExitDesktopListener: Boolean = false,
    ): RunOnTransitStart? {
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
        if (
            !skipWallpaperAndHomeOrdering ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            if (ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER.isTrue) {
                moveWallpaperActivityToBack(wct, displayId)
            } else {
                removeWallpaperTask(wct, displayId)
            }
            if (shouldEndUpAtHome) {
                // If the transition should end up with user going to home, launch home with a
                // pending intent.
                addLaunchHomePendingIntent(wct, displayId)
            }
        }
        return prepareDeskDeactivationIfNeeded(wct, deskId)
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
                // Only handle open or to front transitions
                request.type != TRANSIT_OPEN && request.type != TRANSIT_TO_FRONT -> {
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
                // Check if the closing task needs to be handled
                TransitionUtil.isClosingType(request.type) ->
                    handleTaskClosing(triggerTask, transition, request.type)
                // Check if the top task shouldn't be allowed to enter desktop mode
                isIncompatibleTask(triggerTask) ->
                    handleIncompatibleTaskLaunch(triggerTask, transition)
                // Check if fullscreen task should be updated
                triggerTask.isFullscreen ->
                    handleFullscreenTaskLaunch(triggerTask, transition, request.type)
                // Check if freeform task should be updated
                triggerTask.isFreeform -> handleFreeformTaskLaunch(triggerTask, transition)
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
        if (!isAnyDeskActive(triggerTask.displayId)) {
            return false
        }
        if (!TransitionUtil.isOpeningType(info.type)) {
            return false
        }
        taskRepository.getTaskInFullImmersiveState(displayId = triggerTask.displayId)
            ?: return false
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
    private fun isFreeformRelaunch(triggerTask: RunningTaskInfo, request: TransitionRequestInfo) =
        (triggerTask.windowingMode == WINDOWING_MODE_FREEFORM &&
            TransitionUtil.isOpeningType(request.type) &&
            taskRepository.isActiveTask(triggerTask.taskId))

    /** Returns whether a fullscreen task is being relaunched on the same display or not. */
    private fun isFullscreenRelaunch(
        triggerTask: RunningTaskInfo,
        @WindowManager.TransitionType requestType: Int,
    ): Boolean {
        // Do not treat fullscreen-in-desktop as fullscreen.
        if (taskRepository.isActiveTask(triggerTask.taskId)) return false

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
        val deskId = getOrCreateDefaultDeskId(callingTask.displayId) ?: return
        if (callingTask.isFreeform) {
            val requestedTaskInfo = shellTaskOrganizer.getRunningTaskInfo(requestedTaskId)
            if (requestedTaskInfo?.isFreeform == true) {
                // If requested task is an already open freeform task, just move it to front.
                moveTaskToFront(
                    requestedTaskId,
                    unminimizeReason = UnminimizeReason.APP_HANDLE_MENU_BUTTON,
                )
            } else {
                moveTaskToDesk(
                    requestedTaskId,
                    deskId,
                    WindowContainerTransaction(),
                    DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
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
        val baseActivity = callingTaskInfo.baseActivity ?: return
        val userHandle = UserHandle.of(callingTaskInfo.userId)
        val fillIn: Intent =
            userProfileContexts
                .getOrCreate(callingTaskInfo.userId)
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
        val deskId =
            taskRepository.getDeskIdForTask(callingTaskInfo.taskId)
                ?: getOrCreateDefaultDeskId(callingTaskInfo.displayId)
                ?: return
        val options = createNewWindowOptions(callingTaskInfo, deskId)
        when (options.launchWindowingMode) {
            WINDOWING_MODE_MULTI_WINDOW -> {
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
                )
            }
        }
    }

    private fun createNewWindowOptions(callingTask: RunningTaskInfo, deskId: Int): ActivityOptions {
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
            if (ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue)
                taskRepository.getDisplayForDesk(deskId)
            else DEFAULT_DISPLAY
        return ActivityOptions.makeBasic().apply {
            launchWindowingMode = newTaskWindowingMode
            pendingIntentBackgroundActivityStartMode =
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            launchBounds = bounds
            launchDisplayId = displayId
        }
    }

    private fun handleHomeTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        logV(
            "DesktopTasksController: handleHomeTaskLaunch taskId=%s userId=%s currentUserId=%d",
            task.taskId,
            task.userId,
            userId,
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
                willExitDesktop = true,
                shouldEndUpAtHome = true,
                // No need to clean up the wallpaper / home order if Home is launching directly.
                skipWallpaperAndHomeOrdering = true,
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
                        forceExitDesktop = true,
                    ),
            )
        runOnTransitStart?.invoke(transition)
        wct.reorder(task.token, true)
        return wct
    }

    private fun handleFreeformTaskLaunch(
        task: RunningTaskInfo,
        transition: IBinder,
    ): WindowContainerTransaction? {
        val anyDeskActive = taskRepository.isAnyDeskActive(task.displayId)
        val forceEnterDesktop = forceEnterDesktop(task.displayId)
        logV(
            "handleFreeformTaskLaunch taskId=%d displayId=%d anyDeskActive=%b forceEnterDesktop=%b",
            task.taskId,
            task.displayId,
            anyDeskActive,
            forceEnterDesktop,
        )
        if (keyguardManager.isKeyguardLocked) {
            // Do NOT handle freeform task launch when locked.
            // It will be launched in fullscreen windowing mode (Details: b/160925539)
            logV("skip keyguard is locked")
            return null
        }
        val deskId = getOrCreateDefaultDeskId(task.displayId) ?: return null
        val isKnownDesktopTask = taskRepository.isActiveTask(task.taskId)
        val shouldEnterDesktop =
            forceEnterDesktop
            // New tasks should be forced into desktop, while known desktop tasks should
            // be moved outside of desktop.
            || !isKnownDesktopTask
        logV(
            "handleFreeformTaskLaunch taskId=%d displayId=%d anyDeskActive=%b" +
                " isKnownDesktopTask=%b shouldEnterDesktop=%b",
            task.taskId,
            task.displayId,
            anyDeskActive,
            isKnownDesktopTask,
            shouldEnterDesktop,
        )
        val wct = WindowContainerTransaction()
        if (!anyDeskActive && !shouldEnterDesktop) {
            // We are outside of desktop mode and an already existing desktop task is being
            // launched. We should make this task go to fullscreen instead of freeform. Note
            // that this means any re-launch of a freeform window outside of desktop will be in
            // fullscreen as long as default-desktop flag is disabled.
            val runOnTransitStart =
                addMoveToFullscreenChanges(
                    wct = wct,
                    taskInfo = task,
                    willExitDesktop = false, // Already outside desktop.
                )
            runOnTransitStart?.invoke(transition)
            return wct
        }
        // At this point we're either already in desktop and this task is moving to it, or we're
        // about to enter desktop with this task in it.
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            // Make sure the launching task is moved into the desk.
            desksOrganizer.moveTaskToDesk(wct, deskId, task)
        }
        if (!anyDeskActive) {
            // We are outside of desktop and should enter desktop.
            val runOnTransitStart = addDeskActivationChanges(deskId, wct, task)
            runOnTransitStart?.invoke(transition)
            wct.reorder(task.token, true)
            return wct
        }
        // We were already in desktop.
        val inheritedTaskBounds =
            getInheritedExistingTaskBounds(taskRepository, shellTaskOrganizer, task, deskId)
        if (!taskRepository.isActiveTask(task.taskId) && inheritedTaskBounds != null) {
            // Inherit bounds from closing task instance to prevent application jumping different
            // cascading positions.
            wct.setBounds(task.token, inheritedTaskBounds)
        }
        // TODO(b/365723620): Handle non running tasks that were launched after reboot.
        // If task is already visible, it must have been handled already and added to desktop mode.
        // Cascade task only if it's not visible yet and has no inherited bounds.
        if (
            inheritedTaskBounds == null &&
                DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue() &&
                !taskRepository.isVisibleTask(task.taskId)
        ) {
            val displayLayout = displayController.getDisplayLayout(task.displayId)
            if (displayLayout != null) {
                val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
                val initialBounds = Rect(task.configuration.windowConfiguration.bounds)
                cascadeWindow(initialBounds, displayLayout, deskId, stableBounds)
                wct.setBounds(task.token, initialBounds)
            }
        }
        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(task.token, desktopConfig.desktopDensityOverride)
        }
        // The task that is launching might have been minimized before - in which case this is an
        // unminimize action.
        if (taskRepository.isMinimizedTask(task.taskId)) {
            addPendingUnminimizeTransition(
                transition,
                task.displayId,
                task.taskId,
                UnminimizeReason.TASK_LAUNCH,
            )
        }
        // Desktop Mode is showing and we're launching a new Task:
        // 1) Exit immersive if needed.
        desktopImmersiveController.exitImmersiveIfApplicable(
            transition = transition,
            wct = wct,
            displayId = task.displayId,
            reason = DesktopImmersiveController.ExitReason.TASK_LAUNCH,
        )
        // 2) minimize a Task if needed.
        // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition flag
        val taskIdToMinimize = addAndGetMinimizeChanges(deskId, wct, task.taskId)
        // 3) Remove top transparent fullscreen task if needed.
        val closingTopTransparentTaskId =
            taskRepository.getTopTransparentFullscreenTaskData(deskId)?.taskId
        closeTopTransparentFullscreenTask(wct, deskId)
        addPendingAppLaunchTransition(
            transition,
            task.taskId,
            taskIdToMinimize,
            closingTopTransparentTaskId,
        )
        if (taskIdToMinimize != null) {
            addPendingMinimizeTransition(transition, taskIdToMinimize, MinimizeReason.TASK_LIMIT)
            snapEventHandler.removeTaskIfTiled(task.displayId, taskIdToMinimize)
            return wct
        }
        addPendingTaskLimitTransition(transition, deskId, task.taskId)
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
        if (shouldFullscreenTaskLaunchSwitchToDesktop(task, requestType)) {
            logD("Switch fullscreen task to freeform on transition: taskId=%d", task.taskId)
            return WindowContainerTransaction().also { wct ->
                val deskId = getOrCreateDefaultDeskId(task.displayId) ?: return@also
                addMoveToDeskTaskChanges(wct = wct, task = task, deskId = deskId)
                val runOnTransitStart: RunOnTransitStart? =
                    if (
                        task.baseIntent.flags.and(Intent.FLAG_ACTIVITY_TASK_ON_HOME) != 0 ||
                            !isAnyDeskActive(task.displayId)
                    ) {
                        // In some launches home task is moved behind new task being launched. Make
                        // sure that's not the case for launches in desktop. Also, if this launch is
                        // the first one to trigger the desktop mode (e.g., when
                        // [forceEnterDesktop()]), activate the desk here.
                        val activationRunnable =
                            addDeskActivationChanges(
                                deskId = deskId,
                                wct = wct,
                                newTask = task,
                                addPendingLaunchTransition = true,
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
                                taskRepository.getTopTransparentFullscreenTaskData(deskId)?.taskId
                            closeTopTransparentFullscreenTask(wct, deskId)
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
        } else if (taskRepository.isActiveTask(task.taskId)) {
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
                            forceExitDesktop = true,
                        ),
                )
            runOnTransitStart?.invoke(transition)
            return wct
        }
        return null
    }

    private fun shouldFreeformTaskLaunchSwitchToFullscreen(task: RunningTaskInfo): Boolean =
        !isAnyDeskActive(task.displayId)

    private fun shouldFullscreenTaskLaunchSwitchToDesktop(
        task: RunningTaskInfo,
        @WindowManager.TransitionType requestType: Int,
    ): Boolean {
        val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(task.displayId)
        if (
            DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX.isTrue &&
                isDesktopFirst &&
                isFullscreenRelaunch(task, requestType)
        ) {
            logV(
                "shouldFullscreenTaskLaunchSwitchToDesktop: no switch as fullscreen relaunch on" +
                    " desktop-first display#%s",
                task.displayId,
            )
            return false
        }

        val isAnyDeskActive = isAnyDeskActive(task.displayId)
        logV(
            "shouldFullscreenTaskLaunchSwitchToDesktop, isAnyDeskActive=%s, isDesktopFirst=%s",
            isAnyDeskActive,
            isDesktopFirst,
        )
        return isAnyDeskActive || isDesktopFirst
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
        val inDesktop = isAnyDeskActive(displayId)
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
            if (!inDesktop && !forceEnterDesktop(displayId)) return null
            if (
                isTransparentTask &&
                    (DesktopExperienceFlags.FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK.isTrue ||
                        DesktopModeFlags
                            .INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC
                            .isTrue)
            ) {
                // Only update task repository for transparent task.
                val deskId = taskRepository.getActiveDeskId(displayId)
                deskId?.let { taskRepository.setTopTransparentFullscreenTaskData(it, task) }
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
            val deskId = taskRepository.getActiveDeskId(displayId)
            deskId?.let { taskRepository.setTopTransparentFullscreenTaskData(it, task) }
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
        if (!isAnyDeskActive(task.displayId)) return null
        val deskId = taskRepository.getDeskIdForTask(task.taskId)
        if (deskId == null && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            return null
        }
        val wct = WindowContainerTransaction()
        if (
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                requestType == TRANSIT_TO_BACK
        ) {
            val isLastTask = taskRepository.isOnlyVisibleTask(task.taskId, task.displayId)
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
                    wct = wct,
                    forceToFullscreen = false,
                )
            deactivationRunnable?.invoke(transition)
        }

        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) {
            taskRepository.addClosingTask(
                displayId = task.displayId,
                deskId = deskId,
                taskId = task.taskId,
            )
            snapEventHandler.removeTaskIfTiled(task.displayId, task.taskId)
        }

        taskbarDesktopTaskListener?.onTaskbarCornerRoundingUpdate(
            doesAnyTaskRequireTaskbarRounding(task.displayId, task.taskId)
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
    ): RunOnTransitStart? {
        val runOnTransitStart = addDeskActivationChanges(deskId, wct, task)
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
        val targetDisplayId = taskRepository.getDisplayForDesk(deskId)
        val displayLayout = displayController.getDisplayLayout(targetDisplayId) ?: return
        logV(
            "addMoveToDeskTaskChanges taskId=%d deskId=%d displayId=%d",
            task.taskId,
            deskId,
            targetDisplayId,
        )
        val inheritedTaskBounds =
            getInheritedExistingTaskBounds(taskRepository, shellTaskOrganizer, task, deskId)
        if (inheritedTaskBounds != null) {
            // Inherit bounds from closing task instance to prevent application jumping different
            // cascading positions.
            wct.setBounds(task.token, inheritedTaskBounds)
        } else {
            val initialBounds = getInitialBounds(displayLayout, task, deskId)
            if (canChangeTaskPosition(task)) {
                wct.setBounds(task.token, initialBounds)
            }
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
     * Apply changes to move a freeform task from one display to another, which includes handling
     * density changes between displays.
     */
    private fun applyFreeformDisplayChange(
        wct: WindowContainerTransaction,
        taskInfo: RunningTaskInfo,
        destDisplayId: Int,
        destDeskId: Int,
    ) {
        val sourceLayout = displayController.getDisplayLayout(taskInfo.displayId) ?: return
        val destLayout = displayController.getDisplayLayout(destDisplayId) ?: return
        val bounds = taskInfo.configuration.windowConfiguration.bounds
        val scaledWidth = bounds.width() * destLayout.densityDpi() / sourceLayout.densityDpi()
        val scaledHeight = bounds.height() * destLayout.densityDpi() / sourceLayout.densityDpi()
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
        val boundsWithinDisplay =
            if (destWidthMargin >= 0 && destHeightMargin >= 0) {
                Rect(0, 0, scaledWidth, scaledHeight).apply {
                    offsetTo(
                        scaledLeft.coerceIn(0, destWidthMargin),
                        scaledTop.coerceIn(0, destHeightMargin),
                    )
                }
            } else {
                getInitialBounds(destLayout, taskInfo, destDeskId)
            }
        wct.setBounds(taskInfo.token, boundsWithinDisplay)
    }

    private fun getInitialBounds(
        displayLayout: DisplayLayout,
        taskInfo: RunningTaskInfo,
        deskId: Int,
    ): Rect {
        val bounds =
            if (ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS.isTrue) {
                // If caption insets should be excluded from app bounds, ensure caption insets
                // are excluded from the ideal initial bounds when scaling non-resizeable apps.
                // Caption insets stay fixed and don't scale with bounds.
                val displayId = taskRepository.getDisplayForDesk(deskId)
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

        if (DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue) {
            cascadeWindow(bounds, displayLayout, deskId)
        }
        return bounds
    }

    /** Applies the changes needed to enter fullscreen and clean up the desktop if needed. */
    private fun addMoveToFullscreenChanges(
        wct: WindowContainerTransaction,
        taskInfo: TaskInfo,
        willExitDesktop: Boolean,
        displayId: Int = taskInfo.displayId,
    ): RunOnTransitStart? {
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)!!
        val tdaWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
        val targetWindowingMode =
            if (tdaWindowingMode == WINDOWING_MODE_FULLSCREEN) {
                // Display windowing is fullscreen, set to undefined and inherit it
                WINDOWING_MODE_UNDEFINED
            } else {
                WINDOWING_MODE_FULLSCREEN
            }
        wct.setWindowingMode(taskInfo.token, targetWindowingMode)
        wct.setBounds(taskInfo.token, Rect())
        if (desktopConfig.useDesktopOverrideDensity) {
            wct.setDensityDpi(taskInfo.token, getDefaultDensityDpi())
        }
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            wct.reparent(taskInfo.token, tdaInfo.token, /* onTop= */ true)
        } else if (enableAltTabKqsFlatenning.isTrue) {
            // Until multiple desktops is enabled, we still want to reorder the task to top so that
            // if the task is not on top we can still switch to it using Alt+Tab.
            wct.reorder(taskInfo.token, /* onTop= */ true)
        }

        val deskId =
            taskRepository.getDeskIdForTask(taskInfo.taskId)
                ?: if (enableAltTabKqsFlatenning.isTrue) {
                    taskRepository.getActiveDeskId(displayId)
                } else {
                    null
                }

        return performDesktopExitCleanUp(
            wct = wct,
            deskId = deskId,
            displayId = displayId,
            willExitDesktop = willExitDesktop,
            shouldEndUpAtHome = false,
        )
    }

    private fun cascadeWindow(
        bounds: Rect,
        displayLayout: DisplayLayout,
        deskId: Int,
        stableBounds: Rect = Rect(),
    ) {
        if (stableBounds.isEmpty) {
            displayLayout.getStableBoundsForDesktopMode(stableBounds)
        }

        val activeTasks = taskRepository.getExpandedTasksIdsInDeskOrdered(deskId)
        activeTasks
            .firstOrNull { !taskRepository.isClosingTask(it) }
            ?.let { activeTask ->
                shellTaskOrganizer.getRunningTaskInfo(activeTask)?.let {
                    cascadeWindow(
                        context.resources,
                        stableBounds,
                        it.configuration.windowConfiguration.bounds,
                        bounds,
                    )
                }
            }
    }

    /**
     * Adds split screen changes to a transaction. Note that bounds are not reset here due to
     * animation; see {@link onDesktopSplitSelectAnimComplete}
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
        wct.setDensityDpi(taskInfo.token, getDefaultDensityDpi())
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
    ): Int? {
        if (DesktopExperienceFlags.ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION.isTrue) return null
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
        // TODO b/359523924: pass immersive task here?
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

    private fun activateDefaultDeskInDisplay(
        displayId: Int,
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
    ) {
        val deskId = getOrCreateDefaultDeskId(displayId) ?: return
        activateDesk(deskId, remoteTransition, taskIdToReorderToFront)
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
        displayId: Int = taskRepository.getDisplayForDesk(deskId),
    ): RunOnTransitStart {
        val newTaskIdInFront = newTask?.taskId
        logV(
            "addDeskActivationChanges newTaskId=%d deskId=%d displayId=%d",
            newTask?.taskId,
            deskId,
            displayId,
        )
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            val taskIdToMinimize = bringDesktopAppsToFront(displayId, wct, newTask?.taskId)
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
                        taskRepository.getTopTransparentFullscreenTaskData(deskId)?.taskId
                    closeTopTransparentFullscreenTask(wct, deskId)
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
            doesAnyTaskRequireTaskbarRounding(displayId)
        )
        val expandedTasksOrderedFrontToBack =
            taskRepository.getExpandedTasksIdsInDeskOrdered(deskId = deskId)
        // If we're adding a new Task we might need to minimize an old one
        // TODO: b/32994943 - remove dead code when cleaning up task_limit_separate_transition flag
        val taskIdToMinimize =
            getTaskIdToMinimize(expandedTasksOrderedFrontToBack, newTaskIdInFront)
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
                        wct.startTask(taskId, createActivityOptionsForStartTask().toBundle())
                    } else {
                        desksOrganizer.reorderTaskToFront(wct, deskId, runningTaskInfo)
                    }
                }
        }
        val deactivatingDesk = taskRepository.getActiveDeskId(displayId)?.takeIf { it != deskId }
        val deactivationRunnable = prepareDeskDeactivationIfNeeded(wct, deactivatingDesk)
        return { transition ->
            val activateDeskTransition =
                if (newTaskIdInFront != null) {
                    DeskTransition.ActivateDeskWithTask(
                        token = transition,
                        displayId = displayId,
                        deskId = deskId,
                        enterTaskId = newTaskIdInFront,
                        runOnTransitEnd = { snapEventHandler.onDeskActivated(deskId, displayId) },
                    )
                } else {
                    DeskTransition.ActivateDesk(
                        token = transition,
                        displayId = displayId,
                        deskId = deskId,
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

    private fun closeTopTransparentFullscreenTask(wct: WindowContainerTransaction, deskId: Int) {
        if (!DesktopExperienceFlags.FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK.isTrue) return
        val data = taskRepository.getTopTransparentFullscreenTaskData(deskId)
        if (data != null) {
            logD("closeTopTransparentFullscreenTask: taskId=%d, deskId=%d", data.taskId, deskId)
            wct.removeTask(data.token)
        }
    }

    /** Activates the desk at the given index if it exists. */
    fun activatePreviousDesk(displayId: Int) {
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
        val activeDeskId = taskRepository.getActiveDeskId(validDisplay)
        if (activeDeskId == null) {
            logV("activatePreviousDesk no active desk in display=%d", validDisplay)
            return
        }
        val destinationDeskId = taskRepository.getPreviousDeskId(activeDeskId)
        if (destinationDeskId == null) {
            logV(
                "activatePreviousDesk no previous desk before deskId=%d in display=%d",
                activeDeskId,
                validDisplay,
            )
            // TODO: b/389957556 - add animation.
            return
        }
        logV("activatePreviousDesk from deskId=%d to deskId=%d", activeDeskId, destinationDeskId)
        activateDesk(destinationDeskId)
    }

    /** Activates the desk at the given index if it exists. */
    fun activateNextDesk(displayId: Int) {
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
        val activeDeskId = taskRepository.getActiveDeskId(validDisplay)
        if (activeDeskId == null) {
            logV("activateNextDesk no active desk in display=%d", validDisplay)
            return
        }
        val destinationDeskId = taskRepository.getNextDeskId(activeDeskId)
        if (destinationDeskId == null) {
            logV(
                "activateNextDesk no next desk before deskId=%d in display=%d",
                activeDeskId,
                validDisplay,
            )
            // TODO: b/389957556 - add animation.
            return
        }
        logV("activateNextDesk from deskId=%d to deskId=%d", activeDeskId, destinationDeskId)
        activateDesk(destinationDeskId)
    }

    /**
     * Activates the given desk and brings [taskIdToReorderToFront] to front if provided and is
     * already on the given desk.
     */
    fun activateDesk(
        deskId: Int,
        remoteTransition: RemoteTransition? = null,
        taskIdToReorderToFront: Int? = null,
    ) {
        logD(
            "activateDesk deskId=%d taskIdToReorderToFront=%d remoteTransition=%s",
            deskId,
            taskIdToReorderToFront,
            remoteTransition,
        )
        if (!taskRepository.getAllDeskIds().contains(deskId)) {
            logW(
                "Request to activate desk=%d but desk not found for user=%d",
                deskId,
                taskRepository.userId,
            )
            return
        }
        if (
            taskIdToReorderToFront != null &&
                taskRepository.getDeskIdForTask(taskIdToReorderToFront) != deskId
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
        val runOnTransitStart = addDeskActivationChanges(deskId, wct, newTaskInFront)

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
                    createActivityOptionsForStartTask().toBundle(),
                )
            }
            else -> {
                logW("activateDesk taskIdToReorderToFront=%d not found", taskIdToReorderToFront)
            }
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

    /**
     * TODO: b/393978539 - Deactivation should not happen in desktop-first devices when going home.
     */
    private fun prepareDeskDeactivationIfNeeded(
        wct: WindowContainerTransaction,
        deskId: Int?,
    ): RunOnTransitStart? {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return null
        if (deskId == null) return null
        desksOrganizer.deactivateDesk(wct, deskId)
        return { transition ->
            desksTransitionObserver.addPendingTransition(
                DeskTransition.DeactivateDesk(
                    token = transition,
                    deskId = deskId,
                    runOnTransitEnd = { snapEventHandler.onDeskDeactivated(deskId) },
                )
            )
        }
    }

    /** Removes the default desk in the given display. */
    @Deprecated("Deprecated with multi-desks.", ReplaceWith("removeDesk()"))
    fun removeDefaultDeskInDisplay(displayId: Int) {
        val deskId = getOrCreateDefaultDeskId(displayId) ?: return
        removeDesk(displayId = displayId, deskId = deskId)
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
    private fun getOrCreateDefaultDeskId(displayId: Int): Int? {
        val existingDefaultDeskId = taskRepository.getDefaultDeskId(displayId)
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

    private suspend fun getOrCreateDefaultDeskIdSuspending(displayId: Int): Int =
        taskRepository.getDefaultDeskId(displayId)
            ?: createDeskSuspending(displayId, userId, enforceDeskLimit = false)

    /** Removes the given desk. */
    fun removeDesk(deskId: Int, desktopRepository: DesktopRepository = taskRepository) {
        if (!desktopRepository.getAllDeskIds().contains(deskId)) {
            logW("Request to remove desk=%d but desk not found for user=%d", deskId, userId)
            return
        }
        val displayId = desktopRepository.getDisplayForDesk(deskId)
        removeDesk(displayId = displayId, deskId = deskId, desktopRepository = desktopRepository)
    }

    /** Removes all the available desks on all displays. */
    fun removeAllDesks() {
        taskRepository.getAllDeskIds().forEach { deskId -> removeDesk(deskId) }
    }

    private fun removeDesk(
        displayId: Int,
        deskId: Int,
        desktopRepository: DesktopRepository = taskRepository,
    ) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) return
        logV("removeDesk deskId=%d from displayId=%d", deskId, displayId)

        val tasksToRemove =
            if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                desktopRepository.getActiveTaskIdsInDesk(deskId)
            } else {
                // TODO: 362720497 - make sure minimized windows are also removed in WM
                //  and the repository.
                desktopRepository.removeDesk(deskId)
            }

        val wct = WindowContainerTransaction()
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
        }
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue && wct.isEmpty) return
        val transition = transitions.startTransition(TRANSIT_CLOSE, wct, /* handler= */ null)
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desksTransitionObserver.addPendingTransition(
                DeskTransition.RemoveDesk(
                    token = transition,
                    displayId = displayId,
                    deskId = deskId,
                    tasks = tasksToRemove,
                    onDeskRemovedListener = onDeskRemovedListener,
                    runOnTransitEnd = { snapEventHandler.onDeskRemoved(deskId) },
                )
            )
        }
    }

    /** Enter split by using the focused desktop task in given `displayId`. */
    fun enterSplit(displayId: Int, leftOrTop: Boolean) {
        getFocusedDesktopTask(displayId)?.let { requestSplit(it, leftOrTop) }
    }

    private fun getFocusedDesktopTask(displayId: Int): RunningTaskInfo? {
        if (
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue ||
                !DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue
        ) {
            return shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo ->
                taskInfo.isFocused && taskInfo.windowingMode == WINDOWING_MODE_FREEFORM
            }
        }
        val deskId = taskRepository.getActiveDeskId(displayId) ?: return null
        return shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo ->
            taskInfo.isFocused && taskRepository.isActiveTaskInDesk(taskInfo.taskId, deskId)
        }
    }

    /**
     * Requests a task be transitioned from desktop to split select. Applies needed windowing
     * changes if this transition is enabled.
     */
    @JvmOverloads
    fun requestSplit(taskInfo: RunningTaskInfo, leftOrTop: Boolean = false) {
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
                val deskId = taskRepository.getDeskIdForTask(taskInfo.taskId)
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
    ) {
        if (taskInfo.configuration.windowConfiguration.windowingMode != WINDOWING_MODE_FREEFORM) {
            return
        }

        val indicator = getVisualIndicator() ?: return
        val indicatorType =
            indicator.updateIndicatorType(
                displayId,
                PointF(inputCoordinate.x, currentDragBounds.top.toFloat()),
            )
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

                if (destinationBounds == dragStartBounds) {
                    // There's no actual difference between the start and end bounds, so while a
                    // WCT change isn't needed, the dragged surface still needs to be snapped back
                    // to its original location.
                    releaseVisualIndicator()
                    returnToDragStartAnimator.start(
                        taskInfo.taskId,
                        taskSurface,
                        startBounds = currentDragBounds,
                        endBounds = dragStartBounds,
                    )
                    return
                }

                val newDisplayId = motionEvent.getDisplayId()
                val displayAreaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(newDisplayId)
                val isCrossDisplayDrag =
                    DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue() &&
                        newDisplayId != taskInfo.getDisplayId() &&
                        displayAreaInfo != null

                if (isCrossDisplayDrag) {
                    moveToDisplay(
                        taskInfo,
                        newDisplayId,
                        destinationBounds,
                        dragToDisplayTransitionHandler,
                    )
                } else {
                    // Update task bounds so that the task position will match the position of its
                    // leash
                    val wct = WindowContainerTransaction()
                    wct.setBounds(taskInfo.token, destinationBounds)
                    transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
                }

                releaseVisualIndicator()
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
            doesAnyTaskRequireTaskbarRounding(taskInfo.displayId)
        )
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
                // Start a new jank interaction for the drag release to desktop window animation.
                interactionJankMonitor.begin(
                    taskSurface,
                    context,
                    handler,
                    CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE,
                    "to_desktop",
                )
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
        taskRepository.updateTaskExclusionRegions(taskId, exclusionRegion)
    }

    /** Remove a previously tracked exclusion region for a specified task. */
    fun removeExclusionRegionForTask(taskId: Int) {
        taskRepository.removeExclusionRegion(taskId)
    }

    /**
     * Adds a listener to find out about changes in the visibility of freeform tasks.
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun addVisibleTasksListener(listener: VisibleTasksListener, callbackExecutor: Executor) {
        taskRepository.addVisibleTasksListener(listener, callbackExecutor)
    }

    /**
     * Adds a listener to track changes to desktop task gesture exclusion regions
     *
     * @param listener the listener to add.
     * @param callbackExecutor the executor to call the listener on.
     */
    fun setTaskRegionListener(listener: Consumer<Region>, callbackExecutor: Executor) {
        taskRepository.setExclusionRegionListener(listener, callbackExecutor)
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
                getFocusedNonDesktopTasks(destinationDisplay).isNotEmpty()
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
                // Use default bounds, but with the top-center at the drop point.
                newWindowBounds.set(calculateDefaultDesktopTaskBounds(displayLayout))
                newWindowBounds.offsetTo(
                    dragEvent.x.toInt() - (newWindowBounds.width() / 2),
                    dragEvent.y.toInt(),
                )
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
                val deskId = getOrCreateDefaultDeskId(destinationDisplay) ?: return false
                startLaunchTransition(
                    TRANSIT_OPEN,
                    wct,
                    launchingTaskId = null,
                    deskId = deskId,
                    displayId = destinationDisplay,
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
        logV("onUserChanged previousUserId=%d, newUserId=%d", userId, newUserId)
        updateCurrentUser(newUserId)
    }

    private fun updateCurrentUser(newUserId: Int) {
        userId = newUserId
        taskRepository = userRepositories.getProfile(userId)
        if (this::snapEventHandler.isInitialized) {
            snapEventHandler.onUserChange(userId)
        }
    }

    /** Called when a task's info changes. */
    fun onTaskInfoChanged(taskInfo: RunningTaskInfo) {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return
        val inImmersive = taskRepository.isTaskInFullImmersiveState(taskInfo.taskId)
        val requestingImmersive = taskInfo.requestingImmersive
        if (
            inImmersive &&
                !requestingImmersive &&
                !RecentsTransitionStateListener.isRunning(recentsTransitionState)
        ) {
            // Exit immersive if the app is no longer requesting it.
            desktopImmersiveController.moveTaskToNonImmersive(
                taskInfo,
                DesktopImmersiveController.ExitReason.APP_NOT_IMMERSIVE,
            )
        }
    }

    private fun createActivityOptionsForStartTask(): ActivityOptions {
        return ActivityOptions.makeBasic().apply {
            launchWindowingMode = WINDOWING_MODE_FREEFORM
            splashScreenStyle = SPLASH_SCREEN_STYLE_ICON
        }
    }

    private fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}DesktopTasksController")
        desktopConfig.dump(pw, innerPrefix)
        userRepositories.dump(pw, innerPrefix)
        focusTransitionObserver.dump(pw, innerPrefix)
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
                this@DesktopTasksController.moveFocusedTaskToDesktop(displayId, transitionSource)
            }
        }

        override fun moveFocusedTaskToFullscreen(
            displayId: Int,
            transitionSource: DesktopModeTransitionSource,
        ) {
            logV("moveFocusedTaskToFullscreen")
            mainExecutor.execute {
                this@DesktopTasksController.enterFullscreen(displayId, transitionSource)
            }
        }

        override fun moveFocusedTaskToStageSplit(displayId: Int, leftOrTop: Boolean) {
            logV("moveFocusedTaskToStageSplit")
            mainExecutor.execute { this@DesktopTasksController.enterSplit(displayId, leftOrTop) }
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

        override fun removeDesk(deskId: Int) {
            executeRemoteCallWithTaskPermission(controller, "removeDesk") { c ->
                c.removeDesk(deskId)
            }
        }

        override fun removeAllDesks() {
            executeRemoteCallWithTaskPermission(controller, "removeAllDesks") { c ->
                c.removeAllDesks()
            }
        }

        override fun activateDesk(
            deskId: Int,
            remoteTransition: RemoteTransition?,
            taskIdInFront: Int,
        ) {
            executeRemoteCallWithTaskPermission(controller, "activateDesk") { c ->
                c.activateDesk(
                    deskId,
                    remoteTransition,
                    if (taskIdInFront != INVALID_TASK_ID) taskIdInFront else null,
                )
            }
        }

        override fun showDesktopApps(
            displayId: Int,
            remoteTransition: RemoteTransition?,
            taskIdInFront: Int,
        ) {
            executeRemoteCallWithTaskPermission(controller, "showDesktopApps") { c ->
                c.showDesktopApps(
                    displayId,
                    remoteTransition,
                    if (taskIdInFront != INVALID_TASK_ID) taskIdInFront else null,
                )
            }
        }

        override fun showDesktopApp(
            taskId: Int,
            remoteTransition: RemoteTransition?,
            toFrontReason: DesktopTaskToFrontReason,
        ) {
            executeRemoteCallWithTaskPermission(controller, "showDesktopApp") { c ->
                c.moveTaskToFront(taskId, remoteTransition, toFrontReason.toUnminimizeReason())
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

        override fun onDesktopSplitSelectAnimComplete(taskInfo: RunningTaskInfo) {
            executeRemoteCallWithTaskPermission(controller, "onDesktopSplitSelectAnimComplete") { c
                ->
                c.onDesktopSplitSelectAnimComplete(taskInfo)
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

        override fun removeDefaultDeskInDisplay(displayId: Int) {
            executeRemoteCallWithTaskPermission(controller, "removeDefaultDeskInDisplay") { c ->
                c.removeDefaultDeskInDisplay(displayId)
            }
        }

        override fun moveToExternalDisplay(taskId: Int) {
            executeRemoteCallWithTaskPermission(controller, "moveTaskToExternalDisplay") { c ->
                c.moveToNextDisplay(taskId)
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
                    c.taskRepository.getDeskDisplayStateForRemote(),
                    c.canCreateDesks(),
                )
            }
        }

        private fun registerListeners(c: DesktopTasksController) {
            if (c.desktopState.enableMultipleDesktops) {
                c.taskRepository.addDeskChangeListener(deskChangeListener, c.mainExecutor)
            }
            c.taskRepository.addVisibleTasksListener(visibleTasksListener, c.mainExecutor)
            c.taskbarDesktopTaskListener = taskbarDesktopTaskListener
            c.desktopModeEnterExitTransitionListener = desktopModeEntryExitTransitionListener
        }

        private fun unregisterListeners(c: DesktopTasksController) {
            if (c.desktopState.enableMultipleDesktops) {
                c.taskRepository.removeDeskChangeListener(deskChangeListener)
            }
            c.taskRepository.removeVisibleTasksListener(visibleTasksListener)
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
        @JvmField
        val DESKTOP_MODE_INITIAL_BOUNDS_SCALE =
            SystemProperties.getInt("persist.wm.debug.desktop_mode_initial_bounds_scale", 75) / 100f

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
