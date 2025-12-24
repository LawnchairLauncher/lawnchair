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

package com.android.launcher3.util

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

/** A wrapper around Settings.Secure for easier testing */
class SecureStringObserver(
    private val context: Context,
    private val handler: Handler,
    private val key: String,
) : ContentObserver(handler) {

    var callback: Runnable = Runnable {}

    init {
        handler.post {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(key),
                true /* notifyForDescendants */,
                this,
            )
        }
    }

    fun getValue(): String? = Settings.Secure.getString(context.contentResolver, key)

    override fun onChange(selfChange: Boolean) {
        callback.run()
    }

    fun close() {
        handler.post { context.contentResolver.unregisterContentObserver(this) }
    }
}
