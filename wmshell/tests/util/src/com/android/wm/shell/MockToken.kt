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

package com.android.wm.shell

import android.os.IBinder
import android.window.IWindowContainerToken
import android.window.WindowContainerToken
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * [WindowContainerToken] wrapper that provides a mock token with a mock binder.
 *
 * This implementation provide backward compatibility for existing Java and Kotlin code
 * while encouraging a more idiomatic, static-style access for new Kotlin code.
 *
 * - **Java (legacy):** `new MockToken().token()`
 * - **Kotlin (legacy):** `MockToken().token()`
 * - **Kotlin (modern):** `MockToken.token()`
 */
class MockToken {
    /** A mocked token instance for legacy, instance-based access. */
    private val token = createMockToken()

    /** Returns the mocked [WindowContainerToken]. */
    fun token(): WindowContainerToken = this.token

    companion object {
        /**
         * Returns a new mocked [WindowContainerToken].
         * This is the recommended accessor for modern code.
         * It can be called from Kotlin as `MockToken.token()`.
         */
        fun token(): WindowContainerToken = createMockToken()

        /** Creates a mock [WindowContainerToken] backed by a mocked binder interface. */
        private fun createMockToken() = WindowContainerToken(mock<IWindowContainerToken> {
            on { asBinder() } doReturn mock<IBinder>()
        })
    }
}
