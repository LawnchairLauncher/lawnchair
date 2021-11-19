/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.launcher3.BaseDraggingActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.Themes
import com.android.quickstep.KtR
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer

class TaskMenuViewWithArrow<T : BaseDraggingActivity> : ArrowPopup<T> {
    companion object {
        const val TAG = "TaskMenuViewWithArrow"

        fun showForTask(taskContainer: TaskIdAttributeContainer): Boolean {
            val activity = BaseDraggingActivity
                .fromContext<BaseDraggingActivity>(taskContainer.taskView.context)
            val taskMenuViewWithArrow = activity.layoutInflater
                .inflate(
                    KtR.layout.task_menu_with_arrow,
                    activity.dragLayer,
                    false
                ) as TaskMenuViewWithArrow<*>

            return taskMenuViewWithArrow.populateAndShowForTask(taskContainer)
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        clipToOutline = true

        shouldScaleArrow = true
        // This synchronizes the arrow and menu to open at the same time
        OPEN_CHILD_FADE_START_DELAY = OPEN_FADE_START_DELAY
        OPEN_CHILD_FADE_DURATION = OPEN_FADE_DURATION
        CLOSE_FADE_START_DELAY = CLOSE_CHILD_FADE_START_DELAY
        CLOSE_FADE_DURATION = CLOSE_CHILD_FADE_DURATION
    }

    private val menuWidth = context.resources.getDimensionPixelSize(R.dimen.task_menu_width_grid)

    private lateinit var taskView: TaskView
    private lateinit var optionLayout: LinearLayout
    private lateinit var taskContainer: TaskIdAttributeContainer

    private var optionMeasuredHeight = 0
    private val arrowHorizontalPadding: Int
        get() = if (taskView.isFocusedTask)
            resources.getDimensionPixelSize(KtR.dimen.task_menu_horizontal_padding)
        else
            0

    override fun isOfType(type: Int): Boolean = type and TYPE_TASK_MENU != 0

    override fun getTargetObjectLocation(outPos: Rect?) {
        popupContainer.getDescendantRectRelativeToSelf(taskContainer.iconView, outPos)
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (!popupContainer.isEventOverView(this, ev)) {
                close(true)
                return true
            }
        }
        return false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        optionLayout = findViewById(KtR.id.menu_option_layout)
    }

    private fun populateAndShowForTask(taskContainer: TaskIdAttributeContainer): Boolean {
        if (isAttachedToWindow) {
            return false
        }

        taskView = taskContainer.taskView
        this.taskContainer = taskContainer
        if (!populateMenu()) return false
        show()
        return true
    }

    /** @return true if successfully able to populate task view menu, false otherwise
     */
    private fun populateMenu(): Boolean {
        // Icon may not be loaded
        if (taskContainer.task.icon == null) return false

        addMenuOptions()
        return true
    }

    private fun addMenuOptions() {
        // Add the options
        TaskOverlayFactory
            .getEnabledShortcuts(taskView, mActivityContext.deviceProfile, taskContainer)
            .forEach { this.addMenuOption(it) }

        // Add the spaces between items
        val divider = ShapeDrawable(RectShape())
        divider.paint.color = resources.getColor(android.R.color.transparent)
        val dividerSpacing = resources.getDimension(KtR.dimen.task_menu_spacing).toInt()
        optionLayout.showDividers = SHOW_DIVIDER_MIDDLE

        // Set the orientation, which makes the menu show
        val recentsView: RecentsView<*, *> = mActivityContext.getOverviewPanel()
        val orientationHandler = recentsView.pagedOrientationHandler
        val deviceProfile: DeviceProfile = mActivityContext.deviceProfile
        orientationHandler.setTaskOptionsMenuLayoutOrientation(
            deviceProfile,
            optionLayout,
            dividerSpacing,
            divider
        )
    }

