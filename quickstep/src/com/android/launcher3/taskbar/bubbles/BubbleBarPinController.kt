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
import com.android.launcher3.taskbar.bubbles.BubbleBarController.BubbleBarLocationListener
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.wm.shell.shared.bubbles.BaseBubblePinController
import com.android.wm.shell.shared.bubbles.BubbleBarLocation

/**
 * Controller to manage pinning bubble bar to left or right when dragging starts from the bubble bar
 */
class BubbleBarPinController(
    private val context: Context,
    private val container: FrameLayout,
    screenSizeProvider: () -> Point
) : BaseBubblePinController(screenSizeProvider) {

    private lateinit var bubbleBarViewController: BubbleBarViewController
    private lateinit var bubbleStashController: BubbleStashController
    private lateinit var bubbleBarLocationListener: BubbleBarLocationListener
    private var exclRectWidth: Float = 0f
    private var exclRectHeight: Float = 0f

    private var dropTargetView: View? = null

    fun init(
        bubbleControllers: BubbleControllers,
        bubbleBarLocationListener: BubbleBarLocationListener
    ) {
        this.bubbleBarLocationListener = bubbleBarLocationListener
        bubbleBarViewController = bubbleControllers.bubbleBarViewController
        bubbleStashController = bubbleControllers.bubbleStashController
        exclRectWidth = context.resources.getDimension(R.dimen.bubblebar_dismiss_zone_width)
        exclRectHeight = context.resources.getDimension(R.dimen.bubblebar_dismiss_zone_height)
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
            .inflate(R.layout.bubble_bar_drop_target, container, false)
            .also { view ->
                dropTargetView = view
                container.addView(view)
            }
    }

    @SuppressLint("RtlHardcoded")
    override fun updateLocation(location: BubbleBarLocation) {
        val onLeft = location.isOnLeft(container.isLayoutRtl)

        val bounds = bubbleBarViewController.bubbleBarBounds
        val horizontalMargin = bubbleBarViewController.horizontalMargin
        bubbleBarLocationListener.onBubbleBarLocationAnimated(location)
        dropTargetView?.updateLayoutParams<FrameLayout.LayoutParams> {
            width = bounds.width()
            height = bounds.height()
            gravity = BOTTOM or (if (onLeft) LEFT else RIGHT)
            leftMargin = horizontalMargin
            rightMargin = horizontalMargin
            bottomMargin = -bubbleStashController.bubbleBarTranslationY.toInt()
        }
    }
}
