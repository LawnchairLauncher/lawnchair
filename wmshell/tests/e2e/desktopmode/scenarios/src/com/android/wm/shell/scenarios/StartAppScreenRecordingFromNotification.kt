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

import android.app.Instrumentation
import android.platform.systemui_tapl.controller.QuickSettingsController
import android.platform.systemui_tapl.ui.Root
import android.tools.Rotation
import android.tools.helpers.SYSTEMUI_PACKAGE
import android.tools.helpers.retryIfStaleObject
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.systemui.Flags.qsUiRefactorComposeFragment
import java.util.regex.Pattern
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Test for starting screen recording from a QS Tile while in Desktop:
 * - Start Desktop Mode by moving an app to Desktop.
 * - Open the notification shade and use a QuickSettings Tile for start screen recording.
 * - Then make sure the panel for single app selection appears.
 */
@Ignore("Test Base Class")
abstract class StartAppScreenRecordingFromNotification(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val device = UiDevice.getInstance(instrumentation)
    private val packageManager = instrumentation.context.packageManager

    val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val sysuiSelectorApp =
        ComponentNameMatcher(
            SYSTEMUI_PACKAGE,
            "com.android.systemui.mediaprojection.appselector.MediaProjectionAppSelectorActivity"
        )

    @Before
    fun setup() {
        QuickSettingsController.get().addAsFirstTile(SCREEN_RECORD_TILE_NAME)
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun startMediaProjection() {
        startSingleAppMediaProjection(wmHelper)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }

    fun startSingleAppMediaProjection(wmHelper: WindowManagerStateHelper) {
        openScreenRecorder()
        chooseSingleAppOption()
        startScreenSharing()
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceAppeared(sysuiSelectorApp)
            .waitForAndVerify()
    }

    protected fun openScreenRecorder() {
        val quickSettings = Root.get().openQuickSettings()

        // Launch the screen recorder by clicking the QuickSettings tile
        if (qsUiRefactorComposeFragment()) {
            val screenRecordTile = quickSettings.findComposeTile("Screen record")
            screenRecordTile.click()
        } else {
            val screenRecordTile = quickSettings.findTile("Screen record")
            screenRecordTile.clickWithoutAssertions()
        }

        // Wait for the app selection to launch
        Root.get().mediaProjectionPermissionDialog
    }

    private fun chooseSingleAppOption() {
        findObject(By.res(SCREEN_SHARE_OPTIONS_PATTERN)).also { it.click() }

        val singleAppString = getSysUiResourceString(SINGLE_APP_STRING_RES_NAME)
        retryIfStaleObject { findObject(By.text(singleAppString)).also { it.click() } }
    }

    private fun startScreenSharing() {
        findObject(By.res(ACCEPT_RESOURCE_ID)).also { it.click() }
    }

    private fun findObject(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), TIMEOUT) ?: error("Can't find object $selector")

    private fun getSysUiResourceString(resName: String): String =
        with(packageManager.getResourcesForApplication(SYSTEMUI_PACKAGE)) {
            getString(getIdentifier(resName, "string", SYSTEMUI_PACKAGE))
        }

    companion object {
        const val SCREEN_RECORD_TILE_NAME = "screenrecord"
        const val TIMEOUT: Long = 5000L
        const val ACCEPT_RESOURCE_ID: String = "android:id/button1"
        val SCREEN_SHARE_OPTIONS_PATTERN: Pattern =
            Pattern.compile("$SYSTEMUI_PACKAGE:id/screen_share_mode_(options|spinner)")
        const val SINGLE_APP_STRING_RES_NAME: String =
            "screenrecord_permission_dialog_option_text_single_app"
    }
}
