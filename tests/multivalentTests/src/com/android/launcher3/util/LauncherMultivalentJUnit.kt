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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.ImmutableList
import java.util.Locale
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import org.junit.runner.Runner
import org.junit.runners.Suite

/**
 * A custom runner for multivalent tests with launcher specific features
 * 1) Adds support for @UiThread annotations in deviceless tests
 * 2) Allows emulating multiple devices when running in deviceless mode
 */
class LauncherMultivalentJUnit(klass: Class<*>?) : Suite(klass, ImmutableList.of()) {

    val runners: List<Runner> =
        (testClass.getAnnotation(EmulatedDevices::class.java)?.value ?: emptyArray()).let { devices
            ->
            if (!isRunningInRobolectric) {
                return@let null
            }
            try {
                (testClass.javaClass.classLoader.loadClass(ROBOLECTRIC_RUNNER) as Class<Runner>)
                    .getConstructor(Class::class.java, String::class.java)
                    .let { ctor ->
                        if (devices.isEmpty()) listOf(ctor.newInstance(testClass.javaClass, null))
                        else devices.map { ctor.newInstance(testClass.javaClass, it) }
                    }
            } catch (e: Exception) {
                null
            }
        }
            ?: listOf(AndroidJUnit4(testClass.javaClass))

    override fun getChildren() = runners

    /**
     * Annotation to be added to a test so run it on a list of emulated devices for deviceless test
     */
    @Retention(RUNTIME) @Target(CLASS) annotation class EmulatedDevices(val value: Array<String>)

    companion object {
        private const val ROBOLECTRIC_RUNNER = "com.android.launcher3.util.RobolectricDeviceRunner"

        val isRunningInRobolectric: Boolean
            get() =
                if (
                    System.getProperty("java.runtime.name")
                        .lowercase(Locale.getDefault())
                        .contains("android")
                ) {
                    false
                } else {
                    try {
                        // Check if robolectric runner exists
                        Class.forName("org.robolectric.RobolectricTestRunner") != null
                    } catch (e: ClassNotFoundException) {
                        false
                    }
                }
    }
}
