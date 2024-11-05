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

package com.android.wm.shell.flicker.utils

import android.app.Instrumentation
import android.graphics.Point
import android.os.SystemClock
import android.tools.Rotation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toFlickerComponent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.NotificationAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.server.wm.flicker.testapp.ActivityOptions.SplitScreen.Primary
import org.junit.Assert.assertNotNull

object SplitScreenUtils {
    private const val TIMEOUT_MS = 3_000L
    private const val DRAG_DURATION_MS = 1_000L
    private const val NOTIFICATION_SCROLLER = "notification_stack_scroller"
    private const val DIVIDER_BAR = "docked_divider_handle"
    private const val OVERVIEW_SNAPSHOT = "snapshot"
    private const val GESTURE_STEP_MS = 16L
    private val LONG_PRESS_TIME_MS = ViewConfiguration.getLongPressTimeout() * 2L
    private val SPLIT_DECOR_MANAGER = ComponentNameMatcher("", "SplitDecorManager#")

    private val notificationScrollerSelector: BySelector
        get() = By.res(SYSTEM_UI_PACKAGE_NAME, NOTIFICATION_SCROLLER)
    private val notificationContentSelector: BySelector
        get() = By.text("Flicker Test Notification")
    private val dividerBarSelector: BySelector
        get() = By.res(SYSTEM_UI_PACKAGE_NAME, DIVIDER_BAR)
    private val overviewSnapshotSelector: BySelector
        get() = By.res(LAUNCHER_UI_PACKAGE_NAME, OVERVIEW_SNAPSHOT)

    fun getPrimary(instrumentation: Instrumentation): StandardAppHelper =
        SimpleAppHelper(
            instrumentation,
            ActivityOptions.SplitScreen.Primary.LABEL,
            ActivityOptions.SplitScreen.Primary.COMPONENT.toFlickerComponent()
        )

    fun getSecondary(instrumentation: Instrumentation): StandardAppHelper =
        SimpleAppHelper(
            instrumentation,
            ActivityOptions.SplitScreen.Secondary.LABEL,
            ActivityOptions.SplitScreen.Secondary.COMPONENT.toFlickerComponent()
        )

    fun getNonResizeable(instrumentation: Instrumentation): NonResizeableAppHelper =
        NonResizeableAppHelper(instrumentation)

    fun getSendNotification(instrumentation: Instrumentation): NotificationAppHelper =
        NotificationAppHelper(instrumentation)

    fun getIme(instrumentation: Instrumentation): ImeAppHelper = ImeAppHelper(instrumentation)

    fun waitForSplitComplete(
        wmHelper: WindowManagerStateHelper,
        primaryApp: IComponentMatcher,
        secondaryApp: IComponentMatcher,
    ) {
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceAppeared(primaryApp)
            .withWindowSurfaceAppeared(secondaryApp)
            .withSplitDividerVisible()
            .waitForAndVerify()
    }

    fun enterSplit(
        wmHelper: WindowManagerStateHelper,
        tapl: LauncherInstrumentation,
        device: UiDevice,
        primaryApp: StandardAppHelper,
        secondaryApp: StandardAppHelper,
        rotation: Rotation
    ) {
        primaryApp.launchViaIntent(wmHelper)
        secondaryApp.launchViaIntent(wmHelper)
        ChangeDisplayOrientationRule.setRotation(rotation)
        tapl.goHome()
        wmHelper.StateSyncBuilder().withHomeActivityVisible().waitForAndVerify()
        splitFromOverview(tapl, device, rotation)
        waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
    }

    fun enterSplitViaIntent(
        wmHelper: WindowManagerStateHelper,
        primaryApp: StandardAppHelper,
        secondaryApp: StandardAppHelper
    ) {
        val stringExtras = mapOf(Primary.EXTRA_LAUNCH_ADJACENT to "true")
        primaryApp.launchViaIntent(wmHelper, null, null, stringExtras)
        waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
    }

