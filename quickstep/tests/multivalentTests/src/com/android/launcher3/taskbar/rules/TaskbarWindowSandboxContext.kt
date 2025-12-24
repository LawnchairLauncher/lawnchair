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

import android.companion.datatransfer.continuity.TaskContinuityManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.launcher3.util.VirtualDisplaysRule
import com.android.quickstep.SystemUiProxy
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [SandboxApplication] for running Taskbar tests.
 *
 * Tests need to run on a [VirtualDisplay] to avoid conflicting with Launcher's Taskbar on the
 * [DEFAULT_DISPLAY] (i.e. test is executing on a device).
 */
class TaskbarWindowSandboxContext
private constructor(
    val base: SandboxApplication,
    val virtualDisplayRule: VirtualDisplaysRule,
    private val params: SandboxParams,
) : ContextWrapper(base), TestRule {

    val settingsCacheSandbox = SettingsCacheSandbox()
    val windowManagerSpy = base.spyService(WindowManager::class.java)
    val taskContinuityManagerMock: TaskContinuityManager = mock<TaskContinuityManager>()

    private val sandboxSpyServicesRule =
        object : ExternalResource() {
            override fun before() {
                // Filter out DEFAULT_DISPLAY in case code accesses displays property. The primary
                // virtual display has a different ID.
                val dm = base.spyService(DisplayManager::class.java)
                base.mockService(
                    Context.TASK_CONTINUITY_SERVICE,
                    TaskContinuityManager::class.java,
                    taskContinuityManagerMock,
                )
                whenever(dm.displays).thenAnswer { i ->
                    @Suppress("UNCHECKED_CAST")
                    val displays = i.callRealMethod() as? Array<Display> ?: emptyArray<Display>()
                    displays.filter { it.displayId != DEFAULT_DISPLAY }.toTypedArray()
                }

                // Have displays appear as if they support Taskbar.
                if (!DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue) {
                    whenever(windowManagerSpy.shouldShowSystemDecors(any())).thenReturn(true)
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
            .around(sandboxSpyServicesRule)
            .around(singletonSetupRule)
            .apply(statement, description)
    }

    companion object {
        /** Creates a [SandboxApplication] for Taskbar tests. */
        fun create(params: SandboxParams = SandboxParams()): TaskbarWindowSandboxContext {
            val base = ApplicationProvider.getApplicationContext<Context>()
            val virtualDisplaysRule = VirtualDisplaysRule(base)
            val defaultDisplay =
                checkNotNull(
                    base.resources.displayMetrics.let {
                        virtualDisplaysRule[
                            virtualDisplaysRule.add(it.widthPixels, it.heightPixels, it.densityDpi)]
                    }
                )
            return TaskbarWindowSandboxContext(
                SandboxApplication(base = base.createDisplayContext(defaultDisplay.display)),
                virtualDisplaysRule,
                params,
            )
        }
    }
}

/** Include additional bindings when building a [TaskbarSandboxComponent]. */
data class SandboxParams(
    val systemUiProxyProvider: (Context) -> SystemUiProxy = { SystemUiProxy(it, MAIN_EXECUTOR, UI_HELPER_EXECUTOR) },
    val builderBase: TaskbarSandboxComponent.Builder = DaggerTaskbarSandboxComponent.builder(),
)
