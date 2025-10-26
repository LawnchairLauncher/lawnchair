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

import androidx.test.filters.SmallTest
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class DaggerSingletonDeadlockTest(val method: KFunction<*>, val methodName: String) {

    private val context = SandboxModelContext()

    @After
    fun tearDown() {
        context.onDestroy()
    }

    /** Test to verify that the object can be created successfully on the main thread. */
    @Test
    fun objectCreationOnMainThread() {
        Executors.MAIN_EXECUTOR.submit {
                method.call(context.appComponent).also(Assert::assertNotNull)
            }
            .get(10, SECONDS)
    }

    /**
     * Test to verify that the object can be created successfully on the background thread, when the
     * main thread is blocked.
     */
    @Test
    fun objectCreationOnBackgroundThread() {
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {
            Executors.THREAD_POOL_EXECUTOR.submit {
                    method.call(context.appComponent).also(Assert::assertNotNull)
                }
                .get(10, SECONDS)
        }
    }

    companion object {
        @Parameters(name = "{1}")
        @JvmStatic
        fun getTestMethods() =
            LauncherAppComponent::class
                .memberFunctions
                .filter { it.parameters.size == 1 }
                .map {
                    arrayOf(it, if (it.name.startsWith("get")) it.name.substring(3) else it.name)
                }
    }
}
