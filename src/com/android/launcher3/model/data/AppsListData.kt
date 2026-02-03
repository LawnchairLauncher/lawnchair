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

package com.android.launcher3.model.data

import com.android.launcher3.util.PackageUserKey

/** Immutable representation of all-apps list data */
class AppsListData(
    val apps: Array<AppInfo>,
    // Current model flags
    val flags: Int,
) {

    val packageUserKeyToUidMap: Map<PackageUserKey, Int> =
        apps.associateBy(
            keySelector = { PackageUserKey(it.targetComponent.packageName, it.user) },
            valueTransform = { it.uid },
        )

    companion object {
        // If the launcher has permission to access deep shortcuts.
        const val FLAG_HAS_SHORTCUT_PERMISSION: Int = 1 shl 0

        // If quiet mode is enabled for any user
        const val FLAG_QUIET_MODE_ENABLED: Int = 1 shl 1

        // If launcher can change quiet mode
        const val FLAG_QUIET_MODE_CHANGE_PERMISSION: Int = 1 shl 2

        // If quiet mode is enabled for work profile user
        const val FLAG_WORK_PROFILE_QUIET_MODE_ENABLED: Int = 1 shl 3

        // If quiet mode is enabled for private profile user
        const val FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED: Int = 1 shl 4
    }
}
