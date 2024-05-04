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

package com.android.launcher3.util.rule

import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.EmulatedDeviceAndroidJUnit.Companion.isRunningInRobolectric
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A test rule to add support for @UiThreadTest annotations when running in robolectric until is it
 * natively supported by the robolectric runner:
 * https://github.com/robolectric/robolectric/issues/9026
 */
class RobolectricUiThreadRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        if (!shouldRunOnUiThread(description)) base else UiThreadStatement(base)

    private fun shouldRunOnUiThread(description: Description): Boolean {
        if (!isRunningInRobolectric) {
            // If not running in robolectric, let the default runner handle this
            return false
        }
        var clazz = description.testClass
        try {
            if (
                clazz
                    .getDeclaredMethod(description.methodName)
                    .getAnnotation(UiThreadTest::class.java) != null
            ) {
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        while (!clazz.isAnnotationPresent(UiThreadTest::class.java)) {
            clazz = clazz.superclass ?: return false
        }
        return true
    }

    private class UiThreadStatement(val base: Statement) : Statement() {

        override fun evaluate() {
            val exceptionRef = AtomicReference<Throwable>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                try {
                    base.evaluate()
                } catch (throwable: Throwable) {
                    exceptionRef.set(throwable)
                }
            }
            exceptionRef.get()?.let { throw it }
        }
    }
}
