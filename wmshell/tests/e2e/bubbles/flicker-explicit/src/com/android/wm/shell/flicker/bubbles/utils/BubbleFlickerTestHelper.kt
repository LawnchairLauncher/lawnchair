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

package com.android.wm.shell.flicker.bubbles.utils

import android.content.Context
import android.platform.systemui_tapl.ui.Bubble
import android.platform.systemui_tapl.ui.BubbleBarItem
import android.platform.systemui_tapl.ui.Root
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.MapsAppHelper
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.ConditionsFactory
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.WindowManagerStateHelper.StateSyncBuilder
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.AppIcon
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_ALL_APPS
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_HOME_SCREEN
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_TASK_BAR
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.DismissSource.FROM_BUBBLE_BAR_HANDLE
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.DismissSource.FROM_BUBBLE_BAR_ITEM
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.DismissSource.FROM_FLOATING_BUBBLE_ICON
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A helper to build the bubble operations.
 */
internal object BubbleFlickerTestHelper {

    /**
     * Launches [testApp] into bubble via clicking bubble menu.
     *
     * @param testApp the test app to launch into bubble
     * @param tapl the [LauncherInstrumentation]
     * @param wmHelper the [WindowManagerStateHelper]
     * @param fromSource the source of launching bubble
     */
    fun launchBubbleViaBubbleMenu(
        testApp: StandardAppHelper,
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
        fromSource: BubbleLaunchSource = FROM_ALL_APPS,
    ) {
        val appName = testApp.appName
        val workspace = tapl.goHome()
        // Go to all apps to launch app into a bubble.
        val appIcon = when (fromSource) {
            FROM_ALL_APPS -> workspace.switchToAllApps().getAppIcon(appName)
            FROM_TASK_BAR -> {
                SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, appName)
                val overview = tapl.goHome().switchToOverview()
                val taskBar = overview.taskbar ?: error("Can't find TaskBar")
                taskBar.getAppIcon(testApp.appName)
            }
            FROM_HOME_SCREEN -> {
                val homeScreenIcon = workspace.tryGetWorkspaceAppIcon(testApp.appName)
                if (homeScreenIcon != null) {
                    // If there's an icon on the homeScreen, just use it.
                    homeScreenIcon
                } else {
                    // If not, create a shortcut on the workspace by dragging it from all apps.
                    workspace
                        .switchToAllApps()
                        .getAppIcon(appName)
                        .dragToWorkspace(false /* startActivity */, false /* isWidgetShortcut */)
                    tapl.workspace.getWorkspaceAppIcon(appName)
                }
            }
        }
        launchAndWaitForBubbleAppExpanded(testApp, appIcon, wmHelper)
    }

    /**
     * Launches [testApp] into bubble via dragging the icon from task bar to bubble bar location.
     *
     * @param testApp the test app to launch into bubble
     * @param tapl the [LauncherInstrumentation]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun launchBubbleViaDragToBubbleBar(
        testApp: StandardAppHelper,
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Switch to overview to show task bar.
        val overview = tapl.goHome().switchToOverview()
        val taskBar = overview.taskbar ?: error("Can't find TaskBar")
        val taskBarAppIcon = taskBar.getAppIcon(testApp.appName)
        taskBarAppIcon.dragToBubbleBarLocation(false /* isBubbleBarLeftDropTarget */)

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
        tapl.launchedAppState.assertTaskbarHidden()
        assertWithMessage("The education must not show for Application bubble")
            .that(Root.get().bubble.isEducationVisible).isFalse()
    }

    /**
     * Launch bubble via clicking the overflow view.
     *
     * @param testApp the test app to launch into bubble
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun launchBubbleViaOverflow(testApp: StandardAppHelper, wmHelper: WindowManagerStateHelper) {
        val overflow = Root.get().expandedBubbleStack.openOverflow()
        overflow.verifyHasBubbles()
        overflow.openBubble()

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    }

    /**
     * Collapses the bubble app [testApp] via back key.
     *
     * @param testApp the bubble app to collapse
     * @param tapl the [LauncherInstrumentation]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun collapseBubbleAppViaBackKey(
        testApp: StandardAppHelper,
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
    ) {
        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

        // Press back key to collapse bubble
        tapl.pressBack()

        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)
    }

    /**
     * Collapses the bubble app [testApp] via touching outside the bubble app.
     *
     * @param testApp the bubble app to collapse
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun collapseBubbleAppViaTouchOutside(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

        Root.get().expandedBubbleStack.closeByClickingOutside()

        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)
    }

    /**
     * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble stack.
     *
     * @param testApp the bubble app to expand
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun expandBubbleAppViaTapOnBubbleStack(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Ensure Bubble is in collapse state.
        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)

        // Click bubble to expand
        Root.get().selectedBubble.expand()

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    }

    /**
     * Switches from one expanded bubble to another.
     *
     * @param appSwitchedFrom The app currently expanded in a bubble.
     * @param appSwitchTo The app to switch to, which is in a collapsed bubble.
     * @param wmHelper The [WindowManagerStateHelper].
     */
    fun switchBubble(
        appSwitchedFrom: StandardAppHelper,
        appSwitchTo: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Checks the previous app is in expanded state.
        waitAndAssertBubbleAppInExpandedState(appSwitchedFrom, wmHelper)

        val bubbles = Root.get().expandedBubbleStack.bubbles
        val bubbleAppIcon =
            bubbles.find { bubble -> bubble.containsBubbleApp(appSwitchTo) } ?: error(
                "Can't find the bubble with ${appSwitchTo.packageName}. "
                        + "Bubbles are ${bubbles.describeAll()}"
            )
        bubbleAppIcon.click()

        waitAndAssertBubbleAppInExpandedState(appSwitchTo, wmHelper)
    }

    /** Returns a string describing all bubbles in the list for debugging messages. */
    private fun List<Bubble>.describeAll(): String {
        return joinToString(separator = ", ") { bubble -> bubble.contentDescription() }
    }

    /**
     * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble bar.
     * Note that this method only works on device with bubble bar.
     *
     * @param testApp the bubble app to expand
     * @param uiDevice the UI automator to get the bubble bar [UiObject2]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun expandBubbleAppViaBubbleBar(
        testApp: StandardAppHelper,
        uiDevice: UiDevice,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Ensure Bubble is in collapse state.
        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)

        // Click bubble bar to expand
        uiDevice.bubbleBar?.click() ?: error("Can't find bubble bar")

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    }

    /**
     * Dismisses the bubble app via dragging the bubble to dismiss view.
     *
     * @param testApp the bubble app to dismiss
     * @param wmHelper the [WindowManagerStateHelper]
     * @param previousApp the last focused bubble app, which defaults to `null`
     */
    fun dismissBubbleAppViaBubbleView(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
        previousApp: StandardAppHelper? = null,
    ) = dismissBubble(testApp, wmHelper, FROM_FLOATING_BUBBLE_ICON, previousApp)

    /**
     * Dismisses the bubble app via dragging the bubble bar handle to dismiss view.
     *
     * @param testApp the bubble app to dismiss
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun dismissBubbleAppViaBubbleBarHandle(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
        previousApp: StandardAppHelper? = null,
    ) = dismissBubble(testApp, wmHelper, FROM_BUBBLE_BAR_HANDLE, previousApp)

    /**
     * Dismisses the bubble app via dragging bubble bar item to dismiss view.
     *
     * @param testApp the bubble app to dismiss
     * @param wmHelper the [WindowManagerStateHelper]
     * @param previousApp the last focused bubble app, which defaults to `null`
     */
    fun dismissBubbleAppViaBubbleBarItem(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
        previousApp: StandardAppHelper? = null,
    ) = dismissBubble(testApp, wmHelper, FROM_BUBBLE_BAR_ITEM, previousApp)

    private fun dismissBubble(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
        from: DismissSource,
        previousApp: StandardAppHelper? = null,
    ) {
        if (from == FROM_BUBBLE_BAR_HANDLE) {
            // bubble bar handle is only available when bubble app is expanded.
            waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
        } else {
            // Checks bubble is showing.
            wmHelper
                .StateSyncBuilder()
                .add(ConditionsFactory.isWMStateComplete())
                .withAppTransitionIdle()
                .withBubbleShown()
                .waitForAndVerify()
        }

        when (from) {
            FROM_FLOATING_BUBBLE_ICON -> {
                val bubbles = Root.get().expandedBubbleStack.bubbles
                bubbles.find { bubble -> bubble.containsBubbleApp(testApp) }
                    ?.dismiss()
                    ?: error(
                        "Can't find the bubble with ${testApp.packageName}. "
                                + "Bubbles are ${bubbles.describeAll()}"
                    )
            }
            FROM_BUBBLE_BAR_HANDLE -> {
                Root.get().expandedBubbleStack.bubbleBarHandle.dragToDismiss()
            }
            FROM_BUBBLE_BAR_ITEM -> {
                Root.get().bubbleBar.bubbles.find { item ->
                    item.containsBubbleApp(testApp)
                }
                    ?.dragToDismiss()
                    ?: error("Can't find the bubble bar item")
            }
        }

        if (previousApp != null) {
            // If there's a previous app, the app will be expanded.
            waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
        } else {
            // Otherwise, if there's no previous app, the bubble bar or floating icon will be
            // dismissed.
            waitAndVerifyBubbleGone(wmHelper)
        }
    }

    /**
     * Dismisses the collapsed bubble bar or floating stack.
     *
     * @param tapl the [LauncherInstrumentation]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun dismissAllBubbles(
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
    ) {
        Root.get().verifyNoExpandedBubbleIsVisible()
        if (tapl.isTablet) {
            Root.get().bubbleBar.dragToDismiss()
        } else {
            Root.get().selectedBubble.dismiss()
        }
        waitAndVerifyBubbleGone(wmHelper)
    }

    /**
     * Waits for the bubble to be fully dismissed and gone from the screen.
     *
     * @param displayId The ID of the target display.
     */
    fun StateSyncBuilder.withBubbleFullyDismissedAndGone(displayId: Int = Display.DEFAULT_DISPLAY) =
        withAppTransitionIdle(displayId)
            .add(ConditionsFactory.isWMStateComplete())
            .withBubbleGone()

    /**
     * Waits and verifies the bubble (represented as bubble icon or bubble bar) is gone.
     */
    fun waitAndVerifyBubbleGone(wmHelper: WindowManagerStateHelper) {
        wmHelper.StateSyncBuilder().withBubbleFullyDismissedAndGone().waitForAndVerify()
    }

    fun launchMultipleBubbleAppsViaBubbleMenuAndCollapse(
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
    ): List<Bubble> {
        // Go to all apps to launch app into a bubble.
        tapl.goHome().switchToAllApps()
        val allApps = tapl.allApps

        bubbleApps.forEach { testApp ->
            val appIcon = allApps.getAppIcon(testApp.appName)
            launchAndWaitForBubbleAppExpanded(testApp, appIcon, wmHelper)
            if (testApp != bubbleApps.last()) {
                Root.get().expandedBubbleStack.closeByClickingOutside()
            }
        }

        assertBubbleIconsAligned(tapl)

        val expandedBubbleStack = Root.get().expandedBubbleStack
        val bubbles = expandedBubbleStack.bubbles
        expandedBubbleStack.closeByClickingOutside()

        return bubbles
    }

    /**
     * Dismisses all bubble apps launched by [launchMultipleBubbleAppsViaBubbleMenuAndCollapse].
     */
    fun dismissMultipleBubbles() {
        bubbleApps.forEach { app -> app.exit() }
    }

    /**
     * Dumps the current window hierarchy to a file.
     *
     * It's useful to debug view hierarchy issue.
     */
    fun dumpViewHierarchy() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dumpFile = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "hierarchy_dump.xml"
        )

        try {
            FileOutputStream(dumpFile).use { outputStream ->
                uiDevice.dumpWindowHierarchy(outputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Waits for a bubble app matching [componentMatcher] to be visible and expanded.
     *
     * @param componentMatcher component to search for.
     * @param displayId of the target display
     */
    fun StateSyncBuilder.withBubbleExpanded(
        componentMatcher: IComponentMatcher,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) =
        withAppTransitionIdle(displayId)
            .add(ConditionsFactory.isWMStateComplete())
            .withTopVisibleApp(componentMatcher)
            .withBubbleShown()

    /**
     * Waits for a bubble app matching [componentMatcher] to be in a collapsed state.
     *
     * @param componentMatcher component to search for.
     * @param displayId of the target display
     */
    fun StateSyncBuilder.withBubbleCollapsed(
        componentMatcher: IComponentMatcher,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) =
        withAppTransitionIdle(displayId)
            .add(ConditionsFactory.isWMStateComplete())
            .withWindowSurfaceDisappeared(componentMatcher)
            .withBubbleShown()

    private fun assertBubbleIconsAligned(tapl: LauncherInstrumentation) {
        val isBubbleIconsAligned = Root.get().expandedBubbleStack.bubbles.stream()
            .mapToInt { bubbleIcon: Bubble ->
                if (tapl.isTablet && !Flags.enableBubbleBar()) {
                    // For large screen devices without bubble bar, the bubble icons are aligned
                    // vertically.
                    bubbleIcon.visibleCenter.x
                } else {
                    // Otherwise, the bubble icons are aligned horizontally.
                    bubbleIcon.visibleCenter.y
                }
            }
            .distinct()
            .count() == 1L


        val bubblePositions = StringBuilder()
        if (!isBubbleIconsAligned) {
            Root.get().expandedBubbleStack.bubbles.forEach { bubble ->
                bubblePositions.append(
                    "{${bubble.contentDescription()} center: ${bubble.visibleCenter}}, "
                )
            }
        }
        assertWithMessage("The bubble icons must be aligned, but was $bubblePositions")
            .that(isBubbleIconsAligned)
            .isTrue()
    }

    private fun launchAndWaitForBubbleAppExpanded(
        testApp: StandardAppHelper,
        appIcon: AppIcon,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Open the bubble menu and click.
        appIcon.openMenu().bubbleMenuItem.click()

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

        // The bubble will be occluded if IME shows.
        if (testApp !is ImeAppHelper) {
            assertWithMessage("The education must not show for Application bubble")
                .that(Root.get().bubble.isEducationVisible).isFalse()
        }
    }

    private fun waitAndAssertBubbleAppInExpandedState(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        wmHelper.StateSyncBuilder().withBubbleExpanded(testApp).waitForAndVerify()

        // Don't check the overflow if the testApp is IME because IME occludes the overflow.
        if (testApp !is ImeAppHelper) {
            Root.get().expandedBubbleStack.verifyBubbleOverflowIsVisible()
        }
    }

    private fun waitAndAssertBubbleAppInCollapseState(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        wmHelper.StateSyncBuilder().withBubbleCollapsed(testApp).waitForAndVerify()
    }

    private val UiDevice.bubbleBar: UiObject2?
        get() = wait(Until.findObject(launcherSelector(RES_ID_BUBBLE_BAR)), FIND_OBJECT_TIMEOUT)

    private fun UiDevice.launcherSelector(resourcesId: String): BySelector =
        By.pkg(launcherPackageName).res(launcherPackageName, resourcesId)

    private fun Bubble.containsBubbleApp(testApp: StandardAppHelper): Boolean =
        contentDescription().contains(testApp.packageName)

    private fun BubbleBarItem.containsBubbleApp(testApp: StandardAppHelper): Boolean =
        item.contentDescription.contains(testApp.packageName)

    /** The source to launch the bubble app by bubble menu. */
    internal enum class BubbleLaunchSource {
        /** Launches the bubble from all apps page. */
        FROM_ALL_APPS,

        /** Launches the bubble from home screen page. */
        FROM_HOME_SCREEN,

        /** Launches the bubble from the task bar. */
        FROM_TASK_BAR,
    }

    private const val FIND_OBJECT_TIMEOUT = 4000L
    private const val RES_ID_BUBBLE_BAR = "taskbar_bubbles"

    // TODO(b/396020056): The max number of bubbles is 5. Make the test more flexible
//  if the max number could be overridden.
    private val bubbleApps = listOf(
        CalculatorAppHelper(),
        BrowserAppHelper(),
        MapsAppHelper(),
        MessagingAppHelper(),
        ClockAppHelper(),
    )

    private enum class DismissSource {
        /** Dismisses a bubble app from bubble icon. */
        FROM_FLOATING_BUBBLE_ICON,
        /** Dismisses a bubble app from bubble bar handle. */
        FROM_BUBBLE_BAR_HANDLE,
        /** Dismisses a bubble app from bubble bar. */
        FROM_BUBBLE_BAR_ITEM,
    }
}