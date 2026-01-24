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
    // Whether the current RecentsView state supports task overlays.
    // TODO(b/331753115): Derive from RecentsView state flow once migrated to MVVM.
    val overlayEnabled = MutableStateFlow(false)

    // The settled set of visible taskIds that is updated after RecentsView scroll settles.
    val settledFullyVisibleTaskIds = MutableStateFlow(emptySet<Int>())

    // The id for the task ids in the TaskView that controls the Actions View
    val centralTaskIds = MutableStateFlow(emptySet<Int>())

    // A list of taskIds that are associated with a RecentsAnimationController. */
    val runningTaskIds = MutableStateFlow(emptySet<Int>())

    // Whether we should use static screenshot instead of live tile for taskIds in [runningTaskIds]
    val runningTaskShowScreenshot = MutableStateFlow(false)
}
