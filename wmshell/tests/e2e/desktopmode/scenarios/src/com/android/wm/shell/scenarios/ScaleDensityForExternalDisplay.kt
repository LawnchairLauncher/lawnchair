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

package com.android.wm.shell.scenarios

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.tools.NavBar
import android.tools.Rotation
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By.desc
import androidx.test.uiautomator.By.text
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import org.junit.After
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
)
abstract class ScaleDensityForExternalDisplay : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val wm: IWindowManager = requireNotNull(WindowManagerGlobal.getWindowManagerService())
    private val activityManager: ActivityManager? = instrumentation.context.getSystemService(ActivityManager::class.java)

    private val settingsResources =
        instrumentation.context.packageManager.getResourcesForApplication(SETTINGS_PACKAGE_NAME)
    private val externalDisplaySettings = getSettingsString(EXTERNAL_DISPLAY_SETTING_RES)
    private val increaseDensityDescription = getSettingsString(INCREASE_DENSITY_DESCRIPTION_RES)
    private val decreaseDensityDescription = getSettingsString(DECREASE_DENSITY_DESCRIPTION_RES)

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1) val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Test
    fun increaseDensity() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())
        val initialDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        instrumentation.context
            .startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )

        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(desc(increaseDensityDescription)).click()
        device.waitForWindowUpdate(SETTINGS_PACKAGE_NAME, SETTINGS_UPDATE_TIME_OUT)
        device.waitForIdle()
        val currentDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        assertThat(initialDensity).isLessThan(currentDensity)
    }

    @Test
    fun decreaseDensity() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())
        val initialDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        instrumentation.context
            .startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(desc(decreaseDensityDescription)).click()
        device.waitForWindowUpdate(SETTINGS_PACKAGE_NAME, SETTINGS_UPDATE_TIME_OUT)
        device.waitForIdle()
        val currentDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        assertThat(initialDensity).isGreaterThan(currentDensity)
    }

    @Test
    fun restoreDensityAfterReconnection() {
        var connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())

        instrumentation.context
            .startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )

        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(desc(increaseDensityDescription)).click()
        device.waitForWindowUpdate(SETTINGS_PACKAGE_NAME, SETTINGS_UPDATE_TIME_OUT)
        val lastDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        var idAfterReconnection = connectedDisplayRule.setupTestDisplay()
        device.waitForIdle()
        val densityAfterReconnection = wm.getBaseDisplayDensity(idAfterReconnection)

        assertThat(lastDensity).isEqualTo(densityAfterReconnection)
    }

    @After
    fun teardown() {
        activityManager?.forceStopPackage(SETTINGS_PACKAGE_NAME)
    }

    private fun getSettingsString(resName: String): String {
        val identifier = settingsResources.getIdentifier(resName, "string", SETTINGS_PACKAGE_NAME)
        return settingsResources.getString(identifier)
    }

    private companion object {
        const val SETTINGS_PACKAGE_NAME = "com.android.settings"
        const val EXTERNAL_DISPLAY_SETTING_RES = "external_display_settings_title"
        const val INCREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_larger_desc"
        const val DECREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_smaller_desc"
        const val SETTINGS_UPDATE_TIME_OUT: Long = 2000
    }
}