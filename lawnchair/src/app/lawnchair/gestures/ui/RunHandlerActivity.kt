/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.gestures.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.lawnchair.LawnchairLauncher

class RunHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == LawnchairShortcutActivity.START_ACTION) {
            startActivity(
                Intent(this, LawnchairLauncher::class.java).apply {
                    action = LawnchairShortcutActivity.START_ACTION
                    putExtra(
                        LawnchairShortcutActivity.EXTRA_HANDLER,
                        intent.getStringExtra(LawnchairShortcutActivity.EXTRA_HANDLER),
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }
        finish()
    }
}
