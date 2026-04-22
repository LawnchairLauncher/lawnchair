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

package com.android.wm.shell.compatui.impl

import com.android.wm.shell.compatui.api.CompatUISpec

/**
 * Fake implementation for {@link ompatUISpec}
 */
class FakeCompatUISpec(
    val name: String,
    val lifecycle: FakeCompatUILifecyclePredicates = FakeCompatUILifecyclePredicates(),
    val layout: FakeCompatUILayout
) {
    fun getSpec(): CompatUISpec = CompatUISpec(
        name = name,
        log = {str -> android.util.Log.d("COMPAT_UI_TEST", str)},
        lifecycle = lifecycle.getLifecycle(),
        layout = layout.getLayout()
    )
}
