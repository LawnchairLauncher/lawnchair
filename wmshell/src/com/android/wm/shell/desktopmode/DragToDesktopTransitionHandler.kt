package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityOptions.SourceInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.content.Intent
import android.content.Intent.FILL_IN_COMPONENT
import android.graphics.PointF
import android.graphics.Rect
import android.os.IBinder
import android.os.SystemClock
import android.os.SystemProperties
import android.os.UserHandle
import android.util.Size
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.DesktopModeFlags.ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.dynamicanimation.animation.SpringForce
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.protolog.ProtoLog
import com.android.internal.util.LatencyTracker
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.animation.FloatProperties
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubbleTransitions
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator.Companion.DRAG_FREEFORM_SCALE
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import java.util.Optional
import java.util.function.Supplier
import kotlin.math.max

/**
 * Handles the transition to enter desktop from fullscreen by dragging on the handle bar. It also
 * handles the cancellation case where the task is dragged back to the status bar area in the same
 * gesture.
 *
 * It's a base sealed class that delegates flag dependant logic to its subclasses:
 * [DefaultDragToDesktopTransitionHandler] and [SpringDragToDesktopTransitionHandler]
 *
 * TODO(b/356764679): Clean up after the full flag rollout
 */
sealed class DragToDesktopTransitionHandler(
    private val context: Context,
    private val transitions: Transitions,
    private val taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val desksOrganizer: DesksOrganizer,
    private val desktopUserRepositories: DesktopUserRepositories,
    protected val interactionJankMonitor: InteractionJankMonitor,
    private val bubbleController: Optional<BubbleController>,
    protected val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private val desktopState: DesktopState,
) : TransitionHandler {

    protected val rectEvaluator = RectEvaluator(Rect())
    private val launchHomeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private lateinit var splitScreenController: SplitScreenController
    private var transitionState: TransitionState? = null

    /** Whether a drag-to-desktop transition is in progress. */
    val inProgress: Boolean
        get() = transitionState != null

    /** The task id of the task currently being dragged from fullscreen/split. */
    val draggingTaskId: Int
        get() = transitionState?.draggedTaskId ?: INVALID_TASK_ID

    /** Listener to receive callback about events during the transition animation. */
    var dragToDesktopStateListener: DragToDesktopStateListener? = null

    /** Task listener for animation start, task bounds resize, and the animation finish */
    lateinit var onTaskResizeAnimationListener: OnTaskResizeAnimationListener

    /** Setter needed to avoid cyclic dependency. */
    fun setSplitScreenController(controller: SplitScreenController) {
        splitScreenController = controller
    }

    /**
     * Starts a transition that performs a transient launch of Home so that Home is brought to the
     * front while still keeping the currently focused task that is being dragged resumed. This
     * allows the animation handler to reorder the task to the front and to scale it with the
     * gesture into the desktop area with the Home and wallpaper behind it.
     *
     * Note that the transition handler for this transition doesn't call the finish callback until
     * after one of the "end" or "cancel" transitions is merged into this transition.
     */
    fun startDragToDesktopTransition(
        taskInfo: RunningTaskInfo,
        dragToDesktopAnimator: MoveToDesktopAnimator,
        visualIndicator: DesktopModeVisualIndicator?,
        dragCancelCallback: Runnable,
    ) {
        if (inProgress) {
            logV("Drag to desktop transition already in progress.")
            return
        }

        val options =
            ActivityOptions.makeBasic().apply {
                setTransientLaunch()
                setSourceInfo(SourceInfo.TYPE_DESKTOP_ANIMATION, SystemClock.uptimeMillis())
                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue) {
                    launchDisplayId = taskInfo.displayId
                }
            }
        // If we are launching home for a profile of a user, just use the [userId] of that user
        // instead of the [profileId] to create the context.
        val userToLaunchWith =
            UserHandle.of(desktopUserRepositories.getUserIdForProfile(taskInfo.userId))
        val pendingIntent =
            PendingIntent.getActivityAsUser(
                context.createContextAsUser(userToLaunchWith, /* flags= */ 0),
                /* requestCode= */ 0,
                launchHomeIntent,
                FLAG_MUTABLE or FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT or FILL_IN_COMPONENT,
                options.toBundle(),
                userToLaunchWith,
            )
        val wct = WindowContainerTransaction()
        // The app that is being dragged into desktop mode might cause new transitions, make this
        // launch transient to make sure those transitions can execute in parallel and thus won't
        // block the end-drag transition.
        val intentOptions = ActivityOptions.makeBasic().setTransientLaunch()
        wct.sendPendingIntent(pendingIntent, launchHomeIntent, intentOptions.toBundle())
        val startTransitionToken =
            transitions.startTransition(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP, wct, this)

        transitionState =
            if (isSplitTask(taskInfo.taskId)) {
                val otherTask =
                    getOtherSplitTask(taskInfo.taskId)
                        ?: throw IllegalStateException("Expected split task to have a counterpart.")
                TransitionState.FromSplit(
                    draggedTaskId = taskInfo.taskId,
                    dragAnimator = dragToDesktopAnimator,
                    startTransitionToken = startTransitionToken,
                    otherSplitTask = otherTask,
                    visualIndicator = visualIndicator,
                    dragCancelCallback = dragCancelCallback,
                )
            } else {
                TransitionState.FromFullscreen(
                    draggedTaskId = taskInfo.taskId,
                    dragAnimator = dragToDesktopAnimator,
                    startTransitionToken = startTransitionToken,
                    visualIndicator = visualIndicator,
                    dragCancelCallback = dragCancelCallback,
                )
            }
    }

    /**
     * Starts a transition that "finishes" the drag to desktop gesture. This transition is intended
     * to merge into the "start" transition and is the one that actually applies the bounds and
     * windowing mode changes to the dragged task. This is called when the dragged task is released
     * inside the desktop drop zone.
     */
    fun finishDragToDesktopTransition(wct: WindowContainerTransaction): IBinder? {
        if (!inProgress) {
            logV("finishDragToDesktop: not in progress, returning")
            // Don't attempt to finish a drag to desktop transition since there is no transition in
            // progress which means that the drag to desktop transition was never successfully
            // started.
            return null
        }
        val state = requireTransitionState()
        if (state.startAborted) {
            logV("finishDragToDesktop: start was aborted, clearing state")
            // Don't attempt to complete the drag-to-desktop since the start transition didn't
            // succeed as expected. Just reset the state as if nothing happened.
            clearState()
            return null
        }
        if (state.startInterrupted) {
            logV("finishDragToDesktop: start was interrupted, returning")
            // If start was interrupted we've either already requested a cancel/end transition - so
            // we should let that request play out, or we're cancelling the drag-to-desktop
            // transition altogether, so just return here.
            return null
        }
        state.endTransitionToken =
            transitions.startTransition(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP, wct, this)
        return state.endTransitionToken
    }

    /**
     * Starts a transition that "cancels" the drag to desktop gesture. This transition is intended
     * to merge into the "start" transition and it restores the transient state that was used to
     * launch the Home task over the dragged task. This is called when the dragged task is released
     * outside the desktop drop zone and is instead dropped back into the status bar region that
     * means the user wants to remain in their current windowing mode.
     */
    fun cancelDragToDesktopTransition(cancelState: CancelState) {
        logV("cancelDragToDesktop cancel=%s", cancelState)
        if (!inProgress) {
            logV("cancelDragToDesktop: not in progress, returning")
            // Don't attempt to cancel a drag to desktop transition since there is no transition in
            // progress which means that the drag to desktop transition was never successfully
            // started.
            return
        }
        val state = requireTransitionState()
        if (state.startAborted) {
            logV("cancelDragToDesktop: start was aborted, clearing state")
            // Don't attempt to cancel the drag-to-desktop since the start transition didn't
            // succeed as expected. Just reset the state as if nothing happened.
            clearState()
            return
        }
        if (state.startInterrupted) {
            logV("cancelDragToDesktop: start was interrupted, returning")
            // If start was interrupted we've either already requested a cancel/end transition - so
            // we should let that request play out, or we're cancelling the drag-to-desktop
            // transition altogether, so just return here.
            return
        }
        state.cancelState = cancelState

        if (state.draggedTaskChange != null && cancelState == CancelState.STANDARD_CANCEL) {
            // Regular case, transient launch of Home happened as is waiting for the cancel
            // transient to start and merge. Animate the cancellation (scale back to original
            // bounds) first before actually starting the cancel transition so that the wallpaper
            // is visible behind the animating task.
            state.activeCancelAnimation = startCancelAnimation()
        } else if (
            state.draggedTaskChange != null &&
                (cancelState == CancelState.CANCEL_SPLIT_LEFT ||
                    cancelState == CancelState.CANCEL_SPLIT_RIGHT)
        ) {
            // We have a valid dragged task, but the animation will be handled by
            // SplitScreenController; request the transition here.
            @SplitPosition
            val splitPosition =
                if (cancelState == CancelState.CANCEL_SPLIT_LEFT) {
                    SPLIT_POSITION_TOP_OR_LEFT
                } else {
                    SPLIT_POSITION_BOTTOM_OR_RIGHT
                }
            logV(
                "cancelDragToDesktop finishing start-transition and starting split-select " +
                    "request to position=%s with state=%s",
                splitPosition,
                state,
            )
            state.startTransitionFinishTransaction?.apply()
            val finishWCT = WindowContainerTransaction()
            val taskInfo = state.draggedTaskChange?.taskInfo ?: error("Expected non-null taskInfo")
            finishWCT.setDoNotPip(taskInfo.token)
            state.startTransitionFinishCb?.onTransitionFinished(finishWCT)
            requestSplitFromScaledTask(splitPosition, homeRunning = true)
            clearState()
        } else if (
            state.draggedTaskChange != null &&
                (cancelState == CancelState.CANCEL_BUBBLE_LEFT ||
                    cancelState == CancelState.CANCEL_BUBBLE_RIGHT)
        ) {
            if (bubbleController.isEmpty || state !is TransitionState.FromFullscreen) {
                // TODO(b/388853233): add support for dragging split task to bubble
                state.activeCancelAnimation = startCancelAnimation()
            } else {
                // Animation is handled by BubbleController
                val onLeft = cancelState == CancelState.CANCEL_BUBBLE_LEFT
                requestBubbleFromScaledTask(onLeft)
            }
        } else {
            // There's no dragged task, this can happen when the "cancel" happened too quickly
            // before the "start" transition is even ready (like on a fling gesture). The
            // "shrink" animation didn't even start, so there's no need to animate the "cancel".
            // We also don't want to start the cancel transition yet since we don't have
            // enough info to restore the order. We'll check for the cancelled state flag when
            // the "start" animation is ready and cancel from #startAnimation instead.
        }
    }

    /** Calculate the bounds of a scaled task, then use those bounds to request split select. */
    private fun requestSplitFromScaledTask(
        @SplitPosition splitPosition: Int,
        homeRunning: Boolean,
    ) {
        val state = requireTransitionState()
        val taskInfo = state.draggedTaskChange?.taskInfo ?: error("Expected non-null taskInfo")
        val animatedTaskBounds = getAnimatedTaskBounds()
        state.dragAnimator.cancelAnimator()
        requestSplitSelect(taskInfo, splitPosition, animatedTaskBounds, homeRunning)
    }

    private fun getAnimatedTaskBounds(): Rect {
        val state = requireTransitionState()
        val taskInfo = state.draggedTaskChange?.taskInfo ?: error("Expected non-null taskInfo")
        val taskBounds = Rect(taskInfo.configuration.windowConfiguration.bounds)
        val taskScale = state.dragAnimator.scale
        val scaledWidth = taskBounds.width() * taskScale
        val scaledHeight = taskBounds.height() * taskScale
        val dragPosition = PointF(state.dragAnimator.position)
        return Rect(
            dragPosition.x.toInt(),
            dragPosition.y.toInt(),
            (dragPosition.x + scaledWidth).toInt(),
            (dragPosition.y + scaledHeight).toInt(),
        )
    }

    private fun requestSplitSelect(
        taskInfo: RunningTaskInfo,
        @SplitPosition splitPosition: Int,
        taskBounds: Rect = Rect(taskInfo.configuration.windowConfiguration.bounds),
        homeRunning: Boolean,
    ) {
        val wct = WindowContainerTransaction()
        if (!DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue) {
            wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_MULTI_WINDOW)
        }
        wct.setDensityDpi(taskInfo.token, context.resources.displayMetrics.densityDpi)

        val startRecents = !homeRunning
        val delegateWctToRecents = startRecents && !wct.isEmpty
        if (!startRecents && !wct.isEmpty) {
            // Split-select won't start a transition, so apply |wct| here.
            transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
        }
        splitScreenController.requestEnterSplitSelect(
            taskInfo,
            splitPosition,
            taskBounds,
            startRecents,
            /* withRecentsWct= */ if (delegateWctToRecents) wct else null,
        )
    }

    private fun requestBubbleFromScaledTask(onLeft: Boolean) {
        // TODO(b/391928049): update density once we can drag from desktop to bubble
        val state = requireTransitionState()
        val taskInfo = state.draggedTaskChange?.taskInfo ?: error("Expected non-null taskInfo")
        val dragPosition = PointF(state.dragAnimator.position)
        val scale = state.dragAnimator.scale
        val cornerRadius = state.dragAnimator.cornerRadius
        state.dragAnimator.cancelAnimator()
        requestBubble(taskInfo, onLeft, scale, cornerRadius, dragPosition)
    }

    private fun requestBubble(
        taskInfo: RunningTaskInfo,
        onLeft: Boolean,
        taskScale: Float = 1f,
        cornerRadius: Float = 0f,
        dragPosition: PointF = PointF(0f, 0f),
    ) {
        val controller =
            bubbleController.orElseThrow { IllegalStateException("BubbleController not set") }
        controller.expandStackAndSelectBubble(
            taskInfo,
            BubbleTransitions.DragData(onLeft, taskScale, cornerRadius, dragPosition),
        )
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val state = requireTransitionState()

        if (
            handleCancelOrExitAfterInterrupt(
                transition,
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
                state,
            )
        ) {
            return true
        }

        val isStartDragToDesktop =
            info.type == TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP &&
                transition == state.startTransitionToken
        if (!isStartDragToDesktop) {
            return false
        }

        logV("startAnimation: state=${state.toSimpleString()}")

        val layers = calculateStartDragLayers(info)
        val leafTaskFilter = TransitionUtil.LeafTaskFilter()
        info.changes.withIndex().forEach { (i, change) ->
            if (TransitionUtil.isWallpaper(change)) {
                logV("Wallpaper change: change=$change")
                val layer = layers.topWallpaperLayer - i
                startTransaction.apply {
                    setLayer(change.leash, layer)
                    show(change.leash)
                }
            } else if (isHomeChange(change)) {
                logV("Home change: change=$change")
                state.homeChange = change
                val layer = layers.topHomeLayer - i
                startTransaction.apply {
                    setLayer(change.leash, layer)
                    show(change.leash)
                }
            } else if (TransitionInfo.isIndependent(change, info)) {
                logV("Independent change: taskId=${change.taskInfo?.taskId}, change=$change")
                // Root(s).
                when (state) {
                    is TransitionState.FromSplit -> {
                        state.splitRootChange = change
                        val layer =
                            if (state.cancelState == CancelState.NO_CANCEL) {
                                // Normal case, split root goes to the bottom behind everything
                                // else.
                                layers.topAppLayer - i
                            } else {
                                // Cancel-early case, pretend nothing happened so split root stays
                                // top.
                                layers.dragLayer
                            }
                        startTransaction.apply {
                            setLayer(change.leash, layer)
                            show(change.leash)
                        }
                    }
                    is TransitionState.FromFullscreen -> {
                        // Most of the time we expect one change/task here, which should be the
                        // same that initiated the drag and that should be layered on top of
                        // everything.
                        if (change.taskInfo?.taskId == state.draggedTaskId) {
                            state.draggedTaskChange = change
                            val bounds = change.endAbsBounds
                            startTransaction.apply {
                                setLayer(change.leash, layers.dragLayer)
                                setWindowCrop(change.leash, bounds.width(), bounds.height())
                                show(change.leash)
                            }
                        } else {
                            // It's possible to see an additional change that isn't the dragged
                            // task when the dragged task is translucent and so the task behind it
                            // is included in the transition since it was visible and is now being
                            // occluded by the Home task. Just layer it at the bottom and save it
                            // in case we need to restore order if the drag is cancelled.
                            state.otherRootChanges.add(change)
                            val bounds = change.endAbsBounds
                            startTransaction.apply {
                                setLayer(change.leash, layers.topAppLayer - i)
                                setWindowCrop(change.leash, bounds.width(), bounds.height())
                                show(change.leash)
                            }
                        }
                    }
                }
            } else if (leafTaskFilter.test(change)) {
                logV("Leaf task: taskId=${change.taskInfo?.taskId}, change=$change")
                // When dragging one of the split tasks, the dragged leaf needs to be re-parented
                // so that it can be layered separately from the rest of the split root/stages.
                // The split root including the other split side was layered behind the wallpaper
                // and home while the dragged split needs to be layered in front of them.
                // Do not do this in the cancel-early case though, since in that case nothing should
                // happen on screen so the layering will remain the same as if no transition
                // occurred.
                if (
                    change.taskInfo?.taskId == state.draggedTaskId &&
                        state.cancelState != CancelState.STANDARD_CANCEL
                ) {
                    // We need access to the dragged task's change in both non-cancel and split
                    // cancel cases.
                    state.draggedTaskChange = change
                }
                if (
                    change.taskInfo?.taskId == state.draggedTaskId &&
                        state.cancelState == CancelState.NO_CANCEL
                ) {
                    taskDisplayAreaOrganizer.reparentToDisplayArea(
                        change.endDisplayId,
                        change.leash,
                        startTransaction,
                    )
                    val bounds = change.endAbsBounds
                    startTransaction.apply {
                        setLayer(change.leash, layers.dragLayer)
                        setWindowCrop(change.leash, bounds.width(), bounds.height())
                        show(change.leash)
                    }
                }
            }
        }
        state.surfaceLayers = layers
        state.startTransitionFinishCb = finishCallback
        state.startTransitionFinishTransaction = finishTransaction

        val taskChange = state.draggedTaskChange ?: error("Expected non-null task change.")
        val taskInfo = taskChange.taskInfo ?: error("Expected non-null task info.")

        if (DesktopModeFlags.ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX.isTrue) {
            attachIndicatorToTransitionRoot(state, info, taskInfo, startTransaction)
        }
        startTransaction.apply()

        if (state.cancelState == CancelState.NO_CANCEL) {
            // Normal case, start animation to scale down the dragged task. It'll also be moved to
            // follow the finger and when released we'll start the next phase/transition.
            state.dragAnimator.startAnimation()
        } else if (state.cancelState == CancelState.STANDARD_CANCEL) {
            // Cancel-early case, the state was flagged was cancelled already, which means the
            // gesture ended in the cancel region. This can happen even before the start transition
            // is ready/animate here when cancelling quickly like with a fling. There's no point
            // in starting the scale down animation that we would scale up anyway, so just jump
            // directly into starting the cancel transition to restore WM order. Surfaces should
            // not move as if no transition happened.
            startCancelDragToDesktopTransition()
        } else if (
            state.cancelState == CancelState.CANCEL_SPLIT_LEFT ||
                state.cancelState == CancelState.CANCEL_SPLIT_RIGHT
        ) {
            // Cancel-early case for split-cancel. The state was flagged already as a cancel for
            // requesting split select. Similar to the above, this can happen due to quick fling
            // gestures. We can simply request split here without needing to calculate animated
            // task bounds as the task has not shrunk at all.
            val splitPosition =
                if (state.cancelState == CancelState.CANCEL_SPLIT_LEFT) {
                    SPLIT_POSITION_TOP_OR_LEFT
                } else {
                    SPLIT_POSITION_BOTTOM_OR_RIGHT
                }
            state.startTransitionFinishTransaction?.apply()
            state.startTransitionFinishCb?.onTransitionFinished(/* wct= */ null)
            requestSplitSelect(taskInfo, splitPosition, homeRunning = true)
        } else if (
            state.cancelState == CancelState.CANCEL_BUBBLE_LEFT ||
                state.cancelState == CancelState.CANCEL_BUBBLE_RIGHT
        ) {
            if (bubbleController.isEmpty || state !is TransitionState.FromFullscreen) {
                // TODO(b/388853233): add support for dragging split task to bubble
                startCancelDragToDesktopTransition()
                return true
            }
            val taskInfo =
                state.draggedTaskChange?.taskInfo ?: error("Expected non-null task info.")
            val onLeft = state.cancelState == CancelState.CANCEL_BUBBLE_LEFT
            requestBubble(taskInfo, onLeft)
        }
        return true
    }

    private fun attachIndicatorToTransitionRoot(
        state: TransitionState,
        info: TransitionInfo,
        taskInfo: RunningTaskInfo,
        t: SurfaceControl.Transaction,
    ) {
        val transitionRoot = info.getRoot(info.findRootIndex(taskInfo.displayId))
        state.visualIndicator?.let {
            // Attach the indicator to the transition root so that it's removed at the end of the
            // transition regardless of whether we managed to release the indicator.
            it.reparentLeash(t, transitionRoot.leash)
            it.fadeInIndicator()
        }
    }

    private fun handleCancelOrExitAfterInterrupt(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        state: TransitionState,
    ): Boolean {
        if (!ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX.isTrue) {
            return false
        }
        val isCancelDragToDesktop =
            info.type == TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP &&
                transition == state.cancelTransitionToken
        val isEndDragToDesktop =
            info.type == TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP &&
                transition == state.endTransitionToken
        // We should only receive cancel or end transitions through startAnimation() if the
        // start transition was interrupted while a cancel- or end-transition had already
        // been requested. Finish the cancel/end transition to avoid having to deal with more
        // incoming transitions, and clear the state for the next start-drag transition.
        if (!isCancelDragToDesktop && !isEndDragToDesktop) {
            return false
        }
        if (!state.startInterrupted) {
            logW(
                "Not interrupted, but received startAnimation for cancel/end drag." +
                    "isCancel=$isCancelDragToDesktop, isEnd=$isEndDragToDesktop"
            )
            return false
        }
        logV(
            "startAnimation: interrupted -> " +
                "isCancel=$isCancelDragToDesktop, isEnd=$isEndDragToDesktop"
        )
        if (isEndDragToDesktop) {
            setupEndDragToDesktop(info, startTransaction, finishTransaction)
            animateEndDragToDesktop(startTransaction = startTransaction, finishCallback)
        } else { // isCancelDragToDesktop
            // Similar to when we merge the cancel transition: ensure all tasks involved in the
            // cancel transition are shown, and finish the transition immediately.
            info.changes.forEach { change ->
                startTransaction.show(change.leash)
                finishTransaction.show(change.leash)
            }
        }
        startTransaction.apply()
        finishCallback.onTransitionFinished(/* wct= */ null)
        clearState()
        return true
    }

    /**
     * Calculates start drag layers for transition [info]. The leash layer is calculated based on
     * its change position in the transition, e.g. `appLayer = appLayers - i`, where i is the change
     * index.
     */
    protected fun calculateStartDragLayers(info: TransitionInfo): DragToDesktopLayers {
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            val hasDesktop = desktopState.isDesktopModeSupportedOnDisplay(info.getRoot(0).displayId)
            if (!hasDesktop) {
                return calculateStartDragLayersWithoutDesktop(info)
            }
        }
        return calculateStartDragLayersWithDesktop(info)
    }

    protected abstract fun calculateStartDragLayersWithDesktop(
        info: TransitionInfo
    ): DragToDesktopLayers

    /**
     * @return layers in order:
     * - appLayers - below everything z < 0, effectively hides the leash
     * - wallpaperLayers - wallpaper on top of apps, z in 0..<size
     * - homeLayers - home task on top of wallpaper, z in size..<size*2
     * - dragLayer - the dragged task on top of everything, z == size*2
     */
    private fun calculateStartDragLayersWithoutDesktop(info: TransitionInfo): DragToDesktopLayers {
        return DragToDesktopLayers(
            topAppLayer = -1,
            topWallpaperLayer = info.changes.size - 1,
            topHomeLayer = info.changes.size * 2 - 1,
            dragLayer = info.changes.size * 2,
        )
    }

    override fun mergeAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        mergeTarget: IBinder,
        finishCallback: Transitions.TransitionFinishCallback,
    ) {
        val state = requireTransitionState()
        // We don't want to merge the split select animation if that's what we requested.
        if (
            state.cancelState == CancelState.CANCEL_SPLIT_LEFT ||
                state.cancelState == CancelState.CANCEL_SPLIT_RIGHT
        ) {
            logV("mergeAnimation: cancel through split")
            clearState()
            return
        }
        // In case of bubble animation, finish the initial desktop drag animation, but keep the
        // current animation running and have bubbles take over
        if (info.type == TRANSIT_CONVERT_TO_BUBBLE) {
            logV("mergeAnimation: convert-to-bubble")
            state.startTransitionFinishCb?.onTransitionFinished(/* wct= */ null)
            clearState()
            return
        }
        val isCancelTransition =
            info.type == TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP &&
                transition == state.cancelTransitionToken &&
                mergeTarget == state.startTransitionToken
        val isEndTransition =
            info.type == TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP &&
                mergeTarget == state.startTransitionToken

        val startTransactionFinishT =
            state.startTransitionFinishTransaction
                ?: error("Start transition expected to be waiting for merge but wasn't")
        val startTransitionFinishCb =
            state.startTransitionFinishCb
                ?: error("Start transition expected to be waiting for merge but wasn't")
        if (isEndTransition) {
            logV("mergeAnimation: end-transition, target=$mergeTarget")
            state.mergedEndTransition = true
            setupEndDragToDesktop(
                info,
                startTransaction = startT,
                finishTransaction = startTransactionFinishT,
            )
            // Call finishCallback to merge animation before startTransitionFinishCb is called
            finishCallback.onTransitionFinished(/* wct= */ null)
            LatencyTracker.getInstance(context)
                .onActionEnd(LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG)
            animateEndDragToDesktop(startTransaction = startT, startTransitionFinishCb)
            return
        }
        if (isCancelTransition) {
            logV("mergeAnimation: cancel-transition, target=$mergeTarget")
            LatencyTracker.getInstance(context)
                .onActionCancel(LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG)
            info.changes.forEach { change ->
                startT.show(change.leash)
                startTransactionFinishT.show(change.leash)
            }
            startT.apply()
            finishCallback.onTransitionFinished(/* wct= */ null)
            startTransitionFinishCb.onTransitionFinished(/* wct= */ null)
            clearState()
            return
        }
        logW("unhandled merge transition: transitionInfo=$info")
        // Handle unknown incoming transitions by finishing the start transition. For now, only do
        // this if we've already requested a cancel- or end transition. If we've already merged the
        // end-transition, or if the end-transition is running on its own, then just wait until that
        // finishes instead. If we've merged the cancel-transition we've finished the
        // start-transition and won't reach this code.
        if (mergeTarget == state.startTransitionToken && !state.mergedEndTransition) {
            interruptStartTransition(state)
        }
    }

    private fun isCancelOrEndTransitionRequested(state: TransitionState): Boolean =
        state.cancelTransitionToken != null || state.endTransitionToken != null

    private fun interruptStartTransition(state: TransitionState) {
        if (!ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX.isTrue) {
            return
        }
        if (isCancelOrEndTransitionRequested(state)) {
            logV("interruptStartTransition, bookend requested -> finish start transition")
            // Finish the start-drag transition, we will finish the overall transition properly when
            // receiving #startAnimation for Cancel/End.
            state.startTransitionFinishCb?.onTransitionFinished(/* wct= */ null)
            state.dragAnimator.cancelAnimator()
        } else {
            logV("interruptStartTransition, bookend not requested -> animate to Home")
            // Animate to Home, and then finish the start-drag transition. Since there is no other
            // (end/cancel) transition requested that will be the end of the overall transition.
            state.dragAnimator.cancelAnimator()
            state.dragCancelCallback?.run()
            createInterruptToHomeAnimator(transactionSupplier.get(), state) {
                state.startTransitionFinishCb?.onTransitionFinished(/* wct= */ null)
                clearState()
            }
        }
        state.activeCancelAnimation?.removeAllListeners()
        state.activeCancelAnimation?.cancel()
        state.activeCancelAnimation = null
        // Keep the transition state so we can deal with Cancel/End properly in #startAnimation.
        state.startInterrupted = true
        dragToDesktopStateListener?.onTransitionInterrupted()
        // Cancel CUJs here as they won't be accurate now that an incoming transition is playing.
        interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD)
        interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE)
        LatencyTracker.getInstance(context)
            .onActionCancel(LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG)
    }

    private fun createInterruptToHomeAnimator(
        transaction: Transaction,
        state: TransitionState,
        endCallback: Runnable,
    ) {
        val homeLeash = state.homeChange?.leash ?: error("Expected home leash to be non-null")
        val draggedTaskLeash =
            state.draggedTaskChange?.leash ?: error("Expected dragged leash to be non-null")
        val homeAnimator = createInterruptAlphaAnimator(transaction, homeLeash, toShow = true)
        val draggedTaskAnimator =
            createInterruptAlphaAnimator(transaction, draggedTaskLeash, toShow = false)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(homeAnimator, draggedTaskAnimator)
        animatorSet.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    endCallback.run()
                }
            }
        )
        animatorSet.start()
    }

    private fun createInterruptAlphaAnimator(
        transaction: Transaction,
        leash: SurfaceControl,
        toShow: Boolean,
    ) =
        ValueAnimator.ofFloat(if (toShow) 0f else 1f, if (toShow) 1f else 0f).apply {
            transaction.show(leash)
            duration = DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                transaction
                    .setAlpha(leash, animation.animatedValue as Float)
                    .setFrameTimeline(Choreographer.getInstance().vsyncId)
                    .apply()
            }
        }

    protected open fun setupEndDragToDesktop(
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        val state = requireTransitionState()
        val freeformTaskChanges = mutableListOf<Change>()
        info.changes.forEachIndexed { i, change ->
            when {
                state is TransitionState.FromSplit &&
                    change.taskInfo?.taskId == state.otherSplitTask -> {
                    // If we're exiting split, hide the remaining split task.
                    startTransaction.hide(change.leash)
                    finishTransaction.hide(change.leash)
                }
                change.mode == TRANSIT_CLOSE -> {
                    startTransaction.hide(change.leash)
                    finishTransaction.hide(change.leash)
                }
                change.taskInfo?.taskId == state.draggedTaskId -> {
                    startTransaction.show(change.leash)
                    finishTransaction.show(change.leash)
                    state.draggedTaskChange = change
                    // Restoring the dragged leash layer as it gets reset in the merge transition
                    state.surfaceLayers?.let {
                        startTransaction.setLayer(change.leash, it.dragLayer)
                    }
                }
                // Other desktop tasks that are being restored go behind the dragged task.
                change.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM
                // Except for the desk root, that should not be sent back or it might
                // take the dragged task surface back with it.
                && !desksOrganizer.isDeskChange(change) -> {
                    val draggedTaskLeash =
                        state.draggedTaskChange?.leash
                            ?: error("Expected dragged leash to be non-null")
                    startTransaction.setRelativeLayer(change.leash, draggedTaskLeash, -i)
                    finishTransaction.setRelativeLayer(change.leash, draggedTaskLeash, -i)
                    freeformTaskChanges.add(change)
                }
            }
        }

        state.freeformTaskChanges = freeformTaskChanges
    }

    protected open fun animateEndDragToDesktop(
        startTransaction: SurfaceControl.Transaction,
        startTransitionFinishCb: Transitions.TransitionFinishCallback,
    ) {
        val state = requireTransitionState()
        val draggedTaskChange =
            state.draggedTaskChange ?: error("Expected non-null change of dragged task")
        val draggedTaskLeash = draggedTaskChange.leash
        val startBounds = draggedTaskChange.startAbsBounds
        val endBounds = draggedTaskChange.endAbsBounds

        // Cancel any animation that may be currently playing; we will use the relevant
        // details of that animation here.
        state.dragAnimator.cancelAnimator()
        // We still apply scale to task bounds; as we animate the bounds to their
        // end value, animate scale to 1.
        val startScale = state.dragAnimator.scale
        val startPosition = state.dragAnimator.position
        val unscaledStartWidth = startBounds.width()
        val unscaledStartHeight = startBounds.height()
        val unscaledStartBounds =
            Rect(
                startPosition.x.toInt(),
                startPosition.y.toInt(),
                startPosition.x.toInt() + unscaledStartWidth,
                startPosition.y.toInt() + unscaledStartHeight,
            )

        dragToDesktopStateListener?.onCommitToDesktopAnimationStart()
        // Accept the merge by applying the merging transaction (applied by #showResizeVeil)
        // and finish callback. Show the veil and position the task at the first frame before
        // starting the final animation.
        onTaskResizeAnimationListener.onAnimationStart(
            state.draggedTaskId,
            startTransaction,
            unscaledStartBounds,
        )
        val tx: SurfaceControl.Transaction = transactionSupplier.get()
        ValueAnimator.ofObject(rectEvaluator, unscaledStartBounds, endBounds)
            .setDuration(DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS)
            .apply {
                addUpdateListener { animator ->
                    val animBounds = animator.animatedValue as Rect
                    val animFraction = animator.animatedFraction
                    // Progress scale from starting value to 1 as animation plays.
                    val animScale = startScale + animFraction * (1 - startScale)
                    tx.apply {
                        setScale(draggedTaskLeash, animScale, animScale)
                        setPosition(
                            draggedTaskLeash,
                            animBounds.left.toFloat(),
                            animBounds.top.toFloat(),
                        )
                        setWindowCrop(draggedTaskLeash, animBounds.width(), animBounds.height())
                    }
                    onTaskResizeAnimationListener.onBoundsChange(
                        state.draggedTaskId,
                        tx,
                        animBounds,
                    )
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onTaskResizeAnimationListener.onAnimationEnd(state.draggedTaskId)
                            startTransitionFinishCb.onTransitionFinished(/* wct= */ null)
                            clearState()
                            interactionJankMonitor.end(
                                CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE
                            )
                        }
                    }
                )
                start()
            }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        // Only handle transitions started from shell.
        return null
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        val state = transitionState ?: return
        if (!aborted) {
            return
        }
        if (state.startTransitionToken == transition) {
            logV("onTransitionConsumed() start transition aborted")
            state.startAborted = true
            // The start-transition (DRAG_HOLD) is aborted, cancel its jank interaction.
            interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD)
        } else if (state.cancelTransitionToken == transition) {
            state.draggedTaskChange?.leash?.let { state.startTransitionFinishTransaction?.show(it) }
            state.startTransitionFinishCb?.onTransitionFinished(/* wct= */ null)
            clearState()
        } else {
            // This transition being aborted is neither the start, nor the cancel transition, so
            // it must be the finish transition (DRAG_RELEASE); cancel its jank interaction.
            interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE)
        }
    }

    /** Checks if the change is a home task change */
    @VisibleForTesting
    fun isHomeChange(change: Change): Boolean {
        return change.taskInfo?.let {
            it.activityType == ACTIVITY_TYPE_HOME &&
                // Skip translucent wizard task with type home
                // TODO(b/368334295): Remove when the multiple home changes issue is resolved
                !(it.isTopActivityTransparent && it.numActivities == 1)
        } ?: false
    }

    private fun startCancelAnimation(): Animator {
        val state = requireTransitionState()
        val dragToDesktopAnimator = state.dragAnimator

        val draggedTaskChange =
            state.draggedTaskChange ?: throw IllegalStateException("Expected non-null task change")
        val sc = draggedTaskChange.leash
        // Pause the animation that shrinks the window when task is first dragged from fullscreen
        dragToDesktopAnimator.cancelAnimator()
        // Then animate the scaled window back to its original bounds.
        val x: Float = dragToDesktopAnimator.position.x
        val y: Float = dragToDesktopAnimator.position.y
        val targetX = draggedTaskChange.endAbsBounds.left
        val targetY = draggedTaskChange.endAbsBounds.top
        val dx = targetX - x
        val dy = targetY - y
        val tx: SurfaceControl.Transaction = transactionSupplier.get()
        return ValueAnimator.ofFloat(DRAG_FREEFORM_SCALE, 1f)
            .setDuration(DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS)
            .apply {
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    val fraction = animator.animatedFraction
                    val animX = x + (dx * fraction)
                    val animY = y + (dy * fraction)
                    tx.apply {
                        setPosition(sc, animX, animY)
                        setScale(sc, scale, scale)
                        show(sc)
                        apply()
                    }
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            state.activeCancelAnimation = null
                            dragToDesktopStateListener?.onCancelToDesktopAnimationEnd()
                            // Start the cancel transition to restore order.
                            startCancelDragToDesktopTransition()
                        }
                    }
                )
                start()
            }
    }

    private fun startCancelDragToDesktopTransition() {
        val state = requireTransitionState()
        val wct = WindowContainerTransaction()
        restoreWindowOrder(wct, state)
        state.cancelTransitionToken =
            transitions.startTransition(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP, wct, this)
    }

    private fun restoreWindowOrder(
        wct: WindowContainerTransaction,
        state: TransitionState = requireTransitionState(),
    ) {
        when (state) {
            is TransitionState.FromFullscreen -> {
                // There may have been tasks sent behind home that are not the dragged task (like
                // when the dragged task is translucent and that makes the task behind it visible).
                // Restore the order of those first.
                state.otherRootChanges
                    .mapNotNull { it.container }
                    .forEach { wc ->
                        // TODO(b/322852244): investigate why even though these "other" tasks are
                        //  reordered in front of home and behind the translucent dragged task, its
                        //  surface is not visible on screen.
                        wct.reorder(wc, /* onTop= */ true)
                    }
                val wc =
                    state.draggedTaskChange?.container
                        ?: error("Dragged task should be non-null before cancelling")
                // Then the dragged task a the very top.
                wct.reorder(wc, /* onTop= */ true)
            }
            is TransitionState.FromSplit -> {
                val wc =
                    state.splitRootChange?.container
                        ?: error("Split root should be non-null before cancelling")
                wct.reorder(wc, /* onTop= */ true)
            }
        }
        val homeWc =
            state.homeChange?.container ?: error("Home task should be non-null before cancelling")
        wct.restoreTransientOrder(homeWc)
    }

    protected fun clearState() {
        transitionState = null
    }

    private fun isSplitTask(taskId: Int): Boolean =
        splitScreenController.isTaskInSplitScreen(taskId)

    private fun getOtherSplitTask(taskId: Int): Int? {
        val splitPos = splitScreenController.getSplitPosition(taskId)
        if (splitPos == SPLIT_POSITION_UNDEFINED) return null
        val otherTaskPos =
            if (splitPos == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
                SPLIT_POSITION_TOP_OR_LEFT
            } else {
                SPLIT_POSITION_BOTTOM_OR_RIGHT
            }
        return splitScreenController.getTaskInfo(otherTaskPos)?.taskId
    }

    protected fun requireTransitionState(): TransitionState =
        transitionState ?: error("Expected non-null transition state")

    /**
     * Represents the layering (Z order) that will be given to any window based on its type during
     * the "start" transition of the drag-to-desktop transition.
     *
     * @param topAppLayer Used to calculate the app layer z-order = `topAppLayer - changeIndex`.
     * @param topHomeLayer Used to calculate the home layer z-order = `topHomeLayer - changeIndex`.
     * @param topWallpaperLayer Used to calculate the wallpaper layer z-order = `topWallpaperLayer -
     *   changeIndex`
     * @param dragLayer Defines the drag layer z-order
     */
    data class DragToDesktopLayers(
        val topAppLayer: Int,
        val topHomeLayer: Int,
        val topWallpaperLayer: Int,
        val dragLayer: Int,
    )

    /** Listener for various events happening during the DragToDesktop transition. */
    interface DragToDesktopStateListener {
        /** Indicates that the animation into Desktop has started. */
        fun onCommitToDesktopAnimationStart()

        /** Called when the animation to cancel the desktop-drag has finished. */
        fun onCancelToDesktopAnimationEnd()

        /** Indicates that the drag-to-desktop transition has been interrupted. */
        fun onTransitionInterrupted()
    }

    sealed class TransitionState {
        abstract val draggedTaskId: Int
        abstract val dragAnimator: MoveToDesktopAnimator
        abstract val startTransitionToken: IBinder
        abstract var startTransitionFinishCb: Transitions.TransitionFinishCallback?
        abstract var startTransitionFinishTransaction: SurfaceControl.Transaction?
        abstract var cancelTransitionToken: IBinder?
        abstract var homeChange: Change?
        abstract var draggedTaskChange: Change?
        abstract var freeformTaskChanges: List<Change>
        abstract var surfaceLayers: DragToDesktopLayers?
        abstract var cancelState: CancelState
        abstract var startAborted: Boolean
        abstract val visualIndicator: DesktopModeVisualIndicator?
        abstract var startInterrupted: Boolean
        abstract var endTransitionToken: IBinder?
        abstract var mergedEndTransition: Boolean
        abstract var activeCancelAnimation: Animator?
        abstract var dragCancelCallback: Runnable?

        data class FromFullscreen(
            override val draggedTaskId: Int,
            override val dragAnimator: MoveToDesktopAnimator,
            override val startTransitionToken: IBinder,
            override var startTransitionFinishCb: Transitions.TransitionFinishCallback? = null,
            override var startTransitionFinishTransaction: SurfaceControl.Transaction? = null,
            override var cancelTransitionToken: IBinder? = null,
            override var homeChange: Change? = null,
            override var draggedTaskChange: Change? = null,
            override var freeformTaskChanges: List<Change> = emptyList(),
            override var surfaceLayers: DragToDesktopLayers? = null,
            override var cancelState: CancelState = CancelState.NO_CANCEL,
            override var startAborted: Boolean = false,
            override val visualIndicator: DesktopModeVisualIndicator?,
            override var startInterrupted: Boolean = false,
            override var endTransitionToken: IBinder? = null,
            override var mergedEndTransition: Boolean = false,
            override var activeCancelAnimation: Animator? = null,
            override var dragCancelCallback: Runnable? = null,
            var otherRootChanges: MutableList<Change> = mutableListOf(),
        ) : TransitionState()

        data class FromSplit(
            override val draggedTaskId: Int,
            override val dragAnimator: MoveToDesktopAnimator,
            override val startTransitionToken: IBinder,
            override var startTransitionFinishCb: Transitions.TransitionFinishCallback? = null,
            override var startTransitionFinishTransaction: SurfaceControl.Transaction? = null,
            override var cancelTransitionToken: IBinder? = null,
            override var homeChange: Change? = null,
            override var draggedTaskChange: Change? = null,
            override var freeformTaskChanges: List<Change> = emptyList(),
            override var surfaceLayers: DragToDesktopLayers? = null,
            override var cancelState: CancelState = CancelState.NO_CANCEL,
            override var startAborted: Boolean = false,
            override val visualIndicator: DesktopModeVisualIndicator?,
            override var startInterrupted: Boolean = false,
            override var endTransitionToken: IBinder? = null,
            override var mergedEndTransition: Boolean = false,
            override var activeCancelAnimation: Animator? = null,
            override var dragCancelCallback: Runnable? = null,
            var splitRootChange: Change? = null,
            var otherSplitTask: Int,
        ) : TransitionState()

        fun toSimpleString(): String {
            val origin =
                when (this) {
                    is FromSplit -> "FromSplit"
                    is FromFullscreen -> "FromFullscreen"
                }
            val draggedTask = "draggedTaskId=$draggedTaskId"
            val aborted = "aborted=$startAborted:"
            val interrupted = "interrupted=$startInterrupted"
            val cancelState = "cancelState=$cancelState"
            return "$origin: $draggedTask, $aborted, $interrupted, $cancelState"
        }
    }

    /** Enum to provide context on cancelling a drag to desktop event. */
    enum class CancelState {
        /** No cancel case; this drag is not flagged for a cancel event. */
        NO_CANCEL,
        /** A standard cancel event; should restore task to previous windowing mode. */
        STANDARD_CANCEL,
        /** A cancel event where the task will request to enter split on the left side. */
        CANCEL_SPLIT_LEFT,
        /** A cancel event where the task will request to enter split on the right side. */
        CANCEL_SPLIT_RIGHT,
        /** A cancel event where the task will request to bubble on the left side. */
        CANCEL_BUBBLE_LEFT,
        /** A cancel event where the task will request to bubble on the right side. */
        CANCEL_BUBBLE_RIGHT,
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DragToDesktopTransitionHandler"
        /** The duration of the animation to commit or cancel the drag-to-desktop gesture. */
        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
        const val DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS = 336L
    }
}

