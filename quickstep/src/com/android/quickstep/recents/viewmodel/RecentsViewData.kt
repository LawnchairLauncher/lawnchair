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

package com.android.quickstep.recents.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow

// This is far from complete but serves the purpose of enabling refactoring in other areas
class RecentsViewData {
    val fullscreenProgress = MutableStateFlow(1f)

    // This is typically a View concern but it is used to invalidate rendering in other Views
    val scale = MutableStateFlow(1f)
}
