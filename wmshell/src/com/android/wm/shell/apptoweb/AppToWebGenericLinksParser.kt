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

package com.android.wm.shell.apptoweb

import android.content.Context
import android.provider.DeviceConfig
import android.webkit.URLUtil
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig

/**
 * Retrieves the build-time or server-side generic links list and parses and stores the
 * package-to-url pairs.
 */
class AppToWebGenericLinksParser(
    private val context: Context,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    private val desktopConfig: DesktopConfig,
) {
    private val genericLinksMap: MutableMap<String, String> = mutableMapOf()

    init {
        // If using the server-side generic links list, register a listener
        if (!desktopConfig.useAppToWebBuildTimeGenericLinks) {
            DeviceConfigListener()
        }

        updateGenericLinksMap()
    }

    /** Returns the generic link associated with the [packageName] or null if there is none. */
    fun getGenericLink(packageName: String): String? = genericLinksMap[packageName]

    private fun updateGenericLinksMap() {
        val genericLinksList =
            if (desktopConfig.useAppToWebBuildTimeGenericLinks) {
                context.resources.getString(R.string.generic_links_list)
            } else {
                DeviceConfig.getString(NAMESPACE, FLAG_GENERIC_LINKS, /* defaultValue= */ "")
            } ?: return

        parseGenericLinkList(genericLinksList)
    }

    private fun parseGenericLinkList(genericLinksList: String) {
        val newEntries =
            genericLinksList
                .split(" ")
                .filter { it.contains(':') }
                .map {
                    val (packageName, url) = it.split(':', limit = 2)
                    return@map packageName to url
                }
                .filter { URLUtil.isNetworkUrl(it.second) }

        genericLinksMap.clear()
        genericLinksMap.putAll(newEntries)
    }

    /**
     * Listens for changes to the server-side generic links list and updates the package to url map
     * if [DesktopModeStatus#useBuildTimeGenericLinkList()] is set to false.
     */
    inner class DeviceConfigListener : DeviceConfig.OnPropertiesChangedListener {
        init {
            DeviceConfig.addOnPropertiesChangedListener(NAMESPACE, mainExecutor, this)
        }

        override fun onPropertiesChanged(properties: DeviceConfig.Properties) {
            if (properties.keyset.contains(FLAG_GENERIC_LINKS)) {
                updateGenericLinksMap()
            }
        }
    }

    companion object {
        private const val NAMESPACE = DeviceConfig.NAMESPACE_APP_COMPAT_OVERRIDES
        @VisibleForTesting const val FLAG_GENERIC_LINKS = "generic_links_flag"
    }
}
