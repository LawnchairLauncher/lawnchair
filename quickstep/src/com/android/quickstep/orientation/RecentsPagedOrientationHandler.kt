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

import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.util.FloatProperty
import android.util.Pair
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.touch.PagedOrientationHandler
import com.android.launcher3.touch.PagedOrientationHandler.Float2DAction
import com.android.launcher3.touch.PagedOrientationHandler.Int2DAction
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.views.IconAppChipView

/**
 * Abstraction layer to separate horizontal and vertical specific implementations for
 * [com.android.quickstep.views.RecentsView]. Majority of these implementations are (should be) as
 * simple as choosing the correct X and Y analogous methods.
 */
interface RecentsPagedOrientationHandler : PagedOrientationHandler {
    fun <T> setSecondary(target: T, action: Float2DAction<T>, param: Float)

    operator fun <T> set(target: T, action: Int2DAction<T>, primaryParam: Int, secondaryParam: Int)

    fun getPrimarySize(view: View): Int

    fun getPrimarySize(rect: RectF): Float

    val secondaryTranslationDirectionFactor: Int

    val degreesRotated: Float

    val rotation: Int

    val isLayoutNaturalToLauncher: Boolean

    fun <T> getPrimaryValue(x: T, y: T): T

    fun <T> getSecondaryValue(x: T, y: T): T

    fun setPrimaryScale(view: View, scale: Float)

    fun setSecondaryScale(view: View, scale: Float)

    fun getStart(rect: RectF): Float

    fun getEnd(rect: RectF): Float

    fun getClearAllSidePadding(view: View, isRtl: Boolean): Int

    fun getSecondaryDimension(view: View): Int

    val primaryViewTranslate: FloatProperty<View>
    val secondaryViewTranslate: FloatProperty<View>

    fun getSplitTranslationDirectionFactor(
        @StagePosition stagePosition: Int,
        deviceProfile: DeviceProfile
    ): Int

    fun <T> getSplitSelectTaskOffset(
        primary: FloatProperty<T>,
        secondary: FloatProperty<T>,
        deviceProfile: DeviceProfile
    ): Pair<FloatProperty<T>, FloatProperty<T>>

    fun getDistanceToBottomOfRect(dp: DeviceProfile, rect: Rect): Int

    fun getSplitPositionOptions(dp: DeviceProfile): List<SplitPositionOption>

    /** @param placeholderHeight height of placeholder view in portrait, width in landscape */
    fun getInitialSplitPlaceholderBounds(
        placeholderHeight: Int,
        placeholderInset: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out: Rect
    )

    /**
     * Centers an icon in the split staging area, accounting for insets.
     *
     * @param out The icon that needs to be centered.
     * @param onScreenRectCenterX The x-center of the on-screen staging area (most of the Rect is
     *   offscreen).
     * @param onScreenRectCenterY The y-center of the on-screen staging area (most of the Rect is
     *   offscreen).
     * @param fullscreenScaleX A x-scaling factor used to convert coordinates back into pixels.
     * @param fullscreenScaleY A y-scaling factor used to convert coordinates back into pixels.
     * @param drawableWidth The icon's drawable (final) width.
     * @param drawableHeight The icon's drawable (final) height.
     * @param dp The device profile, used to report rotation and hardware insets.
     * @param stagePosition 0 if the staging area is pinned to top/left, 1 for bottom/right.
     */
    fun updateSplitIconParams(
        out: View,
        onScreenRectCenterX: Float,
        onScreenRectCenterY: Float,
        fullscreenScaleX: Float,
        fullscreenScaleY: Float,
        drawableWidth: Int,
        drawableHeight: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int
    )

    /**
     * Sets positioning and rotation for a SplitInstructionsView.
     *
     * @param out The SplitInstructionsView that needs to be positioned.
     * @param dp The device profile, used to report rotation and device type.
     * @param splitInstructionsHeight The SplitInstructionView's height.
     * @param splitInstructionsWidth The SplitInstructionView's width.
     */
    fun setSplitInstructionsParams(
        out: View,
        dp: DeviceProfile,
        splitInstructionsHeight: Int,
        splitInstructionsWidth: Int
    )

