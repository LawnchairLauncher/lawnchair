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

package com.android.wm.shell.bubbles.appinfo

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleController
import javax.inject.Inject

/**
 * A concrete implementation of [BubbleAppInfoProvider] that uses [PackageManager] to resolve app
 * info.
 */
class PackageManagerBubbleAppInfoProvider @Inject constructor() : BubbleAppInfoProvider {

    private companion object {
        const val TAG = "PackageManagerBubbleAppInfoProvider"
    }

    override fun resolveAppInfo(context: Context, bubble: Bubble): BubbleAppInfo? {
        // App name & app icon
        val pm = BubbleController.getPackageManagerForUser(context, bubble.user.identifier)
        try {
            val appInfo = pm.getApplicationInfo(
                bubble.packageName,
                (PackageManager.MATCH_UNINSTALLED_PACKAGES
                        or PackageManager.MATCH_DISABLED_COMPONENTS
                        or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                        or PackageManager.MATCH_DIRECT_BOOT_AWARE)
            )
            val appName = if (appInfo != null) pm.getApplicationLabel(appInfo)?.toString() else null
            val appIcon = pm.getApplicationIcon(bubble.packageName)
            return BubbleAppInfo(
                appName = appName,
                appIcon = appIcon,
                user = bubble.user
            )
        } catch (exception: PackageManager.NameNotFoundException) {
            // If we can't find package... don't think we should show the bubble.
            Log.w(TAG, "Unable to find package: ${bubble.packageName}")
            return null
        }
    }
}
