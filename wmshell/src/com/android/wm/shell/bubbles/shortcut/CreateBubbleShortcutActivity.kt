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

import android.app.Activity
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES

/** Activity to create a shortcut to open bubbles */
class CreateBubbleShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Flags.enableRetrievableBubbles()) {
            ProtoLog.d(WM_SHELL_BUBBLES, "Creating a shortcut for bubbles")
            createShortcut()
        }
        finish()
    }

    private fun createShortcut() {
        val icon = Icon.createWithResource(this, R.drawable.ic_bubbles_shortcut_widget)
        // TODO(b/340337839): shortcut shows the sysui icon
        val shortcutInfo = BubbleShortcutHelper.createShortcut(this, icon)
        val shortcutManager = getSystemService(ShortcutManager::class.java)
        val shortcutIntent = shortcutManager?.createShortcutResultIntent(shortcutInfo)
        if (shortcutIntent != null) {
            setResult(RESULT_OK, shortcutIntent)
        } else {
            setResult(RESULT_CANCELED)
        }
    }
}
