/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Process
import android.view.ViewGroup
import com.android.wm.shell.shared.bubbles.BubbleInfo

object FakeBubbleViewFactory {

    /** Inflates a [BubbleView] and adds it to the [parent] view if it is present. */
    fun createBubble(
        context: Context,
        key: String,
        parent: ViewGroup?,
        iconColor: Int,
        badgeColor: Int = Color.RED,
        suppressNotification: Boolean = false,
    ): BubbleView {
        val flags =
            if (suppressNotification) Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION else 0
        val bubbleInfo =
            BubbleInfo(
                key,
                flags,
                "non-existent-bubble-shortcut-id",
                Icon.createWithAdaptiveBitmap(
                    Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(iconColor)
                    }
                ),
                Process.myUserHandle().identifier,
                context.packageName,
                "test app",
                "test app",
                false,
                true,
                null,
            )
        return BubbleCreator(context)
            .populateBubble(
                context,
                bubbleInfo,
                AdaptiveIconDrawable(ColorDrawable(badgeColor), null),
                bubbleInfo.appName,
                parent,
                null,
            )!!
            .view
    }
}
