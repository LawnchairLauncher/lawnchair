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

package com.android.wm.shell.desktopmode.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import java.util.function.Supplier

/**
 * This supplies the package name of default home in an efficient way. The query to package manager
 * only executes on initialization and when the preferred activity (e.g. default home) is changed.
 * Note that this returns null package name if the default home is setup wizard.
 */
class DefaultHomePackageSupplier(
    private val context: Context,
    shellInit: ShellInit,
    @ShellMainThread private val mainHandler: Handler,
) : BroadcastReceiver(), Supplier<String?> {

    private var defaultHomePackage: String? = null
    private var isSetupWizard: Boolean = false

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        context.registerReceiver(
            this,
            IntentFilter(Intent.ACTION_PREFERRED_ACTIVITY_CHANGED),
            null /* broadcastPermission */,
            mainHandler,
        )
    }

    private fun updateDefaultHomePackage(): String? {
        defaultHomePackage = context.packageManager.getHomeActivities(ArrayList())?.packageName
        isSetupWizard =
            defaultHomePackage != null &&
                context.packageManager.resolveActivity(
                    Intent()
                        .setPackage(defaultHomePackage)
                        .addCategory(Intent.CATEGORY_SETUP_WIZARD),
                    PackageManager.MATCH_SYSTEM_ONLY,
                ) != null
        return defaultHomePackage
    }

    override fun onReceive(contxt: Context?, intent: Intent?) {
        updateDefaultHomePackage()
    }

    override fun get(): String? {
        if (isSetupWizard) return null
        return defaultHomePackage ?: updateDefaultHomePackage()
    }
}