/** Enables flagged rollout of the [SpringDragToDesktopTransitionHandler] */
class DefaultDragToDesktopTransitionHandler
@JvmOverloads
constructor(
    context: Context,
    transitions: Transitions,
    taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    desksOrganizer: DesksOrganizer,
    desktopUserRepositories: DesktopUserRepositories,
    interactionJankMonitor: InteractionJankMonitor,
    bubbleController: Optional<BubbleController>,
    transactionSupplier: Supplier<SurfaceControl.Transaction> = Supplier {
        SurfaceControl.Transaction()
    },
    desktopState: DesktopState,
) :
    DragToDesktopTransitionHandler(
        context,
        transitions,
        taskDisplayAreaOrganizer,
        desksOrganizer,
        desktopUserRepositories,
        interactionJankMonitor,
        bubbleController,
        transactionSupplier,
        desktopState,
    ) {

    /**
     * @return layers in order:
     * - appLayers - non-wallpaper, non-home tasks excluding the dragged task go at the bottom
     * - homeLayers - home task on top of apps
     * - wallpaperLayers - wallpaper on top of home
     * - dragLayer - the dragged task on top of everything, there's only 1 dragged task
     */
    override fun calculateStartDragLayersWithDesktop(info: TransitionInfo): DragToDesktopLayers =
        DragToDesktopLayers(
            topAppLayer = info.changes.size,
            topHomeLayer = info.changes.size * 2,
            topWallpaperLayer = info.changes.size * 3,
            dragLayer = info.changes.size * 3,
        )
}

