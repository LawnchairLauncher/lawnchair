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

package com.android.launcher3.taskbar

import android.app.Instrumentation
import android.app.PendingIntent
import android.content.IIntentSender
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.android.launcher3.LauncherAppState
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.quickstep.AllAppsActionManager
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.TouchInteractionService.TISBinder
import org.junit.Assume.assumeTrue
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/**
 * Manages the Taskbar lifecycle for unit tests.
 *
 * See [InjectController] for grabbing controller(s) under test with minimal boilerplate.
 *
 * The rule interacts with [TaskbarManager] on the main thread. A good rule of thumb for tests is
 * that code that is executed on the main thread in production should also happen on that thread
 * when tested.
 *
 * `@UiThreadTest` is a simple way to run an entire test body on the main thread. But if a test
 * executes code that appends message(s) to the main thread's `MessageQueue`, the annotation will
 * prevent those messages from being processed until after the test body finishes.
 *
 * To test pending messages, instead use something like [Instrumentation.runOnMainSync] to perform
 * only sections of the test body on the main thread synchronously:
 * ```
 * @Test
 * fun example() {
 *     instrumentation.runOnMainSync { doWorkThatPostsMessage() }
 *     // Second lambda will not execute until message is processed.
 *     instrumentation.runOnMainSync { verifyMessageResults() }
 * }
 * ```
 */
class TaskbarUnitTestRule : MethodRule {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val serviceTestRule = ServiceTestRule()

    private lateinit var taskbarManager: TaskbarManager
    private lateinit var target: Any

    val activityContext: TaskbarActivityContext
        get() {
            return taskbarManager.currentActivityContext
                ?: throw RuntimeException("Failed to obtain TaskbarActivityContext.")
        }

    override fun apply(base: Statement, method: FrameworkMethod, target: Any): Statement {
        return object : Statement() {
            override fun evaluate() {
                this@TaskbarUnitTestRule.target = target

                val context = instrumentation.targetContext
                instrumentation.runOnMainSync {
                    assumeTrue(
                        LauncherAppState.getIDP(context).getDeviceProfile(context).isTaskbarPresent
                    )
                }

                // Check for existing Taskbar instance from Launcher process.
                val launcherTaskbarManager: TaskbarManager? =
                    if (!isRunningInRobolectric) {
                        try {
                            val tisBinder =
                                serviceTestRule.bindService(
                                    Intent(context, TouchInteractionService::class.java)
                                ) as? TISBinder
                            tisBinder?.taskbarManager
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                instrumentation.runOnMainSync {
                    taskbarManager =
                        TaskbarManager(
                            context,
                            AllAppsActionManager(context, UI_HELPER_EXECUTOR) {
                                PendingIntent(IIntentSender.Default())
                            },
                            object : TaskbarNavButtonCallbacks {},
                        )
                }

                try {
                    // Replace Launcher Taskbar window with test instance.
                    instrumentation.runOnMainSync {
                        launcherTaskbarManager?.removeTaskbarRootViewFromWindow()
                        taskbarManager.onUserUnlocked() // Required to complete initialization.
                    }

                    injectControllers()
                    base.evaluate()
                } finally {
                    // Revert Taskbar window.
                    instrumentation.runOnMainSync {
                        taskbarManager.destroy()
                        launcherTaskbarManager?.addTaskbarRootViewToWindow()
                    }
                }
            }
        }
    }

    /** Simulates Taskbar recreation lifecycle. */
    fun recreateTaskbar() {
        taskbarManager.recreateTaskbar()
        injectControllers()
    }

    private fun injectControllers() {
        val controllers = activityContext.controllers
        val controllerFieldsByType = controllers.javaClass.fields.associateBy { it.type }
        target.javaClass.fields
            .filter { it.isAnnotationPresent(InjectController::class.java) }
            .forEach {
                it.set(
                    target,
                    controllerFieldsByType[it.type]?.get(controllers)
                        ?: throw NoSuchElementException("Failed to find controller for ${it.type}"),
                )
            }
    }

    /**
     * Annotates test controller fields to inject the corresponding controllers from the current
     * [TaskbarControllers] instance.
     *
     * Controllers are injected during test setup and upon calling [recreateTaskbar].
     *
     * Multiple controllers can be injected if needed.
     */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class InjectController
}
