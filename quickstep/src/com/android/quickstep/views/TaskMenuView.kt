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
package com.android.quickstep.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.R
import com.android.launcher3.anim.AnimationSuccessListener
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskUtils
import com.android.quickstep.util.TaskCornerRadius
import java.util.function.Consumer
import kotlin.math.max

/** Contains options for a recent task when long-pressing its icon. */
class TaskMenuView
@JvmOverloads
constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int = 0) :
    AbstractFloatingView(context, attrs, defStyleAttr) {
    private val recentsViewContainer: RecentsViewContainer =
        RecentsViewContainer.containerFromContext(context)
    private val tempRect = Rect()
    private val taskName: TextView by lazy { findViewById(R.id.task_name) }
    private val optionLayout: LinearLayout by lazy { findViewById(R.id.menu_option_layout) }
    private var openCloseAnimator: AnimatorSet? = null
    private var revealAnimator: ValueAnimator? = null
    private var onClosingStartCallback: Runnable? = null
    private lateinit var taskView: TaskView
    private lateinit var taskContainer: TaskContainer
    private var menuTranslationXBeforeOpen = 0f
    private var menuTranslationYBeforeOpen = 0f

    // Spaced claimed below Overview (taskbar and insets)
    private val taskbarTop by lazy {
        recentsViewContainer.deviceProfile.heightPx -
            recentsViewContainer.deviceProfile.overviewActionsClaimedSpaceBelow
    }
    private val minMenuTop by lazy { taskContainer.iconView.height.toFloat() }
    // TODO(b/401476868): Replace overviewRowSpacing with correct margin to the taskbarTop.
    private val maxMenuBottom by lazy {
        (taskbarTop - recentsViewContainer.deviceProfile.overviewRowSpacing).toFloat()
    }

    init {
        clipToOutline = true
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (!recentsViewContainer.dragLayer.isEventOverView(this, ev)) {
                // TODO: log this once we have a new container type for it?
                animateOpenOrClosed(true)
                return true
            }
        }
        return false
    }

    override fun handleClose(animate: Boolean) {
        animateOpenOrClosed(true, animated = false)
    }

    override fun isOfType(type: Int): Boolean = (type and TYPE_TASK_MENU) != 0

    override fun getOutlineProvider(): ViewOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    TaskCornerRadius.get(view.context),
                )
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var heightMeasure = heightMeasureSpec
        val maxMenuHeight = calculateMaxHeight()
        if (MeasureSpec.getSize(heightMeasure) > maxMenuHeight) {
            heightMeasure = MeasureSpec.makeMeasureSpec(maxMenuHeight, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, heightMeasure)
    }

    fun onRotationChanged() {
        openCloseAnimator?.let { if (it.isRunning) it.end() }
        if (mIsOpen) {
            optionLayout.removeAllViews()
            if (enableOverviewIconMenu() || !populateAndLayoutMenu()) {
                close(false)
            }
        }
    }

    private fun populateAndShowForTask(taskContainer: TaskContainer): Boolean {
        if (isAttachedToWindow) return false
        recentsViewContainer.dragLayer.addView(this)
        taskView = taskContainer.taskView
        this.taskContainer = taskContainer
        if (!populateAndLayoutMenu()) return false
        post { this.animateOpen() }
        return true
    }

    /** @return true if successfully able to populate task view menu, false otherwise */
    private fun populateAndLayoutMenu(): Boolean {
        addMenuOptions(taskContainer)
        orientAroundTaskView(taskContainer)
        return true
    }

    private fun addMenuOptions(taskContainer: TaskContainer) {
        if (enableOverviewIconMenu()) {
            removeView(taskName)
        } else {
            taskName.text = TaskUtils.getTitle(context, taskContainer.task)
            taskName.setOnClickListener { close(true) }
        }
        TaskOverlayFactory.getEnabledShortcuts(taskView, taskContainer)
            .forEach(Consumer { menuOption: SystemShortcut<*> -> this.addMenuOption(menuOption) })
    }

    private fun addMenuOption(menuOption: SystemShortcut<*>) {
        val menuOptionView =
            recentsViewContainer.layoutInflater.inflate(R.layout.task_view_menu_option, this, false)
                as LinearLayout
        if (enableOverviewIconMenu()) {
            menuOptionView.background =
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.app_chip_menu_item_bg,
                    context.theme,
                )
            menuOptionView.foreground =
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.app_chip_menu_item_fg,
                    context.theme,
                )
        }
        menuOption.setIconAndLabelFor(
            menuOptionView.findViewById(R.id.icon),
            menuOptionView.findViewById(R.id.text),
        )
        val lp = menuOptionView.layoutParams as LayoutParams
        taskView.pagedOrientationHandler.setLayoutParamsForTaskMenuOptionItem(
            lp,
            menuOptionView,
            recentsViewContainer.deviceProfile,
        )
        // Set an onClick listener on each menu option. The onClick method is responsible for
        // ending LiveTile mode on the thumbnail if needed.
        menuOptionView.setOnClickListener { v: View? -> menuOption.onClick(v) }
        optionLayout.addView(menuOptionView)
    }

    private fun orientAroundTaskView(taskContainer: TaskContainer) {
        val recentsView = recentsViewContainer.getOverviewPanel<RecentsView<*, *>>()
        val orientationHandler = recentsView.pagedOrientationHandler
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        // Get Position
        val deviceProfile = recentsViewContainer.deviceProfile
        recentsViewContainer.dragLayer.getDescendantRectRelativeToSelf(
            if (enableOverviewIconMenu()) iconView.findViewById(R.id.icon_view_menu_anchor)
            else taskContainer.snapshotView,
            tempRect,
        )
        val insets = recentsViewContainer.dragLayer.getInsets()
        val params = layoutParams as BaseDragLayer.LayoutParams
        params.width =
            orientationHandler.getTaskMenuWidth(
                taskContainer.snapshotView,
                deviceProfile,
                taskContainer.stagePosition,
            )
        // Gravity set to Left instead of Start as sTempRect.left measures Left distance not Start
        params.gravity = Gravity.LEFT
        layoutParams = params
        scaleX = taskView.scaleX
        scaleY = taskView.scaleY

        // Set divider spacing
        val divider = ShapeDrawable(RectShape())
        divider.paint.color = resources.getColor(android.R.color.transparent)
        val dividerSpacing = resources.getDimension(R.dimen.task_menu_spacing).toInt()
        optionLayout.showDividers =
            if (enableOverviewIconMenu()) SHOW_DIVIDER_NONE else SHOW_DIVIDER_MIDDLE

        optionLayout.background =
            if (enableOverviewIconMenu()) {
                ResourcesCompat.getDrawable(resources, R.drawable.app_chip_menu_bg, context.theme)
            } else {
                null
            }

        orientationHandler.setTaskOptionsMenuLayoutOrientation(
            deviceProfile,
            optionLayout,
            dividerSpacing,
            divider,
        )
        val thumbnailAlignedX = (tempRect.left - insets.left).toFloat()
        val thumbnailAlignedY = (tempRect.top - insets.top).toFloat()

        // Changing pivot to make computations easier
        // NOTE: Changing the pivots means the rotated view gets rotated about the new pivots set,
        // which would render the X and Y position set here incorrect
        pivotX = 0f
        pivotY = 0f
        rotation = orientationHandler.degreesRotated

        if (enableOverviewIconMenu()) {
            elevation = resources.getDimension(R.dimen.task_thumbnail_icon_menu_elevation)
            translationX = thumbnailAlignedX
            translationY = thumbnailAlignedY
        } else {
            // Margin that insets the menuView inside the taskView
            val taskInsetMargin = resources.getDimension(R.dimen.task_card_margin)
            translationX =
                orientationHandler.getTaskMenuX(
                    thumbnailAlignedX,
                    this.taskContainer.snapshotView,
                    deviceProfile,
                    taskInsetMargin,
                    iconView,
                )
            translationY =
                orientationHandler.getTaskMenuY(
                    thumbnailAlignedY,
                    this.taskContainer.snapshotView,
                    this.taskContainer.stagePosition,
                    this,
                    taskInsetMargin,
                    iconView,
                )
        }
    }

    private fun animateOpen() {
        menuTranslationYBeforeOpen = translationY
        menuTranslationXBeforeOpen = translationX
        animateOpenOrClosed(false)
        mIsOpen = true
    }

    private val iconView: View
        get() = taskContainer.iconView.asView()

    private fun animateOpenOrClosed(closing: Boolean, animated: Boolean = true) {
        openCloseAnimator?.let { if (it.isRunning) it.cancel() }
        openCloseAnimator = AnimatorSet()
        // If we're opening, we just start from the beginning as a new `TaskMenuView` is created
        // each time we do the open animation so there will never be a partial value here.
        var revealAnimationStartProgress = 0f
        if (closing && revealAnimator != null) {
            revealAnimationStartProgress = 1f - revealAnimator!!.animatedFraction
        }
        revealAnimator =
            createOpenCloseOutlineProvider()
                .createRevealAnimator(this, closing, revealAnimationStartProgress)
        revealAnimator!!.interpolator =
            if (enableOverviewIconMenu()) Interpolators.EMPHASIZED else Interpolators.DECELERATE
        val openCloseAnimatorBuilder = openCloseAnimator!!.play(revealAnimator)
        if (enableOverviewIconMenu()) {
            animateOpenOrCloseAppChip(closing, openCloseAnimatorBuilder)
        }
        openCloseAnimatorBuilder.with(
            ObjectAnimator.ofFloat(this, ALPHA, (if (closing) 0 else 1).toFloat())
        )
        if (enableRefactorTaskThumbnail()) {
            revealAnimator?.addUpdateListener { animation: ValueAnimator ->
                val animatedFraction = animation.animatedFraction
                val openProgress = if (closing) (1 - animatedFraction) else animatedFraction
                taskContainer.updateMenuOpenProgress(openProgress)
            }
        } else {
            openCloseAnimatorBuilder.with(
                ObjectAnimator.ofFloat(
                    taskContainer.thumbnailViewDeprecated,
                    TaskThumbnailViewDeprecated.DIM_ALPHA,
                    if (closing) 0f else TaskView.MAX_PAGE_SCRIM_ALPHA,
                )
            )
        }
        openCloseAnimator!!.addListener(
            object : AnimationSuccessListener() {
                override fun onAnimationStart(animation: Animator) {
                    visibility = VISIBLE
                    if (closing) onClosingStartCallback?.run()
                }

                override fun onAnimationSuccess(animator: Animator) {
                    if (closing) closeComplete()
                }
            }
        )
        val animationDuration =
            when {
                animated && closing -> REVEAL_CLOSE_DURATION
                animated && !closing -> REVEAL_OPEN_DURATION
                else -> 0L
            }
        openCloseAnimator!!.setDuration(animationDuration)
        openCloseAnimator!!.start()
    }

    private fun TaskView.isOnGridBottomRow(): Boolean =
        (recentsViewContainer.getOverviewPanel<View>() as RecentsView<*, *>).isOnGridBottomRow(this)

    private fun closeComplete() {
        mIsOpen = false
        recentsViewContainer.dragLayer.removeView(this)
        revealAnimator = null
    }

    private fun createOpenCloseOutlineProvider(): RoundedRectRevealOutlineProvider {
        val radius = TaskCornerRadius.get(mContext)
        val fromRect =
            Rect(
                if (enableOverviewIconMenu() && isLayoutRtl) width else 0,
                0,
                if (enableOverviewIconMenu() && !isLayoutRtl) 0 else width,
                0,
            )
        val toRect = Rect(0, 0, width, height)
        return RoundedRectRevealOutlineProvider(radius, radius, fromRect, toRect)
    }

    /**
     * Calculates max height based on how much space we have available. If not enough space then the
     * view will scroll. The maximum menu size will sit inside the task with a margin on the top and
     * bottom.
     */
    private fun calculateMaxHeight(): Int =
        taskView.pagedOrientationHandler.getTaskMenuHeight(
            taskInsetMargin = resources.getDimension(R.dimen.task_card_margin), // taskInsetMargin
            deviceProfile = recentsViewContainer.deviceProfile,
            taskMenuX = translationX,
            taskMenuY =
                // Bottom menu can translate up to show more options. So we use the min
                // translation allowed to calculate its max height.
                if (enableOverviewIconMenu() && taskView.isOnGridBottomRow()) minMenuTop
                else translationY,
        )

    private fun setOnClosingStartCallback(onClosingStartCallback: Runnable?) {
        this.onClosingStartCallback = onClosingStartCallback
    }

    private fun animateOpenOrCloseAppChip(closing: Boolean, animatorBuilder: AnimatorSet.Builder) {
        val iconAppChip = taskContainer.iconView.asView() as IconAppChipView

        // Animate menu up for enough room to display full menu when task on bottom row.
        var additionalTranslationY = 0f
        if (taskView.isOnGridBottomRow()) {
            val currentMenuBottom: Float = menuTranslationYBeforeOpen + height
            additionalTranslationY =
                if (currentMenuBottom < maxMenuBottom) 0f
                // Translate menu up for enough room to display full menu when task on bottom row.
                else maxMenuBottom - currentMenuBottom

            val currentMenuTop = menuTranslationYBeforeOpen + additionalTranslationY
            // If it translate above the min accepted, it translates to the top of the screen
            if (currentMenuTop < minMenuTop) {
                // It subtracts the menuTranslation to make it 0 (top of the screen) + chip size.
                additionalTranslationY = -menuTranslationYBeforeOpen + minMenuTop
            }
        }

        val translationYAnim =
            ObjectAnimator.ofFloat(
                this,
                TRANSLATION_Y,
                if (closing) menuTranslationYBeforeOpen
                else menuTranslationYBeforeOpen + additionalTranslationY,
            )
        translationYAnim.interpolator = Interpolators.EMPHASIZED
        animatorBuilder.with(translationYAnim)

        val menuTranslationYAnim: ObjectAnimator =
            ObjectAnimator.ofFloat(
                iconAppChip.getMenuTranslationY(),
                MultiPropertyFactory.MULTI_PROPERTY_VALUE,
                if (closing) 0f else additionalTranslationY,
            )
        menuTranslationYAnim.interpolator = Interpolators.EMPHASIZED
        animatorBuilder.with(menuTranslationYAnim)

        var additionalTranslationX = 0f
        if (
            taskContainer.stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
        ) {
            // Animate menu and icon when split task would display off the side of the screen.
            additionalTranslationX =
                max(
                        (translationX + width -
                                (recentsViewContainer.deviceProfile.widthPx -
                                    resources.getDimensionPixelSize(
                                        R.dimen.task_menu_edge_padding
                                    ) * 2))
                            .toDouble(),
                        0.0,
                    )
                    .toFloat()
        }

        val translationXAnim =
            ObjectAnimator.ofFloat(
                this,
                TRANSLATION_X,
                if (closing) menuTranslationXBeforeOpen
                else menuTranslationXBeforeOpen - additionalTranslationX,
            )
        translationXAnim.interpolator = Interpolators.EMPHASIZED
        animatorBuilder.with(translationXAnim)

        val menuTranslationXAnim: ObjectAnimator =
            ObjectAnimator.ofFloat(
                iconAppChip.getMenuTranslationX(),
                MultiPropertyFactory.MULTI_PROPERTY_VALUE,
                if (closing) 0f else -additionalTranslationX,
            )
        menuTranslationXAnim.interpolator = Interpolators.EMPHASIZED
        animatorBuilder.with(menuTranslationXAnim)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (enableOverviewIconMenu()) {
            if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

            val isFirstMenuOptionFocused = optionLayout.indexOfChild(optionLayout.focusedChild) == 0
            val isLastMenuOptionFocused =
                optionLayout.indexOfChild(optionLayout.focusedChild) == optionLayout.childCount - 1
            if (
                (isLastMenuOptionFocused && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) ||
                    (isFirstMenuOptionFocused && event.keyCode == KeyEvent.KEYCODE_DPAD_UP)
            ) {
                iconView.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private val REVEAL_OPEN_DURATION = if (enableOverviewIconMenu()) 417L else 150L
        private val REVEAL_CLOSE_DURATION = if (enableOverviewIconMenu()) 333L else 100L

        /** Show a task menu for the given taskContainer. */
        /** Show a task menu for the given taskContainer. */
        @JvmOverloads
        fun showForTask(
            taskContainer: TaskContainer,
            onClosingStartCallback: Runnable? = null,
        ): Boolean {
            val container: RecentsViewContainer =
                RecentsViewContainer.containerFromContext(taskContainer.taskView.context)
            val taskMenuView =
                container.layoutInflater.inflate(R.layout.task_menu, container.dragLayer, false)
                    as TaskMenuView
            taskMenuView.setOnClosingStartCallback(onClosingStartCallback)
            return taskMenuView.populateAndShowForTask(taskContainer)
        }
    }
}
