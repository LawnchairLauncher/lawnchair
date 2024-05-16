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
import android.util.Pair
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.core.util.component1
import androidx.core.util.component2
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.views.IconAppChipView

class SeascapePagedViewHandler : LandscapePagedViewHandler() {
    override val secondaryTranslationDirectionFactor: Int = -1

    override fun getSplitTranslationDirectionFactor(
        stagePosition: Int,
        deviceProfile: DeviceProfile
    ): Int = if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) -1 else 1

    override fun getRecentsRtlSetting(resources: Resources): Boolean = Utilities.isRtl(resources)

    override val degreesRotated: Float = 270f

    override val rotation: Int = Surface.ROTATION_270

    override fun adjustFloatingIconStartVelocity(velocity: PointF) =
        velocity.set(velocity.y, -velocity.x)

    override fun getTaskMenuX(
        x: Float,
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float = x + taskInsetMargin

    override fun getTaskMenuY(
        y: Float,
        thumbnailView: View,
        stagePosition: Int,
        taskMenuView: View,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float {
        if (Flags.enableOverviewIconMenu()) {
            return y
        }
        val lp = taskMenuView.layoutParams as BaseDragLayer.LayoutParams
        val taskMenuWidth = lp.width
        return if (stagePosition == STAGE_POSITION_UNDEFINED) {
            y + taskInsetMargin + (thumbnailView.measuredHeight + taskMenuWidth) / 2f
        } else {
            y + taskMenuWidth + taskInsetMargin
        }
    }

    override fun getTaskMenuHeight(
        taskInsetMargin: Float,
        deviceProfile: DeviceProfile,
        taskMenuX: Float,
        taskMenuY: Float
    ): Int = (deviceProfile.availableWidthPx - taskInsetMargin - taskMenuX).toInt()

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

        // In seascape, the primary thumbnail is counterintuitively placed at the physical bottom of
        // the screen. This is to preserve consistency when the user rotates: From the user's POV,
        // the primary should always be on the left.
        if (desiredStagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            outRect.top += (outRect.height() * (1 - topLeftTaskPercent)).toInt()
        } else {
            outRect.bottom -= (outRect.height() * (topLeftTaskPercent + dividerBarPercent)).toInt()
        }
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

        val bannerParams = banner.layoutParams as FrameLayout.LayoutParams
        bannerParams.gravity = Gravity.BOTTOM or if (isRtl) Gravity.END else Gravity.START
        banner.pivotX = 0f
        banner.pivotY = 0f
        banner.rotation = degreesRotated

        val translationX: Float = (taskViewWidth - banner.height).toFloat()
        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = taskViewHeight - snapshotParams.topMargin
            return Pair(translationX, banner.height.toFloat())
        }

        // Set correct width and translations
        val translationY: Float
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].measuredHeight
            val bottomRightTaskPlusDividerPercent =
                if (splitBounds.appsStackedVertically) {
                    1f - splitBounds.topTaskPercent
                } else {
                    1f - splitBounds.leftTaskPercent
                }
            translationY =
                banner.height -
                    (taskViewHeight - snapshotParams.topMargin) * bottomRightTaskPlusDividerPercent
        } else {
            bannerParams.width = thumbnailViews[1].measuredHeight
            translationY = banner.height.toFloat()
        }

        return Pair(translationX, translationY)
    }

    override fun getDistanceToBottomOfRect(dp: DeviceProfile, rect: Rect): Int =
        dp.widthPx - rect.right

    override fun getSplitPositionOptions(dp: DeviceProfile): List<SplitPositionOption> =
        // Add "right" option which is actually the top
        listOf(
            SplitPositionOption(
                R.drawable.ic_split_horizontal,
                R.string.recent_task_option_split_screen,
                STAGE_POSITION_BOTTOM_OR_RIGHT,
                STAGE_TYPE_MAIN
            )
        )

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
        // Adjust for any insets on the right edge
        val insetCorrectionX = dp.insets.right
        // Center the view in case of unbalanced insets on top or bottom of screen
        val insetCorrectionY = (dp.insets.bottom - dp.insets.top) / 2
        out.translationX = (splitInstructionsWidth - distanceToEdge + insetCorrectionX).toFloat()
        out.translationY =
            (-splitInstructionsHeight + splitInstructionsWidth) / 2f + insetCorrectionY
        // Setting gravity to RIGHT instead of the lint-recommended END because we always want this
        // view to be screen-right when phone is in seascape, regardless of the RtL setting.
        val lp = out.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        out.layoutParams = lp
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
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }
        iconParams.setMargins(-taskIconHeight - taskIconMargin / 2, thumbnailTopMargin / 2, 0, 0)
    }

    override fun setIconAppChipChildrenParams(
        iconParams: FrameLayout.LayoutParams,
        chipChildMarginStart: Int
    ) {
        iconParams.setMargins(0, 0, 0, 0)
        iconParams.marginStart = chipChildMarginStart
        iconParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    override fun setIconAppChipMenuParams(
        iconAppChipView: IconAppChipView,
        iconMenuParams: FrameLayout.LayoutParams,
        iconMenuMargin: Int,
        thumbnailTopMargin: Int
    ) {
        val isRtl = iconAppChipView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val iconCenter = iconAppChipView.getHeight() / 2f

        if (isRtl) {
            iconMenuParams.gravity = Gravity.TOP or Gravity.END
            iconMenuParams.topMargin = iconMenuMargin
            iconMenuParams.marginEnd = thumbnailTopMargin
            // Use half menu height to place the pivot within the X/Y center of icon in the menu.
            iconAppChipView.pivotX = iconMenuParams.width / 2f
            iconAppChipView.pivotY = iconMenuParams.width / 2f
        } else {
            iconMenuParams.gravity = Gravity.BOTTOM or Gravity.START
            iconMenuParams.topMargin = 0
            iconMenuParams.marginEnd = 0
            iconAppChipView.pivotX = iconCenter
            iconAppChipView.pivotY = iconCenter - iconMenuMargin
        }
        iconMenuParams.marginStart = 0
        iconMenuParams.bottomMargin = 0
        iconAppChipView.setSplitTranslationY(0f)
        iconAppChipView.setRotation(degreesRotated)
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
        secondarySnapshot.translationY = 0f
        primarySnapshot.translationY =
            (taskViewSecond.y + spaceAboveSnapshot + dividerBar).toFloat()
        primarySnapshot.measure(
            MeasureSpec.makeMeasureSpec(taskViewFirst.x, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(taskViewFirst.y, MeasureSpec.EXACTLY)
        )
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
        // Measure and layout the thumbnails bottom up, since the primary is on the visual left
        // (portrait bottom) and secondary is on the right (portrait top)
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

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */
    override val upDownSwipeDirection: SingleAxisSwipeDetector.Direction =
        SingleAxisSwipeDetector.HORIZONTAL

    override fun getUpDirection(isRtl: Boolean): Int =
        if (isRtl) SingleAxisSwipeDetector.DIRECTION_POSITIVE
        else SingleAxisSwipeDetector.DIRECTION_NEGATIVE

    override fun isGoingUp(displacement: Float, isRtl: Boolean): Boolean =
        if (isRtl) displacement > 0 else displacement < 0

    override fun getTaskDragDisplacementFactor(isRtl: Boolean): Int = if (isRtl) -1 else 1
    /* -------------------- */

    override fun getSplitIconsPosition(
        taskIconHeight: Int,
        primarySnapshotHeight: Int,
        totalThumbnailHeight: Int,
        isRtl: Boolean,
        overviewTaskMarginPx: Int,
        dividerSize: Int,
    ): SplitIconPositions {
        return if (Flags.enableOverviewIconMenu()) {
            if (isRtl) {
                SplitIconPositions(
                    topLeftY = totalThumbnailHeight - primarySnapshotHeight,
                    bottomRightY = 0
                )
            } else {
                SplitIconPositions(
                    topLeftY = 0,
                    bottomRightY = -(primarySnapshotHeight + dividerSize)
                )
            }
        } else {
            // In seascape, the icons are initially placed at the bottom start of the
            // display (portrait locked). The values defined here are used to translate the icons
            // from the bottom to the almost-center of the screen using the bottom margin.
            // The primary snapshot is placed at the bottom, thus we translate the icons using
            // the size of the primary snapshot minus the icon size for the top-left icon.
            SplitIconPositions(
                topLeftY = primarySnapshotHeight - taskIconHeight,
                bottomRightY = primarySnapshotHeight + dividerSize
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
    override fun updateSplitIconsPosition(iconView: View, translationY: Int, isRtl: Boolean) {
        val layoutParams = iconView.layoutParams as FrameLayout.LayoutParams

        if (Flags.enableOverviewIconMenu()) {
            val appChipView = iconView as IconAppChipView
            layoutParams.gravity =
                if (isRtl) Gravity.TOP or Gravity.END else Gravity.BOTTOM or Gravity.START
            appChipView.layoutParams = layoutParams
            appChipView.setSplitTranslationX(0f)
            appChipView.setSplitTranslationY(translationY.toFloat())
        } else {
            layoutParams.gravity = Gravity.BOTTOM or Gravity.LEFT
            iconView.translationX = 0f
            iconView.translationY = 0f
            layoutParams.bottomMargin = translationY
            iconView.layoutParams = layoutParams
        }
    }

    @Override
    override fun getHandlerTypeForLogging(): LauncherAtom.TaskSwitcherContainer.OrientationHandler =
        LauncherAtom.TaskSwitcherContainer.OrientationHandler.SEASCAPE
}
