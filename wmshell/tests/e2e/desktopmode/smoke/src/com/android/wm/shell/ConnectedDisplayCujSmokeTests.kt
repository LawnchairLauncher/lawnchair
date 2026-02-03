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

package com.android.wm.shell

import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.windowingModeToString
import android.content.Intent
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayTopology
import android.hardware.input.InputManager
import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.rule.ScreenRecordRule
import android.platform.uiautomatorhelpers.BetterSwipe
import android.platform.uiautomatorhelpers.WaitUtils
import android.provider.Settings
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.helpers.SYSTEMUI_PACKAGE
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.launcher3.tapl.TestHelpers
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import platform.test.desktop.DesktopMouseTestRule
import platform.test.desktop.ShadeDisplayGoesAroundTestRule
import platform.test.desktop.SimulatedConnectedDisplayTestRule

// TODO(b/416608975) - Move the utility methods to shared library or/and utilize existing library (
// e.g., sysui-tapl).
// TODO(b/416610249) - Support all form-factors
// TODO(b/418620154) - Use test apps instead of real apps.
/**
 * Tests to verify the smoke test scenario defined in go/cd-smoke.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
@RunWith(BlockJUnit4ClassRunner::class)
@Postsubmit
class ConnectedDisplayCujSmokeTests {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val browserApp = BrowserAppHelper(instrumentation)
    private val clockApp = ClockAppHelper(instrumentation)
    private val desktopState = DesktopState.fromContext(context)
    private val canEnterExtended = desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    // TODO(b/419392000) - Remove once [DesktopMouseTestRule] supports dynamic display changes.
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val displayIdsWithMouseScalingDisabled = mutableListOf<Int>()

    @get:Rule(order = 0)
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    // This rule must have higher priority than other setup-related rules to skip certain tests on
    // the unsupported device as soon as possible.
    @get:Rule(order = 1)
    val desktopDeviceTypeRule = DesktopDeviceTypeRule()

    @get:Rule(order = 2)
    val screenRecordRule = ScreenRecordRule(/* keepTestLevelRecordingOnSuccess= */ false)

    @get:Rule(order = 3)
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @get:Rule(order = 4)
    val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @get:Rule(order = 5)
    val shadeDisplayGoesAroundTestRule = ShadeDisplayGoesAroundTestRule()

    @get:Rule(order = 6)
    val desktopMouseRule = DesktopMouseTestRule()

    @Before
    fun setup() {
        Assume.assumeTrue(desktopState.canEnterDesktopMode)

        // Ensure rotation in launcher.
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(Rotation.ROTATION_0.value)

        // Ensure launcher is visible.
        device.pressHome()
        instrumentation.waitForIdleSync()
        device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)),
            TIMEOUT.inWholeMilliseconds)

        // Ensure the transient taskbar is disabled.
        tapl.enableTransientTaskbar(false)
    }

    fun cuj1() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        disableMouseScaling(externalDisplayId)

        // Open settings.
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(DEFAULT_DISPLAY).toBundle()
        )

        // Reset topology.
        resetTopology(externalDisplayId)

        // Navigate to display topology settings in Settings app
        checkNotNull(
            device.wait(
                Until.findObject(By.text(CONNECTED_DEVICES_TEXT)),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Can't find a connected device on setting" }.click()
        checkNotNull(
            device.wait(
                Until.findObject(By.text(EXTERNAL_DISPLAY_TEXT)),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Can't find a external display on setting" }.click()

        // Modify the topology.
        val paneObject = checkNotNull(
            device.wait(
                Until.findObject(By.res(SETTINGS_PACKAGE, DISPLAY_TOPOLOGY_PANE_CONTENT_RES_ID)),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Can't find a display panel on setting" }

        val defaultDisplayObject = findDefaultDisplayObject(paneObject)
        val originalTopology = displayManager.displayTopology
        BetterSwipe.swipe(
            start = PointF(
                defaultDisplayObject.visibleBounds.exactCenterX(),
                defaultDisplayObject.visibleBounds.exactCenterY()
            ),
            end = PointF(
                defaultDisplayObject.visibleBounds.exactCenterX(),
                paneObject.visibleBounds.bottom.toFloat() - 1f
            )
        )
        WaitUtils.ensureThat("Display topology changed", timeout = TIMEOUT.toJavaDuration()) {
            originalTopology != displayManager.displayTopology
        }

        // Ensure a cursor moves between displays.
        desktopMouseRule.move(
            externalDisplayId,
            device.getDisplayWidth(externalDisplayId) / 2,
            device.getDisplayHeight(externalDisplayId) / 2,
        )
        desktopMouseRule.move(
            DEFAULT_DISPLAY,
            device.getDisplayWidth(DEFAULT_DISPLAY) / 2,
            device.getDisplayHeight(DEFAULT_DISPLAY) / 2,
        )
    }

    // Extended: When a device is connected to ext.display, the user can change topology in Settings
    // > Connected devices > Connected Display
    @Test
    @ExtendedOnly
    fun cuj1e() {
        cuj1()
    }

    // Projected: When a device is connected to ext.display, the user can change topology in
    // Settings > Connected devices > Connected Display
    @Test
    @ProjectedOnly
    fun cuj1p() {
        cuj1()
    }

    // Extended: When an ext. display is connected, Taskbar shows on both displays
    @Test
    @ExtendedOnly
    fun cuj2e() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()

        assertTaskbarVisible(DEFAULT_DISPLAY)
        assertTaskbarVisible(externalDisplayId)
    }

    // Extended: When an ext. display is connected, apps can be opened via Taskbar or All Apps on
    // the external monitor and they default to Desktop Windowing mode
    @Test
    @ExtendedOnly
    fun cuj3e() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        launchAppFromAllApps(externalDisplayId, clockApp)
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
    }

    // Projected: When an ext. display is connected, apps can be opened via Taskbar or All Apps on
    // the external monitor and they default to Desktop Windowing mode
    @Test
    @ProjectedOnly
    fun cuj3p() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        launchAppFromAllApps(externalDisplayId, clockApp)
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
    }

    // Projected: When a phone / foldable is connected to a display, the phone / foldable state
    // remains unchanged and a blank desktop session starts on the external monitor
    @Test
    @ProjectedOnly
    fun cuj4p() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()

        assertTaskbarInvisible(DEFAULT_DISPLAY)
        assertTaskbarVisible(externalDisplayId)
        // Check that no task but fullscreen Launcher is visible on the external display.
        verifyActivityState(
            ComponentNameMatcher(device.launcherPackageName, className = ""),
            WINDOWING_MODE_FULLSCREEN,
            externalDisplayId,
            visible = true
        )
    }

    // Extended: All apps can be invoked on either display at any time, but will only ever be shown
    // on one
    @Test
    @ExtendedOnly
    fun cuj5e() {
        browserApp.launchViaIntent()
        verifyActivityState(browserApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)
        verifyWindowCount(browserApp, expectedCount = 1)

        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        launchAppFromTaskbar(externalDisplayId, browserApp)
        // TODO(b/418620963) - Check the display id of the app window here.
        verifyWindowCount(browserApp, expectedCount = 1)
    }

    // Projected: All apps can be invoked on either display at any time, but will only ever be shown
    // on one
    @Test
    @ProjectedOnly
    fun cuj5p() {
        launchAppFromAllApps(DEFAULT_DISPLAY, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)
        verifyWindowCount(browserApp, expectedCount = 1)

        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
        verifyWindowCount(browserApp, expectedCount = 1)
    }

    fun cuj6() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)
        context.startActivity(
            clockApp.openAppIntent,
            ActivityOptions.makeBasic().setLaunchDisplayId(externalDisplayId).toBundle()
        )
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        // Fullscreen via app header.
        openAppHeaderMenuForTheApp(clockApp)
        waitForSysUiObjectForTheApp(clockApp, FULLSCREEN_BUTTON_RES_ID).click()
        verifyActivityState(
            clockApp,
            WINDOWING_MODE_FULLSCREEN,
            externalDisplayId,
            visible = true
        )

        // Enter desktop via app header.
        openAppHandleMenuForFullscreenApp(externalDisplayId)
        waitForSysUiObjectForTheApp(clockApp, DESKTOP_BUTTON_RES_ID).click()
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        // TODO(b/418620952) - Add splitscreen test once it's ready.
    }

    // Extended: All window modes are supported on the connected display, including split screen
    @Test
    @ExtendedOnly
    fun cuj6e() {
        cuj6()
    }

    // Projected: All window modes are supported on the connected display, including split screen
    @Test
    @ProjectedOnly
    fun cuj6p() {
        cuj6()
    }

    // Extended: Opening an app from a full screen view will switch back to the desktop session,
    // going to overview in this state will show the desktop view, with any full screen apps as
    // tiles to the left
    @Test
    @ExtendedOnly
    fun cuj7e() {
        // TODO(b/416610249) - Remove assumption of touch-first-by-default.
        // Start an fullscreen app assuming the device is in touch-first mode.
        clockApp.launchViaIntent()
        verifyActivityState(clockApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)

        connectedDisplayRule.setupTestDisplay()

        // Start a freeform app.
        launchAppFromTaskbar(DEFAULT_DISPLAY, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, DEFAULT_DISPLAY, visible = true)
        verifyActivityState(clockApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = false)

        // Verify the overview has both the fullscreen app and the desktop.
        val overview = tapl.launchedAppState.switchToOverview()
        overview.flingBackward()
        assertTrue("Can't find a desktop overview item", overview.currentTask.isDesktop)
        overview.flingForward()
        assertOverviewItemVisible(clockApp)
    }

    // Projected: On the external display, opening an app from a full screen view will switch back
    // to the desktop session, going to overview in this state will show the desktop view, with any
    // full screen apps as tiles to the left
    @Test
    @ProjectedOnly
    fun cuj7p() {
        // Clear all tasks
        with(tapl.workspace.openOverviewFromRecentsKeyboardShortcut()) {
            if (hasTasks()) {
                dismissAllTasks()
            }
        }

        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        // Start an app and make it fullscreen.
        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
        openAppHeaderMenuForTheApp(browserApp)
        waitForSysUiObjectForTheApp(browserApp, FULLSCREEN_BUTTON_RES_ID).click()
        verifyActivityState(
            browserApp,
            WINDOWING_MODE_FULLSCREEN,
            externalDisplayId,
            visible = true
        )

        // Start a freeform app.
        context.startActivity(
            clockApp.openAppIntent,
            ActivityOptions.makeBasic().setLaunchDisplayId(externalDisplayId).toBundle()
        )
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
        verifyActivityState(
            browserApp,
            WINDOWING_MODE_FULLSCREEN,
            externalDisplayId,
            visible = false
        )

        // Verify the overview has both the fullscreen app and the desktop.
        val overview = tapl.workspace.openOverviewFromRecentsKeyboardShortcut()
        overview.flingBackward()
        assertTrue("Can't find a desktop overview item", overview.currentTask.isDesktop)
        overview.flingForward()
        assertOverviewItemVisible(browserApp)
    }

    // Extended: Desktop Windows can be dragged across displays using a cursor when external display
    // isn’t in fullscreen
    @Test
    @ExtendedOnly
    fun cuj8e() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        disableMouseScaling(externalDisplayId)
        assertTaskbarVisible(DEFAULT_DISPLAY)

        launchAppFromAllApps(DEFAULT_DISPLAY, clockApp)
        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, DEFAULT_DISPLAY, visible = true)

        // Move the cursor to the caption.
        val captionBounds =
            checkNotNull(
                waitForSysUiObjectForTheApp(
                    clockApp,
                    OPEN_MENU_BUTTON_RES_ID
                ).visibleBounds
            )
        desktopMouseRule.move(DEFAULT_DISPLAY, captionBounds.centerX(), captionBounds.centerY())

        // Drag the window to the external display.
        desktopMouseRule.startDrag()
        desktopMouseRule.move(
            externalDisplayId,
            device.getDisplayWidth(externalDisplayId) / 2,
            device.getDisplayHeight(externalDisplayId) / 2,
        )
        desktopMouseRule.stopDrag()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        verifyActivityState(clockApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)
    }

    // Projected: If an app is open on a device, and selected on the other display on the taskbar,
    // it is moved across
    @Test
    @ProjectedOnly
    fun cuj9p() {
        browserApp.launchViaIntent()
        verifyActivityState(browserApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)

        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        assertTaskbarVisible(externalDisplayId)

        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        launchAppFromAllApps(DEFAULT_DISPLAY, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)
    }

    fun cuj10() {
        clockApp.launchViaIntent()
        verifyActivityState(clockApp, WINDOWING_MODE_FULLSCREEN, DEFAULT_DISPLAY, visible = true)

        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        launchAppFromTaskbar(externalDisplayId, browserApp)
        verifyActivityState(browserApp, WINDOWING_MODE_FREEFORM, externalDisplayId, visible = true)

        // Verify connecting a display doesn't crash.
        connectedDisplayRule.setupTestDisplays(0)

        // Verify disconnecting the display doesn't crash.
        connectedDisplayRule.setupTestDisplay()
    }

    // Extended: Device state should be recoverable when connecting and disconnecting an
    // ext.display (i.e. does not crash)
    @Test
    @ExtendedOnly
    fun cuj10e() {
        cuj10()
    }

    // Projected: Device state should be recoverable when connecting and disconnecting an
    // ext.display (i.e. does not crash)
    @Test
    @ProjectedOnly
    fun cuj10p() {
        cuj10()
    }

    @After
    fun teardown() {
        // TODO(b/419392000) - Remove once [DesktopMouseTestRule] supports dynamic display changes.
        for (displayId in displayIdsWithMouseScalingDisabled) {
            inputManager.setMouseScalingEnabled(true, displayId)
        }
        displayIdsWithMouseScalingDisabled.clear()

        closeAllDesktopApps()
        connectedDisplayRule.setupTestDisplays(0)
    }

    // TODO(b/418095917) - Find more reliable way.
    fun findDefaultDisplayObject(paneObject: UiObject2) = paneObject.children[1]

    fun launchAppFromTaskbar(displayId: Int, appHelper: StandardAppHelper) {
        val selector = By.text(appHelper.appName).hasParent(taskbarSelector(displayId))
        val appName = appHelper.appName
        checkNotNull(
            device.wait(
                Until.findObject(selector),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Can't find an app icon of $appName on taskbar on display#$displayId" }.click()
    }

    fun openAllApps(displayId: Int) {
        if (!canEnterExtended && displayId == DEFAULT_DISPLAY) {
            val swipeY = device.getDisplayHeight(displayId) / 2f
            val swipeX = device.getDisplayWidth(displayId) / 2f
            BetterSwipe.swipe(
                start = PointF(swipeX, swipeY),
                end = PointF(swipeX, 0f),
                displayId = displayId
            )
            instrumentation.uiAutomation.syncInputTransactions()
        } else {
            val taskbar =
                checkNotNull(
                    device.wait(
                    Until.findObject(taskbarSelector(displayId)),
                    TIMEOUT.inWholeMilliseconds
                    )
                ) { "Can't find a taskbar on display#$displayId" }
            taskbar.children.first().click()
        }
    }

    fun launchAppFromAllApps(displayId: Int, appHelper: StandardAppHelper) =
        launchAppFromAllApps(displayId, appHelper.appName)

    fun launchAppFromAllApps(displayId: Int, appName: String) {
        openAllApps(displayId)

        val appsListSelector = appsListSelector(displayId)
        val appsList = device.wait(Until.findObject(appsListSelector), TIMEOUT.inWholeMilliseconds)
        val appIconSelector = By.text(appName).hasParent(appsListSelector)

        // Scroll down All Apps until the app icon is visible.
        val appIcon = checkNotNull((1..SCROLL_RETRY_MAX).firstNotNullOfOrNull {
            device.wait(Until.findObject(appIconSelector), TIMEOUT.inWholeMilliseconds) ?: run {
                BetterSwipe.swipe(
                    start = PointF(
                        appsList.visibleBounds.exactCenterX(),
                        appsList.visibleBounds.exactCenterY()
                    ),
                    end = PointF(
                        appsList.visibleBounds.exactCenterX(),
                        appsList.visibleBounds.top.toFloat() + 1f
                    ),
                    displayId = displayId
                )
                instrumentation.uiAutomation.syncInputTransactions()
                null
            }
        }) { "Can't find an app icon of $appName on all apps on display#$displayId" }
        appIcon.click()
    }

    fun assertTaskbarVisible(displayId: Int) =
        checkNotNull(
            device.wait(
                Until.findObject(taskbarSelector(displayId)),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Can't find a taskbar on display#$displayId" }

    fun assertTaskbarInvisible(displayId: Int) =
        checkNotNull(
            device.wait(Until.gone(taskbarSelector(displayId)), TIMEOUT.inWholeMilliseconds)
        ) { "A taskbar is visible unexpectedly on display#$displayId" }

    fun waitForSysUiObjectForTheApp(
        componentMatcher: IComponentNameMatcher,
        resId: String
    ): UiObject2 {
        val objects =
            checkNotNull(
                device.wait(
                    Until.findObjects(By.res(SYSTEMUI_PACKAGE, resId)),
                    TIMEOUT.inWholeMilliseconds
                )
            ) { "Unable to find view for $resId" }
        // TODO(b/416608975) - Check the app window bounds to filter out the uninteresting objects.
        return objects.first()
    }

    fun openAppHeaderMenuForTheApp(componentMatcher: IComponentNameMatcher) =
        waitForSysUiObjectForTheApp(componentMatcher, OPEN_MENU_BUTTON_RES_ID).click()

    fun openAppHandleMenuForFullscreenApp(displayId: Int) {
        val selector = By.res(SYSTEMUI_PACKAGE, STATUS_BAR_CONTAINER_RES_ID).displayId(displayId)
        (checkNotNull(
            device.wait(
                Until.findObject(selector),
                TIMEOUT.inWholeMilliseconds
            )
        ) { "Unable to find view for $selector" }).click()
    }

    fun assertOverviewItemVisible(appHelper: StandardAppHelper) {
        val objects =
            checkNotNull(
                device.wait(
                    Until.findObjects(
                        By.res(
                            TestHelpers.getOverviewPackageName(),
                            TASK_VIEW_SINGLE_RES_ID
                        )
                    ), TIMEOUT.inWholeMilliseconds
                )
            ) { "Unable to find overview item" }

        assertNotNull("Can't find overview item for ${appHelper.appName}",
            objects.find { it.hasObject(By.descEndsWith(appHelper.appName)) })
    }

    fun verifyActivityState(
        componentMatcher: IComponentNameMatcher,
        windowingMode: Int,
        displayId: Int,
        visible: Boolean
    ) {
        val packageName = componentMatcher.packageName
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle(displayId)
            .add("$packageName is on display#$displayId") { dump ->
                val display = requireNotNull(dump.wmState.getDisplay(displayId)) {
                    "Display#$displayId not found"
                }
                display.containsActivity(componentMatcher)
            }
            .add("$packageName is " + (if (visible) "visible" else "invisible")) { dump ->
                dump.wmState.isActivityVisible(componentMatcher) == visible
            }
            .add("$packageName is in ${windowingModeToString(windowingMode)}") { dump ->
                dump.wmState.getActivity(componentMatcher)?.windowingMode == windowingMode
            }
            .waitForAndVerify()
    }

    fun verifyWindowCount(componentMatcher: IComponentNameMatcher, expectedCount: Int) {
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            .add("${componentMatcher.packageName} has $expectedCount windows") { dump ->
                val appWindows = dump.wmState.appWindows
                appWindows.count { componentMatcher.windowMatchesAnyOf(it) } == expectedCount
            }
            .waitForAndVerify()
    }

    fun resetTopology(externalDisplayId: Int) {
        val displayInfos = arrayListOf<DisplayInfo>(DisplayInfo().also {
            displayManager.getDisplay(DEFAULT_DISPLAY).getDisplayInfo(it)
        }, DisplayInfo().also {
            displayManager.getDisplay(externalDisplayId).getDisplayInfo(it)
        })
        val topology = DisplayTopology()
        for (info in displayInfos) {
            topology.addDisplay(
                    info.displayId, info.logicalWidth, info.logicalHeight, info.logicalDensityDpi)
        }
        displayManager.displayTopology = topology
        WaitUtils.ensureThat("Display topology updated", timeout = TIMEOUT.toJavaDuration()) {
            topology == displayManager.displayTopology
        }
    }

    fun closeAllDesktopApps() {
        val closeButtonSelector = By.res(SYSTEMUI_PACKAGE, CLOSE_BUTTON)
        device.wait(
            Until.findObjects(closeButtonSelector),
            TIMEOUT.inWholeMilliseconds
        )?.forEach {
            it.click()
        }
        device.wait(
            Until.gone(closeButtonSelector),
            TIMEOUT.inWholeMilliseconds
        )
    }

    fun taskbarSelector(displayId: Int): BySelector =
        By.res(device.launcherPackageName, TASKBAR_RES_ID).displayId(displayId)

    fun appsListSelector(displayId: Int): BySelector =
        By.res(device.launcherPackageName, APPS_LIST_VIEW_RES_ID).displayId(displayId)

    // TODO(b/419392000) - Remove once [DesktopMouseTestRule] supports dynamic display changes.
    fun disableMouseScaling(displayId: Int) {
        displayIdsWithMouseScalingDisabled += displayId
        inputManager.setMouseScalingEnabled(false, displayId)
    }

    private companion object {
        const val TASKBAR_RES_ID = "taskbar_view"
        const val STATUS_BAR_CONTAINER_RES_ID = "status_bar_container"
        const val OPEN_MENU_BUTTON_RES_ID = "open_menu_button"
        const val FULLSCREEN_BUTTON_RES_ID = "fullscreen_button"
        const val DESKTOP_BUTTON_RES_ID = "desktop_button"
        const val TASK_VIEW_SINGLE_RES_ID = "task_view_single"
        const val APPS_LIST_VIEW_RES_ID = "apps_list_view"
        const val CLOSE_BUTTON = "close_window"
        const val DISPLAY_TOPOLOGY_PANE_CONTENT_RES_ID = "display_topology_pane_content"
        const val EXTERNAL_DISPLAY_TEXT = "External Display"
        const val CONNECTED_DEVICES_TEXT = "Connected devices"
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SCROLL_RETRY_MAX = 5
        val TIMEOUT: Duration = 10.seconds
    }
}