/** Desktop transition handler with spring based animation for the end drag to desktop transition */
class SpringDragToDesktopTransitionHandler
@JvmOverloads
constructor(
    context: Context,
    transitions: Transitions,
    taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    desksOrganizer: DesksOrganizer,
    desktopUserRepositories: DesktopUserRepositories,
    interactionJankMonitor: InteractionJankMonitor,
    bubbleController: Optional<BubbleController>,
    transactionSupplier: Supplier<SurfaceControl.Transaction> = Supplier {
        SurfaceControl.Transaction()
    },
    desktopState: DesktopState,
) :
    DragToDesktopTransitionHandler(
        context,
        transitions,
        taskDisplayAreaOrganizer,
        desksOrganizer,
        desktopUserRepositories,
        interactionJankMonitor,
        bubbleController,
        transactionSupplier,
        desktopState,
    ) {

    private val positionSpringConfig =
        PhysicsAnimator.SpringConfig(POSITION_SPRING_STIFFNESS, POSITION_SPRING_DAMPING_RATIO)

    private val sizeSpringConfig =
        PhysicsAnimator.SpringConfig(SIZE_SPRING_STIFFNESS, SIZE_SPRING_DAMPING_RATIO)

    /**
     * @return layers in order:
     * - appLayers - below everything z < 0, effectively hides the leash
     * - homeLayers - home task on top of apps, z in 0..<size
     * - wallpaperLayers - wallpaper on top of home, z in size..<size*2
     * - dragLayer - the dragged task on top of everything, z == size*2
     */
    override fun calculateStartDragLayersWithDesktop(info: TransitionInfo): DragToDesktopLayers =
        DragToDesktopLayers(
            topAppLayer = -1,
            topHomeLayer = info.changes.size - 1,
            topWallpaperLayer = info.changes.size * 2 - 1,
            dragLayer = info.changes.size * 2,
        )

    override fun setupEndDragToDesktop(
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        super.setupEndDragToDesktop(info, startTransaction, finishTransaction)

        val state = requireTransitionState()
        val homeLeash = state.homeChange?.leash
        if (homeLeash == null) {
            logE("home leash is null")
        } else {
            // Hide home on finish to prevent flickering when wallpaper activity flag is enabled
            finishTransaction.hide(homeLeash)
        }
        // Setup freeform tasks before animation
        state.freeformTaskChanges.forEach { change ->
            val startScale = FREEFORM_TASKS_INITIAL_SCALE
            val startX =
                change.endAbsBounds.left + change.endAbsBounds.width() * (1 - startScale) / 2
            val startY =
                change.endAbsBounds.top + change.endAbsBounds.height() * (1 - startScale) / 2
            startTransaction.setPosition(change.leash, startX, startY)
            startTransaction.setScale(change.leash, startScale, startScale)
            startTransaction.setAlpha(change.leash, 0f)
        }
    }

    override fun animateEndDragToDesktop(
        startTransaction: SurfaceControl.Transaction,
        startTransitionFinishCb: Transitions.TransitionFinishCallback,
    ) {
        val state = requireTransitionState()
        val draggedTaskChange =
            state.draggedTaskChange ?: error("Expected non-null change of dragged task")
        val draggedTaskLeash = draggedTaskChange.leash
        val freeformTaskChanges = state.freeformTaskChanges
        val startSize =
            Size(
                draggedTaskChange.startAbsBounds.width(),
                draggedTaskChange.startAbsBounds.height(),
            )
        val endBounds = draggedTaskChange.endAbsBounds
        val currentVelocity = state.dragAnimator.computeCurrentVelocity()

        // Cancel any animation that may be currently playing; we will use the relevant
        // details of that animation here.
        state.dragAnimator.cancelAnimator()
        // We still apply scale to task bounds; as we animate the bounds to their
        // end value, animate scale to 1.
        val startScale = state.dragAnimator.scale
        val startPosition = state.dragAnimator.position
        val startBounds =
            Rect(
                startPosition.x.toInt(),
                startPosition.y.toInt(),
                startPosition.x.toInt() + startSize.width,
                startPosition.x.toInt() + startSize.height,
            )

        logV(
            "animateEndDragToDesktop: startSize=$startSize, endBounds=$endBounds, " +
                "startScale=$startScale, startPosition=$startPosition, " +
                "startBounds=$startBounds"
        )

        dragToDesktopStateListener?.onCommitToDesktopAnimationStart()
        // Accept the merge by applying the merging transaction (applied by #showResizeVeil)
        // and finish callback. Show the veil and position the task at the first frame before
        // starting the final animation.
        onTaskResizeAnimationListener.onAnimationStart(
            state.draggedTaskId,
            startTransaction,
            startBounds,
        )

        val tx: SurfaceControl.Transaction = transactionSupplier.get()
        PhysicsAnimator.getInstance(startBounds)
            .spring(
                FloatProperties.RECT_X,
                endBounds.left.toFloat(),
                currentVelocity.x,
                positionSpringConfig,
            )
            .spring(
                FloatProperties.RECT_Y,
                endBounds.top.toFloat(),
                currentVelocity.y,
                positionSpringConfig,
            )
            .spring(FloatProperties.RECT_WIDTH, endBounds.width().toFloat(), sizeSpringConfig)
            .spring(FloatProperties.RECT_HEIGHT, endBounds.height().toFloat(), sizeSpringConfig)
            .addUpdateListener { animBounds, _ ->
                val animFraction =
                    getAnimationFraction(
                        startBounds = startBounds,
                        endBounds = endBounds,
                        animBounds = animBounds,
                    )
                val animScale = startScale + animFraction * (1 - startScale)
                // Freeform animation starts with freeform animation offset relative to the commit
                // animation and plays until the commit animation ends. For instance:
                // - if the freeform animation offset is `0.0` the freeform tasks animate alongside
                // - if the freeform animation offset is `0.6` the freeform tasks will
                //   start animating at 60% fraction of the commit animation and will complete when
                //   the commit animation fraction is 100%.
                // - if the freeform animation offset is `1.0` then freeform tasks will appear
                //   without animation after commit animation finishes.
                val freeformAnimFraction =
                    if (FREEFORM_TASKS_ANIM_OFFSET != 1f) {
                        max(animFraction - FREEFORM_TASKS_ANIM_OFFSET, 0f) /
                            (1f - FREEFORM_TASKS_ANIM_OFFSET)
                    } else {
                        0f
                    }
                val freeformStartScale = FREEFORM_TASKS_INITIAL_SCALE
                val freeformAnimScale =
                    freeformStartScale + freeformAnimFraction * (1 - freeformStartScale)
                tx.apply {
                    // Update dragged task
                    setScale(draggedTaskLeash, animScale, animScale)
                    setPosition(
                        draggedTaskLeash,
                        animBounds.left.toFloat(),
                        animBounds.top.toFloat(),
                    )
                    // Update freeform tasks
                    freeformTaskChanges.forEach {
                        val startX =
                            it.endAbsBounds.left +
                                it.endAbsBounds.width() * (1 - freeformAnimScale) / 2
                        val startY =
                            it.endAbsBounds.top +
                                it.endAbsBounds.height() * (1 - freeformAnimScale) / 2
                        setPosition(it.leash, startX, startY)
                        setScale(it.leash, freeformAnimScale, freeformAnimScale)
                        setAlpha(it.leash, freeformAnimFraction)
                    }
                }
                onTaskResizeAnimationListener.onBoundsChange(state.draggedTaskId, tx, animBounds)
            }
            .withEndActions({
                onTaskResizeAnimationListener.onAnimationEnd(state.draggedTaskId)
                startTransitionFinishCb.onTransitionFinished(/* wct= */ null)
                clearState()
                interactionJankMonitor.end(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE)
            })
            .start()
    }

    companion object {
        private const val TAG = "SpringDragToDesktopTransitionHandler"

        fun getAnimationFraction(startBounds: Rect, endBounds: Rect, animBounds: Rect): Float {
            if (startBounds.width() != endBounds.width()) {
                return (animBounds.width() - startBounds.width()).toFloat() /
                    (endBounds.width() - startBounds.width())
            }
            if (startBounds.height() != endBounds.height()) {
                return (animBounds.height() - startBounds.height()).toFloat() /
                    (endBounds.height() - startBounds.height())
            }
            logW(
                "same start and end sizes, returning 0: " +
                    "startBounds=$startBounds, endBounds=$endBounds, animBounds=$animBounds"
            )
            return 0f
        }

        private fun logV(msg: String, vararg arguments: Any?) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
        }

        private fun logW(msg: String, vararg arguments: Any?) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
        }

        private fun logE(msg: String, vararg arguments: Any?) {
            ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
        }

        /** The freeform tasks initial scale when committing the drag-to-desktop gesture. */
        private val FREEFORM_TASKS_INITIAL_SCALE =
            propertyValue("freeform_tasks_initial_scale", scale = 100f, default = 0.9f)

        /** The freeform tasks animation offset relative to the whole animation duration. */
        private val FREEFORM_TASKS_ANIM_OFFSET =
            propertyValue("freeform_tasks_anim_offset", scale = 100f, default = 0.5f)

        /** The spring force stiffness used to place the window into the final position. */
        val POSITION_SPRING_STIFFNESS =
            propertyValue("position_stiffness", default = SpringForce.STIFFNESS_LOW)

        /** The spring force damping ratio used to place the window into the final position. */
        val POSITION_SPRING_DAMPING_RATIO =
            propertyValue(
                "position_damping_ratio",
                scale = 100f,
                default = SpringForce.DAMPING_RATIO_LOW_BOUNCY,
            )

        /** The spring force stiffness used to resize the window into the final bounds. */
        val SIZE_SPRING_STIFFNESS =
            propertyValue("size_stiffness", default = SpringForce.STIFFNESS_LOW)

        /** The spring force damping ratio used to resize the window into the final bounds. */
        val SIZE_SPRING_DAMPING_RATIO =
            propertyValue(
                "size_damping_ratio",
                scale = 100f,
                default = SpringForce.DAMPING_RATIO_NO_BOUNCY,
            )

        /** Drag to desktop transition system properties group. */
        @VisibleForTesting
        const val SYSTEM_PROPERTIES_GROUP = "persist.wm.debug.desktop_transitions.drag_to_desktop"

        /**
         * Drag to desktop transition system property value with [name].
         *
         * @param scale an optional scale to apply to the value read from the system property.
         * @param default a default value to return if the system property isn't set.
         */
        @VisibleForTesting
        fun propertyValue(name: String, scale: Float = 1f, default: Float = 0f): Float =
            SystemProperties.getInt(
                /* key= */ "$SYSTEM_PROPERTIES_GROUP.$name",
                /* def= */ (default * scale).toInt(),
            ) / scale
    }
}
