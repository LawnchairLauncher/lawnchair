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

package com.android.quickstep.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable

class FloatingFullscreenAppPairBackground(
        context: Context,
        floatingView: FloatingAppPairView,
        private val iconToLaunch: Drawable,
        dividerPos: Int) :
        FloatingAppPairBackground(
                context,
                floatingView,
                iconToLaunch,
                null /*appIcon2*/,
                dividerPos
) {

    /** Animates the background as if launching a fullscreen task. */
    override fun draw(canvas: Canvas) {
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

        // Draw background
        drawCustomRoundedRect(
                canvas,
                RectF(0f, 0f, width, height),
                floatArrayOf(
                        cornerRadiusX,
                        cornerRadiusY,
                        cornerRadiusX,
                        cornerRadiusY,
                        cornerRadiusX,
                        cornerRadiusY,
                        cornerRadiusX,
                        cornerRadiusY,
                )
        )

        // Calculate changing measurements for icon.
        val changingIconSizeX =
                (STARTING_ICON_SIZE_PX +
                        ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                                expandXInterpolator.getInterpolation(progress))) / scaleFactorX
        val changingIconSizeY =
                (STARTING_ICON_SIZE_PX +
                        ((ENDING_ICON_SIZE_PX - STARTING_ICON_SIZE_PX) *
                                expandYInterpolator.getInterpolation(progress))) / scaleFactorY

        val changingIcon1Left = (width / 2f) - (changingIconSizeX / 2f)
        val changingIconTop = (height / 2f) - (changingIconSizeY / 2f)
        val changingIconScaleX = changingIconSizeX / iconToLaunch.bounds.width()
        val changingIconScaleY = changingIconSizeY / iconToLaunch.bounds.height()
        val changingIconAlpha =
                (255 - (255 * iconFadeInterpolator.getInterpolation(progress))).toInt()

        // Draw icon
        canvas.save()
        canvas.translate(changingIcon1Left, changingIconTop)
        canvas.scale(changingIconScaleX, changingIconScaleY)
        iconToLaunch.alpha = changingIconAlpha
        iconToLaunch.draw(canvas)
        canvas.restore()
    }
}