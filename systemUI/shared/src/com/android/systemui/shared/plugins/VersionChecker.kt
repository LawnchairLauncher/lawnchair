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

import com.android.systemui.plugins.Plugin
import javax.inject.Inject

/** Class that compares a plugin class against an implementation for version matching. */
interface VersionChecker {
    /** Compares two plugin classes. Returns true when they match. */
    fun <T : Plugin> checkVersion(
        instanceClass: Class<T>,
        pluginClass: Class<T>,
        plugin: Plugin?,
    ): Boolean

    /** Returns VersionInfo for the target class */
    fun <T : Plugin> getVersionInfo(instanceClass: Class<T>): VersionInfo?
}

/** Class that compares a plugin class against an implementation for version matching. */
class VersionCheckerImpl @Inject constructor() : VersionChecker {
    override fun <T : Plugin> checkVersion(
        instanceClass: Class<T>,
        pluginClass: Class<T>,
        plugin: Plugin?,
    ): Boolean {
        val pluginVersion = VersionInfo().addClass(pluginClass)
        val instanceVersion = VersionInfo().addClass(instanceClass)
        if (instanceVersion.hasVersionInfo()) {
            pluginVersion.checkVersion(instanceVersion)
        } else if (plugin != null) {
            val fallbackVersion = plugin.version
            if (fallbackVersion != pluginVersion.defaultVersion) {
                return false
            }
        }
        return true
    }

    /** Returns the version info for the class */
    override fun <T : Plugin> getVersionInfo(instanceClass: Class<T>): VersionInfo? {
        val instanceVersion = VersionInfo().addClass(instanceClass)
        return if (instanceVersion.hasVersionInfo()) instanceVersion else null
    }
}