    fun splitFromOverview(tapl: LauncherInstrumentation, device: UiDevice, rotation: Rotation) {
        // Note: The initial split position in landscape is different between tablet and phone.
        // In landscape, tablet will let the first app split to right side, and phone will
        // split to left side.
        if (tapl.isTablet) {
            // TAPL's currentTask on tablet is sometimes not what we expected if the overview
            // contains more than 3 task views. We need to use uiautomator directly to find the
            // second task to split.
            val home = tapl.workspace.switchToOverview()
            ChangeDisplayOrientationRule.setRotation(rotation)
            val isGridOnlyOverviewEnabled = tapl.isGridOnlyOverviewEnabled
            if (isGridOnlyOverviewEnabled) {
                home.currentTask.tapMenu().tapSplitMenuItem()
            } else {
                home.overviewActions.clickSplit()
            }
            val snapshots = device.wait(Until.findObjects(overviewSnapshotSelector), TIMEOUT_MS)
            if (snapshots == null || snapshots.size < 1) {
                error("Fail to find a overview snapshot to split.")
            }

            // Find the second task in the upper (or bottom for grid only Overview) right corner in
            // split select mode by sorting 'left' in descending order and 'top' in ascending (or
            // descending for grid only Overview) order.
            snapshots.sortWith { t1: UiObject2, t2: UiObject2 ->
                t2.getVisibleBounds().left - t1.getVisibleBounds().left
            }
            snapshots.sortWith { t1: UiObject2, t2: UiObject2 ->
                if (isGridOnlyOverviewEnabled) {
                    t2.getVisibleBounds().top - t1.getVisibleBounds().top
                } else {
                    t1.getVisibleBounds().top - t2.getVisibleBounds().top
                }
            }
            snapshots[0].click()
        } else {
            val rotationCheckEnabled = tapl.getExpectedRotationCheckEnabled()
            tapl.setExpectedRotationCheckEnabled(false) // disable rotation check to enter overview
            val home = tapl.workspace.switchToOverview()
            tapl.setExpectedRotationCheckEnabled(rotationCheckEnabled) // restore rotation checks
            ChangeDisplayOrientationRule.setRotation(rotation)
            home.currentTask.tapMenu().tapSplitMenuItem().currentTask.open()
        }
        SystemClock.sleep(TIMEOUT_MS)
    }

    fun dragFromNotificationToSplit(
        instrumentation: Instrumentation,
        device: UiDevice,
        wmHelper: WindowManagerStateHelper
    ) {
        val displayBounds =
            wmHelper.currentState.layerState.displays.firstOrNull { !it.isVirtual }?.layerStackSpace
                ?: error("Display not found")
        val swipeXCoordinate = displayBounds.centerX() / 2

        // Pull down the notifications
        device.swipe(swipeXCoordinate, 5, swipeXCoordinate, displayBounds.bottom, 50 /* steps */)
        SystemClock.sleep(TIMEOUT_MS)

        // Find the target notification
        val notificationScroller =
            device.wait(Until.findObject(notificationScrollerSelector), TIMEOUT_MS)
                ?: error("Unable to find view $notificationScrollerSelector")
        var notificationContent = notificationScroller.findObject(notificationContentSelector)

        while (notificationContent == null) {
            device.swipe(
                displayBounds.centerX(),
                displayBounds.centerY(),
                displayBounds.centerX(),
                displayBounds.centerY() - 150,
                20 /* steps */
            )
            notificationContent = notificationScroller.findObject(notificationContentSelector)
        }

        // Drag to split
        val dragStart = notificationContent.visibleCenter
        val dragMiddle = Point(dragStart.x + 50, dragStart.y)
        val dragEnd = Point(displayBounds.width() / 4, displayBounds.width() / 4)
        val downTime = SystemClock.uptimeMillis()

        touch(instrumentation, MotionEvent.ACTION_DOWN, downTime, downTime, TIMEOUT_MS, dragStart)
        // It needs a horizontal movement to trigger the drag
        touchMove(
            instrumentation,
            downTime,
            SystemClock.uptimeMillis(),
            DRAG_DURATION_MS,
            dragStart,
            dragMiddle
        )
        touchMove(
            instrumentation,
            downTime,
            SystemClock.uptimeMillis(),
            DRAG_DURATION_MS,
            dragMiddle,
            dragEnd
        )
        // Wait for a while to start splitting
        SystemClock.sleep(TIMEOUT_MS)
        touch(
            instrumentation,
            MotionEvent.ACTION_UP,
            downTime,
            SystemClock.uptimeMillis(),
            GESTURE_STEP_MS,
            dragEnd
        )
        SystemClock.sleep(TIMEOUT_MS)
    }

    fun touch(
        instrumentation: Instrumentation,
        action: Int,
        downTime: Long,
        eventTime: Long,
        duration: Long,
        point: Point
    ) {
        val motionEvent =
            MotionEvent.obtain(downTime, eventTime, action, point.x.toFloat(), point.y.toFloat(), 0)
        motionEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(motionEvent, true)
        motionEvent.recycle()
        SystemClock.sleep(duration)
    }

