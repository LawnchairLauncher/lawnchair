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

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.Wait.atMost
import java.util.function.Predicate
import org.junit.Rule

/**
 * Base class for tests which use Launcher activity with some utility methods.
 *
 * This should instead be a rule, but is kept as a base class for easier migration from TAPL
 */
open class BaseLauncherActivityTest<LAUNCHER_TYPE : Launcher> {

    @get:Rule
    var launcherActivity =
        LauncherActivityScenarioRule<LAUNCHER_TYPE>(getInstrumentation().targetContext)

    @JvmField val uiDevice = UiDevice.getInstance(getInstrumentation())

    protected fun loadLauncherSync() {
        LauncherAppState.getInstance(targetContext()).model.loadModelSync()
        launcherActivity.initializeActivity()
    }

    protected fun targetContext(): Context = getInstrumentation().targetContext

    protected fun waitForLauncherCondition(message: String, condition: (LAUNCHER_TYPE) -> Boolean) =
        atMost(message, { launcherActivity.getFromLauncher(condition)!! })

    protected fun waitForLauncherCondition(
        message: String,
        condition: (LAUNCHER_TYPE) -> Boolean,
        timeout: Long,
    ) = atMost(message, { launcherActivity.getFromLauncher(condition)!! }, null, timeout)

    @JvmOverloads
    fun startAppFast(
        packageName: String,
        intent: Intent = targetContext().packageManager.getLaunchIntentForPackage(packageName)!!,
    ) {
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        targetContext().startActivity(intent)
        uiDevice.waitForIdle()
    }

    fun freezeAllApps() =
        launcherActivity.executeOnLauncher {
            it.appsView.appsStore.enableDeferUpdates(DEFER_UPDATES_TEST)
        }

    fun executeShellCommand(cmd: String) = uiDevice.executeShellCommand(cmd)

    fun addToWorkspace(view: View) {
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {
            view.accessibilityDelegate.performAccessibilityAction(
                view,
                R.id.action_add_to_workspace,
                null,
            )
        }
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }

    /**
     * Match the behavior with how widget is added in reality with "tap to add" (even with screen
     * readers).
     */
    fun addWidgetToWorkspace(view: View) {
        launcherActivity.executeOnLauncher {
            view.performClick()
            UiDevice.getInstance(getInstrumentation()).waitForIdle()
            view.findViewById<View>(R.id.widget_add_button).performClick()
        }
    }

    fun ViewGroup.searchView(filter: Predicate<View>): View? {
        if (filter.test(this)) return this
        for (child in children) {
            if (filter.test(child)) return child
            if (child is ViewGroup)
                child.searchView(filter)?.let {
                    return it
                }
        }
        return null
    }
}
