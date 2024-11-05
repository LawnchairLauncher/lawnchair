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

package com.android.wm.shell.bubbles.shortcut

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import com.android.wm.shell.R

/** Helper class for creating a shortcut to open bubbles */
object BubbleShortcutHelper {
    const val SHORTCUT_ID = "bubbles_shortcut_id"
    const val ACTION_SHOW_BUBBLES = "com.android.wm.shell.bubbles.action.SHOW_BUBBLES"

    /** Create a shortcut that launches [ShowBubblesActivity] */
    fun createShortcut(context: Context, icon: Icon): ShortcutInfo {
        return ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setIntent(ShowBubblesActivity.createIntent(context))
            .setActivity(ShowBubblesActivity.createComponent(context))
            .setShortLabel(context.getString(R.string.bubble_shortcut_label))
            .setLongLabel(context.getString(R.string.bubble_shortcut_long_label))
            .setLongLived(true)
            .setIcon(icon)
            .build()
    }
}
