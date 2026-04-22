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

package com.android.launcher3.integration.util

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.debug.TestEventEmitter.TestEvent
import com.android.launcher3.integration.events.EventWaiter
import com.android.launcher3.integration.util.events.ActivityTestEvents.createStateWaiter
import com.android.launcher3.tapl.TestHelpers
import com.android.launcher3.util.Executors
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.Wait.atMost
import java.util.ArrayDeque
import java.util.Queue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import java.util.function.Supplier
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

open class LauncherActivityScenarioRule<LAUNCHER_TYPE : Launcher>(
    val context: Context,
    val initializeOnStart: Boolean = true,
) : TestRule {

    private var currentScenario: ActivityScenario<LAUNCHER_TYPE>? = null

    @JvmField val uiDevice = UiDevice.getInstance(getInstrumentation())

    val activity: ActivityScenario<LAUNCHER_TYPE>
        get() =
            currentScenario
                ?: ActivityScenario.launch<LAUNCHER_TYPE>(
                        TestHelpers.getHomeIntentInPackage(context),
                        null,
                    )
                    .also { currentScenario = it }

    fun initializeActivity() {
        val onLauncherCreateWaiter = EventWaiter(TestEvent.LAUNCHER_ON_CREATE)
        activity.onActivity { onLauncherCreateWaiter.terminate() }
        activity.recreate()
        activity.moveToState(Lifecycle.State.RESUMED)
        onLauncherCreateWaiter.waitForSignal()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (initializeOnStart) {
                    initializeActivity()
                }
                base.evaluate()
                activity.close()
            }
        }
    }

    fun close() {
        currentScenario?.close()
        currentScenario = null
    }

    fun executeOnLauncher(f: ActivityAction<LAUNCHER_TYPE>): ActivityScenario<LAUNCHER_TYPE> =
        activity.onActivity(f)

    fun <T> getFromLauncher(f: Function<in LAUNCHER_TYPE, out T?>): T? {
        val result = AtomicReference<T>()
        activity.onActivity { result.set(f.apply(it)) }
        return result.get()
    }

    fun goToState(state: LauncherState) {
        val stateWaiter = createStateWaiter(state)
        executeOnLauncher { it.stateManager.goToState(state, 0) }
        stateWaiter.waitForSignal()
    }

    fun <T> getOnceNotNull(message: String, f: Function<LAUNCHER_TYPE, T?>): T? {
        var output: T? = null
        atMost(
            message,
            {
                val fromLauncher = getFromLauncher<T> { f.apply(it) }
                output = fromLauncher
                fromLauncher != null
            },
        )
        return output
    }

    @JvmOverloads
    fun injectKeyEvent(keyCode: Int, actionDown: Boolean, metaState: Int = 0) {
        uiDevice.waitForIdle()
        val eventTime = SystemClock.uptimeMillis()
        val event =
            KeyEvent(
                eventTime,
                eventTime,
                if (actionDown) KeyEvent.ACTION_DOWN else MotionEvent.ACTION_UP,
                keyCode,
                /* repeat= */ 0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                /* scancode= */ 0,
                /* flags= */ 0,
                InputDevice.SOURCE_KEYBOARD,
            )
        executeOnLauncher { it.dispatchKeyEvent(event) }
    }

    fun isInState(state: Supplier<LauncherState>): Boolean =
        getFromLauncher { it.stateManager.state == state.get() }!!

    /**
     * For the given view, it iterates over all of the child views in a preorder traversal returning
     * the first match to the filter
     */
    fun ViewGroup.searchView(filter: (view: View) -> Boolean): View? {
        val viewQueue: Queue<View> = ArrayDeque()
        viewQueue.add(this)
        while (!viewQueue.isEmpty()) {
            val view = viewQueue.poll()
            if (filter(view)) return view
            if (view is ViewGroup) viewQueue.addAll(view.children)
        }
        return null
    }
}
