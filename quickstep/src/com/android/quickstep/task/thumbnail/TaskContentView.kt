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

package com.android.quickstep.task.thumbnail

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.AttributeSet
import android.util.FloatProperty
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.launcher3.R
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.MultiPropertyDelegate
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.ViewPool
import com.android.quickstep.DesktopFullscreenDrawParams.Companion.computeCornerRadius
import com.android.quickstep.compose.QuickstepComposeFacade
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.apptimer.TaskAppTimerUiState.Uninitialized
import com.android.quickstep.task.apptimer.TaskAppTimerViewModel
import com.android.quickstep.task.apptimer.TimerTextHelper
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_BORDER_COLOR
import com.android.quickstep.util.BorderAnimator.Companion.DEFAULT_INTERPOLATOR
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.setActivityStarterClickListener
import com.android.quickstep.views.TaskHeaderView
import kotlin.math.max

/**
 * TaskContentView is a wrapper around the TaskHeaderView, TaskThumbnailView and Digital wellbeing
 * app timer toast. It is a sibling to AiAi (TaskOverlay).
 *
 * When enableRefactorDigitalWellbeingToast is off, it is sibling to digital wellbeing toast unlike
 * when the flag is on.
 */
class TaskContentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs), ViewPool.Reusable {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var taskHeaderView: TaskHeaderView? = null
        private set

    private var taskThumbnailView: TaskThumbnailView? = null
    private val useComposeTaskAppTimer
        get() = QuickstepComposeFacade.isComposeAvailable() && enableRefactorDigitalWellbeingToast()

    @Deprecated("This toast is getting replaced by the compose version taskAppTimerToastCompose")
    private var taskAppTimerToast: TextView? = null

    private var taskAppTimerToastCompose: View? = null
    private val taskAppTimerViewModel by lazy { TaskAppTimerViewModel() }

    private var timerTextHelper: TimerTextHelper? = null
    private var timerUiState: TaskAppTimerUiState = Uninitialized
    private var timerUsageAccessibilityAction: AccessibilityAction? = null
    private val timerToastHeight =
        context.resources.getDimensionPixelSize(R.dimen.digital_wellbeing_toast_height)

    private var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null
    private val outlinePath = Path()

