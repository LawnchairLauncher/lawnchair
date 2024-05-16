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
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.popup.RoundedArrowDrawable
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.Themes
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.views.TaskView.TaskContainer

class TaskMenuViewWithArrow<T> : ArrowPopup<T> where T : RecentsViewContainer, T : Context {
    companion object {
        const val TAG = "TaskMenuViewWithArrow"

        fun showForTask(taskContainer: TaskContainer, alignedOptionIndex: Int = 0): Boolean {
            val container: RecentsViewContainer =
                RecentsViewContainer.containerFromContext(taskContainer.taskView.context)
            val taskMenuViewWithArrow =
                container.layoutInflater.inflate(
                    R.layout.task_menu_with_arrow,
                    container.dragLayer,
                    false
                ) as TaskMenuViewWithArrow<*>

            return taskMenuViewWithArrow.populateAndShowForTask(taskContainer, alignedOptionIndex)
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        clipToOutline = true

        shouldScaleArrow = true
        mIsArrowRotated = true
        // This synchronizes the arrow and menu to open at the same time
        mOpenChildFadeStartDelay = mOpenFadeStartDelay
        mOpenChildFadeDuration = mOpenFadeDuration
        mCloseFadeStartDelay = mCloseChildFadeStartDelay
        mCloseFadeDuration = mCloseChildFadeDuration
    }

    private var alignedOptionIndex: Int = 0
    private val extraSpaceForRowAlignment: Int
        get() = optionMeasuredHeight * alignedOptionIndex
    private val menuPaddingEnd = context.resources.getDimensionPixelSize(R.dimen.task_card_margin)

    private lateinit var taskView: TaskView
    private lateinit var optionLayout: LinearLayout
    private lateinit var taskContainer: TaskContainer

    private var optionMeasuredHeight = 0
    private val arrowHorizontalPadding: Int
        get() =
            if (taskView.isFocusedTask)
                resources.getDimensionPixelSize(R.dimen.task_menu_horizontal_padding)
            else 0

    private var iconView: IconView? = null
    private var scrim: View? = null
    private val scrimAlpha = 0.8f

    override fun isOfType(type: Int): Boolean = type and TYPE_TASK_MENU != 0

    override fun getTargetObjectLocation(outPos: Rect?) {
        popupContainer.getDescendantRectRelativeToSelf(taskContainer.iconView.asView(), outPos)
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
        optionLayout = requireViewById(R.id.menu_option_layout)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxMenuHeight: Int = calculateMaxHeight()
        val newHeightMeasureSpec =
            if (MeasureSpec.getSize(heightMeasureSpec) > maxMenuHeight) {
                MeasureSpec.makeMeasureSpec(maxMenuHeight, MeasureSpec.AT_MOST)
            } else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }

    private fun calculateMaxHeight(): Int {
        val taskInsetMargin = resources.getDimension(R.dimen.task_card_margin)
        return taskView.pagedOrientationHandler.getTaskMenuHeight(
            taskInsetMargin,
            mActivityContext.deviceProfile,
            translationX,
            translationY
        )
    }

    private fun populateAndShowForTask(
        taskContainer: TaskContainer,
        alignedOptionIndex: Int
    ): Boolean {
        if (isAttachedToWindow) {
            return false
        }

        taskView = taskContainer.taskView
        this.taskContainer = taskContainer
        this.alignedOptionIndex = alignedOptionIndex
        if (!populateMenu()) return false
        addScrim()
        show()
        return true
    }

    private fun addScrim() {
        scrim =
            View(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                setBackgroundColor(Themes.getAttrColor(context, R.attr.overviewScrimColor))
                alpha = 0f
            }
        popupContainer.addView(scrim)
    }

    /** @return true if successfully able to populate task view menu, false otherwise */
    private fun populateMenu(): Boolean {
        // Icon may not be loaded
        if (taskContainer.iconView.drawable == null) return false

        addMenuOptions()
        return optionLayout.childCount > 0
    }

    private fun addMenuOptions() {
        // Add the options
        TaskOverlayFactory.getEnabledShortcuts(taskView, taskContainer).forEach {
            this.addMenuOption(it)
        }

        // Add the spaces between items
        val divider = ShapeDrawable(RectShape())
        divider.paint.color = resources.getColor(android.R.color.transparent)
        val dividerSpacing = resources.getDimension(R.dimen.task_menu_spacing).toInt()
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
        val menuOptionView =
            mActivityContext.layoutInflater.inflate(R.layout.task_view_menu_option, this, false)
                as LinearLayout
        menuOption.setIconAndLabelFor(
            menuOptionView.requireViewById(R.id.icon),
            menuOptionView.requireViewById(R.id.text)
        )
        val lp = menuOptionView.layoutParams as LayoutParams
        lp.width = LayoutParams.MATCH_PARENT
        menuOptionView.setPaddingRelative(
            menuOptionView.paddingStart,
            menuOptionView.paddingTop,
            menuPaddingEnd,
            menuOptionView.paddingBottom
        )
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
        scrim?.let {
            anim.play(
                ObjectAnimator.ofFloat(it, View.ALPHA, 0f, scrimAlpha)
                    .setDuration(mOpenDuration.toLong())
            )
        }
    }

    override fun onCreateCloseAnimation(anim: AnimatorSet) {
        scrim?.let {
            anim.play(
                ObjectAnimator.ofFloat(it, View.ALPHA, scrimAlpha, 0f)
                    .setDuration(mCloseDuration.toLong())
            )
        }
    }

    override fun closeComplete() {
        super.closeComplete()
        popupContainer.removeView(scrim)
        popupContainer.removeView(iconView)
    }

    /**
     * Copy the iconView from taskView to dragLayer so it can stay on top of the scrim. It needs to
     * be called after [getTargetObjectLocation] because [mTempRect] needs to be populated.
     */
    private fun copyIconToDragLayer(insets: Rect) {
        iconView =
            IconView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        taskContainer.iconView.width,
                        taskContainer.iconView.height
                    )
                x = mTempRect.left.toFloat() - insets.left
                y = mTempRect.top.toFloat() - insets.top
                drawable = taskContainer.iconView.drawable
                setDrawableSize(
                    taskContainer.iconView.drawableWidth,
                    taskContainer.iconView.drawableHeight
                )
            }

