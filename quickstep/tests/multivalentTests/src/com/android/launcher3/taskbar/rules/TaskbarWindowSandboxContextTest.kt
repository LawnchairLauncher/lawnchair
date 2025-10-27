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

package com.android.launcher3.taskbar.rules

import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023"])
class TaskbarWindowSandboxContextTest {

    @Test
    fun testVirtualDisplay_releasedOnTeardown() {
        val context = TaskbarWindowSandboxContext.create()
        assertThat(context.virtualDisplay.token).isNotNull()

        context
            .apply(
                object : Statement() {
                    override fun evaluate() = Unit
                },
                Description.createSuiteDescription(TaskbarWindowSandboxContextTest::class.java),
            )
            .evaluate()

        assertThat(context.virtualDisplay.token).isNull()
    }
}
