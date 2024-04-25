/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.quickstep.orientation

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.util.FloatProperty
import android.util.Pair
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.VelocityTracker
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.core.util.component1
import androidx.core.util.component2
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer
import com.android.launcher3.touch.PagedOrientationHandler.ChildBounds
import com.android.launcher3.touch.PagedOrientationHandler.Float2DAction
import com.android.launcher3.touch.PagedOrientationHandler.Int2DAction
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.views.IconAppChipView
import kotlin.math.max

open class LandscapePagedViewHandler : RecentsPagedOrientationHandler {
    override fun <T> getPrimaryValue(x: T, y: T): T = y

    override fun <T> getSecondaryValue(x: T, y: T): T = x

    override fun getPrimaryValue(x: Int, y: Int): Int = y

    override fun getSecondaryValue(x: Int, y: Int): Int = x

    override fun getPrimaryValue(x: Float, y: Float): Float = y

    override fun getSecondaryValue(x: Float, y: Float): Float = x

    override val isLayoutNaturalToLauncher: Boolean = false

    override fun adjustFloatingIconStartVelocity(velocity: PointF) {
        val oldX = velocity.x
        val oldY = velocity.y
        velocity.set(-oldY, oldX)
    }

    override fun fixBoundsForHomeAnimStartRect(outStartRect: RectF, deviceProfile: DeviceProfile) {
        // We don't need to check the "top" value here because the startRect is in the orientation
        // of the app, not of the fixed portrait launcher.
        if (outStartRect.left > deviceProfile.heightPx) {
            outStartRect.offsetTo(0f, outStartRect.top)
        } else if (outStartRect.left < -deviceProfile.heightPx) {
            outStartRect.offsetTo(0f, outStartRect.top)
        }
    }

    override fun <T> setPrimary(target: T, action: Int2DAction<T>, param: Int) =
        action.call(target, 0, param)

    override fun <T> setPrimary(target: T, action: Float2DAction<T>, param: Float) =
        action.call(target, 0f, param)

    override fun <T> setSecondary(target: T, action: Float2DAction<T>, param: Float) =
        action.call(target, param, 0f)

    override fun <T> set(
        target: T,
        action: Int2DAction<T>,
        primaryParam: Int,
        secondaryParam: Int
    ) = action.call(target, secondaryParam, primaryParam)

    override fun getPrimaryDirection(event: MotionEvent, pointerIndex: Int): Float =
        event.getY(pointerIndex)

    override fun getPrimaryVelocity(velocityTracker: VelocityTracker, pointerId: Int): Float =
        velocityTracker.getYVelocity(pointerId)

    override fun getMeasuredSize(view: View): Int = view.measuredHeight

    override fun getPrimarySize(view: View): Int = view.height

    override fun getPrimarySize(rect: RectF): Float = rect.height()

    override fun getStart(rect: RectF): Float = rect.top

    override fun getEnd(rect: RectF): Float = rect.bottom

    override fun getClearAllSidePadding(view: View, isRtl: Boolean): Int =
        if (isRtl) view.paddingBottom / 2 else -view.paddingTop / 2

    override fun getSecondaryDimension(view: View): Int = view.width

    override val primaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_Y

    override val secondaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_X

    override fun getPrimaryScroll(view: View): Int = view.scrollY

    override fun getPrimaryScale(view: View): Float = view.scaleY

    override fun setMaxScroll(event: AccessibilityEvent, maxScroll: Int) {
        event.maxScrollY = maxScroll
    }

    override fun getRecentsRtlSetting(resources: Resources): Boolean = !Utilities.isRtl(resources)

    override val degreesRotated: Float = 90f

    override val rotation: Int = Surface.ROTATION_90

    override fun setPrimaryScale(view: View, scale: Float) {
        view.scaleY = scale
    }

    override fun setSecondaryScale(view: View, scale: Float) {
        view.scaleX = scale
    }

    override fun getChildStart(view: View): Int = view.top

    override fun getCenterForPage(view: View, insets: Rect): Int =
        (view.paddingLeft + view.measuredWidth + insets.left - insets.right - view.paddingRight) / 2

