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

import android.app.Instrumentation
import android.app.PendingIntent
import android.content.IIntentSender
import android.provider.Settings.Secure.NAV_BAR_KIDS_MODE
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.provider.Settings.Secure.getUriFor
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherAppState
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarManagerImpl
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks
import com.android.launcher3.taskbar.TaskbarUIController
import com.android.launcher3.taskbar.bubbles.BubbleControllers
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.android.quickstep.AllAppsActionManager
import com.android.quickstep.LauncherDisplaysWithDecorationsRepositoryCompat
import com.android.quickstep.fallback.window.RecentsWindowManager
import com.android.quickstep.input.QuickstepKeyGestureEventsManager
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.util.Locale
import java.util.Optional
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Manages the Taskbar lifecycle for unit tests.
 *
 * Tests should pass in themselves as [testInstance]. They also need to provide their target
 * [context] through the constructor.
 *
 * See [InjectController] for grabbing controller(s) under test with minimal boilerplate.
 *
 * The rule interacts with [TaskbarManager] on the main thread. A good rule of thumb for tests is
 * that code that is executed on the main thread in production should also happen on that thread
 * when tested.
 *
 * `@UiThreadTest` is incompatible with this rule. The annotation causes this rule to run on the
 * main thread, but it needs to be run on the test thread for it to work properly. Instead, only run
 * code that requires the main thread using something like [Instrumentation.runOnMainSync] or
 * [TestUtil.getOnUiThread].
 *
 * ```
 * @Test
 * fun example() {
 *     instrumentation.runOnMainSync { doWorkThatPostsMessage() }
 *     // Second lambda will not execute until message is processed.
 *     instrumentation.runOnMainSync { verifyMessageResults() }
 * }
 * ```
 */
class TaskbarUnitTestRule(
    private val testInstance: Any,
    private val context: TaskbarWindowSandboxContext,
    private val controllerInjectionCallback: () -> Unit = {},
) : TestRule {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var taskbarManager: TaskbarManagerImpl

    val activityContext: TaskbarActivityContext
        get() {
            return taskbarManager.currentActivityContext
                ?: throw RuntimeException("Failed to obtain TaskbarActivityContext.")
        }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {

                // Only run test when Taskbar is enabled.
                instrumentation.runOnMainSync {
                    assumeTrue(
                        LauncherAppState.getIDP(context).getDeviceProfile(context).isTaskbarPresent
                    )
                }

                // Process secure setting annotations.
                context.settingsCacheSandbox[getUriFor(USER_SETUP_COMPLETE)] =
                    if (description.getAnnotation(UserSetupMode::class.java) != null) 0 else 1
                context.settingsCacheSandbox[getUriFor(NAV_BAR_KIDS_MODE)] =
                    if (description.getAnnotation(NavBarKidsMode::class.java) != null) 1 else 0

                val quickstepKeyGestureEventsManagerSpy =
                    spy(QuickstepKeyGestureEventsManager(context))
                doNothing()
                    .whenever(quickstepKeyGestureEventsManagerSpy)
                    .registerAllAppsKeyGestureEvent(any())
                doNothing()
                    .whenever(quickstepKeyGestureEventsManagerSpy)
                    .unregisterAllAppsKeyGestureEvent()
                doNothing()
                    .whenever(quickstepKeyGestureEventsManagerSpy)
                    .registerOverviewKeyGestureEvent(any())
                doNothing()
                    .whenever(quickstepKeyGestureEventsManagerSpy)
                    .unregisterOverviewKeyGestureEvent()
                taskbarManager =
                    TestUtil.getOnUiThread {
                        object :
                            TaskbarManagerImpl(
                                context,
                                AllAppsActionManager(
                                    context,
                                    UI_HELPER_EXECUTOR,
                                    quickstepKeyGestureEventsManagerSpy,
                                ) {
                                    PendingIntent(IIntentSender.Default())
                                },
                                object : TaskbarNavButtonCallbacks {},
                                RecentsWindowManager.REPOSITORY_INSTANCE.get(context),
                                LauncherDisplaysWithDecorationsRepositoryCompat.INSTANCE.get(
                                    context
                                ),
                                ProductionDispatchers.main,
                            ) {
                            override fun recreateTaskbars() {
                                super.recreateTaskbars()
                                if (currentActivityContext != null) {
                                    injectControllers()
                                    // TODO(b/346394875): we should test a non-default uiController.
                                    activityContext.setUIController(TaskbarUIController.DEFAULT)
                                    controllerInjectionCallback.invoke()
                                }
                            }

                            override fun recreateTaskbarForDisplay(displayId: Int, duration: Int) {
                                super.recreateTaskbarForDisplay(displayId, duration)
                                if (
                                    displayId == context.displayId && currentActivityContext != null
                                ) {
                                    injectControllers()
                                    // TODO(b/346394875): we should test a non-default uiController.
                                    activityContext.setUIController(TaskbarUIController.DEFAULT)
                                    controllerInjectionCallback.invoke()
                                }
                            }
                        }
                    }

                if (description.getAnnotation(ForceRtl::class.java) != null) {
                    // Needs to be set on window context instead of sandbox context, because it does
                    // does not propagate between them. However, this change will impact created
                    // TaskbarActivityContext instances, since they wrap the window context.
                    // TODO: iterate through all window contexts and do this.
                    taskbarManager.primaryWindowContext.resources.configuration.setLayoutDirection(
                        RTL_LOCALE
                    )
                }

                try {
                    // Required to complete initialization.
                    instrumentation.runOnMainSync { taskbarManager.onUserUnlocked() }

                    base.evaluate()
                } finally {
                    instrumentation.runOnMainSync { taskbarManager.destroy() }
                    context.displayControllerSpy?.cleanup()
                }
            }
        }
    }

    /** Simulates Taskbar recreation lifecycle. */
    fun recreateTaskbar() = instrumentation.runOnMainSync { taskbarManager.recreateTaskbars() }

    private fun injectControllers() {
        val bubbleControllerTypes =
            BubbleControllers::class.java.fields.map { f ->
                if (f.type == Optional::class.java) {
                    (f.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
                } else {
                    f.type
                }
            }
        testInstance.javaClass.fields
            .filter { it.isAnnotationPresent(InjectController::class.java) }
            .forEach {
                val controllers: Any =
                    if (it.type in bubbleControllerTypes) {
                        activityContext.controllers.bubbleControllers.orElseThrow {
                            NoSuchElementException("Bubble controllers are not initialized")
                        }
                    } else {
                        activityContext.controllers
                    }
                injectController(it, testInstance, controllers)
            }
    }

    private fun injectController(field: Field, testInstance: Any, controllers: Any) {
        val controllerFieldsByType = controllers.javaClass.fields.associateBy { it.type }
        field.set(
            testInstance,
            controllerFieldsByType[field.type]?.get(controllers)
                ?: throw NoSuchElementException("Failed to find controller for ${field.type}"),
        )
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

    /** Overrides [USER_SETUP_COMPLETE] to be `false` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class UserSetupMode

    /** Overrides [NAV_BAR_KIDS_MODE] to be `true` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class NavBarKidsMode

    /** Forces RTL UI for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class ForceRtl
}

private val RTL_LOCALE = Locale.of("ar", "XB")
