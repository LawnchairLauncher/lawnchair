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

package com.android.launcher3.taskbar.bubbles.testing

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.PathParser
import android.view.LayoutInflater
import android.view.ViewGroup
import com.android.launcher3.R
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.wm.shell.shared.bubbles.BubbleInfo

object FakeBubbleViewFactory {

    /** Inflates a [BubbleView] and adds it to the [parent] view if it is present. */
    fun createBubble(
        context: Context,
        key: String,
        parent: ViewGroup?,
        iconSize: Int = 50,
        iconColor: Int,
        badgeColor: Int = Color.RED,
        dotColor: Int = Color.BLUE,
        suppressNotification: Boolean = false,
    ): BubbleView {
        val inflater = LayoutInflater.from(context)
        // BubbleView uses launcher's badge to icon ratio and expects the badge image to already
        // have the right size
        val badgeToIconRatio = 0.444f
        val badgeRadius = iconSize * badgeToIconRatio / 2
        val icon = createCircleBitmap(radius = iconSize / 2, color = iconColor)
        val badge = createCircleBitmap(radius = badgeRadius.toInt(), color = badgeColor)

        val flags =
            if (suppressNotification) Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION else 0
        val bubbleInfo =
            BubbleInfo(
                key,
                flags,
                null,
                null,
                0,
                context.packageName,
                null,
                null,
                false,
                true,
                null,
            )
        val bubbleView = inflater.inflate(R.layout.bubblebar_item_view, parent, false) as BubbleView
        val dotPath =
            PathParser.createPathFromPathData(
                context.resources.getString(com.android.internal.R.string.config_icon_mask)
            )
        val bubble =
            BubbleBarBubble(
                bubbleInfo,
                bubbleView,
                badge,
                icon,
                dotColor,
                dotPath,
                "test app",
                null,
            )
        bubbleView.setBubble(bubble)
        return bubbleView
    }

    private fun createCircleBitmap(radius: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawARGB(0, 0, 0, 0)
        val paint = Paint()
        paint.color = color
        canvas.drawCircle(radius.toFloat(), radius.toFloat(), radius.toFloat(), paint)
        return bitmap
    }
}