    override fun getScrollOffsetStart(view: View, insets: Rect): Int = insets.top + view.paddingTop

    override fun getScrollOffsetEnd(view: View, insets: Rect): Int =
        view.height - view.paddingBottom - insets.bottom

    override val secondaryTranslationDirectionFactor: Int = 1

    override fun getSplitTranslationDirectionFactor(
        stagePosition: Int,
        deviceProfile: DeviceProfile
    ): Int = if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) -1 else 1

    override fun getTaskMenuX(
        x: Float,
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float = thumbnailView.measuredWidth + x - taskInsetMargin

    override fun getTaskMenuY(
        y: Float,
        thumbnailView: View,
        stagePosition: Int,
        taskMenuView: View,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float {
        val layoutParams = taskMenuView.layoutParams as BaseDragLayer.LayoutParams
        var taskMenuY = y + taskInsetMargin

        if (stagePosition == STAGE_POSITION_UNDEFINED) {
            taskMenuY += (thumbnailView.measuredHeight - layoutParams.width) / 2f
        }

        return taskMenuY
    }

    override fun getTaskMenuWidth(
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        @StagePosition stagePosition: Int
    ): Int =
        when {
            Flags.enableOverviewIconMenu() ->
                thumbnailView.resources.getDimensionPixelSize(
                    R.dimen.task_thumbnail_icon_menu_expanded_width
                )
            stagePosition == STAGE_POSITION_UNDEFINED -> thumbnailView.measuredWidth
            else -> thumbnailView.measuredHeight
        }

    override fun getTaskMenuHeight(
        taskInsetMargin: Float,
        deviceProfile: DeviceProfile,
        taskMenuX: Float,
        taskMenuY: Float
    ): Int = (taskMenuX - taskInsetMargin).toInt()

    override fun setTaskOptionsMenuLayoutOrientation(
        deviceProfile: DeviceProfile,
        taskMenuLayout: LinearLayout,
        dividerSpacing: Int,
        dividerDrawable: ShapeDrawable
    ) {
        taskMenuLayout.orientation = LinearLayout.VERTICAL
        dividerDrawable.intrinsicHeight = dividerSpacing
        taskMenuLayout.dividerDrawable = dividerDrawable
    }

    override fun setLayoutParamsForTaskMenuOptionItem(
        lp: LinearLayout.LayoutParams,
        viewGroup: LinearLayout,
        deviceProfile: DeviceProfile
    ) {
        // Phone fake landscape
        viewGroup.orientation = LinearLayout.HORIZONTAL
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    override fun getDwbLayoutTranslations(
        taskViewWidth: Int,
        taskViewHeight: Int,
        splitBounds: SplitBounds?,
        deviceProfile: DeviceProfile,
        thumbnailViews: Array<View>,
        desiredTaskId: Int,
        banner: View
    ): Pair<Float, Float> {
        val snapshotParams = thumbnailViews[0].layoutParams as FrameLayout.LayoutParams
        val isRtl = banner.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val translationX = banner.height.toFloat()

        val bannerParams = banner.layoutParams as FrameLayout.LayoutParams
        bannerParams.gravity = Gravity.TOP or if (isRtl) Gravity.END else Gravity.START
        banner.pivotX = 0f
        banner.pivotY = 0f
        banner.rotation = degreesRotated

        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = taskViewHeight - snapshotParams.topMargin
            return Pair(translationX, snapshotParams.topMargin.toFloat())
        }

        // Set correct width and translations
        val translationY: Float
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].measuredHeight
            translationY = snapshotParams.topMargin.toFloat()
        } else {
            bannerParams.width = thumbnailViews[1].measuredHeight
            val topLeftTaskPlusDividerPercent =
                if (splitBounds.appsStackedVertically) {
                    splitBounds.topTaskPercent + splitBounds.dividerHeightPercent
                } else {
                    splitBounds.leftTaskPercent + splitBounds.dividerWidthPercent
                }
            translationY =
                snapshotParams.topMargin +
                    (taskViewHeight - snapshotParams.topMargin) * topLeftTaskPlusDividerPercent
        }

        return Pair(translationX, translationY)
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */
    override val upDownSwipeDirection: SingleAxisSwipeDetector.Direction =
        SingleAxisSwipeDetector.HORIZONTAL

    override fun getUpDirection(isRtl: Boolean): Int =
        if (isRtl) SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        else SingleAxisSwipeDetector.DIRECTION_POSITIVE

    override fun isGoingUp(displacement: Float, isRtl: Boolean): Boolean =
        if (isRtl) displacement < 0 else displacement > 0

    override fun getTaskDragDisplacementFactor(isRtl: Boolean): Int = if (isRtl) 1 else -1
    /* -------------------- */

    override fun getChildBounds(
        child: View,
        childStart: Int,
        pageCenter: Int,
        layoutChild: Boolean
    ): ChildBounds {
        val childHeight = child.measuredHeight
        val childWidth = child.measuredWidth
        val childBottom = childStart + childHeight
        val childLeft = pageCenter - childWidth / 2
        if (layoutChild) {
            child.layout(childLeft, childStart, childLeft + childWidth, childBottom)
        }
        return ChildBounds(childHeight, childWidth, childBottom, childLeft)
    }

    override fun getDistanceToBottomOfRect(dp: DeviceProfile, rect: Rect): Int = rect.left

    override fun getSplitPositionOptions(dp: DeviceProfile): List<SplitPositionOption> =
        // Add "left" side of phone which is actually the top
        listOf(
            SplitPositionOption(
                R.drawable.ic_split_horizontal,
                R.string.recent_task_option_split_screen,
                STAGE_POSITION_TOP_OR_LEFT,
                STAGE_TYPE_MAIN
            )
        )

    override fun getInitialSplitPlaceholderBounds(
        placeholderHeight: Int,
        placeholderInset: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out: Rect
    ) {
        // In fake land/seascape, the placeholder always needs to go to the "top" of the device,
        // which is the same bounds as 0 rotation.
        val width = dp.widthPx
        val insetSizeAdjustment = getPlaceholderSizeAdjustment(dp)
        out.set(0, 0, width, placeholderHeight + insetSizeAdjustment)
        out.inset(placeholderInset, 0)

        // Adjust the top to account for content off screen. This will help to animate the view in
        // with rounded corners.
        val screenWidth = dp.widthPx
        val screenHeight = dp.heightPx
        val totalHeight =
            (1.0f * screenHeight / 2 * (screenWidth - 2 * placeholderInset) / screenWidth).toInt()
        out.top -= totalHeight - placeholderHeight
    }

    override fun updateSplitIconParams(
        out: View,
        onScreenRectCenterX: Float,
        onScreenRectCenterY: Float,
        fullscreenScaleX: Float,
        fullscreenScaleY: Float,
        drawableWidth: Int,
        drawableHeight: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int
    ) {
        val insetAdjustment = getPlaceholderSizeAdjustment(dp) / 2f
        out.x = (onScreenRectCenterX / fullscreenScaleX - 1.0f * drawableWidth / 2)
        out.y =
            ((onScreenRectCenterY + insetAdjustment) / fullscreenScaleY - 1.0f * drawableHeight / 2)
    }

    /**
     * The split placeholder comes with a default inset to buffer the icon from the top of the
     * screen. But if the device already has a large inset (from cutouts etc), use that instead.
     */
    private fun getPlaceholderSizeAdjustment(dp: DeviceProfile?): Int =
        max((dp!!.insets.top - dp.splitPlaceholderInset).toDouble(), 0.0).toInt()

    override fun setSplitInstructionsParams(
        out: View,
        dp: DeviceProfile,
        splitInstructionsHeight: Int,
        splitInstructionsWidth: Int
    ) {
        out.pivotX = 0f
        out.pivotY = splitInstructionsHeight.toFloat()
        out.rotation = degreesRotated
        val distanceToEdge =
            out.resources.getDimensionPixelSize(
                R.dimen.split_instructions_bottom_margin_phone_landscape
            )
        // Adjust for any insets on the left edge
        val insetCorrectionX = dp.insets.left
        // Center the view in case of unbalanced insets on top or bottom of screen
        val insetCorrectionY = (dp.insets.bottom - dp.insets.top) / 2
        out.translationX = (distanceToEdge - insetCorrectionX).toFloat()
        out.translationY =
            (-splitInstructionsHeight - splitInstructionsWidth) / 2f + insetCorrectionY
        // Setting gravity to LEFT instead of the lint-recommended START because we always want this
        // view to be screen-left when phone is in landscape, regardless of the RtL setting.
        val lp = out.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        out.layoutParams = lp
    }

    override fun getFinalSplitPlaceholderBounds(
        splitDividerSize: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out1: Rect,
        out2: Rect
    ) {
        // In fake land/seascape, the window bounds are always top and bottom half
        val screenHeight = dp.heightPx
        val screenWidth = dp.widthPx
        out1.set(0, 0, screenWidth, screenHeight / 2 - splitDividerSize)
        out2.set(0, screenHeight / 2 + splitDividerSize, screenWidth, screenHeight)
    }

    override fun setSplitTaskSwipeRect(
        dp: DeviceProfile,
        outRect: Rect,
        splitInfo: SplitBounds,
        desiredStagePosition: Int
    ) {
        val topLeftTaskPercent: Float
        val dividerBarPercent: Float
        if (splitInfo.appsStackedVertically) {
            topLeftTaskPercent = splitInfo.topTaskPercent
            dividerBarPercent = splitInfo.dividerHeightPercent
        } else {
            topLeftTaskPercent = splitInfo.leftTaskPercent
            dividerBarPercent = splitInfo.dividerWidthPercent
        }

        if (desiredStagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            outRect.bottom = outRect.top + (outRect.height() * topLeftTaskPercent).toInt()
        } else {
            outRect.top += (outRect.height() * (topLeftTaskPercent + dividerBarPercent)).toInt()
        }
    }

    override fun measureGroupedTaskViewThumbnailBounds(
        primarySnapshot: View,
        secondarySnapshot: View,
        parentWidth: Int,
        parentHeight: Int,
        splitBoundsConfig: SplitBounds,
        dp: DeviceProfile,
        isRtl: Boolean
    ) {
        val primaryParams = primarySnapshot.layoutParams as FrameLayout.LayoutParams
        val secondaryParams = secondarySnapshot.layoutParams as FrameLayout.LayoutParams

        // Swap the margins that are set in TaskView#setRecentsOrientedState()
        secondaryParams.topMargin = dp.overviewTaskThumbnailTopMarginPx
        primaryParams.topMargin = 0

        // Measure and layout the thumbnails bottom up, since the primary is on the visual left
        // (portrait bottom) and secondary is on the right (portrait top)
        val spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx
        val totalThumbnailHeight = parentHeight - spaceAboveSnapshot
        val dividerBar = getDividerBarSize(totalThumbnailHeight, splitBoundsConfig)

        val (taskViewFirst, taskViewSecond) =
            getGroupedTaskViewSizes(dp, splitBoundsConfig, parentWidth, parentHeight)

        primarySnapshot.translationY = spaceAboveSnapshot.toFloat()
        primarySnapshot.measure(
            MeasureSpec.makeMeasureSpec(taskViewFirst.x, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(taskViewFirst.y, MeasureSpec.EXACTLY)
        )
        val translationY = taskViewFirst.y + spaceAboveSnapshot + dividerBar
        secondarySnapshot.translationY = (translationY - spaceAboveSnapshot).toFloat()
        secondarySnapshot.measure(
            MeasureSpec.makeMeasureSpec(taskViewSecond.x, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(taskViewSecond.y, MeasureSpec.EXACTLY)
        )
    }

    override fun getGroupedTaskViewSizes(
        dp: DeviceProfile,
        splitBoundsConfig: SplitBounds,
        parentWidth: Int,
        parentHeight: Int
    ): Pair<Point, Point> {
        val spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx
        val totalThumbnailHeight = parentHeight - spaceAboveSnapshot
        val dividerBar = getDividerBarSize(totalThumbnailHeight, splitBoundsConfig)

        val taskPercent =
            if (splitBoundsConfig.appsStackedVertically) {
                splitBoundsConfig.topTaskPercent
            } else {
                splitBoundsConfig.leftTaskPercent
            }
        val firstTaskViewSize = Point(parentWidth, (totalThumbnailHeight * taskPercent).toInt())
        val secondTaskViewSize =
            Point(parentWidth, totalThumbnailHeight - firstTaskViewSize.y - dividerBar)
        return Pair(firstTaskViewSize, secondTaskViewSize)
    }

    override fun setTaskIconParams(
        iconParams: FrameLayout.LayoutParams,
        taskIconMargin: Int,
        taskIconHeight: Int,
        thumbnailTopMargin: Int,
        isRtl: Boolean
    ) {
        iconParams.gravity =
            if (isRtl) {
                Gravity.START or Gravity.CENTER_VERTICAL
            } else {
                Gravity.END or Gravity.CENTER_VERTICAL
            }
        iconParams.rightMargin = -taskIconHeight - taskIconMargin / 2
        iconParams.leftMargin = 0
        iconParams.topMargin = thumbnailTopMargin / 2
        iconParams.bottomMargin = 0
    }

    override fun setIconAppChipChildrenParams(
        iconParams: FrameLayout.LayoutParams,
        chipChildMarginStart: Int
    ) {
        iconParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconParams.marginStart = chipChildMarginStart
        iconParams.topMargin = 0
    }

    override fun setIconAppChipMenuParams(
        iconAppChipView: IconAppChipView,
        iconMenuParams: FrameLayout.LayoutParams,
        iconMenuMargin: Int,
        thumbnailTopMargin: Int
    ) {
        val isRtl = iconAppChipView.layoutDirection == View.LAYOUT_DIRECTION_RTL

        if (isRtl) {
            iconMenuParams.gravity = Gravity.START or Gravity.BOTTOM
            iconMenuParams.marginStart = iconMenuMargin
            iconMenuParams.bottomMargin = iconMenuMargin
            iconAppChipView.pivotX = iconMenuParams.width - iconMenuParams.height / 2f
            iconAppChipView.pivotY = iconMenuParams.height / 2f
        } else {
            iconMenuParams.gravity = Gravity.END or Gravity.TOP
            iconMenuParams.marginStart = 0
            iconMenuParams.bottomMargin = 0
            iconAppChipView.pivotX = iconMenuParams.width / 2f
            iconAppChipView.pivotY = iconMenuParams.width / 2f
        }

        iconMenuParams.topMargin = iconMenuMargin
        iconMenuParams.marginEnd = iconMenuMargin
        iconAppChipView.setSplitTranslationY(0f)
        iconAppChipView.setRotation(degreesRotated)
    }

    override fun setSplitIconParams(
        primaryIconView: View,
        secondaryIconView: View,
        taskIconHeight: Int,
        primarySnapshotWidth: Int,
        primarySnapshotHeight: Int,
        groupedTaskViewHeight: Int,
        groupedTaskViewWidth: Int,
        isRtl: Boolean,
        deviceProfile: DeviceProfile,
        splitConfig: SplitBounds
    ) {
        val spaceAboveSnapshot = deviceProfile.overviewTaskThumbnailTopMarginPx
        val totalThumbnailHeight = groupedTaskViewHeight - spaceAboveSnapshot
        val dividerBar: Int = getDividerBarSize(totalThumbnailHeight, splitConfig)

        val (topLeftY, bottomRightY) =
            getSplitIconsPosition(
                taskIconHeight,
                primarySnapshotHeight,
                totalThumbnailHeight,
                isRtl,
                deviceProfile.overviewTaskMarginPx,
                dividerBar
            )

        updateSplitIconsPosition(primaryIconView, topLeftY, isRtl)
        updateSplitIconsPosition(secondaryIconView, bottomRightY, isRtl)
    }

    override fun getDefaultSplitPosition(deviceProfile: DeviceProfile): Int {
        throw IllegalStateException("Default position not available in fake landscape")
    }

    override fun <T> getSplitSelectTaskOffset(
        primary: FloatProperty<T>,
        secondary: FloatProperty<T>,
        deviceProfile: DeviceProfile
    ): Pair<FloatProperty<T>, FloatProperty<T>> = Pair(primary, secondary)

    override fun getFloatingTaskOffscreenTranslationTarget(
        floatingTask: View,
        onScreenRect: RectF,
        @StagePosition stagePosition: Int,
        dp: DeviceProfile
    ): Float = floatingTask.translationY - onScreenRect.height()

    override fun setFloatingTaskPrimaryTranslation(
        floatingTask: View,
        translation: Float,
        dp: DeviceProfile
    ) {
        floatingTask.translationY = translation
    }

    override fun getFloatingTaskPrimaryTranslation(floatingTask: View, dp: DeviceProfile): Float =
        floatingTask.translationY

    override fun getHandlerTypeForLogging(): TaskSwitcherContainer.OrientationHandler =
        TaskSwitcherContainer.OrientationHandler.LANDSCAPE

    /**
     * Retrieves split icons position
     *
     * @param taskIconHeight The height of the task icon.
     * @param primarySnapshotHeight The height for the primary snapshot (i.e., top-left snapshot).
     * @param totalThumbnailHeight The total height for the group task view.
     * @param isRtl Whether the layout direction is RTL (or false for LTR).
     * @param overviewTaskMarginPx The space under the focused task icon provided by Device Profile.
     * @param dividerSize The size of the divider for the group task view.
     * @return The top-left and right-bottom positions for the icon views.
     */
    @VisibleForTesting
    open fun getSplitIconsPosition(
        taskIconHeight: Int,
        primarySnapshotHeight: Int,
        totalThumbnailHeight: Int,
        isRtl: Boolean,
        overviewTaskMarginPx: Int,
        dividerSize: Int,
    ): SplitIconPositions {
        return if (Flags.enableOverviewIconMenu()) {
            if (isRtl) {
                SplitIconPositions(0, -(totalThumbnailHeight - primarySnapshotHeight))
            } else {
                SplitIconPositions(0, primarySnapshotHeight + dividerSize)
            }
        } else {
            val topLeftY = primarySnapshotHeight + overviewTaskMarginPx
            SplitIconPositions(
                topLeftY = topLeftY,
                bottomRightY = topLeftY + dividerSize + taskIconHeight
            )
        }
    }

    /**
     * Updates icon view gravity and translation for split tasks
     *
     * @param iconView View to be updated
     * @param translationY the translationY that should be applied
     * @param isRtl Whether the layout direction is RTL (or false for LTR).
     */
    @SuppressLint("RtlHardcoded")
    @VisibleForTesting
    open fun updateSplitIconsPosition(iconView: View, translationY: Int, isRtl: Boolean) {
        val layoutParams = iconView.layoutParams as FrameLayout.LayoutParams

        if (Flags.enableOverviewIconMenu()) {
            val appChipView = iconView as IconAppChipView
            layoutParams.gravity =
                if (isRtl) Gravity.BOTTOM or Gravity.START else Gravity.TOP or Gravity.END
            appChipView.layoutParams = layoutParams
            appChipView.setSplitTranslationX(0f)
            appChipView.setSplitTranslationY(translationY.toFloat())
        } else {
            layoutParams.gravity = Gravity.TOP or Gravity.RIGHT
            layoutParams.topMargin = translationY
            iconView.translationX = 0f
            iconView.translationY = 0f
            iconView.layoutParams = layoutParams
        }
    }

    /**
     * It calculates the divider's size in the group task view.
     *
     * @param totalThumbnailHeight The total height for the group task view
     * @param splitConfig Contains information about sizes and proportions for split task.
     * @return The divider size for the group task view.
     */
    protected fun getDividerBarSize(totalThumbnailHeight: Int, splitConfig: SplitBounds): Int {
        return Math.round(
            totalThumbnailHeight *
                if (splitConfig.appsStackedVertically) splitConfig.dividerHeightPercent
                else splitConfig.dividerWidthPercent
        )
    }

    /**
     * Data structure to keep the y position to be used for the split task icon views translation.
     *
     * @param topLeftY The y-axis position for the task view position on the Top or Left side.
     * @param bottomRightY The y-axis position for the task view position on the Bottom or Right
     *   side.
     */
    data class SplitIconPositions(val topLeftY: Int, val bottomRightY: Int)
}
