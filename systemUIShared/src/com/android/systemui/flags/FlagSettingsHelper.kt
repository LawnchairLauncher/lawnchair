/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.flags

import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.Settings

class FlagSettingsHelper(private val contentResolver: ContentResolver) {

    fun getStringFromSecure(key: String): String? = Settings.Secure.getString(contentResolver, key)

    fun getString(key: String): String? = Settings.Global.getString(contentResolver, key)

    fun registerContentObserver(
        name: String,
        notifyForDescendants: Boolean,
        observer: ContentObserver
    ) {
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(name),
            notifyForDescendants,
            observer
        )
    }

    fun unregisterContentObserver(observer: ContentObserver) {
        contentResolver.unregisterContentObserver(observer)
    }
}