    private fun addMenuOption(menuOption: SystemShortcut<*>) {
        val menuOptionView = mActivityContext.layoutInflater.inflate(
            KtR.layout.task_view_menu_option, this, false
        ) as LinearLayout
        menuOption.setIconAndLabelFor(
            menuOptionView.findViewById(R.id.icon),
            menuOptionView.findViewById(R.id.text)
        )
        val lp = menuOptionView.layoutParams as LayoutParams
        lp.width = menuWidth
        menuOptionView.setOnClickListener { view: View? -> menuOption.onClick(view) }
        optionLayout.addView(menuOptionView)
    }

    override fun assignMarginsAndBackgrounds(viewGroup: ViewGroup) {
        assignMarginsAndBackgrounds(
            this,
            Themes.getAttrColor(context, com.android.internal.R.attr.colorSurface)
        )
    }

    override fun onCreateOpenAnimation(anim: AnimatorSet) {
        anim.play(
            ObjectAnimator.ofFloat(
                taskContainer.thumbnailView, TaskThumbnailView.DIM_ALPHA,
                TaskView.MAX_PAGE_SCRIM_ALPHA
            )
        )
    }

    override fun onCreateCloseAnimation(anim: AnimatorSet) {
        anim.play(
            ObjectAnimator.ofFloat(taskContainer.thumbnailView, TaskThumbnailView.DIM_ALPHA, 0f)
        )
    }

    /**
     * Orients this container to the left or right of the given icon, aligning with the first option
     * or second.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Right and first option aligned
     * - Right and second option aligned
     * - Left and first option aligned
     * - Left and second option aligned
     *
     * So we always align right if there is enough horizontal space
     */
    override fun orientAboutObject() {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        // Needed for offsets later
        optionMeasuredHeight = optionLayout.getChildAt(0).measuredHeight
        val extraHorizontalSpace = (mArrowHeight + mArrowOffsetVertical + arrowHorizontalPadding)

        val widthWithArrow = measuredWidth + paddingLeft + paddingRight + extraHorizontalSpace
        getTargetObjectLocation(mTempRect)
        val dragLayer: InsettableFrameLayout = popupContainer
        val insets = dragLayer.insets

        // Put to the right of the icon if there is space, which means left aligned with the menu
        val rightAlignedMenuStartX = mTempRect.left - widthWithArrow
        val leftAlignedMenuStartX = mTempRect.right + extraHorizontalSpace
        mIsLeftAligned = if (mIsRtl) {
            rightAlignedMenuStartX + insets.left < 0
        } else {
            leftAlignedMenuStartX + (widthWithArrow - extraHorizontalSpace) + insets.left <
                    dragLayer.width - insets.right
        }

        var menuStartX = if (mIsLeftAligned) leftAlignedMenuStartX else rightAlignedMenuStartX

        // Offset y so that the arrow and first row are center-aligned with the original icon.
        val iconHeight = mTempRect.height()
        val optionHeight = optionMeasuredHeight
        val yOffset = (optionHeight - iconHeight) / 2
        var menuStartY = mTempRect.top - yOffset

        // Insets are added later, so subtract them now.
        menuStartX -= insets.left
        menuStartY -= insets.top

        setX(menuStartX.toFloat())
        setY(menuStartY.toFloat())

        val lp = layoutParams as FrameLayout.LayoutParams
        val arrowLp = mArrow.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP
        arrowLp.gravity = lp.gravity
    }

    override fun addArrow() {
        popupContainer.addView(mArrow)
        mArrow.x = getArrowX()
        mArrow.y = y + (optionMeasuredHeight / 2) - (mArrowHeight / 2)

        updateArrowColor()

        // This is inverted (x = height, y = width) because the arrow is rotated
        mArrow.pivotX = if (mIsLeftAligned) 0f else mArrowHeight.toFloat()
        mArrow.pivotY = 0f
    }

    private fun getArrowX(): Float {
        return if (mIsLeftAligned)
            x - mArrowHeight
        else
            x + measuredWidth + mArrowOffsetVertical
    }

    override fun updateArrowColor() {
        mArrow.background = RoundedArrowDrawable(
            mArrowWidth.toFloat(),
            mArrowHeight.toFloat(),
            mArrowPointRadius.toFloat(),
            mIsLeftAligned,
            mArrowColor
        )
        elevation = mElevation
        mArrow.elevation = mElevation
    }

}