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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.views.BaseDragLayer

/**
 * A temporary View that is created for the app pair launch animation and destroyed at the end.
 * Matches the size & position of the app pair icon graphic, and expands to full screen.
 */
class FloatingAppPairView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    companion object {
        fun getFloatingAppPairView(
            launcher: StatefulActivity<*>,
            originalView: View,
            appIcon1: Drawable?,
            appIcon2: Drawable?,
            dividerPos: Int
        ): FloatingAppPairView {
            val dragLayer: ViewGroup = launcher.getDragLayer()
            val floatingView =
                launcher
                    .getLayoutInflater()
                    .inflate(R.layout.floating_app_pair_view, dragLayer, false)
                    as FloatingAppPairView
            floatingView.init(launcher, originalView, appIcon1, appIcon2, dividerPos)
            dragLayer.addView(floatingView, dragLayer.childCount - 1)
            return floatingView
        }
    }

    val startingPosition = RectF()
    private lateinit var background: FloatingAppPairBackground
    var progress = 0f

    /** Initializes the view, copying the bounds and location of the original icon view. */
    fun init(
        launcher: StatefulActivity<*>,
        originalView: View,
        appIcon1: Drawable?,
        appIcon2: Drawable?,
        dividerPos: Int
    ) {
        val viewBounds = Rect(0, 0, originalView.width, originalView.height)
        Utilities.getBoundsForViewInDragLayer(
            launcher.getDragLayer(),
            originalView,
            viewBounds,
            false /* ignoreTransform */,
            null /* recycle */,
            startingPosition
        )
        val lp =
            BaseDragLayer.LayoutParams(
                Math.round(startingPosition.width()),
                Math.round(startingPosition.height())
            )
        lp.ignoreInsets = true

        // Position the floating view exactly on top of the original
        lp.topMargin = Math.round(startingPosition.top)
        lp.leftMargin = Math.round(startingPosition.left)

        layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin + lp.height)
        layoutParams = lp

        // Prepare to draw app pair icon background
        background = if (appIcon1 == null || appIcon2 == null) {
            val iconToAnimate = appIcon1 ?: appIcon2
            checkNotNull(iconToAnimate)
            FloatingFullscreenAppPairBackground(context, this, iconToAnimate,
                    dividerPos)
        } else {
            FloatingAppPairBackground(context, this, appIcon1, appIcon2, dividerPos)
        }
        background.setBounds(0, 0, lp.width, lp.height)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        background.draw(canvas)
    }
}
