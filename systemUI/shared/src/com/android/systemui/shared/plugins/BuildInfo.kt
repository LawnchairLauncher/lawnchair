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
package com.android.systemui.shared.plugins

import android.os.Build

enum class BuildVariant {
    Eng,
    UserDebug,
    User;

    companion object {
        // LC: Ignore other variants
        val CURRENT = User
    }
}

data class BuildInfo(val variant: BuildVariant, val isDebuggable: Boolean) {
    val isEng = variant == BuildVariant.Eng

    companion object {
        val CURRENT = BuildInfo(BuildVariant.CURRENT, Build.IS_DEBUGGABLE)
    }
}
