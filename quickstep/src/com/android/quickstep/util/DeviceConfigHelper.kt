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

package com.android.quickstep.util

import android.app.ActivityThread
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.provider.DeviceConfig
import android.provider.DeviceConfig.OnPropertiesChangedListener
import android.provider.DeviceConfig.Properties
import androidx.annotation.WorkerThread
import com.android.launcher3.BuildConfig
import com.android.launcher3.util.Executors

/** Utility class to manage a set of device configurations */
class DeviceConfigHelper<ConfigType>(private val factory: (PropReader) -> ConfigType) {

    var config: ConfigType
        private set
    private val allKeys: Set<String>
    private val propertiesListener = OnPropertiesChangedListener { onDevicePropsChanges(it) }
    private val sharedPrefChangeListener = OnSharedPreferenceChangeListener { _, _ ->
        recreateConfig()
    }

    private val changeListeners = mutableListOf<Runnable>()

    init {
        // Initialize the default config once.
        allKeys = HashSet()
        config =
            factory(
                PropReader(
                    object : PropProvider {
                        override fun <T : Any> get(key: String, fallback: T): T {
                            if (fallback is Int) {
                                allKeys.add(key)
                                return DeviceConfig.getInt(NAMESPACE_LAUNCHER, key, fallback) as T
                            } else if (fallback is Boolean) {
                                allKeys.add(key)
                                return DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, key, fallback)
                                    as T
                            } else return fallback
                        }
                    }
                )
            )

        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_LAUNCHER,
            Executors.UI_HELPER_EXECUTOR,
            propertiesListener
        )
        if (BuildConfig.IS_DEBUG_DEVICE) {
            prefs.registerOnSharedPreferenceChangeListener(sharedPrefChangeListener)
        }
    }

    @WorkerThread
    private fun onDevicePropsChanges(properties: Properties) {
        if (NAMESPACE_LAUNCHER != properties.namespace) return
        if (!allKeys.any(properties.keyset::contains)) return
        recreateConfig()
    }

    private fun recreateConfig() {
        val myProps =
            DeviceConfig.getProperties(NAMESPACE_LAUNCHER, *allKeys.toTypedArray<String>())
        config =
            factory(
                PropReader(
                    object : PropProvider {
                        override fun <T : Any> get(key: String, fallback: T): T {
                            if (fallback is Int) return myProps.getInt(key, fallback) as T
                            else if (fallback is Boolean)
                                return myProps.getBoolean(key, fallback) as T
                            else return fallback
                        }
                    }
                )
            )
        Executors.MAIN_EXECUTOR.execute { changeListeners.forEach(Runnable::run) }
    }

    /** Adds a listener for property changes */
    fun addChangeListener(r: Runnable) = changeListeners.add(r)

    /** Removes a previously added listener */
    fun removeChangeListener(r: Runnable) = changeListeners.remove(r)

    fun close() {
        DeviceConfig.removeOnPropertiesChangedListener(propertiesListener)
        if (BuildConfig.IS_DEBUG_DEVICE) {
            prefs.unregisterOnSharedPreferenceChangeListener(sharedPrefChangeListener)
        }
    }

    internal interface PropProvider {
        fun <T : Any> get(key: String, fallback: T): T
    }

    /** The reader is sent to the config for initialization */
    class PropReader internal constructor(private val f: PropProvider) {

        @JvmOverloads
        fun <T : Any> get(key: String, fallback: T, desc: String? = null): T {
            val v = f.get(key, fallback)
            if (BuildConfig.IS_DEBUG_DEVICE && desc != null) {
                if (v is Int) {
                    allProps[key] = DebugInfo(key, desc, true, fallback)
                    return prefs.getInt(key, v) as T
                } else if (v is Boolean) {
                    allProps[key] = DebugInfo(key, desc, false, fallback)
                    return prefs.getBoolean(key, v) as T
                }
            }
            return v
        }
    }

    class DebugInfo<T>(
        val key: String,
        val desc: String,
        val isInt: Boolean,
        val valueInCode: T,
    )

    companion object {
        const val NAMESPACE_LAUNCHER = "launcher"

        val allProps = mutableMapOf<String, DebugInfo<*>>()

        private const val FLAGS_PREF_NAME = "featureFlags"

        val prefs: SharedPreferences by lazy {
            ActivityThread.currentApplication()
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE)
        }
    }
}
