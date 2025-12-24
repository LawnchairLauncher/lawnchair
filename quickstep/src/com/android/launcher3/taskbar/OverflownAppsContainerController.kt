/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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
package com.android.launcher3.taskbar

import com.android.launcher3.model.data.ItemInfo

/**
 * Controller of the container that contains the overflown pinned apps which don't fit in the main
 * taskbar. This is activated by toggling the overflow icon on the taskbar.
 */
class OverflownAppsContainerController(private val activityContext: TaskbarActivityContext) {
    private var overflownAppsViewController: OverflownAppsViewController? = null
    private lateinit var viewCallbacks: TaskbarViewCallbacks

    fun init(callbacks: TaskbarViewCallbacks) {
        viewCallbacks = callbacks
    }

    fun toggleOverflownAppsView(
        overflowIcon: TaskbarOverflowView,
        overflownApps: List<ItemInfo>,
        onClosed: Runnable,
    ) {
        overflownAppsViewController?.let {
            return it.close(true)
        }

        overflownAppsViewController =
            OverflownAppsViewController(activityContext, viewCallbacks, overflowIcon) {
                overflownAppsViewController = null
                onClosed.run()
            }
        overflownAppsViewController?.show(overflownApps)
    }
}