    /**
     * @param splitDividerSize height of split screen drag handle in portrait, width in landscape
     * @param stagePosition the split position option (top/left, bottom/right) of the first task
     *   selected for entering split
     * @param out1 the bounds for where the first selected app will be
     * @param out2 the bounds for where the second selected app will be, complimentary to {@param
     *   out1} based on {@param initialSplitOption}
     */
    fun getFinalSplitPlaceholderBounds(
        splitDividerSize: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out1: Rect,
        out2: Rect
    )

    fun getDefaultSplitPosition(deviceProfile: DeviceProfile): Int

    /**
     * @param outRect This is expected to be the rect that has the dimensions for a non-split,
     *   fullscreen task in overview. This will directly be modified.
     * @param desiredStagePosition Which stage position (topLeft/rightBottom) we want to resize
     *   outRect for
     */
    fun setSplitTaskSwipeRect(
        dp: DeviceProfile,
        outRect: Rect,
        splitInfo: SplitConfigurationOptions.SplitBounds,
        @StagePosition desiredStagePosition: Int
    )

    fun measureGroupedTaskViewThumbnailBounds(
        primarySnapshot: View,
        secondarySnapshot: View,
        parentWidth: Int,
        parentHeight: Int,
        splitBoundsConfig: SplitConfigurationOptions.SplitBounds,
        dp: DeviceProfile,
        isRtl: Boolean
    )

    /**
     * Creates two Points representing the dimensions of the two tasks in a GroupedTaskView
     *
     * @return first -> primary task snapshot, second -> secondary task snapshot. x -> width, y ->
     *   height
     */
    fun getGroupedTaskViewSizes(
        dp: DeviceProfile,
        splitBoundsConfig: SplitConfigurationOptions.SplitBounds,
        parentWidth: Int,
        parentHeight: Int
    ): Pair<Point, Point>
    // Overview TaskMenuView methods
    /** Sets layout params on a task's app icon. Only use this when app chip is disabled. */
    fun setTaskIconParams(
        iconParams: FrameLayout.LayoutParams,
        taskIconMargin: Int,
        taskIconHeight: Int,
        thumbnailTopMargin: Int,
        isRtl: Boolean
    )

    /**
     * Sets layout params on the children of an app chip. Only use this when app chip is enabled.
     */
    fun setIconAppChipChildrenParams(
        iconParams: FrameLayout.LayoutParams,
        chipChildMarginStart: Int
    )

    fun setIconAppChipMenuParams(
        iconAppChipView: IconAppChipView,
        iconMenuParams: FrameLayout.LayoutParams,
        iconMenuMargin: Int,
        thumbnailTopMargin: Int
    )

    fun setSplitIconParams(
        primaryIconView: View,
        secondaryIconView: View,
        taskIconHeight: Int,
        primarySnapshotWidth: Int,
        primarySnapshotHeight: Int,
        groupedTaskViewHeight: Int,
        groupedTaskViewWidth: Int,
        isRtl: Boolean,
        deviceProfile: DeviceProfile,
        splitConfig: SplitConfigurationOptions.SplitBounds
    )

