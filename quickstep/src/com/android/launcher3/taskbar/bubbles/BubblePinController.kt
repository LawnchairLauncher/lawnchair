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

package com.android.launcher3.taskbar.bubbles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.view.Gravity.BOTTOM
import android.view.Gravity.LEFT
import android.view.Gravity.RIGHT
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.android.launcher3.R
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.wm.shell.shared.bubbles.BaseBubblePinController
import com.android.wm.shell.shared.bubbles.BubbleBarLocation

/** Controller to manage pinning bubble bar to left or right when dragging starts from a bubble */
class BubblePinController(
    private val context: Context,
    private val container: FrameLayout,
    screenSizeProvider: () -> Point
) : BaseBubblePinController(screenSizeProvider) {

    var dropTargetSize: Point? = null

    private lateinit var bubbleBarViewController: BubbleBarViewController
    private lateinit var bubbleStashController: BubbleStashController
    private var exclRectWidth: Float = 0f
    private var exclRectHeight: Float = 0f

    private var dropTargetView: View? = null
    // Fallback width and height in case shell has not sent the size over
    private var dropTargetDefaultWidth: Int = 0
    private var dropTargetDefaultHeight: Int = 0
    private var dropTargetMargin: Int = 0

    fun init(bubbleControllers: BubbleControllers) {
        bubbleBarViewController = bubbleControllers.bubbleBarViewController
        bubbleStashController = bubbleControllers.bubbleStashController
        exclRectWidth = context.resources.getDimension(R.dimen.bubblebar_dismiss_zone_width)
        exclRectHeight = context.resources.getDimension(R.dimen.bubblebar_dismiss_zone_height)
        dropTargetDefaultWidth =
            context.resources.getDimensionPixelSize(
                R.dimen.bubble_expanded_view_drop_target_default_width
            )
        dropTargetDefaultHeight =
            context.resources.getDimensionPixelSize(
                R.dimen.bubble_expanded_view_drop_target_default_height
            )
        dropTargetMargin =
            context.resources.getDimensionPixelSize(R.dimen.bubble_expanded_view_drop_target_margin)
    }

    override fun getExclusionRectWidth(): Float {
        return exclRectWidth
    }

    override fun getExclusionRectHeight(): Float {
        return exclRectHeight
    }

    override fun getDropTargetView(): View? {
        return dropTargetView
    }

    override fun removeDropTargetView(view: View) {
        container.removeView(view)
        dropTargetView = null
    }

    override fun createDropTargetView(): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.bubble_expanded_view_drop_target, container, false)
            .also { view ->
                dropTargetView = view
                container.addView(view)
            }
    }

    @SuppressLint("RtlHardcoded")
    override fun updateLocation(location: BubbleBarLocation) {
        val onLeft = location.isOnLeft(container.isLayoutRtl)

        val bubbleBarBounds = bubbleBarViewController.bubbleBarBounds
        dropTargetView?.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = BOTTOM or (if (onLeft) LEFT else RIGHT)
            width = dropTargetSize?.x ?: dropTargetDefaultWidth
            height = dropTargetSize?.y ?: dropTargetDefaultHeight
            bottomMargin =
                -bubbleStashController.bubbleBarTranslationY.toInt() +
                    bubbleBarBounds.height() +
                    dropTargetMargin
        }
    }
}
