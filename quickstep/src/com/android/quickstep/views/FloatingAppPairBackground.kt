/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.quickstep.util.AnimUtils
import com.android.systemui.shared.system.QuickStepContract

/**
 * A Drawable that is drawn onto [FloatingAppPairView] every frame during the app pair launch
 * animation. Consists of a rectangular background that splits into two, and two app icons that
 * increase in size during the animation.
 */
class FloatingAppPairBackground(
    context: Context,
    private val floatingView: FloatingAppPairView, // the view that we will draw this background on
    private val appIcon1: Drawable,
    private val appIcon2: Drawable,
    dividerPos: Int
) : Drawable() {
    companion object {
        // Design specs -- app icons start small and expand during the animation
        private val STARTING_ICON_SIZE_PX = Utilities.dpToPx(22f)
        private val ENDING_ICON_SIZE_PX = Utilities.dpToPx(66f)

        // Null values to use with drawDoubleRoundRect(), since there doesn't seem to be any other
        // API for drawing rectangles with 4 different corner radii.
        private val EMPTY_RECT = RectF()
        private val ARRAY_OF_ZEROES = FloatArray(8)
    }

    private val container: RecentsViewContainer
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Animation interpolators
    private val expandXInterpolator: Interpolator
    private val expandYInterpolator: Interpolator
    private val cellSplitInterpolator: Interpolator
    private val iconFadeInterpolator: Interpolator

    // Device-specific measurements
    private val deviceCornerRadius: Float
    private val deviceHalfDividerSize: Float
    private val desiredSplitRatio: Float

    init {
        container = RecentsViewContainer.containerFromContext(context)
        val dp = container.deviceProfile
        // Set up background paint color
        val ta = context.theme.obtainStyledAttributes(R.styleable.FolderIconPreview)
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.color = ta.getColor(R.styleable.FolderIconPreview_folderPreviewColor, 0)
        ta.recycle()
        // Set up timings and interpolators
        val timings = AnimUtils.getDeviceAppPairLaunchTimings(container.deviceProfile.isTablet)
        expandXInterpolator =
            Interpolators.clampToProgress(
                timings.getStagedRectScaleXInterpolator(),
                timings.stagedRectSlideStartOffset,
                timings.stagedRectSlideEndOffset
            )
        expandYInterpolator =
            Interpolators.clampToProgress(
                timings.getStagedRectScaleYInterpolator(),
                timings.stagedRectSlideStartOffset,
                timings.stagedRectSlideEndOffset
            )
        cellSplitInterpolator =
            Interpolators.clampToProgress(
                timings.cellSplitInterpolator,
                timings.cellSplitStartOffset,
                timings.cellSplitEndOffset
            )
        iconFadeInterpolator =
            Interpolators.clampToProgress(
                timings.iconFadeInterpolator,
                timings.iconFadeStartOffset,
                timings.iconFadeEndOffset
            )

        // Find device-specific measurements
        deviceCornerRadius = QuickStepContract.getWindowCornerRadius(container.asContext())
        deviceHalfDividerSize =
                container.asContext().resources.getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2f
        val dividerCenterPos = dividerPos + deviceHalfDividerSize
        desiredSplitRatio =
            if (dp.isLeftRightSplit) dividerCenterPos / dp.widthPx
            else dividerCenterPos / dp.heightPx
    }

    override fun draw(canvas: Canvas) {
        if (container.deviceProfile.isLeftRightSplit) {
            drawLeftRightSplit(canvas)
        } else {
            drawTopBottomSplit(canvas)
        }
    }

    /** When device is in landscape, we draw the rectangles with a left-right split. */
    private fun drawLeftRightSplit(canvas: Canvas) {
        val progress = floatingView.progress

        // Since the entire floating app pair surface is scaling up during this animation, we
        // scale down most of these drawn elements so that they appear the proper size on-screen.
        val scaleFactorX = floatingView.scaleX
        val scaleFactorY = floatingView.scaleY

        // Get the bounds where we will draw the background image
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // Get device-specific measurements
        val cornerRadiusX = deviceCornerRadius / scaleFactorX
        val cornerRadiusY = deviceCornerRadius / scaleFactorY
        val halfDividerSize = deviceHalfDividerSize / scaleFactorX

        // Calculate changing measurements for background
        // We add one pixel to some measurements to create a smooth edge with no gaps
        val onePixel = 1f / scaleFactorX
        val changingDividerSize =
            (cellSplitInterpolator.getInterpolation(progress) * halfDividerSize) - onePixel
        val changingInnerRadiusX = cellSplitInterpolator.getInterpolation(progress) * cornerRadiusX
        val changingInnerRadiusY = cellSplitInterpolator.getInterpolation(progress) * cornerRadiusY
        val dividerCenterPos = width * desiredSplitRatio

        // The left half of the background image
        val leftSide = RectF(0f, 0f, dividerCenterPos - changingDividerSize, height)
        // The right half of the background image
        val rightSide = RectF(dividerCenterPos + changingDividerSize, 0f, width, height)

        // Draw background
        drawCustomRoundedRect(
            canvas,
            leftSide,
            floatArrayOf(
                cornerRadiusX,
                cornerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY,
                cornerRadiusX,
                cornerRadiusY,
            )
        )
        drawCustomRoundedRect(
            canvas,
            rightSide,
            floatArrayOf(
                changingInnerRadiusX,
                changingInnerRadiusY,
                cornerRadiusX,
                cornerRadiusY,
                cornerRadiusX,
                cornerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY,
            )
        )

        // Calculate changing measurements for icons.
        val changingIconSizeX =
            (STARTING_ICON_SIZE_PX +
                ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                    expandXInterpolator.getInterpolation(progress))) / scaleFactorX
        val changingIconSizeY =
            (STARTING_ICON_SIZE_PX +
                ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                    expandYInterpolator.getInterpolation(progress))) / scaleFactorY

        val changingIcon1Left = ((width / 2f - halfDividerSize) / 2f) - (changingIconSizeX / 2f)
        val changingIcon2Left =
            (width - ((width / 2f - halfDividerSize) / 2f)) - (changingIconSizeX / 2f)
        val changingIconTop = (height / 2f) - (changingIconSizeY / 2f)
        val changingIconScaleX = changingIconSizeX / appIcon1.bounds.width()
        val changingIconScaleY = changingIconSizeY / appIcon1.bounds.height()
        val changingIconAlpha =
            (255 - (255 * iconFadeInterpolator.getInterpolation(progress))).toInt()

        // Draw first icon
        canvas.save()
        canvas.translate(changingIcon1Left, changingIconTop)
        canvas.scale(changingIconScaleX, changingIconScaleY)
        appIcon1.alpha = changingIconAlpha
        appIcon1.draw(canvas)
        canvas.restore()

        // Draw second icon
        canvas.save()
        canvas.translate(changingIcon2Left, changingIconTop)
        canvas.scale(changingIconScaleX, changingIconScaleY)
        appIcon2.alpha = changingIconAlpha
        appIcon2.draw(canvas)
        canvas.restore()
    }

    /** When device is in portrait, we draw the rectangles with a top-bottom split. */
    private fun drawTopBottomSplit(canvas: Canvas) {
        val progress = floatingView.progress

        // Since the entire floating app pair surface is scaling up during this animation, we
        // scale down most of these drawn elements so that they appear the proper size on-screen.
        val scaleFactorX = floatingView.scaleX
        val scaleFactorY = floatingView.scaleY

        // Get the bounds where we will draw the background image
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // Get device-specific measurements
        val cornerRadiusX = deviceCornerRadius / scaleFactorX
        val cornerRadiusY = deviceCornerRadius / scaleFactorY
        val halfDividerSize = deviceHalfDividerSize / scaleFactorY

        // Calculate changing measurements for background
        // We add one pixel to some measurements to create a smooth edge with no gaps
        val onePixel = 1f / scaleFactorY
        val changingDividerSize =
            (cellSplitInterpolator.getInterpolation(progress) * halfDividerSize) - onePixel
        val changingInnerRadiusX = cellSplitInterpolator.getInterpolation(progress) * cornerRadiusX
        val changingInnerRadiusY = cellSplitInterpolator.getInterpolation(progress) * cornerRadiusY
        val dividerCenterPos = height * desiredSplitRatio

        // The top half of the background image
        val topSide = RectF(0f, 0f, width, dividerCenterPos - changingDividerSize)
        // The bottom half of the background image
        val bottomSide = RectF(0f, dividerCenterPos + changingDividerSize, width, height)

        // Draw background
        drawCustomRoundedRect(
            canvas,
            topSide,
            floatArrayOf(
                cornerRadiusX,
                cornerRadiusY,
                cornerRadiusX,
                cornerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY
            )
        )
        drawCustomRoundedRect(
            canvas,
            bottomSide,
            floatArrayOf(
                changingInnerRadiusX,
                changingInnerRadiusY,
                changingInnerRadiusX,
                changingInnerRadiusY,
                cornerRadiusX,
                cornerRadiusY,
                cornerRadiusX,
                cornerRadiusY
            )
        )

        // Calculate changing measurements for icons.
        val changingIconSizeX =
            (STARTING_ICON_SIZE_PX +
                ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                    expandXInterpolator.getInterpolation(progress))) / scaleFactorX
        val changingIconSizeY =
            (STARTING_ICON_SIZE_PX +
                ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                    expandYInterpolator.getInterpolation(progress))) / scaleFactorY

        val changingIconLeft = (width / 2f) - (changingIconSizeX / 2f)
        val changingIcon1Top = (((height / 2f) - halfDividerSize) / 2f) - (changingIconSizeY / 2f)
        val changingIcon2Top =
            (height - (((height / 2f) - halfDividerSize) / 2f)) - (changingIconSizeY / 2f)
        val changingIconScaleX = changingIconSizeX / appIcon1.bounds.width()
        val changingIconScaleY = changingIconSizeY / appIcon1.bounds.height()
        val changingIconAlpha =
            (255 - 255 * iconFadeInterpolator.getInterpolation(progress)).toInt()

        // Draw first icon
        canvas.save()
        canvas.translate(changingIconLeft, changingIcon1Top)
        canvas.scale(changingIconScaleX, changingIconScaleY)
        appIcon1.alpha = changingIconAlpha
        appIcon1.draw(canvas)
        canvas.restore()

        // Draw second icon
        canvas.save()
        canvas.translate(changingIconLeft, changingIcon2Top)
        canvas.scale(changingIconScaleX, changingIconScaleY)
        appIcon2.alpha = changingIconAlpha
        appIcon2.draw(canvas)
        canvas.restore()
    }

    /**
     * Draws a rectangle with custom rounded corners.
     *
     * @param c The Canvas to draw on.
     * @param rect The bounds of the rectangle.
     * @param radii An array of 8 radii for the corners: top left x, top left y, top right x, top
     *   right y, bottom right x, and so on.
     */
    private fun drawCustomRoundedRect(c: Canvas, rect: RectF, radii: FloatArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Canvas.drawDoubleRoundRect is supported from Q onward
            c.drawDoubleRoundRect(rect, radii, EMPTY_RECT, ARRAY_OF_ZEROES, backgroundPaint)
        } else {
            // Fallback rectangle with uniform rounded corners
            val scaleFactorX = floatingView.scaleX
            val scaleFactorY = floatingView.scaleY
            val cornerRadiusX =
                QuickStepContract.getWindowCornerRadius(container.asContext()) / scaleFactorX
            val cornerRadiusY =
                QuickStepContract.getWindowCornerRadius(container.asContext()) / scaleFactorY
            c.drawRoundRect(rect, cornerRadiusX, cornerRadiusY, backgroundPaint)
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

    override fun setAlpha(i: Int) {
        // Required by Drawable but not used.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Required by Drawable but not used.
    }
}
