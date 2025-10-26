/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins

import com.android.systemui.plugins.annotations.ProtectedInterface
import com.android.systemui.plugins.annotations.ProtectedReturn
import com.android.systemui.plugins.annotations.ProvidesInterface

@ProtectedInterface
@ProvidesInterface(action = TestPlugin.ACTION, version = TestPlugin.VERSION)
/** Interface intended for use in tests */
interface TestPlugin : Plugin {
    companion object {
        const val VERSION = 1

        const val ACTION = "testAction"
    }

    @ProtectedReturn("return new Object();")
    /** Test method, implemented by test */
    fun methodThrowsError(): Object
}