    private val borderWidthPx: Int by lazy {
        context.resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_border_width)
    }

    private var activeFocusAnimator: AnimatorSet? = null
    private var activeHoverAnimator: AnimatorSet? = null

    private val focusBorderColor: Int
    private val hoverBorderColor: Int

    private val focusBorderAnimator: BorderAnimator by lazy {
        createSimpleBorderAnimator(
            borderRadiusPx = computeCornerRadius(context).toInt(),
            borderWidthPx = borderWidthPx,
            boundsBuilder = { it.set(0, 0, width, height) },
            targetView = this,
            borderColor = focusBorderColor,
        )
    }

    private val hoverBorderAnimator: BorderAnimator by lazy {
        createSimpleBorderAnimator(
            borderRadiusPx = computeCornerRadius(context).toInt(),
            borderWidthPx = borderWidthPx,
            boundsBuilder = { it.set(0, 0, width, height) },
            targetView = this,
            borderColor = hoverBorderColor,
        )
    }

    private var hoverBorderVisible = false
        set(value) {
            if (field == value) {
                return
            }

            field = value

            activeHoverAnimator?.cancel()
            activeHoverAnimator = animateBorder(OUTLINE_EXPANSION_HOVER, hoverBorderAnimator, value)
        }

    /**
     * Sets the outline bounds of the view. Default to use view's bound as outline when set to null.
     */
    var outlineBounds: Rect? = null
        set(value) {
            field = value
            invalidateOutline()
        }

    private val bounds = Rect()

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
        }

    private var outlineExpansion = 0.0f
        set(value) {
            field = value
            invalidateOutline()
        }

    private val outlineExpansionFactory: MultiPropertyFactory<TaskContentView> =
        MultiPropertyFactory(this, OUTLINE_EXPANSION, OutlineExpansion.entries.size) {
            a: Float,
            b: Float ->
            max(a, b)
        }
    private var outlineExpansionFocus by
        MultiPropertyDelegate(outlineExpansionFactory, OutlineExpansion.FOCUS)
    private var outlineExpansionHover by
        MultiPropertyDelegate(outlineExpansionFactory, OutlineExpansion.HOVER)

    var isHoverable: Boolean = false

    init {
        setWillNotDraw(false)
        context.obtainStyledAttributes(attrs, R.styleable.TaskContentView).use {
            focusBorderColor =
                it.getColor(R.styleable.TaskContentView_focusBorderColor, DEFAULT_BORDER_COLOR)
            hoverBorderColor =
                it.getColor(R.styleable.TaskContentView_hoverBorderColor, DEFAULT_BORDER_COLOR)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        createTaskThumbnailView()
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        taskThumbnailView?.parentScaleXUpdated(scaleX)
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        taskThumbnailView?.parentScaleYUpdated(scaleY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val outlineRect = outlineBounds ?: bounds
                    val expansion = outlineExpansion.toInt()
                    outlinePath.apply {
                        rewind()
                        addRoundRect(
                            outlineRect.left.toFloat() - expansion,
                            outlineRect.top.toFloat() - expansion,
                            outlineRect.right.toFloat() + expansion,
                            outlineRect.bottom.toFloat() + expansion,
                            (cornerRadius + expansion) / scaleX,
                            (cornerRadius + expansion) / scaleY,
                            Path.Direction.CW,
                        )
                    }
                    outline.setPath(outlinePath)
                }
            }
    }

    override fun onRecycle() {
        taskHeaderView?.isInvisible = true
        taskHeaderView?.alpha = 1.0f
        onSizeChanged = null
        outlineBounds = null
        alpha = 1.0f
        taskThumbnailView?.onRecycle()
        taskAppTimerToast?.isInvisible = true
        timerUiState = Uninitialized
        taskAppTimerViewModel.setState(Uninitialized)
        timerTextHelper = null
        timerUsageAccessibilityAction = null
        outlineExpansionHover = 0f
        outlineExpansionFocus = 0f
        hoverBorderVisible = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun doOnSizeChange(action: (width: Int, height: Int) -> Unit) {
        onSizeChanged = action
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(width, height)
        bounds.set(0, 0, w, h)
        updateTimerText(w)
        invalidateOutline()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (isFocusable()) {
            focusBorderAnimator.drawBorder(canvas)
        }
        if (isHoverable) {
            hoverBorderAnimator.drawBorder(canvas)
        }
    }

    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

        activeFocusAnimator?.cancel()
        activeFocusAnimator = animateBorder(OUTLINE_EXPANSION_FOCUS, focusBorderAnimator, gainFocus)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isHoverable) return false
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                hoverBorderVisible = true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverBorderVisible = false
            }
        }
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        with(info) { taskHeaderView?.getSupportedAccessibilityActions()?.forEach(::addAction) }
    }

    fun onParentAnimationProgress(progress: Float) {
        taskAppTimerToast?.apply { translationY = timerToastHeight * (1f - progress) }
    }

    /** Returns accessibility actions supported by items in the task content view. */
    fun getSupportedAccessibilityActions(): List<AccessibilityAction> {
        return listOfNotNull(timerUsageAccessibilityAction)
    }

    fun handleAccessibilityAction(action: Int): Boolean {
        timerUsageAccessibilityAction?.let {
            if (action == it.id) {
                return taskAppTimerToast?.callOnClick() ?: false
            }
        }
        return false
    }

    fun getTaskAppTimerToastHeight() =
        (if (useComposeTaskAppTimer) taskAppTimerToastCompose else taskAppTimerToast)?.height ?: 0

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        taskHeaderView?.let {
            if (it.handleAccessibilityAction(action)) {
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun createHeaderView(taskHeaderState: TaskHeaderUiState) {
        if (taskHeaderView == null && taskHeaderState is TaskHeaderUiState.ShowHeader) {
            taskHeaderView =
                findViewById<ViewStub>(R.id.task_header_view)
                    .apply { layoutResource = R.layout.task_header_view }
                    .inflate() as TaskHeaderView
        }
    }

    private fun createTaskThumbnailView() {
        if (taskThumbnailView == null) {
            taskThumbnailView =
                findViewById<ViewStub>(R.id.snapshot)
                    .apply { layoutResource = R.layout.task_thumbnail }
                    .inflate() as TaskThumbnailView
        }
    }

    private fun createAppTimerToastView(taskAppTimerUiState: TaskAppTimerUiState) {
        if (taskAppTimerUiState is TaskAppTimerUiState.Timer) {
            when {
                useComposeTaskAppTimer && taskAppTimerToastCompose == null -> {
                    taskAppTimerToastCompose =
                        QuickstepComposeFacade.initComposeView(context).let {
                            QuickstepComposeFacade.startTaskAppTimerToast(
                                view = it,
                                taskAppTimerViewModel,
                            )
                        }
                    addAppTimerToastToLayout()
                }

                !useComposeTaskAppTimer && taskAppTimerToast == null -> {
                    taskAppTimerToast =
                        findViewById<ViewStub>(R.id.task_app_timer_toast)
                            .apply { layoutResource = R.layout.task_app_timer_toast }
                            .inflate() as TextView
                }
            }
        }
    }

    private fun addAppTimerToastToLayout() {
        val params =
            LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
                bottomToBottom = R.id.snapshot
            }
        addView(taskAppTimerToastCompose, params)
    }

    fun setState(
        taskHeaderState: TaskHeaderUiState,
        taskThumbnailUiState: TaskThumbnailUiState,
        taskAppTimerUiState: TaskAppTimerUiState,
        taskId: Int?,
    ) {
        createHeaderView(taskHeaderState)
        taskHeaderView?.setState(taskHeaderState)
        taskThumbnailView?.setState(taskThumbnailUiState, taskId)
        createAppTimerToastView(taskAppTimerUiState)

        if (enableRefactorDigitalWellbeingToast() && timerUiState != taskAppTimerUiState) {
            setAppTimerToastState(taskAppTimerUiState)
            updateContentDescriptionWithTimer(taskAppTimerUiState)
        }
    }

    fun setTaskHeaderAlpha(alpha: Float) {
        taskHeaderView?.alpha = alpha
    }

    private fun updateContentDescriptionWithTimer(state: TaskAppTimerUiState) {
        taskThumbnailView?.contentDescription =
            when (state) {
                is Uninitialized -> return
                is TaskAppTimerUiState.NoTimer -> state.taskDescription
                is TaskAppTimerUiState.Timer ->
                    context.getString(
                        R.string.task_contents_description_with_remaining_time,
                        state.taskDescription,
                        context.getString(
                            R.string.time_left_for_app,
                            taskAppTimerViewModel.getFormattedDuration(state.timeRemaining, context),
                        ),
                    )
            }
    }

    private fun setAppTimerToastState(state: TaskAppTimerUiState) {
        if (useComposeTaskAppTimer) {
            taskAppTimerViewModel.setState(state)
            return
        }

        timerUiState = state

        taskAppTimerToast?.apply {
            when (state) {
                is Uninitialized -> isInvisible = true
                is TaskAppTimerUiState.NoTimer -> isInvisible = true
                is TaskAppTimerUiState.Timer -> {
                    timerTextHelper = TimerTextHelper(context, state.timeRemaining)
                    isInvisible = false
                    updateTimerText(width)

                    // TODO: add WW logging on the app usage settings click.
                    setActivityStarterClickListener(
                        appUsageSettingsIntent(state.taskPackageName),
                        "app usage settings for task ${state.taskDescription}",
                    )

                    timerUsageAccessibilityAction =
                        createAppUsageSettingsAccessibilityAction(
                            context,
                            state.accessibilityActionId,
                            state.taskDescription,
                        )
                }
            }
        }
    }

    private fun updateTimerText(width: Int) {
        taskAppTimerToast?.apply {
            val helper = timerTextHelper

            if (isVisible && helper != null) {
                text = helper.getTextThatFits(width, paint)
            }
        }
    }

    private fun animateBorder(
        outlineProperty: FloatProperty<TaskContentView>,
        borderAnimator: BorderAnimator,
        show: Boolean,
    ): AnimatorSet? {
        val targetOutlineExpansion = if (show) borderWidthPx.toFloat() else 0f
        val outlineExpansionAnimator =
            ObjectAnimator.ofFloat(this, outlineProperty, targetOutlineExpansion)
        val borderEffectAnimator = borderAnimator.buildAnimator(show)
        return AnimatorSet().apply {
            playTogether(outlineExpansionAnimator, borderEffectAnimator)
            duration = borderEffectAnimator.duration
            interpolator = DEFAULT_INTERPOLATOR
            start()
        }
    }

    companion object {
        const val TAG = "TaskContentView"

        private fun appUsageSettingsIntent(packageName: String) =
            Intent(Intent(Settings.ACTION_APP_USAGE_SETTINGS))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        private fun createAppUsageSettingsAccessibilityAction(
            context: Context,
            @IdRes actionId: Int,
            taskDescription: String?,
        ) =
            AccessibilityAction(
                actionId,
                context.getString(R.string.split_app_usage_settings, taskDescription),
            )

        private enum class OutlineExpansion {
            FOCUS,
            HOVER,
        }

        private val OUTLINE_EXPANSION: FloatProperty<TaskContentView> =
            KFloatProperty(TaskContentView::outlineExpansion)
        private val OUTLINE_EXPANSION_FOCUS: FloatProperty<TaskContentView> =
            KFloatProperty(TaskContentView::outlineExpansionFocus)
        private val OUTLINE_EXPANSION_HOVER: FloatProperty<TaskContentView> =
            KFloatProperty(TaskContentView::outlineExpansionHover)
    }
}