    /*
     * The following two methods try to center the TaskMenuView in landscape by finding the center
     * of the thumbnail view and then subtracting half of the taskMenu width. In this case, the
     * taskMenu width is the same size as the thumbnail width (what got set below in
     * getTaskMenuWidth()), so we directly use that in the calculations.
     */
    fun getTaskMenuX(
        x: Float,
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float

    fun getTaskMenuY(
        y: Float,
        thumbnailView: View,
        stagePosition: Int,
        taskMenuView: View,
        taskInsetMargin: Float,
        taskViewIcon: View
    ): Float

    fun getTaskMenuWidth(
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        @StagePosition stagePosition: Int
    ): Int

    fun getTaskMenuHeight(
        taskInsetMargin: Float,
        deviceProfile: DeviceProfile,
        taskMenuX: Float,
        taskMenuY: Float
    ): Int

    /**
     * Sets linear layout orientation for [com.android.launcher3.popup.SystemShortcut] items inside
     * task menu view.
     */
    fun setTaskOptionsMenuLayoutOrientation(
        deviceProfile: DeviceProfile,
        taskMenuLayout: LinearLayout,
        dividerSpacing: Int,
        dividerDrawable: ShapeDrawable
    )

    /**
     * Sets layout param attributes for [com.android.launcher3.popup.SystemShortcut] child views
     * inside task menu view.
     */
    fun setLayoutParamsForTaskMenuOptionItem(
        lp: LinearLayout.LayoutParams,
        viewGroup: LinearLayout,
        deviceProfile: DeviceProfile
    )

    /**
     * Calculates the position where a Digital Wellbeing Banner should be placed on its parent
     * TaskView.
     *
     * @return A Pair of Floats representing the proper x and y translations.
     */
    fun getDwbLayoutTranslations(
        taskViewWidth: Int,
        taskViewHeight: Int,
        splitBounds: SplitConfigurationOptions.SplitBounds?,
        deviceProfile: DeviceProfile,
        thumbnailViews: Array<View>,
        desiredTaskId: Int,
        banner: View
    ): Pair<Float, Float>
    // The following are only used by TaskViewTouchHandler.

    /** @return Either VERTICAL or HORIZONTAL. */
    val upDownSwipeDirection: SingleAxisSwipeDetector.Direction

    /** @return Given [.getUpDownSwipeDirection], whether POSITIVE or NEGATIVE is up. */
    fun getUpDirection(isRtl: Boolean): Int

    /** @return Whether the displacement is going towards the top of the screen. */
    fun isGoingUp(displacement: Float, isRtl: Boolean): Boolean

    /** @return Either 1 or -1, a factor to multiply by so the animation goes the correct way. */
    fun getTaskDragDisplacementFactor(isRtl: Boolean): Int

    /**
     * Maps the velocity from the coordinate plane of the foreground app to that of Launcher's
     * (which now will always be portrait)
     */
    fun adjustFloatingIconStartVelocity(velocity: PointF)

    /**
     * Ensures that outStartRect left bound is within the DeviceProfile's visual boundaries
     *
     * @param outStartRect The start rect that will directly be modified
     */
    fun fixBoundsForHomeAnimStartRect(outStartRect: RectF, deviceProfile: DeviceProfile)

    /**
     * Determine the target translation for animating the FloatingTaskView out. This value could
     * either be an x-coordinate or a y-coordinate, depending on which way the FloatingTaskView was
     * docked.
     *
     * @param floatingTask The FloatingTaskView.
     * @param onScreenRect The current on-screen dimensions of the FloatingTaskView.
     * @param stagePosition STAGE_POSITION_TOP_OR_LEFT or STAGE_POSITION_BOTTOM_OR_RIGHT.
     * @param dp The device profile.
     * @return A float. When an animation translates the FloatingTaskView to this position, it will
     *   appear to tuck away off the edge of the screen.
     */
    fun getFloatingTaskOffscreenTranslationTarget(
        floatingTask: View,
        onScreenRect: RectF,
        @StagePosition stagePosition: Int,
        dp: DeviceProfile
    ): Float

    /**
     * Sets the translation of a FloatingTaskView along its "slide-in/slide-out" axis (could be
     * either x or y), depending on how the view is oriented.
     *
     * @param floatingTask The FloatingTaskView to be translated.
     * @param translation The target translation value.
     * @param dp The current device profile.
     */
    fun setFloatingTaskPrimaryTranslation(floatingTask: View, translation: Float, dp: DeviceProfile)

    /**
     * Gets the translation of a FloatingTaskView along its "slide-in/slide-out" axis (could be
     * either x or y), depending on how the view is oriented.
     *
     * @param floatingTask The FloatingTaskView in question.
     * @param dp The current device profile.
     * @return The current translation value.
     */
    fun getFloatingTaskPrimaryTranslation(floatingTask: View, dp: DeviceProfile): Float

    fun getHandlerTypeForLogging(): LauncherAtom.TaskSwitcherContainer.OrientationHandler

    companion object {
        @JvmField val PORTRAIT: RecentsPagedOrientationHandler = PortraitPagedViewHandler()
        @JvmField val LANDSCAPE: RecentsPagedOrientationHandler = LandscapePagedViewHandler()
        @JvmField val SEASCAPE: RecentsPagedOrientationHandler = SeascapePagedViewHandler()
    }
}
