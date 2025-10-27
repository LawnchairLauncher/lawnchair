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

package com.android.launcher3.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE
import android.content.pm.ApplicationInfo.FLAG_INSTALLED
import android.content.pm.ApplicationInfo.FLAG_SUSPENDED
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.UserHandle
import com.android.launcher3.Flags.enableSupportForArchiving
import com.android.launcher3.Utilities.ATLEAST_V
import kotlin.LazyThreadSafetyMode.NONE

/**
 * A set of utility methods around ApplicationInfo with support for fetching the actual info lazily
 */
class ApplicationInfoWrapper(provider: () -> ApplicationInfo?) {

    constructor(appInfo: ApplicationInfo?) : this({ appInfo })

    constructor(
        ctx: Context,
        pkg: String,
        user: UserHandle,
    ) : this({
        try {
            ctx.getSystemService(LauncherApps::class.java)
                ?.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES, user)
                ?.let { ai ->
                    // its enabled and (either installed or archived)
                    if (
                        ai.enabled &&
                            (ai.flags.and(FLAG_INSTALLED) != 0 ||
                                (ATLEAST_V && enableSupportForArchiving() && ai.isArchived))
                    ) {
                        ai
                    } else {
                        null
                    }
                }
        } catch (e: NameNotFoundException) {
            null
        }
    })

    constructor(
        ctx: Context,
        intent: Intent,
    ) : this(
        provider@{
            try {
                val pm = ctx.packageManager
                val packageName: String =
                    intent.component?.packageName
                        ?: intent.getPackage()
                        ?: return@provider pm.resolveActivity(
                                intent,
                                PackageManager.MATCH_DEFAULT_ONLY,
                            )
                            ?.activityInfo
                            ?.applicationInfo
                pm.getApplicationInfo(packageName, 0)
            } catch (e: NameNotFoundException) {
                null
            }
        }
    )

    private val appInfo: ApplicationInfo? by lazy(NONE, provider)

    private fun hasFlag(flag: Int) = appInfo?.let { it.flags.and(flag) != 0 } ?: false

    /**
     * Returns true if the app can possibly be on the SDCard. This is just a workaround and doesn't
     * guarantee that the app is on SD card.
     */
    fun isOnSdCard() = hasFlag(FLAG_EXTERNAL_STORAGE)

    /** Returns whether the target app is installed for a given user */
    fun isInstalled() = hasFlag(FLAG_INSTALLED)

    /**
     * Returns whether the target app is suspended for a given user as per
     * [android.app.admin.DevicePolicyManager.isPackageSuspended].
     */
    fun isSuspended() = hasFlag(FLAG_INSTALLED) && hasFlag(FLAG_SUSPENDED)

    /** Returns whether the target app is archived for a given user */
    fun isArchived() = ATLEAST_V && enableSupportForArchiving() && appInfo?.isArchived ?: false

    /** Returns whether the target app is a system app */
    fun isSystem() = hasFlag(FLAG_SYSTEM)

    fun getInfo(): ApplicationInfo? = appInfo
}
