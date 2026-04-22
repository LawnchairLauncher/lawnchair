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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl.Transaction
import android.window.IWindowContainerToken
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.suppliers.TransactionSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.MixedLetterboxController
import com.android.wm.shell.compatui.letterbox.asMode
import com.android.wm.shell.sysui.ShellInit
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer

/**
 * Tests for [LetterboxCleanupAdapter].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxCleanupAdapterTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxCleanupAdapterTest : ShellTestCase() {

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the flag is ENABLED the listener is registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkListenerIsRegistered(expected = true)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the flag is DISABLED the listener is NOT registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkListenerIsRegistered(expected = false)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When Task destroyed letterbox surfaces are removed`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.sendTaskDestroyEvent(
                r.createTaskInfo(
                    id = 20,
                    taskDisplayId = 3
                )
            )

            r.checkTransactionSupplierIsInvoked(expected = true)
            r.checkControllerIsInvoked(expected = true, taskId = 20, displayId = 3)
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxCleanerAdapterRobotTest>) {
        val robot = LetterboxCleanerAdapterRobotTest()
        consumer.accept(robot)
    }

    class LetterboxCleanerAdapterRobotTest {

        private val executor: ShellExecutor
        private val shellInit: ShellInit
        private val shellTaskOrganizer: ShellTaskOrganizer
        private val transactionSupplier: TransactionSupplier
        private val transaction: Transaction
        private val mixedLetterboxController: MixedLetterboxController
        private val mLetterboxCleanupAdapter: LetterboxCleanupAdapter

        init {
            executor = mock<ShellExecutor>()
            shellInit = ShellInit(executor)
            shellTaskOrganizer = mock<ShellTaskOrganizer>()
            transactionSupplier = mock<TransactionSupplier>()
            transaction = mock<Transaction>()
            whenever(transactionSupplier.get()).thenReturn(transaction)
            mixedLetterboxController = mock<MixedLetterboxController>()
            mLetterboxCleanupAdapter = LetterboxCleanupAdapter(
                shellInit,
                shellTaskOrganizer,
                transactionSupplier,
                mixedLetterboxController
            )
        }

        fun createTaskInfo(
            id: Int = 0,
            taskDisplayId: Int = DEFAULT_DISPLAY,
            windowingMode: Int = WINDOWING_MODE_FREEFORM,
            windowToken: WindowContainerToken = WindowContainerToken(mock<IWindowContainerToken>())
        ) =
            RunningTaskInfo().apply {
                taskId = id
                displayId = taskDisplayId
                configuration.windowConfiguration.windowingMode = windowingMode
                token = windowToken
                baseIntent = Intent().apply {
                    component = ComponentName("package", "component.name")
                }
            }

        fun sendTaskDestroyEvent(taskInfo: RunningTaskInfo) {
            mLetterboxCleanupAdapter.onTaskVanished(taskInfo)
        }

        fun invokeShellInit() = shellInit.init()

        fun checkListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskVanishedListener(any())
        }

        fun checkTransactionSupplierIsInvoked(expected: Boolean) {
            verify(transactionSupplier, expected.asMode()).get()
        }

        fun checkControllerIsInvoked(expected: Boolean, taskId: Int, displayId: Int) {
            verify(mixedLetterboxController, expected.asMode()).destroyLetterboxSurface(
                eq(
                    LetterboxKey(displayId = displayId, taskId = taskId)
                ), eq(transaction)
            )
        }
    }
}