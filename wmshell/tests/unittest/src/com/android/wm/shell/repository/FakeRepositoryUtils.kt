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

package com.android.wm.shell.repository

// Key for testing
data class FakeKey(val id1: Int, val id2: String)

// Item for testing
data class FakeItem(val value1: Int, val value2: String)

// Fake Factory for the Item for testing
class FakeItemFactory(val value1: Int, val value2: String) : () -> FakeItem {

    var fakeFactoryInvokedTimes = 0

    override fun invoke(): FakeItem = FakeItem(value1, value2).also {
        fakeFactoryInvokedTimes++
    }
}
