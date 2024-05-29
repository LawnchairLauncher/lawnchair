/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.quickstep.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.IdRes
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.core.view.updateLayoutParams
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags.enableCursorHoverStates
import com.android.launcher3.Flags.enableFocusOutline
import com.android.launcher3.Flags.enableGridOnlyOverview
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.Flags.privateSpaceRestrictAccessibilityDrag
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags.ENABLE_KEYBOARD_QUICK_SWITCH
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.TraceHelper
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.set
import com.android.quickstep.RecentsModel
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.TaskUtils
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.orientation.RecentsPagedOrientationHandler
import com.android.quickstep.task.thumbnail.TaskThumbnail
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.task.viewmodel.TaskViewData
import com.android.quickstep.util.ActiveGestureErrorDetector
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.util.TaskRemovedDuringLaunchListener
import com.android.quickstep.views.RecentsView.UNBOUND_TASK_VIEW_ID
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.QuickStepContract

/** A task in the Recents view. */
open class TaskView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    focusBorderAnimator: BorderAnimator? = null,
    hoverBorderAnimator: BorderAnimator? = null
) : FrameLayout(context, attrs), ViewPool.Reusable {
    /**
     * Used in conjunction with [onTaskListVisibilityChanged], providing more granularity on which
     * components of this task require an update
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(FLAG_UPDATE_ALL, FLAG_UPDATE_ICON, FLAG_UPDATE_THUMBNAIL, FLAG_UPDATE_CORNER_RADIUS)
    annotation class TaskDataChanges

    /** Type of task view */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(Type.SINGLE, Type.GROUPED, Type.DESKTOP)
    annotation class Type {
        companion object {
            const val SINGLE = 1
            const val GROUPED = 2
            const val DESKTOP = 3
        }
    }

    val taskViewData = TaskViewData()
    val taskIds: IntArray
        /** Returns a copy of integer array containing taskIds of all tasks in the TaskView. */
        get() = taskContainers.map { it.task.key.id }.toIntArray()

    val thumbnailViews: Array<TaskThumbnailViewDeprecated>
        get() = taskContainers.map { it.thumbnailViewDeprecated }.toTypedArray()

    val isGridTask: Boolean
        /** Returns whether the task is part of overview grid and not being focused. */
        get() = container.deviceProfile.isTablet && !isFocusedTask

    val isRunningTask: Boolean
        get() = this === recentsView?.runningTaskView

    val isFocusedTask: Boolean
        get() = this === recentsView?.focusedTaskView

    val taskCornerRadius: Float
        get() = currentFullscreenParams.cornerRadius

    val recentsView: RecentsView<*, *>?
        get() = parent as? RecentsView<*, *>

    val pagedOrientationHandler: RecentsPagedOrientationHandler
        get() = orientedState.orientationHandler

    @get:Deprecated("Use [taskContainers] instead.")
    val firstTask: Task
        /** Returns the first task bound to this TaskView. */
        get() = taskContainers[0].task

    @get:Deprecated("Use [taskContainers] instead.")
    val firstThumbnailViewDeprecated: TaskThumbnailViewDeprecated
        /** Returns the first thumbnailView of the TaskView. */
        get() = taskContainers[0].thumbnailViewDeprecated

    @get:Deprecated("Use [taskContainers] instead.")
    val firstItemInfo: ItemInfo
        get() = taskContainers[0].itemInfo

    private val currentFullscreenParams = FullscreenDrawParams(context)
    protected val container: RecentsViewContainer =
        RecentsViewContainer.containerFromContext(context)
    protected val lastTouchDownPosition = PointF()

    // Derived view properties
    protected val persistentScale: Float
        /**
         * Returns multiplication of scale that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state.
         */
        get() = Utilities.mapRange(gridProgress, nonGridScale, 1f)

    protected val persistentTranslationX: Float
        /**
         * Returns addition of translationX that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() =
            (getNonGridTrans(nonGridTranslationX) +
                getGridTrans(this.gridTranslationX) +
                getNonGridTrans(nonGridPivotTranslationX))

    protected val persistentTranslationY: Float
        /**
         * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() = boxTranslationY + getGridTrans(gridTranslationY)

    protected val primarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y
            )

    protected val secondarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y
            )

    protected val primaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    protected val secondaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    protected val primaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y
            )

    protected val secondaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y
            )

    protected val taskResistanceTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X,
                TASK_RESISTANCE_TRANSLATION_Y
            )

    private val tempCoordinates = FloatArray(2)
    private val focusBorderAnimator: BorderAnimator?
    private val hoverBorderAnimator: BorderAnimator?
    private val rootViewDisplayId: Int
        get() = rootView.display?.displayId ?: Display.DEFAULT_DISPLAY

    /** Returns a list of all TaskContainers in the TaskView. */
    lateinit var taskContainers: List<TaskContainer>
        protected set

    lateinit var orientedState: RecentsOrientedState

    var taskViewId = UNBOUND_TASK_VIEW_ID
    var isEndQuickSwitchCuj = false

    // Various animation progress variables.
    // progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
    protected var fullscreenProgress = 0f
        set(value) {
            field = Utilities.boundToRange(value, 0f, 1f)
            onFullscreenProgressChanged(field)
        }
    // gridProgress 0 = carousel; 1 = 2 row grid.
    protected var gridProgress = 0f
        set(value) {
            field = value
            onGridProgressChanged()
        }
    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview. 0 being in context with other tasks, 1 being shown on its own.
     */
    protected var modalness = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            onModalnessUpdated(field)
        }

    protected var taskThumbnailSplashAlpha = 0f
        set(value) {
            field = value
            applyThumbnailSplashAlpha()
        }

    protected var nonGridScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var dismissTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskOffsetTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskOffsetTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskResistanceTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskResistanceTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }
    // The following translation variables should only be used in the same orientation as Launcher.
    private var boxTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }
    // The following grid translations scales with mGridProgress.
    protected var gridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    var gridTranslationY = 0f
        protected set(value) {
            field = value
            applyTranslationY()
        }
    // The following grid translation is used to animate closing the gap between grid and clear all.
    private var gridEndTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }
    // Applied as a complement to gridTranslation, for adjusting the carousel overview and quick
    // switch.
    protected var nonGridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    protected var nonGridPivotTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }
    // Used when in SplitScreenSelectState
    private var splitSelectTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var splitSelectTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    protected var stableAlpha = 1f
        set(value) {
            field = value
            alpha = stableAlpha
        }

    protected var shouldShowScreenshot = false
        get() = !isRunningTask || field
    /** Enable or disable showing border on hover and focus change */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    var borderEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            // Set the animation correctly in case it misses the hover/focus event during state
            // transition
            hoverBorderAnimator?.setBorderVisibility(visible = field && isHovered, animated = true)
            focusBorderAnimator?.setBorderVisibility(visible = field && isFocused, animated = true)
        }

    protected var iconScaleAnimStartProgress = 0f
    private var focusTransitionProgress = 1f

    private var iconAndDimAnimator: ObjectAnimator? = null
    // The current background requests to load the task thumbnail and icon
    private val pendingThumbnailLoadRequests = mutableListOf<CancellableTask<*>>()
    private val pendingIconLoadRequests = mutableListOf<CancellableTask<*>>()
    private var isClickableAsLiveTile = true

    init {
        setOnClickListener { _ -> onClick() }
        val keyboardFocusHighlightEnabled =
            (ENABLE_KEYBOARD_QUICK_SWITCH.get() || enableFocusOutline())
        val cursorHoverStatesEnabled = enableCursorHoverStates()
        setWillNotDraw(!keyboardFocusHighlightEnabled && !cursorHoverStatesEnabled)
        context.obtainStyledAttributes(attrs, R.styleable.TaskView, defStyleAttr, defStyleRes).use {
            this.focusBorderAnimator =
                focusBorderAnimator
                    ?: if (keyboardFocusHighlightEnabled)
                        createSimpleBorderAnimator(
                            currentFullscreenParams.cornerRadius.toInt(),
                            context.resources.getDimensionPixelSize(
                                R.dimen.keyboard_quick_switch_border_width
                            ),
                            { bounds: Rect -> getThumbnailBounds(bounds) },
                            this,
                            it.getColor(
                                R.styleable.TaskView_focusBorderColor,
                                BorderAnimator.DEFAULT_BORDER_COLOR
                            )
                        )
                    else null
            this.hoverBorderAnimator =
                hoverBorderAnimator
                    ?: if (cursorHoverStatesEnabled)
                        createSimpleBorderAnimator(
                            currentFullscreenParams.cornerRadius.toInt(),
                            context.resources.getDimensionPixelSize(
                                R.dimen.task_hover_border_width
                            ),
                            { bounds: Rect -> getThumbnailBounds(bounds) },
                            this,
                            it.getColor(
                                R.styleable.TaskView_hoverBorderColor,
                                BorderAnimator.DEFAULT_BORDER_COLOR
                            )
                        )
                    else null
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (borderEnabled) {
            focusBorderAnimator?.setBorderVisibility(gainFocus, /* animated= */ true)
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (borderEnabled) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER ->
                    hoverBorderAnimator?.setBorderVisibility(visible = true, animated = true)
                MotionEvent.ACTION_HOVER_EXIT ->
                    hoverBorderAnimator?.setBorderVisibility(visible = false, animated = true)
                else -> {}
            }
        }
        return super.onHoverEvent(event)
    }

    // avoid triggering hover event on child elements which would cause HOVER_EXIT for this
    // task view
    override fun onInterceptHoverEvent(event: MotionEvent) =
        if (enableCursorHoverStates()) true else super.onInterceptHoverEvent(event)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val recentsView = recentsView ?: return false
        val splitSelectStateController = recentsView.splitSelectController
        // Disable taps for split selection animation unless we have a task not being selected
        if (
            splitSelectStateController.isSplitSelectActive &&
                taskContainers.none { it.task.key.id != splitSelectStateController.initialTaskId }
        ) {
            return false
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            with(lastTouchDownPosition) {
                x = ev.x
                y = ev.y
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun draw(canvas: Canvas) {
        // Draw border first so any child views outside of the thumbnail bounds are drawn above it.
        focusBorderAnimator?.drawBorder(canvas)
        hoverBorderAnimator?.drawBorder(canvas)
        super.draw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val thumbnailTopMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        if (container.deviceProfile.isTablet) {
            pivotX = (if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else right - left).toFloat()
            pivotY = thumbnailTopMargin.toFloat()
        } else {
            pivotX = (right - left) * 0.5f
            pivotY = thumbnailTopMargin + (height - thumbnailTopMargin) * 0.5f
        }
        systemGestureExclusionRects =
            SYSTEM_GESTURE_EXCLUSION_RECT.onEach {
                it.right = width
                it.bottom = height
            }
    }

    override fun onRecycle() {
        resetPersistentViewTransforms()
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        if (enableRefactorTaskThumbnail()) {
            notifyIsRunningTaskUpdated()
        } else {
            taskContainers.forEach { it.thumbnailViewDeprecated.setThumbnail(it.task, null) }
        }
        setOverlayEnabled(false)
        onTaskListVisibilityChanged(false)
        borderEnabled = false
        taskViewId = UNBOUND_TASK_VIEW_ID
        taskContainers.forEach { it.destroy() }
    }

    // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
    override fun hasOverlappingRendering() = false

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        with(info) {
            addAction(
                AccessibilityAction(
                    R.id.action_close,
                    context.getText(R.string.accessibility_close)
                )
            )

            taskContainers.forEach {
                TraceHelper.allowIpcs("TV.a11yInfo") {
                    TaskOverlayFactory.getEnabledShortcuts(this@TaskView, it).forEach { shortcut ->
                        addAction(shortcut.createAccessibilityAction(context))
                    }
                }
            }

            // Add DWB accessibility action at the end of the list
            taskContainers.forEach {
                it.digitalWellBeingToast?.getDWBAccessibilityAction()?.let(::addAction)
            }

            recentsView?.let {
                collectionItemInfo =
                    AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0,
                        1,
                        it.taskViewCount - it.indexOfChild(this@TaskView) - 1,
                        1,
                        false
                    )
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        // TODO(b/343708271): Add support for multiple tasks per action.
        if (action == R.id.action_close) {
            recentsView?.dismissTask(this, true /*animateTaskView*/, true /*removeTask*/)
            return true
        }

        taskContainers.forEach {
            if (it.digitalWellBeingToast?.handleAccessibilityAction(action) == true) {
                return true
            }

            TaskOverlayFactory.getEnabledShortcuts(this, it).forEach { shortcut ->
                if (shortcut.hasHandlerForAction(action)) {
                    shortcut.onClick(this)
                    return true
                }
            }
        }

        return super.performAccessibilityAction(action, arguments)
    }

    /** Updates this task view to the given {@param task}. */
    open fun bind(
        task: Task,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory
    ) {
        cancelPendingLoadTasks()
        taskContainers =
            listOf(
                createTaskContainer(
                    task,
                    R.id.snapshot,
                    R.id.icon,
                    R.id.show_windows,
                    STAGE_POSITION_UNDEFINED,
                    taskOverlayFactory
                )
            )
        setOrientationState(orientedState)
    }

    protected fun createTaskContainer(
        task: Task,
        @IdRes thumbnailViewId: Int,
        @IdRes iconViewId: Int,
        @IdRes showWindowViewId: Int,
        @StagePosition stagePosition: Int,
        taskOverlayFactory: TaskOverlayFactory
    ): TaskContainer {
        val thumbnailViewDeprecated: TaskThumbnailViewDeprecated = findViewById(thumbnailViewId)!!
        val thumbnailView: TaskThumbnailView?
        if (enableRefactorTaskThumbnail()) {
            val indexOfSnapshotView = indexOfChild(thumbnailViewDeprecated)
            thumbnailView =
                TaskThumbnailView(context).apply {
                    layoutParams = thumbnailViewDeprecated.layoutParams
                    addView(this, indexOfSnapshotView)
                }
            thumbnailViewDeprecated.visibility = GONE
        } else {
            thumbnailView = null
        }
        val iconView = getOrInflateIconView(iconViewId)
        return TaskContainer(
                task,
                thumbnailView,
                thumbnailViewDeprecated,
                iconView,
                TransformingTouchDelegate(iconView.asView()),
                stagePosition,
                DigitalWellBeingToast(container, this),
                findViewById(showWindowViewId)!!,
                taskOverlayFactory
            )
            .apply {
                if (enableRefactorTaskThumbnail()) {
                    thumbnailViewDeprecated.setTaskOverlay(overlay)
                    bindThumbnailView()
                } else {
                    thumbnailViewDeprecated.bind(task, overlay)
                }
            }
    }

    protected fun getOrInflateIconView(@IdRes iconViewId: Int): TaskViewIcon {
        val iconView = findViewById<View>(iconViewId)!!
        return iconView as? TaskViewIcon
            ?: (iconView as ViewStub)
                .apply {
                    layoutResource =
                        if (enableOverviewIconMenu()) R.layout.icon_app_chip_view
                        else R.layout.icon_view
                }
                .inflate() as TaskViewIcon
    }

    protected fun isTaskContainersInitialized() = this::taskContainers.isInitialized

    fun containsMultipleTasks() = taskContainers.size > 1

    /**
     * Returns the TaskContainer corresponding to a given taskId, or null if the TaskView does not
     * contain a Task with that ID.
     */
    fun getTaskContainerById(taskId: Int) = taskContainers.firstOrNull { it.task.key.id == taskId }

    /** Check if given `taskId` is tracked in this view */
    fun containsTaskId(taskId: Int) = getTaskContainerById(taskId) != null

    open fun setOrientationState(orientationState: RecentsOrientedState) {
        this.orientedState = orientationState
        taskContainers.forEach { it.iconView.setIconOrientation(orientationState, isGridTask) }
        setThumbnailOrientation(orientationState)
    }

    protected open fun setThumbnailOrientation(orientationState: RecentsOrientedState) {
        taskContainers.forEach {
            it.overlay.updateOrientationState(orientationState)
            it.digitalWellBeingToast?.initialize(it.task)
        }
    }

    /**
     * Updates TaskView scaling and translation required to support variable width if enabled, while
     * ensuring TaskView fits into screen in fullscreen.
     */
    fun updateTaskSize(
        lastComputedTaskSize: Rect,
        lastComputedGridTaskSize: Rect,
        lastComputedCarouselTaskSize: Rect
    ) {
        val thumbnailPadding = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        val taskWidth = lastComputedTaskSize.width()
        val taskHeight = lastComputedTaskSize.height()
        val nonGridScale: Float
        val boxTranslationY: Float
        val expectedWidth: Int
        val expectedHeight: Int
        if (container.deviceProfile.isTablet) {
            val boxWidth: Int
            val boxHeight: Int
            if (isFocusedTask) {
                // Task will be focused and should use focused task size. Use focusTaskRatio
                // that is associated with the original orientation of the focused task.
                boxWidth = taskWidth
                boxHeight = taskHeight
            } else {
                // Otherwise task is in grid, and should use lastComputedGridTaskSize.
                boxWidth = lastComputedGridTaskSize.width()
                boxHeight = lastComputedGridTaskSize.height()
            }

            // Bound width/height to the box size.
            expectedWidth = boxWidth
            expectedHeight = boxHeight + thumbnailPadding

            // Scale to to fit task Rect.
            nonGridScale =
                if (enableGridOnlyOverview()) {
                    lastComputedCarouselTaskSize.width() / taskWidth.toFloat()
                } else {
                    taskWidth / boxWidth.toFloat()
                }

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - thumbnailPadding - taskHeight) / 2.0f
        } else {
            nonGridScale = 1f
            boxTranslationY = 0f
            expectedWidth = if (enableOverviewIconMenu()) taskWidth else LayoutParams.MATCH_PARENT
            expectedHeight =
                if (enableOverviewIconMenu()) taskHeight + thumbnailPadding
                else LayoutParams.MATCH_PARENT
        }
        this.nonGridScale = nonGridScale
        this.boxTranslationY = boxTranslationY
        updateLayoutParams<ViewGroup.LayoutParams> {
            width = expectedWidth
            height = expectedHeight
        }
        updateThumbnailSize()
    }

    protected open fun updateThumbnailSize() {
        // TODO(b/271468547), we should default to setting translations only on the snapshot instead
        //  of a hybrid of both margins and translations
        taskContainers[0].snapshotView.updateLayoutParams<LayoutParams> {
            topMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        }
    }

    /** Returns the thumbnail's bounds, optionally relative to the screen. */
    @JvmOverloads
    open fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean = false) {
        bounds.setEmpty()
        taskContainers.forEach {
            val thumbnailBounds = Rect()
            if (relativeToDragLayer) {
                container.dragLayer.getDescendantRectRelativeToSelf(it.snapshotView, bounds)
            } else {
                thumbnailBounds.set(it.snapshotView)
            }
            bounds.union(thumbnailBounds)
        }
    }

    /**
     * See [TaskDataChanges]
     *
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    fun onTaskListVisibilityChanged(visible: Boolean) {
        onTaskListVisibilityChanged(visible, FLAG_UPDATE_ALL)
    }

    /**
     * See [TaskDataChanges]
     *
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    open fun onTaskListVisibilityChanged(visible: Boolean, @TaskDataChanges changes: Int) {
        cancelPendingLoadTasks()
        val recentsModel = RecentsModel.INSTANCE.get(context)
        // These calls are no-ops if the data is already loaded, try and load the high
        // resolution thumbnail if the state permits
        if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL) && !enableRefactorTaskThumbnail()) {
            taskContainers.forEach {
                if (visible) {
                    recentsModel.thumbnailCache
                        .updateThumbnailInBackground(it.task) { thumbnailData ->
                            it.thumbnailViewDeprecated.setThumbnail(it.task, thumbnailData)
                        }
                        ?.also { request -> pendingThumbnailLoadRequests.add(request) }
                } else {
                    it.thumbnailViewDeprecated.setThumbnail(null, null)
                    // Reset the task thumbnail reference as well (it will be fetched from the
                    // cache or reloaded next time we need it)
                    it.task.thumbnail = null
                }
            }
        }
        if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
            taskContainers.forEach {
                if (visible) {
                    recentsModel.iconCache
                        .updateIconInBackground(it.task) { task ->
                            setIcon(it.iconView, task.icon)
                            if (enableOverviewIconMenu()) {
                                setText(it.iconView, task.title)
                            }
                            it.digitalWellBeingToast?.initialize(task)
                        }
                        ?.also { request -> pendingIconLoadRequests.add(request) }
                } else {
                    setIcon(it.iconView, null)
                    if (enableOverviewIconMenu()) {
                        setText(it.iconView, null)
                    }
                }
            }
        }
        if (needsUpdate(changes, FLAG_UPDATE_CORNER_RADIUS)) {
            currentFullscreenParams.updateCornerRadius(context)
        }
    }

    protected open fun needsUpdate(@TaskDataChanges dataChange: Int, @TaskDataChanges flag: Int) =
        (dataChange and flag) == flag

    protected open fun cancelPendingLoadTasks() {
        pendingThumbnailLoadRequests.forEach { it.cancel() }
        pendingThumbnailLoadRequests.clear()
        pendingIconLoadRequests.forEach { it.cancel() }
        pendingIconLoadRequests.clear()
    }

    protected fun setIcon(iconView: TaskViewIcon, icon: Drawable?) {
        with(iconView) {
            if (icon != null) {
                setDrawable(icon)
                setOnClickListener {
                    if (!confirmSecondSplitSelectApp()) {
                        showTaskMenu(this)
                    }
                }
                setOnLongClickListener {
                    requestDisallowInterceptTouchEvent(true)
                    showTaskMenu(this)
                }
            } else {
                setDrawable(null)
                setOnClickListener(null)
                setOnLongClickListener(null)
            }
        }
    }

    protected fun setText(iconView: TaskViewIcon, text: CharSequence?) {
        iconView.setText(text)
    }

    open fun refreshThumbnails(thumbnailDatas: HashMap<Int, ThumbnailData?>?) {
        if (enableRefactorTaskThumbnail()) {
            // TODO(b/342560598) add thumbnail logic
            return
        }

        taskContainers.forEach {
            val thumbnailData = thumbnailDatas?.get(it.task.key.id)
            if (thumbnailData != null) {
                it.thumbnailViewDeprecated.setThumbnail(it.task, thumbnailData)
            } else {
                it.thumbnailViewDeprecated.refresh()
            }
        }
    }

    private fun onClick() {
        if (confirmSecondSplitSelectApp()) {
            Log.d("b/310064698", "${taskIds.contentToString()} - onClick - split select is active")
            return
        }
        val callbackList =
            launchTasks()?.apply {
                add {
                    Log.d("b/310064698", "${taskIds.contentToString()} - onClick - launchCompleted")
                }
            }
        Log.d("b/310064698", "${taskIds.contentToString()} - onClick - callbackList: $callbackList")
        container.statsLogManager
            .logger()
            .withItemInfo(firstItemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP)
    }

    /**
     * Starts the task associated with this view and animates the startup.
     *
     * @return CompletionStage to indicate the animation completion or null if the launch failed.
     */
    open fun launchTaskAnimated(): RunnableList? {
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString()
        )
        val opts =
            container.getActivityLaunchOptions(this, null).apply {
                options.launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
            }
        if (
            ActivityManagerWrapper.getInstance()
                .startActivityFromRecents(taskContainers[0].task.key, opts.options)
        ) {
            Log.d(
                TAG,
                "launchTaskAnimated - startActivityFromRecents: ${taskIds.contentToString()}"
            )
            ActiveGestureLog.INSTANCE.trackEvent(
                ActiveGestureErrorDetector.GestureEvent.EXPECTING_TASK_APPEARED
            )
            val recentsView = recentsView ?: return null
            if (recentsView.runningTaskViewId != -1) {
                recentsView.onTaskLaunchedInLiveTileMode()

                // Return a fresh callback in the live tile case, so that it's not accidentally
                // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                return RunnableList().also { recentsView.addSideTaskLaunchCallback(it) }
            }
            if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
                // If the recents transition is running (ie. in live tile mode), then the start
                // of a new task will merge into the existing transition and it currently will
                // not be run independently, so we need to rely on the onTaskAppeared() call
                // for the new task to trigger the side launch callback to flush this runnable
                // list (which is usually flushed when the app launch animation finishes)
                recentsView.addSideTaskLaunchCallback(opts.onEndCallback)
            }
            return opts.onEndCallback
        } else {
            notifyTaskLaunchFailed()
            return null
        }
    }

    /** Starts the task associated with this view without any animation */
    fun launchTask(callback: (launched: Boolean) -> Unit) {
        launchTask(callback, isQuickSwitch = false)
    }

    /** Starts the task associated with this view without any animation */
    open fun launchTask(callback: (launched: Boolean) -> Unit, isQuickSwitch: Boolean) {
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString()
        )
        val firstContainer = taskContainers[0]
        val failureListener = TaskRemovedDuringLaunchListener(context.applicationContext)
        if (isQuickSwitch) {
            // We only listen for failures to launch in quickswitch because the during this
            // gesture launcher is in the background state, vs other launches which are in
            // the actual overview state
            failureListener.register(container, firstContainer.task.key.id) {
                notifyTaskLaunchFailed()
                recentsView?.let {
                    // Disable animations for now, as it is an edge case and the app usually
                    // covers launcher and also any state transition animation also gets
                    // clobbered by QuickstepTransitionManager.createWallpaperOpenAnimations
                    // when launcher shows again
                    it.startHome(false /* animated */)
                    // LauncherTaskbarUIController depends on the launcher state when
                    // checking whether to handle resume, but that can come in before
                    // startHome() changes the state, so force-refresh here to ensure the
                    // taskbar is updated
                    it.mSizeStrategy.taskbarController?.refreshResumedState()
                }
            }
        }
        // Indicate success once the system has indicated that the transition has started
        val opts =
            ActivityOptions.makeCustomTaskAnimation(
                    context,
                    0,
                    0,
                    Executors.MAIN_EXECUTOR.handler,
                    { callback(true) }
                ) {
                    failureListener.onTransitionFinished()
                }
                .apply {
                    launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
                    if (isQuickSwitch) {
                        setFreezeRecentTasksReordering()
                    }
                    // TODO(b/334826842) add splash functionality to new TTV
                    if (!enableRefactorTaskThumbnail()) {
                        disableStartingWindow =
                            firstContainer.thumbnailViewDeprecated.shouldShowSplashView()
                    }
                }
        Executors.UI_HELPER_EXECUTOR.execute {
            if (
                !ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(firstContainer.task.key, opts)
            ) {
                // If the call to start activity failed, then post the result immediately,
                // otherwise, wait for the animation start callback from the activity options
                // above
                Executors.MAIN_EXECUTOR.post {
                    notifyTaskLaunchFailed()
                    callback(false)
                }
            }
            Log.d(TAG, "launchTask - startActivityFromRecents: ${taskIds.contentToString()}")
        }
    }

    /** Launch of the current task (both live and inactive tasks) with an animation. */
    fun launchTasks(): RunnableList? {
        val recentsView = recentsView ?: return null
        val remoteTargetHandles = recentsView.mRemoteTargetHandles
        if (!isRunningTask || remoteTargetHandles == null) {
            return launchTaskAnimated()
        }
        if (!isClickableAsLiveTile) {
            Log.e(TAG, "TaskView is not clickable as a live tile; returning to home.")
            return null
        }
        isClickableAsLiveTile = false
        val targets =
            if (remoteTargetHandles.size == 1) {
                remoteTargetHandles[0].transformParams.targetSet
            } else {
                val apps =
                    remoteTargetHandles.flatMap { it.transformParams.targetSet.apps.asIterable() }
                val wallpapers =
                    remoteTargetHandles.flatMap {
                        it.transformParams.targetSet.wallpapers.asIterable()
                    }
                RemoteAnimationTargets(
                    apps.toTypedArray(),
                    wallpapers.toTypedArray(),
                    remoteTargetHandles[0].transformParams.targetSet.nonApps,
                    remoteTargetHandles[0].transformParams.targetSet.targetMode
                )
            }
        if (targets == null) {
            // If the recents animation is cancelled somehow between the parent if block and
            // here, try to launch the task as a non live tile task.
            val runnableList = launchTaskAnimated()
            if (runnableList == null) {
                Log.e(
                    TAG,
                    "Recents animation cancelled and cannot launch task as non-live tile" +
                        "; returning to home"
                )
            }
            isClickableAsLiveTile = true
            return runnableList
        }
        val runnableList = RunnableList()
        with(AnimatorSet()) {
            TaskViewUtils.composeRecentsLaunchAnimator(
                this,
                this@TaskView,
                targets.apps,
                targets.wallpapers,
                targets.nonApps,
                true /* launcherClosing */,
                recentsView.stateManager,
                recentsView,
                recentsView.depthController
            )
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (taskContainers.any { it.task.key.displayId != rootViewDisplayId }) {
                            launchTaskAnimated()
                        }
                        isClickableAsLiveTile = true
                        runEndCallback()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        runEndCallback()
                    }

                    private fun runEndCallback() {
                        runnableList.executeAllAndDestroy()
                    }
                }
            )
            start()
        }
        Log.d(TAG, "launchTasks - composeRecentsLaunchAnimator: ${taskIds.contentToString()}")
        recentsView.onTaskLaunchedInLiveTileMode()
        return runnableList
    }

    private fun notifyTaskLaunchFailed() {
        val sb = StringBuilder("Failed to launch task \n")
        taskContainers.forEach {
            sb.append("(task=${it.task.key.baseIntent} userId=${it.task.key.userId})\n")
        }
        Log.w(TAG, sb.toString())
        Toast.makeText(context, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
    }

    fun initiateSplitSelect(splitPositionOption: SplitPositionOption) {
        recentsView?.initiateSplitSelect(
            this,
            splitPositionOption.stagePosition,
            SplitConfigurationOptions.getLogEventForPosition(splitPositionOption.stagePosition)
        )
    }

    /**
     * Returns `true` if user is already in split select mode and this tap was to choose the second
     * app. `false` otherwise
     */
    protected open fun confirmSecondSplitSelectApp(): Boolean {
        val index = getLastSelectedChildTaskIndex()
        if (index >= taskContainers.size) {
            return false
        }
        val container = taskContainers[index]
        val recentsView = recentsView ?: return false
        return recentsView.confirmSplitSelect(
            this,
            container.task,
            container.iconView.drawable,
            container.thumbnailViewDeprecated,
            container.thumbnailViewDeprecated.thumbnail, /* intent */
            null, /* user */
            null,
            container.itemInfo
        )
    }

    /**
     * Returns the task index of the last selected child task (0 or 1). If we contain multiple tasks
     * and this TaskView is used as part of split selection, the selected child task index will be
     * that of the remaining task.
     */
    protected open fun getLastSelectedChildTaskIndex() = 0

    private fun showTaskMenu(iconView: TaskViewIcon): Boolean {
        val recentsView = recentsView ?: return false
        if (!recentsView.canLaunchFullscreenTask()) {
            // Don't show menu when selecting second split screen app
            return true
        }
        if (!container.deviceProfile.isTablet && !recentsView.isClearAllHidden) {
            recentsView.snapToPage(recentsView.indexOfChild(this))
            return false
        }
        val menuContainer = taskContainers.firstOrNull { it.iconView === iconView } ?: return false
        container.statsLogManager
            .logger()
            .withItemInfo(menuContainer.itemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS)
        return showTaskMenuWithContainer(menuContainer)
    }

    private fun showTaskMenuWithContainer(menuContainer: TaskContainer): Boolean {
        val recentsView = recentsView ?: return false
        return if (enableOverviewIconMenu() && menuContainer.iconView is IconAppChipView) {
            menuContainer.iconView.revealAnim(/* isRevealing= */ true)
            TaskMenuView.showForTask(menuContainer) {
                menuContainer.iconView.revealAnim(/* isRevealing= */ false)
            }
        } else if (container.deviceProfile.isTablet) {
            val alignedOptionIndex =
                if (
                    recentsView.isOnGridBottomRow(menuContainer.taskView) &&
                        container.deviceProfile.isLandscape
                ) {
                    if (enableGridOnlyOverview()) {
                        // With no focused task, there is less available space below the tasks, so
                        // align the arrow to the third option in the menu.
                        2
                    } else {
                        // Bottom row of landscape grid aligns arrow to second option to avoid
                        // clipping
                        1
                    }
                } else {
                    0
                }
            TaskMenuViewWithArrow.showForTask(menuContainer, alignedOptionIndex)
        } else {
            TaskMenuView.showForTask(menuContainer)
        }
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children that
     * might require special handling.
     */
    open fun offerTouchToChildren(event: MotionEvent): Boolean {
        taskContainers.forEach {
            if (event.action == MotionEvent.ACTION_DOWN) {
                computeAndSetIconTouchDelegate(it.iconView, tempCoordinates, it.iconTouchDelegate)
                if (it.iconTouchDelegate.onTouchEvent(event)) {
                    return true
                }
            }
        }
        return false
    }

    private fun computeAndSetIconTouchDelegate(
        view: TaskViewIcon,
        tempCenterCoordinates: FloatArray,
        transformingTouchDelegate: TransformingTouchDelegate
    ) {
        val viewHalfWidth = view.width / 2f
        val viewHalfHeight = view.height / 2f
        Utilities.getDescendantCoordRelativeToAncestor(
            view.asView(),
            container.dragLayer,
            tempCenterCoordinates.apply {
                this[0] = viewHalfWidth
                this[1] = viewHalfHeight
            },
            false
        )
        transformingTouchDelegate.setBounds(
            (tempCenterCoordinates[0] - viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] - viewHalfHeight).toInt(),
            (tempCenterCoordinates[0] + viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] + viewHalfHeight).toInt()
        )
    }

    /** Sets up an on-click listener and the visibility for show_windows icon on top of the task. */
    open fun setUpShowAllInstancesListener() {
        taskContainers.forEach {
            it.showWindowsView?.let { showWindowsView ->
                updateFilterCallback(
                    showWindowsView,
                    getFilterUpdateCallback(it.task.key.packageName)
                )
            }
        }
    }

    /**
     * Returns a callback that updates the state of the filter and the recents overview
     *
     * @param taskPackageName package name of the task to filter by
     */
    private fun getFilterUpdateCallback(taskPackageName: String?) =
        if (recentsView?.filterState?.shouldShowFilterUI(taskPackageName) == true)
            OnClickListener { recentsView?.setAndApplyFilter(taskPackageName) }
        else null

    /**
     * Sets the correct visibility and callback on the provided filterView based on whether the
     * callback is null or not
     */
    private fun updateFilterCallback(filterView: View, callback: OnClickListener?) {
        // Filtering changes alpha instead of the visibility since visibility
        // can be altered separately through RecentsView#resetFromSplitSelectionState()
        with(filterView) {
            alpha = if (callback == null) 0f else 1f
            setOnClickListener(callback)
        }
    }

    protected open fun setIconsAndBannersFullscreenProgress(progress: Float) {
        // Animate icons and DWB banners in/out, except in QuickSwitch state, when tiles are
        // oversized and banner would look disproportionately large.
        if (recentsView?.stateManager?.state == LauncherState.BACKGROUND_APP) {
            return
        }
        setIconsAndBannersTransitionProgress(progress, invert = true)
    }

    /**
     * Called to animate a smooth transition when going directly from an app into Overview (and vice
     * versa). Icons fade in, and DWB banners slide in with a "shift up" animation.
     */
    protected open fun setIconsAndBannersTransitionProgress(progress: Float, invert: Boolean) {
        focusTransitionProgress = if (invert) 1 - progress else progress
        getIconContentScale(invert).let { iconContentScale ->
            taskContainers.forEach {
                it.iconView.setContentAlpha(iconContentScale)
                it.digitalWellBeingToast?.updateBannerOffset(1f - iconContentScale)
            }
        }
    }

    private fun getIconContentScale(invert: Boolean): Float {
        val iconScalePercentage = SCALE_ICON_DURATION.toFloat() / DIM_ANIM_DURATION
        val lowerClamp = if (invert) 1f - iconScalePercentage else 0f
        val upperClamp = if (invert) 1f else iconScalePercentage
        return Interpolators.clampToProgress(Interpolators.FAST_OUT_SLOW_IN, lowerClamp, upperClamp)
            .getInterpolation(focusTransitionProgress)
    }

    fun animateIconScaleAndDimIntoView() {
        iconAndDimAnimator?.cancel()
        iconAndDimAnimator =
            ObjectAnimator.ofFloat(this, FOCUS_TRANSITION, 1f).apply {
                setCurrentFraction(iconScaleAnimStartProgress)
                setDuration(DIM_ANIM_DURATION).interpolator = Interpolators.LINEAR
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            iconAndDimAnimator = null
                        }
                    }
                )
                start()
            }
    }

    fun setIconScaleAndDim(iconScale: Float) {
        iconAndDimAnimator?.cancel()
        setIconsAndBannersTransitionProgress(iconScale, invert = false)
    }

    /** Set a color tint on the snapshot and supporting views. */
    open fun setColorTint(amount: Float, tintColor: Int) {
        taskContainers.forEach {
            if (!enableRefactorTaskThumbnail()) {
                // TODO(b/334832108) Add scrim to new TTV
                it.thumbnailViewDeprecated.dimAlpha = amount
            }
            it.iconView.setIconColorTint(tintColor, amount)
            it.digitalWellBeingToast?.setBannerColorTint(tintColor, amount)
        }
    }

    /**
     * Sets visibility for the thumbnail and associated elements (DWB banners and action chips).
     * IconView is unaffected.
     *
     * @param taskId is only used when setting visibility to a non-[View.VISIBLE] value
     */
    open fun setThumbnailVisibility(visibility: Int, taskId: Int) {
        taskContainers.forEach {
            if (visibility == VISIBLE || it.task.key.id == taskId) {
                it.snapshotView.visibility = visibility
                it.digitalWellBeingToast?.setBannerVisibility(visibility)
                it.showWindowsView?.visibility = visibility
                it.overlay.setVisibility(visibility)
            }
        }
    }

    open fun setOverlayEnabled(overlayEnabled: Boolean) {
        // TODO(b/335606129) Investigate the usage of [TaskOverlay] in the new TaskThumbnailView.
        //  and if it's still necessary we should support that in the new TTV class.
        if (!enableRefactorTaskThumbnail()) {
            taskContainers.forEach { it.thumbnailViewDeprecated.setOverlayEnabled(overlayEnabled) }
        }
    }

    protected open fun refreshTaskThumbnailSplash() {
        if (!enableRefactorTaskThumbnail()) {
            // TODO(b/334826842) add splash functionality to new TTV
            taskContainers.forEach { it.thumbnailViewDeprecated.refreshSplashView() }
        }
    }

    protected fun getScrollAdjustment(gridEnabled: Boolean) =
        if (gridEnabled) gridTranslationX else nonGridTranslationX

    protected fun getOffsetAdjustment(gridEnabled: Boolean) = getScrollAdjustment(gridEnabled)

    fun getSizeAdjustment(fullscreenEnabled: Boolean) = if (fullscreenEnabled) nonGridScale else 1f

    private fun applyScale() {
        val scale = persistentScale * dismissScale
        scaleX = scale
        scaleY = scale
        if (enableRefactorTaskThumbnail()) {
            taskViewData.scale.value = scale
        }
        updateSnapshotRadius()
    }

    protected open fun applyThumbnailSplashAlpha() {
        if (!enableRefactorTaskThumbnail()) {
            // TODO(b/334826842) add splash functionality to new TTV
            taskContainers.forEach {
                it.thumbnailViewDeprecated.setSplashAlpha(taskThumbnailSplashAlpha)
            }
        }
    }

    private fun applyTranslationX() {
        translationX =
            dismissTranslationX +
                taskOffsetTranslationX +
                taskResistanceTranslationX +
                splitSelectTranslationX +
                gridEndTranslationX +
                persistentTranslationX
    }

    private fun applyTranslationY() {
        translationY =
            dismissTranslationY +
                taskOffsetTranslationY +
                taskResistanceTranslationY +
                splitSelectTranslationY +
                persistentTranslationY
    }

    private fun onGridProgressChanged() {
        applyTranslationX()
        applyTranslationY()
        applyScale()
    }

    protected open fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        taskContainers.forEach {
            it.iconView.setVisibility(if (fullscreenProgress < 1) VISIBLE else INVISIBLE)
            it.overlay.setFullscreenProgress(fullscreenProgress)
        }
        setIconsAndBannersFullscreenProgress(fullscreenProgress)
        updateSnapshotRadius()
    }

    protected open fun updateSnapshotRadius() {
        updateCurrentFullscreenParams()
        taskContainers.forEach {
            it.thumbnailViewDeprecated.setFullscreenParams(getThumbnailFullscreenParams())
            it.overlay.setFullscreenParams(getThumbnailFullscreenParams())
        }
    }

    protected open fun updateCurrentFullscreenParams() {
        updateFullscreenParams(currentFullscreenParams)
    }

    protected fun updateFullscreenParams(fullscreenParams: FullscreenDrawParams) {
        recentsView?.let { fullscreenParams.setProgress(fullscreenProgress, it.scaleX, scaleX) }
    }

    protected open fun getThumbnailFullscreenParams(): FullscreenDrawParams =
        currentFullscreenParams

    private fun onModalnessUpdated(modalness: Float) {
        taskContainers.forEach {
            it.iconView.setModalAlpha(1 - modalness)
            it.digitalWellBeingToast?.updateBannerOffset(modalness)
        }
    }

    /** Updates [TaskThumbnailView] to reflect the latest [Task] state (i.e., task isRunning). */
    fun notifyIsRunningTaskUpdated() {
        // TODO(b/335649589): TaskView's VM will already have access to TaskThumbnailView's VM
        //  so there will be no need to access TaskThumbnailView's VM through the TaskThumbnailView
        taskContainers.forEach { it.bindThumbnailView() }
    }

    fun resetPersistentViewTransforms() {
        nonGridTranslationX = 0f
        gridTranslationX = 0f
        gridTranslationY = 0f
        boxTranslationY = 0f
        nonGridPivotTranslationX = 0f
        resetViewTransforms()
    }

    open fun resetViewTransforms() {
        // fullscreenTranslation and accumulatedTranslation should not be reset, as
        // resetViewTransforms is called during QuickSwitch scrolling.
        dismissTranslationX = 0f
        taskOffsetTranslationX = 0f
        taskResistanceTranslationX = 0f
        splitSelectTranslationX = 0f
        gridEndTranslationX = 0f
        dismissTranslationY = 0f
        taskOffsetTranslationY = 0f
        taskResistanceTranslationY = 0f
        if (recentsView?.isSplitSelectionActive != true) {
            splitSelectTranslationY = 0f
        }
        dismissScale = 1f
        translationZ = 0f
        alpha = stableAlpha
        setIconScaleAndDim(1f)
        setColorTint(0f, 0)
        if (!enableRefactorTaskThumbnail()) {
            // TODO(b/335399428) add split select functionality to new TTV
            taskContainers.forEach { it.thumbnailViewDeprecated.resetViewTransforms() }
        }
    }

    private fun getGridTrans(endTranslation: Float) =
        Utilities.mapRange(gridProgress, 0f, endTranslation)

    private fun getNonGridTrans(endTranslation: Float) =
        endTranslation - getGridTrans(endTranslation)

    /** We update and subsequently draw these in [fullscreenProgress]. */
    open class FullscreenDrawParams(context: Context) : SafeCloseable {
        var cornerRadius = 0f
        private var windowCornerRadius = 0f
        var currentDrawnCornerRadius = 0f

        init {
            updateCornerRadius(context)
        }

        /** Recomputes the start and end corner radius for the given Context. */
        fun updateCornerRadius(context: Context) {
            cornerRadius = computeTaskCornerRadius(context)
            windowCornerRadius = computeWindowCornerRadius(context)
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
        open fun computeTaskCornerRadius(context: Context): Float {
            return TaskCornerRadius.get(context)
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
        open fun computeWindowCornerRadius(context: Context): Float {
            return QuickStepContract.getWindowCornerRadius(context)
        }

        /** Sets the progress in range [0, 1] */
        fun setProgress(fullscreenProgress: Float, parentScale: Float, taskViewScale: Float) {
            currentDrawnCornerRadius =
                Utilities.mapRange(fullscreenProgress, cornerRadius, windowCornerRadius) /
                    parentScale /
                    taskViewScale
        }

        override fun close() {}
    }

    /** Holder for all Task dependent information. */
    inner class TaskContainer(
        val task: Task,
        val thumbnailView: TaskThumbnailView?,
        val thumbnailViewDeprecated: TaskThumbnailViewDeprecated,
        val iconView: TaskViewIcon,
        /**
         * This technically can be a vanilla [android.view.TouchDelegate] class, however that class
         * requires setting the touch bounds at construction, so we'd repeatedly be created many
         * instances unnecessarily as scrolling occurs, whereas [TransformingTouchDelegate] allows
         * touch delegated bounds only to be updated.
         */
        val iconTouchDelegate: TransformingTouchDelegate,
        /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
        @StagePosition val stagePosition: Int,
        val digitalWellBeingToast: DigitalWellBeingToast?,
        val showWindowsView: View?,
        taskOverlayFactory: TaskOverlayFactory
    ) {
        val overlay: TaskOverlay<*> = taskOverlayFactory.createOverlay(this)

        val snapshotView: View
            get() = thumbnailView ?: thumbnailViewDeprecated

        /** Builds proto for logging */
        val itemInfo: WorkspaceItemInfo
            get() =
                WorkspaceItemInfo().apply {
                    itemType = LauncherSettings.Favorites.ITEM_TYPE_TASK
                    container = LauncherSettings.Favorites.CONTAINER_TASKSWITCHER
                    val componentKey = TaskUtils.getLaunchComponentKeyForTask(task.key)
                    user = componentKey.user
                    intent = Intent().setComponent(componentKey.componentName)
                    title = task.title
                    recentsView?.let { screenId = it.indexOfChild(this@TaskView) }
                    if (privateSpaceRestrictAccessibilityDrag()) {
                        if (
                            UserCache.getInstance(context).getUserInfo(componentKey.user).isPrivate
                        ) {
                            runtimeStatusFlags =
                                runtimeStatusFlags or ItemInfoWithIcon.FLAG_NOT_PINNABLE
                        }
                    }
                }

        val taskView: TaskView
            get() = this@TaskView

        fun destroy() {
            digitalWellBeingToast?.destroy()
            thumbnailView?.let { taskView.removeView(it) }
        }

        // TODO(b/335649589): TaskView's VM will already have access to TaskThumbnailView's VM
        //  so there will be no need to access TaskThumbnailView's VM through the TaskThumbnailView
        fun bindThumbnailView() {
            // TODO(b/343364498): Existing view has shouldShowScreenshot as an override as well but
            //  this should be decided inside TaskThumbnailViewModel.
            thumbnailView?.viewModel?.bind(TaskThumbnail(task.key.id, isRunningTask))
        }
    }

    companion object {
        private const val TAG = "TaskView"
        const val FLAG_UPDATE_ICON = 1
        const val FLAG_UPDATE_THUMBNAIL = FLAG_UPDATE_ICON shl 1
        const val FLAG_UPDATE_CORNER_RADIUS = FLAG_UPDATE_THUMBNAIL shl 1
        const val FLAG_UPDATE_ALL =
            (FLAG_UPDATE_ICON or FLAG_UPDATE_THUMBNAIL or FLAG_UPDATE_CORNER_RADIUS)

        /** The maximum amount that a task view can be scrimmed, dimmed or tinted. */
        const val MAX_PAGE_SCRIM_ALPHA = 0.4f
        const val SCALE_ICON_DURATION: Long = 120
        private const val DIM_ANIM_DURATION: Long = 700
        private val SYSTEM_GESTURE_EXCLUSION_RECT = listOf(Rect())

        @JvmField
        val FOCUS_TRANSITION: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("focusTransition") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.setIconsAndBannersTransitionProgress(v, false /* invert */)
                }

                override fun get(taskView: TaskView) = taskView.focusTransitionProgress
            }
        private val SPLIT_SELECT_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("splitSelectTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.splitSelectTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.splitSelectTranslationX
            }
        private val SPLIT_SELECT_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("splitSelectTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.splitSelectTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.splitSelectTranslationY
            }
        private val DISMISS_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.dismissTranslationX
            }
        private val DISMISS_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.dismissTranslationY
            }
        private val TASK_OFFSET_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskOffsetTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskOffsetTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.taskOffsetTranslationX
            }
        private val TASK_OFFSET_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskOffsetTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskOffsetTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.taskOffsetTranslationY
            }
        private val TASK_RESISTANCE_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskResistanceTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskResistanceTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.taskResistanceTranslationX
            }
        private val TASK_RESISTANCE_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskResistanceTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskResistanceTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.taskResistanceTranslationY
            }
        @JvmField
        val GRID_END_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("gridEndTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.gridEndTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.gridEndTranslationX
            }
        @JvmField
        val DISMISS_SCALE: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissScale") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissScale = v
                }

                override fun get(taskView: TaskView) = taskView.dismissScale
            }
    }
}
