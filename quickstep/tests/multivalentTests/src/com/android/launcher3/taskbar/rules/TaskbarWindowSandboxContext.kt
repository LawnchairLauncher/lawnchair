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

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.quickstep.SystemUiProxy
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.whenever

/**
 * [SandboxApplication] for running Taskbar tests.
 *
 * Tests need to run on a [VirtualDisplay] to avoid conflicting with Launcher's Taskbar on the
 * [DEFAULT_DISPLAY] (i.e. test is executing on a device).
 */
class TaskbarWindowSandboxContext
private constructor(
    private val base: SandboxApplication,
    val virtualDisplay: VirtualDisplay,
    private val params: SandboxParams,
) : ContextWrapper(base), TestRule {

    val settingsCacheSandbox = SettingsCacheSandbox()

    private val virtualDisplayRule =
        object : ExternalResource() {
            override fun after() = virtualDisplay.release()
        }

    // Filter out DEFAULT_DISPLAY in case code accesses displays property. The primary virtual
    // display has a different ID.
    private val sandboxDisplayManagerRule =
        object : ExternalResource() {
            override fun before() {
                val dm = base.spyService(DisplayManager::class.java)
                whenever(dm.displays).thenAnswer { i ->
                    @Suppress("UNCHECKED_CAST")
                    val displays = i.callRealMethod() as? Array<Display> ?: emptyArray<Display>()
                    displays.filter { it.displayId != DEFAULT_DISPLAY }.toTypedArray()
                }
            }
        }

    private val singletonSetupRule =
        object : ExternalResource() {
            override fun before() {
                val context = this@TaskbarWindowSandboxContext
                val builder =
                    params.builderBase
                        .bindSystemUiProxy(params.systemUiProxyProvider.invoke(context))
                        .bindSettingsCache(settingsCacheSandbox.cache)
                base.initDaggerComponent(builder)
            }
        }

    override fun apply(statement: Statement, description: Description): Statement {
        return RuleChain.outerRule(virtualDisplayRule)
            .around(base)
            .around(sandboxDisplayManagerRule)
            .around(singletonSetupRule)
            .apply(statement, description)
    }

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "TaskbarSandboxDisplay"

        /** Creates a [SandboxApplication] for Taskbar tests. */
        fun create(params: SandboxParams = SandboxParams()): TaskbarWindowSandboxContext {
            val base = ApplicationProvider.getApplicationContext<Context>()
            val displayManager = checkNotNull(base.getSystemService(DisplayManager::class.java))
            // Create virtual display to avoid clashing with Taskbar on default display.
            val virtualDisplay =
                base.resources.displayMetrics.let {
                    displayManager.createVirtualDisplay(
                        VIRTUAL_DISPLAY_NAME,
                        it.widthPixels,
                        it.heightPixels,
                        it.densityDpi,
                        /* surface= */ null,
                        /* flags= */ 0,
                    )
                }

            return TaskbarWindowSandboxContext(
                SandboxApplication(base = base.createDisplayContext(virtualDisplay.display)),
                virtualDisplay,
                params,
            )
        }
    }
}

/** Include additional bindings when building a [TaskbarSandboxComponent]. */
data class SandboxParams(
    val systemUiProxyProvider: (Context) -> SystemUiProxy = { SystemUiProxy(it) },
    val builderBase: TaskbarSandboxComponent.Builder = DaggerTaskbarSandboxComponent.builder(),
)
