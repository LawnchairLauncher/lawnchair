package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.util.KtProtoLog
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator.Companion.DRAG_FREEFORM_SCALE
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import java.util.function.Supplier

/**
 * Handles the transition to enter desktop from fullscreen by dragging on the handle bar. It also
 * handles the cancellation case where the task is dragged back to the status bar area in the same
 * gesture.
 */
class DragToDesktopTransitionHandler(
    private val context: Context,
    private val transitions: Transitions,
    private val taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>
) : TransitionHandler {

    constructor(
        context: Context,
        transitions: Transitions,
        rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    ) : this(
        context,
        transitions,
        rootTaskDisplayAreaOrganizer,
        Supplier { SurfaceControl.Transaction() }
    )

    private val rectEvaluator = RectEvaluator(Rect())
    private val launchHomeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private var dragToDesktopStateListener: DragToDesktopStateListener? = null
    private lateinit var splitScreenController: SplitScreenController
    private var transitionState: TransitionState? = null
    private lateinit var onTaskResizeAnimationListener: OnTaskResizeAnimationListener

    /** Whether a drag-to-desktop transition is in progress. */
    val inProgress: Boolean
        get() = transitionState != null

    /** The task id of the task currently being dragged from fullscreen/split. */
    val draggingTaskId: Int
        get() = transitionState?.draggedTaskId ?: INVALID_TASK_ID
    /** Sets a listener to receive callback about events during the transition animation. */
    fun setDragToDesktopStateListener(listener: DragToDesktopStateListener) {
        dragToDesktopStateListener = listener
    }

    /** Setter needed to avoid cyclic dependency. */
    fun setSplitScreenController(controller: SplitScreenController) {
        splitScreenController = controller
    }

    fun setOnTaskResizeAnimatorListener(listener: OnTaskResizeAnimationListener) {
        onTaskResizeAnimationListener = listener
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
        taskId: Int,
        dragToDesktopAnimator: MoveToDesktopAnimator,
    ) {
        if (inProgress) {
            KtProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                "DragToDesktop: Drag to desktop transition already in progress."
            )
            return
        }

        val options =
            ActivityOptions.makeBasic().apply {
                setTransientLaunch()
                setSourceInfo(SourceInfo.TYPE_DESKTOP_ANIMATION, SystemClock.uptimeMillis())
                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0 /* requestCode */,
                launchHomeIntent,
                FLAG_MUTABLE or FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT or FILL_IN_COMPONENT,
                options.toBundle()
            )
        val wct = WindowContainerTransaction()
        wct.sendPendingIntent(pendingIntent, launchHomeIntent, Bundle())
        val startTransitionToken =
            transitions.startTransition(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP, wct, this)

        transitionState =
            if (isSplitTask(taskId)) {
                val otherTask =
                    getOtherSplitTask(taskId)
                        ?: throw IllegalStateException("Expected split task to have a counterpart.")
                TransitionState.FromSplit(
                    draggedTaskId = taskId,
                    dragAnimator = dragToDesktopAnimator,
                    startTransitionToken = startTransitionToken,
                    otherSplitTask = otherTask
                )
            } else {
                TransitionState.FromFullscreen(
                    draggedTaskId = taskId,
                    dragAnimator = dragToDesktopAnimator,
                    startTransitionToken = startTransitionToken
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
            // Don't attempt to finish a drag to desktop transition since there is no transition in
            // progress which means that the drag to desktop transition was never successfully
            // started.
            return null
        }
        if (requireTransitionState().startAborted) {
            // Don't attempt to complete the drag-to-desktop since the start transition didn't
            // succeed as expected. Just reset the state as if nothing happened.
            clearState()
            return null
        }
        return transitions.startTransition(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP, wct, this)
    }

    /**
     * Starts a transition that "cancels" the drag to desktop gesture. This transition is intended
     * to merge into the "start" transition and it restores the transient state that was used to
     * launch the Home task over the dragged task. This is called when the dragged task is released
     * outside the desktop drop zone and is instead dropped back into the status bar region that
     * means the user wants to remain in their current windowing mode.
     */
    fun cancelDragToDesktopTransition(cancelState: CancelState) {
        if (!inProgress) {
            // Don't attempt to cancel a drag to desktop transition since there is no transition in
            // progress which means that the drag to desktop transition was never successfully
            // started.
            return
        }
        val state = requireTransitionState()
        if (state.startAborted) {
            // Don't attempt to cancel the drag-to-desktop since the start transition didn't
            // succeed as expected. Just reset the state as if nothing happened.
            clearState()
            return
        }
        state.cancelState = cancelState

        if (state.draggedTaskChange != null && cancelState == CancelState.STANDARD_CANCEL) {
            // Regular case, transient launch of Home happened as is waiting for the cancel
            // transient to start and merge. Animate the cancellation (scale back to original
            // bounds) first before actually starting the cancel transition so that the wallpaper
            // is visible behind the animating task.
            startCancelAnimation()
        } else if (
            state.draggedTaskChange != null &&
            (cancelState == CancelState.CANCEL_SPLIT_LEFT ||
                    cancelState == CancelState.CANCEL_SPLIT_RIGHT)
            ) {
            // We have a valid dragged task, but the animation will be handled by
            // SplitScreenController; request the transition here.
            @SplitPosition val splitPosition = if (cancelState == CancelState.CANCEL_SPLIT_LEFT) {
                SPLIT_POSITION_TOP_OR_LEFT
            } else {
                SPLIT_POSITION_BOTTOM_OR_RIGHT
            }
            val wct = WindowContainerTransaction()
            restoreWindowOrder(wct, state)
            state.startTransitionFinishTransaction?.apply()
            state.startTransitionFinishCb?.onTransitionFinished(null /* wct */)
            requestSplitFromScaledTask(splitPosition, wct)
            clearState()
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
        wct: WindowContainerTransaction
    ) {
        val state = requireTransitionState()
        val taskInfo = state.draggedTaskChange?.taskInfo
            ?: error("Expected non-null taskInfo")
        val taskBounds = Rect(taskInfo.configuration.windowConfiguration.bounds)
        val taskScale = state.dragAnimator.scale
        val scaledWidth = taskBounds.width() * taskScale
        val scaledHeight = taskBounds.height() * taskScale
        val dragPosition = PointF(state.dragAnimator.position)
        state.dragAnimator.cancelAnimator()
        val animatedTaskBounds = Rect(
            dragPosition.x.toInt(),
            dragPosition.y.toInt(),
            (dragPosition.x + scaledWidth).toInt(),
            (dragPosition.y + scaledHeight).toInt()
        )
        requestSplitSelect(wct, taskInfo, splitPosition, animatedTaskBounds)
    }

    private fun requestSplitSelect(
        wct: WindowContainerTransaction,
        taskInfo: RunningTaskInfo,
        @SplitPosition splitPosition: Int,
        taskBounds: Rect = Rect(taskInfo.configuration.windowConfiguration.bounds)
    ) {
        // Prepare to exit split in order to enter split select.
        if (taskInfo.windowingMode == WINDOWING_MODE_MULTI_WINDOW) {
            splitScreenController.prepareExitSplitScreen(
                wct,
                splitScreenController.getStageOfTask(taskInfo.taskId),
                SplitScreenController.EXIT_REASON_DESKTOP_MODE
            )
            splitScreenController.transitionHandler.onSplitToDesktop()
        }
        wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_MULTI_WINDOW)
        wct.setDensityDpi(taskInfo.token, context.resources.displayMetrics.densityDpi)
        splitScreenController.requestEnterSplitSelect(
            taskInfo,
            wct,
            splitPosition,
            taskBounds
        )
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback
    ): Boolean {
        val state = requireTransitionState()

        val isStartDragToDesktop =
            info.type == TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP &&
                transition == state.startTransitionToken
        if (!isStartDragToDesktop) {
            return false
        }

        // Layering: non-wallpaper, non-home tasks excluding the dragged task go at the bottom,
        // then Home on top of that, wallpaper on top of that and finally the dragged task on top
        // of everything.
        val appLayers = info.changes.size
        val homeLayers = info.changes.size * 2
        val wallpaperLayers = info.changes.size * 3
        val dragLayer = wallpaperLayers
        val leafTaskFilter = TransitionUtil.LeafTaskFilter()
        info.changes.withIndex().forEach { (i, change) ->
            if (TransitionUtil.isWallpaper(change)) {
                val layer = wallpaperLayers - i
                startTransaction.apply {
                    setLayer(change.leash, layer)
                    show(change.leash)
                }
            } else if (isHomeChange(change)) {
                state.homeToken = change.container
                val layer = homeLayers - i
                startTransaction.apply {
                    setLayer(change.leash, layer)
                    show(change.leash)
                }
            } else if (TransitionInfo.isIndependent(change, info)) {
                // Root(s).
                when (state) {
                    is TransitionState.FromSplit -> {
                        state.splitRootChange = change
                        val layer =
                            if (state.cancelState == CancelState.NO_CANCEL) {
                                // Normal case, split root goes to the bottom behind everything
                                // else.
                                appLayers - i
                            } else {
                                // Cancel-early case, pretend nothing happened so split root stays
                                // top.
                                dragLayer
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
                                setLayer(change.leash, dragLayer)
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
                                setLayer(change.leash, appLayers - i)
                                setWindowCrop(change.leash, bounds.width(), bounds.height())
                                show(change.leash)
                            }
                        }
                    }
                }
            } else if (leafTaskFilter.test(change)) {
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
                        startTransaction
                    )
                    val bounds = change.endAbsBounds
                    startTransaction.apply {
                        setLayer(change.leash, dragLayer)
                        setWindowCrop(change.leash, bounds.width(), bounds.height())
                        show(change.leash)
                    }
                }
            }
        }
        state.startTransitionFinishCb = finishCallback
        state.startTransitionFinishTransaction = finishTransaction
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
            ){
            // Cancel-early case for split-cancel. The state was flagged already as a cancel for
            // requesting split select. Similar to the above, this can happen due to quick fling
            // gestures. We can simply request split here without needing to calculate animated
            // task bounds as the task has not shrunk at all.
            val splitPosition = if (state.cancelState == CancelState.CANCEL_SPLIT_LEFT) {
                SPLIT_POSITION_TOP_OR_LEFT
            } else {
                SPLIT_POSITION_BOTTOM_OR_RIGHT
            }
            val taskInfo = state.draggedTaskChange?.taskInfo
                ?: error("Expected non-null task info.")
            val wct = WindowContainerTransaction()
            restoreWindowOrder(wct)
            state.startTransitionFinishTransaction?.apply()
            state.startTransitionFinishCb?.onTransitionFinished(null /* wct */)
            requestSplitSelect(wct, taskInfo, splitPosition)
        }
        return true
    }

    override fun mergeAnimation(
        transition: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        mergeTarget: IBinder,
        finishCallback: Transitions.TransitionFinishCallback
    ) {
        val state = requireTransitionState()
        // We don't want to merge the split select animation if that's what we requested.
        if (state.cancelState == CancelState.CANCEL_SPLIT_LEFT ||
            state.cancelState == CancelState.CANCEL_SPLIT_RIGHT) {
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
            info.changes.withIndex().forEach { (i, change) ->
                // If we're exiting split, hide the remaining split task.
                if (
                    state is TransitionState.FromSplit &&
                        change.taskInfo?.taskId == state.otherSplitTask
                ) {
                    t.hide(change.leash)
                    startTransactionFinishT.hide(change.leash)
                }
                if (change.mode == TRANSIT_CLOSE) {
                    t.hide(change.leash)
                    startTransactionFinishT.hide(change.leash)
                } else if (change.taskInfo?.taskId == state.draggedTaskId) {
                    t.show(change.leash)
                    startTransactionFinishT.show(change.leash)
                    state.draggedTaskChange = change
                } else if (change.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM) {
                    // Other freeform tasks that are being restored go behind the dragged task.
                    val draggedTaskLeash =
                        state.draggedTaskChange?.leash
                            ?: error("Expected dragged leash to be non-null")
                    t.setRelativeLayer(change.leash, draggedTaskLeash, -i)
                    startTransactionFinishT.setRelativeLayer(change.leash, draggedTaskLeash, -i)
                }
            }

            val draggedTaskChange =
                state.draggedTaskChange
                    ?: throw IllegalStateException("Expected non-null change of dragged task")
            val draggedTaskLeash = draggedTaskChange.leash
            val startBounds = draggedTaskChange.startAbsBounds
            val endBounds = draggedTaskChange.endAbsBounds

            // Pause any animation that may be currently playing; we will use the relevant
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
                    startPosition.y.toInt() + unscaledStartHeight
                )

            dragToDesktopStateListener?.onCommitToDesktopAnimationStart(t)
            // Accept the merge by applying the merging transaction (applied by #showResizeVeil)
            // and finish callback. Show the veil and position the task at the first frame before
            // starting the final animation.
            onTaskResizeAnimationListener.onAnimationStart(
                state.draggedTaskId,
                t,
                unscaledStartBounds
            )
            finishCallback.onTransitionFinished(null /* wct */)
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
                                animBounds.top.toFloat()
                            )
                            setWindowCrop(draggedTaskLeash, animBounds.width(), animBounds.height())
                        }
                        onTaskResizeAnimationListener.onBoundsChange(
                            state.draggedTaskId,
                            tx,
                            animBounds
                        )
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                onTaskResizeAnimationListener.onAnimationEnd(state.draggedTaskId)
                                startTransitionFinishCb.onTransitionFinished(null /* null */)
                                clearState()
                            }
                        }
                    )
                    start()
                }
        } else if (isCancelTransition) {
            info.changes.forEach { change ->
                t.show(change.leash)
                startTransactionFinishT.show(change.leash)
            }
            t.apply()
            finishCallback.onTransitionFinished(null /* wct */)
            startTransitionFinishCb.onTransitionFinished(null /* wct */)
            clearState()
        }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        // Only handle transitions started from shell.
        return null
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?
    ) {
        val state = transitionState ?: return
        if (aborted && state.startTransitionToken == transition) {
            KtProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                "DragToDesktop: onTransitionConsumed() start transition aborted"
            )
            state.startAborted = true
        }
    }

    private fun isHomeChange(change: Change): Boolean {
        return change.taskInfo?.activityType == ACTIVITY_TYPE_HOME
    }

    private fun startCancelAnimation() {
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
        ValueAnimator.ofFloat(DRAG_FREEFORM_SCALE, 1f)
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
                            dragToDesktopStateListener?.onCancelToDesktopAnimationEnd(tx)
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
            transitions.startTransition(
                TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP, wct, this
            )
    }

    private fun restoreWindowOrder(
        wct: WindowContainerTransaction,
        state: TransitionState = requireTransitionState()
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
                        wct.reorder(wc, true /* toTop */)
                    }
                val wc =
                    state.draggedTaskChange?.container
                        ?: error("Dragged task should be non-null before cancelling")
                // Then the dragged task a the very top.
                wct.reorder(wc, true /* toTop */)
            }
            is TransitionState.FromSplit -> {
                val wc =
                    state.splitRootChange?.container
                        ?: error("Split root should be non-null before cancelling")
                wct.reorder(wc, true /* toTop */)
            }
        }
        val homeWc = state.homeToken ?: error("Home task should be non-null before cancelling")
        wct.restoreTransientOrder(homeWc)
    }

    private fun clearState() {
        transitionState = null
    }

    private fun isSplitTask(taskId: Int): Boolean {
        return splitScreenController.isTaskInSplitScreen(taskId)
    }

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

    private fun requireTransitionState(): TransitionState {
        return transitionState ?: error("Expected non-null transition state")
    }

    interface DragToDesktopStateListener {
        fun onCommitToDesktopAnimationStart(tx: SurfaceControl.Transaction)

        fun onCancelToDesktopAnimationEnd(tx: SurfaceControl.Transaction)
    }

    sealed class TransitionState {
        abstract val draggedTaskId: Int
        abstract val dragAnimator: MoveToDesktopAnimator
        abstract val startTransitionToken: IBinder
        abstract var startTransitionFinishCb: Transitions.TransitionFinishCallback?
        abstract var startTransitionFinishTransaction: SurfaceControl.Transaction?
        abstract var cancelTransitionToken: IBinder?
        abstract var homeToken: WindowContainerToken?
        abstract var draggedTaskChange: Change?
        abstract var cancelState: CancelState
        abstract var startAborted: Boolean

        data class FromFullscreen(
            override val draggedTaskId: Int,
            override val dragAnimator: MoveToDesktopAnimator,
            override val startTransitionToken: IBinder,
            override var startTransitionFinishCb: Transitions.TransitionFinishCallback? = null,
            override var startTransitionFinishTransaction: SurfaceControl.Transaction? = null,
            override var cancelTransitionToken: IBinder? = null,
            override var homeToken: WindowContainerToken? = null,
            override var draggedTaskChange: Change? = null,
            override var cancelState: CancelState = CancelState.NO_CANCEL,
            override var startAborted: Boolean = false,
            var otherRootChanges: MutableList<Change> = mutableListOf()
        ) : TransitionState()

        data class FromSplit(
            override val draggedTaskId: Int,
            override val dragAnimator: MoveToDesktopAnimator,
            override val startTransitionToken: IBinder,
            override var startTransitionFinishCb: Transitions.TransitionFinishCallback? = null,
            override var startTransitionFinishTransaction: SurfaceControl.Transaction? = null,
            override var cancelTransitionToken: IBinder? = null,
            override var homeToken: WindowContainerToken? = null,
            override var draggedTaskChange: Change? = null,
            override var cancelState: CancelState = CancelState.NO_CANCEL,
            override var startAborted: Boolean = false,
            var splitRootChange: Change? = null,
            var otherSplitTask: Int
        ) : TransitionState()
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
        CANCEL_SPLIT_RIGHT
    }

    companion object {
        /** The duration of the animation to commit or cancel the drag-to-desktop gesture. */
        private const val DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS = 336L
    }
}
