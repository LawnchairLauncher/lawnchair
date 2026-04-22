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

package com.android.launcher3.util

import android.net.Uri
import com.android.launcher3.util.SettingsCache.OnChangeListener
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Provides [SettingsCache] sandboxed from system settings for testing. */
class SettingsCacheSandbox {
    private val values = mutableMapOf<Uri, Int>()
    private val listeners = mutableMapOf<Uri, MutableSet<OnChangeListener>>()

    /**
     * Fake cache that delegates:
     * - [SettingsCache.getValue] to [values]
     * - [SettingsCache.mListenerMap] to [listeners].
     */
    val cache =
        mock<SettingsCache> {
            on { getValue(any<Uri>()) } doAnswer { mock.getValue(it.getArgument(0), 1) }
            on { getValue(any<Uri>(), any<Int>()) } doAnswer
                {
                    values.getOrDefault(it.getArgument(0), it.getArgument(1)) == 1
                }

            doAnswer {
                    listeners.getOrPut(it.getArgument(0)) { mutableSetOf() }.add(it.getArgument(1))
                }
                .whenever(mock)
                .register(any(), any())
            doAnswer { listeners[it.getArgument(0)]?.remove(it.getArgument(1)) }
                .whenever(mock)
                .unregister(any(), any())
        }

    operator fun get(key: Uri): Int? = values[key]

    operator fun set(key: Uri, value: Int) {
        if (value == values[key]) return
        values[key] = value
        listeners[key]?.forEach { it.onSettingsChanged(value == 1) }
    }
}