        popupContainer.addView(iconView)
    }

    /**
     * Orients this container to the left or right of the given icon, aligning with the desired row.
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

        copyIconToDragLayer(insets)

        // Put this menu to the right of the icon if there is space,
        // which means the arrow is left aligned with the menu
        val rightAlignedMenuStartX = mTempRect.left - widthWithArrow
        val leftAlignedMenuStartX = mTempRect.right + extraHorizontalSpace
        mIsLeftAligned =
            if (mIsRtl) {
                rightAlignedMenuStartX + insets.left < 0
            } else {
                leftAlignedMenuStartX + (widthWithArrow - extraHorizontalSpace) + insets.left <
                    dragLayer.width - insets.right
            }

        var menuStartX = if (mIsLeftAligned) leftAlignedMenuStartX else rightAlignedMenuStartX

        // Offset y so that the arrow and row are center-aligned with the original icon.
        val iconHeight = mTempRect.height()
        val yOffset = (optionMeasuredHeight - iconHeight) / 2
        var menuStartY = mTempRect.top - yOffset - extraSpaceForRowAlignment

        // Insets are added later, so subtract them now.
        menuStartX -= insets.left
        menuStartY -= insets.top

        x = menuStartX.toFloat()
        y = menuStartY.toFloat()

        val lp = layoutParams as FrameLayout.LayoutParams
        val arrowLp = mArrow.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP
        arrowLp.gravity = lp.gravity
    }

    override fun addArrow() {
        popupContainer.addView(mArrow)
        mArrow.x = getArrowX()
        mArrow.y = y + (optionMeasuredHeight / 2) - (mArrowHeight / 2) + extraSpaceForRowAlignment

        updateArrowColor()

        // This is inverted (x = height, y = width) because the arrow is rotated
        mArrow.pivotX = if (mIsLeftAligned) 0f else mArrowHeight.toFloat()
        mArrow.pivotY = 0f
    }

    private fun getArrowX(): Float {
        return if (mIsLeftAligned) x - mArrowHeight else x + measuredWidth + mArrowOffsetVertical
    }

    override fun updateArrowColor() {
        mArrow.background =
            RoundedArrowDrawable.createHorizontalRoundedArrow(
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
