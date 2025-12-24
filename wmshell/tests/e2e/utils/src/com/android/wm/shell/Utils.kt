/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell

import android.app.Instrumentation
import android.content.Intent
import android.platform.test.rule.EnsureDeviceSettingsRule
import android.platform.test.rule.NavigationModeRule
import android.platform.test.rule.PressHomeRule
import android.platform.test.rule.UnlockScreenRule
import android.provider.Settings
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.rules.ArtifactSaverRule
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.LaunchAppRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By.res
import androidx.test.uiautomator.By.text
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until.findObject
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import java.io.IOException
import org.junit.rules.RuleChain

object Utils {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private val settingsResources = instrumentation.context.packageManager
        .getResourcesForApplication(SETTINGS_PACKAGE_NAME)

    private val externalDisplaySettings = getSettingsString(EXTERNAL_DISPLAY_SETTING)

    /**
     * A helper method to initialize a [RuleChain] to set up [navigationMode] and screen [rotation].
     *
     * @param navigationMode the navigation mode to set up, either one of [NavBar.MODE_GESTURAL]
     * or [NavBar.MODE_3BUTTON].
     * @param rotation the screen rotation to apply, which defaults to [Rotation.ROTATION_0]
     * @return the [RuleChain] to set up the [navigationMode] and [rotation].
     */
    fun testSetupRule(navigationMode: NavBar, rotation: Rotation = Rotation.ROTATION_0): RuleChain {
        return RuleChain.outerRule(ArtifactSaverRule())
            .around(UnlockScreenRule())
            .around(NavigationModeRule(navigationMode.value, false))
            .around(
                LaunchAppRule(MessagingAppHelper(instrumentation), clearCacheAfterParsing = false)
            )
            .around(RemoveAllTasksButHomeRule())
            .around(
                ChangeDisplayOrientationRule(
                    rotation,
                    resetOrientationAfterTest = false,
                    clearCacheAfterParsing = false
                )
            )
            .around(PressHomeRule())
            .around(EnsureDeviceSettingsRule())
    }

    /**
     * Resets the frozen recent tasks list (ie. commits the quickswitch to the current task and
     * reorders the current task to the end of the recents list).
     */
    fun resetFreezeRecentTaskList() {
        try {
            device.executeShellCommand("wm reset-freeze-recent-tasks")
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to reset frozen recent tasks list", e)
        }
    }

    fun toggleMirroringSwitchViaSettingsApp() {
        // Launch the Settings app and open the "External Display" settings list
        instrumentation.context
            .startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        waitFindObject(text(externalDisplaySettings)).click()

        // Maximize Settings app's window to avoid scrolling on the display topology settings panel
        // when looking for the mirroring switch
        val maximizeButton = device.wait(findObject(res(SETTINGS_MAXIMIZE_BUTTON_ID)),
            SETTINGS_UPDATE_TIME_OUT)
        maximizeButton?.click()

        // Find the mirroring switch and toggle it
        val switch = checkNotNull(device.wait(findObject(res(MIRROR_BUILT_IN_DISPLAY_SWITCH_ID)),
            SETTINGS_UPDATE_TIME_OUT))
        switch.click()
        device.waitForWindowUpdate(SETTINGS_PACKAGE_NAME, SETTINGS_UPDATE_TIME_OUT)
        device.waitForIdle()
    }

    private fun getSettingsString(resName: String): String {
        val identifier = settingsResources.getIdentifier(resName, "string",
            SETTINGS_PACKAGE_NAME)
        return settingsResources.getString(identifier)
    }

    private const val SETTINGS_PACKAGE_NAME = "com.android.settings"
    private const val EXTERNAL_DISPLAY_SETTING = "external_display_settings_title"
    private const val SETTINGS_MAXIMIZE_BUTTON_ID = "com.android.systemui:id/maximize_window"
    private const val MIRROR_BUILT_IN_DISPLAY_SWITCH_ID = "com.android.settings:id/switchWidget"
    private const val SETTINGS_UPDATE_TIME_OUT: Long = 2000
}
