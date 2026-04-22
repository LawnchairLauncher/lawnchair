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

import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.Sandbox
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Runner which emulates the provided display before running the actual test */
class RobolectricDeviceRunner(testClass: Class<*>?, private val deviceName: String?) :
    RobolectricTestRunner(testClass) {

    private val nameSuffix = deviceName?.let { "-$it" } ?: ""

    override fun getName() = super.getName() + nameSuffix

    override fun testName(method: FrameworkMethod) = super.testName(method) + nameSuffix

    @Throws(Throwable::class)
    override fun beforeTest(sandbox: Sandbox, method: FrameworkMethod, bootstrappedMethod: Method) {
        super.beforeTest(sandbox, method, bootstrappedMethod)

        deviceName ?: return

        val emulator =
            try {
                ReflectionHelpers.loadClass(
                    bootstrappedMethod.declaringClass.classLoader,
                    DEVICE_EMULATOR
                )
            } catch (e: Exception) {
                // Ignore, if the device emulator is not present
                return
            }
        ReflectionHelpers.callStaticMethod<Any>(
            emulator,
            "updateDevice",
            ClassParameter.from(String::class.java, deviceName)
        )
    }

    override fun getHelperTestRunner(clazz: Class<*>) = MyHelperTestRunner(clazz)

    class MyHelperTestRunner(clazz: Class<*>) : HelperTestRunner(clazz) {

        override fun methodBlock(method: FrameworkMethod): Statement =
            // this needs to be run in the test classLoader
            ReflectionHelpers.callStaticMethod(
                method.declaringClass.classLoader,
                RobolectricDeviceRunner::class.qualifiedName,
                "wrapUiThreadMethod",
                ClassParameter.from(FrameworkMethod::class.java, method),
                ClassParameter.from(Statement::class.java, super.methodBlock(method))
            )
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

    companion object {

        private const val DEVICE_EMULATOR = "com.android.launcher3.util.RoboDeviceEmulator"

        @JvmStatic
        fun wrapUiThreadMethod(method: FrameworkMethod, base: Statement): Statement =
            if (
                method.method.isAnnotationPresent(UiThreadTest::class.java) ||
                    method.declaringClass.isAnnotationPresent(UiThreadTest::class.java)
            ) {
                UiThreadStatement(base)
            } else {
                base
            }
    }
}
