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
package com.android.launcher3.taskbar

import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo

/**
 * Handles initialization of the [OverflownAppsContainerView] and supplies it with the list of apps.
 */
class OverflownAppsViewController(
    private val activityContext: TaskbarActivityContext,
    viewCallbacks: TaskbarViewCallbacks,
    overflowIcon: TaskbarOverflowView,
    onClose: Runnable,
) {
    private val overflownAppsContainerView =
        activityContext.layoutInflater.inflate(
            R.layout.overflown_apps_container_view,
            activityContext.dragLayer,
            /* attachToRoot= */ false,
        ) as OverflownAppsContainerView<*>

    init {
        overflownAppsContainerView.init(overflowIcon, viewCallbacks)
        overflownAppsContainerView.addOnCloseCallback(onClose)
    }

    fun show(overflownApps: List<ItemInfo>) {
        activityContext.isTaskbarWindowFullscreen = true
        activityContext.dragLayer.post {
            overflownAppsContainerView.setOverflownApps(overflownApps)
            overflownAppsContainerView.show()
        }
    }

    fun close(animate: Boolean) = overflownAppsContainerView.close(animate)
}
