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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.android.wm.shell.Flags
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES
import com.android.wm.shell.util.KtProtoLog

/** Activity that sends a broadcast to open bubbles */
class ShowBubblesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Flags.enableRetrievableBubbles()) {
            val intent =
                Intent().apply {
                    action = BubbleShortcutHelper.ACTION_SHOW_BUBBLES
                    // Set the package as the receiver is not exported
                    `package` = packageName
                }
            KtProtoLog.v(WM_SHELL_BUBBLES, "Sending broadcast to show bubbles")
            sendBroadcast(intent)
        }
        finish()
    }

    companion object {
        /** Create intent to launch this activity */
        fun createIntent(context: Context): Intent {
            return Intent(context, ShowBubblesActivity::class.java).apply {
                action = BubbleShortcutHelper.ACTION_SHOW_BUBBLES
            }
        }

        /** Create component for this activity */
        fun createComponent(context: Context): ComponentName {
            return ComponentName(context, ShowBubblesActivity::class.java)
        }
    }
}
