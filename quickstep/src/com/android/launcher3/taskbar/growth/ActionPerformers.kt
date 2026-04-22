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
package com.android.launcher3.taskbar.growth

import android.content.Context
import android.content.Intent
import android.net.Uri

object ActionPerformers {
    fun interface DismissCallback {
        fun invoke()
    }

    fun performActions(actions: List<Action>, context: Context, dismissCallback: DismissCallback) {
        for (action in actions) {
            performAction(action, context, dismissCallback)
        }
    }

    private fun performAction(action: Action, context: Context, dismissCallback: DismissCallback) {
        when (action) {
            is Action.Dismiss -> {
                // TODO: b/396239267 - Handle marking the campaign dismissed with dismissal
                //  retention.
                dismissCallback.invoke()
            }
            is Action.OpenUrl -> openUrl(action.url, context)
        // Handle other actions
        }
    }

    fun openUrl(url: String, context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