    fun touchMove(
        instrumentation: Instrumentation,
        downTime: Long,
        eventTime: Long,
        duration: Long,
        from: Point,
        to: Point
    ) {
        val steps: Long = duration / GESTURE_STEP_MS
        var currentTime = eventTime
        var currentX = from.x.toFloat()
        var currentY = from.y.toFloat()
        val stepX = (to.x.toFloat() - from.x.toFloat()) / steps.toFloat()
        val stepY = (to.y.toFloat() - from.y.toFloat()) / steps.toFloat()

        for (i in 1..steps) {
            val motionMove =
                MotionEvent.obtain(
                    downTime,
                    currentTime,
                    MotionEvent.ACTION_MOVE,
                    currentX,
                    currentY,
                    0
                )
            motionMove.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(motionMove, true)
            motionMove.recycle()

            currentTime += GESTURE_STEP_MS
            if (i == steps - 1) {
                currentX = to.x.toFloat()
                currentY = to.y.toFloat()
            } else {
                currentX += stepX
                currentY += stepY
            }
            SystemClock.sleep(GESTURE_STEP_MS)
        }
    }

    fun createShortcutOnHotseatIfNotExist(tapl: LauncherInstrumentation, appName: String) {
        tapl.workspace.deleteAppIcon(tapl.workspace.getHotseatAppIcon(0))
        val allApps = tapl.workspace.switchToAllApps()
        allApps.freeze()
        try {
            allApps.getAppIcon(appName).dragToHotseat(0)
        } finally {
            allApps.unfreeze()
        }
    }

    fun dragDividerToResizeAndWait(device: UiDevice, wmHelper: WindowManagerStateHelper) {
        val displayBounds =
            wmHelper.currentState.layerState.displays.firstOrNull { !it.isVirtual }?.layerStackSpace
                ?: error("Display not found")
        val dividerBar = device.wait(Until.findObject(dividerBarSelector), TIMEOUT_MS)
        dividerBar.drag(Point(displayBounds.width() * 1 / 3, displayBounds.height() * 2 / 3), 200)

        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceDisappeared(SPLIT_DECOR_MANAGER)
            .waitForAndVerify()
    }

    fun dragDividerToDismissSplit(
        device: UiDevice,
        wmHelper: WindowManagerStateHelper,
        dragToRight: Boolean,
        dragToBottom: Boolean
    ) {
        val displayBounds =
            wmHelper.currentState.layerState.displays.firstOrNull { !it.isVirtual }?.layerStackSpace
                ?: error("Display not found")
        val dividerBar = device.wait(Until.findObject(dividerBarSelector), TIMEOUT_MS)
        dividerBar.drag(
            Point(
                if (dragToRight) {
                    displayBounds.right
                } else {
                    displayBounds.left
                },
                if (dragToBottom) {
                    displayBounds.bottom
                } else {
                    displayBounds.top
                }
            )
        )
    }

    fun doubleTapDividerToSwitch(device: UiDevice) {
        val dividerBar = device.wait(Until.findObject(dividerBarSelector), TIMEOUT_MS)
        val interval =
            (ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getDoubleTapMinTime()) / 2
        dividerBar.click()
        SystemClock.sleep(interval.toLong())
        dividerBar.click()
    }

    fun copyContentInSplit(
        instrumentation: Instrumentation,
        device: UiDevice,
        sourceApp: IComponentNameMatcher,
        destinationApp: IComponentNameMatcher,
    ) {
        // Copy text from sourceApp
        val textView =
            device.wait(
                Until.findObject(By.res(sourceApp.packageName, "SplitScreenTest")),
                TIMEOUT_MS
            )
        assertNotNull("Unable to find the TextView", textView)
        textView.click(LONG_PRESS_TIME_MS)

        val copyBtn = device.wait(Until.findObject(By.text("Copy")), TIMEOUT_MS)
        assertNotNull("Unable to find the copy button", copyBtn)
        copyBtn.click()

        // Paste text to destinationApp
        val editText =
            device.wait(
                Until.findObject(By.res(destinationApp.packageName, "plain_text_input")),
                TIMEOUT_MS
            )
        assertNotNull("Unable to find the EditText", editText)
        editText.click(LONG_PRESS_TIME_MS)

        val pasteBtn = device.wait(Until.findObject(By.text("Paste")), TIMEOUT_MS)
        assertNotNull("Unable to find the paste button", pasteBtn)
        pasteBtn.click()

        // Verify text
        if (!textView.text.contentEquals(editText.text)) {
            error("Fail to copy content in split")
        }
    }
}
