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
package com.android.quickstep

import android.platform.test.annotations.EnableFlags
import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.IgnoreLimit
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.launcher3.BuildConfig
import com.android.launcher3.Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW
import com.android.launcher3.LauncherState
import com.android.launcher3.tapl.BaseOverview
import com.android.launcher3.tapl.LaunchedAppState
import com.android.launcher3.tapl.OverviewTask
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape
import com.android.quickstep.AbstractTaplTestsTaskbar.CALCULATOR_APP_NAME
import com.android.quickstep.AbstractTaplTestsTaskbar.CALCULATOR_APP_PACKAGE
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.function.Supplier
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Test Desktop windowing in Overview. */
@AllowedDevices(allowed = [DeviceProduct.CF_TABLET, DeviceProduct.TANGORPRO])
@IgnoreLimit(ignoreLimit = BuildConfig.IS_STUDIO_BUILD)
class TaplTestsOverviewDesktop : AbstractQuickStepTest() {
    @Before
    fun setup() {
        clearAllRecentTasks()
        startTestAppsWithCheck()
        mLauncher.goHome()
    }

    @EnableFlags(FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun testAllTasksRemovalFromCloseButtonInExplodedView() {
        val desktopTaskView =
            mLauncher.workspace
                .switchToOverview()
                // Move last launched TEST_ACTIVITY_2 into Desktop
                .moveTaskToDesktop(TEST_ACTIVITY_2)
                .switchToOverview()
                // Scroll back to TEST_ACTIVITY_1, then move it into Desktop
                .apply { flingForward() }
                .moveTaskToDesktop(TEST_ACTIVITY_1)
                .switchToOverview()
                .currentTask

        // There should be two desktop thumbnail views in Overview.
        assertThat(desktopTaskView.getDesktopThumbnailViewCount()).isEqualTo(2)

        // Tap on the close button of [TEST_ACTIVITY_1]'s thumbnail view header.
        desktopTaskView.tapCloseDesktopThumbnailView("TestActivity$TEST_ACTIVITY_1")
        assertThat(desktopTaskView.getDesktopThumbnailViewCount()).isEqualTo(1)

        // Tap on the second close button. Since there will be no thumbnail windows and no other
        // task view tiles, Overview should have dismissed.
        desktopTaskView.tapCloseDesktopThumbnailView("TestActivity$TEST_ACTIVITY_2")
        assertTrue(
            "Launcher internal state is not Workspace",
            isInState(Supplier { LauncherState.NORMAL }),
        )
    }

    @EnableFlags(FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun testActivateIndividualTaskFromExplodedView() {
        var desktopTaskView =
            mLauncher.workspace
                .switchToOverview()
                // Move last launched TEST_ACTIVITY_2 into Desktop
                .moveTaskToDesktop(TEST_ACTIVITY_2)
                .switchToOverview()
                // Scroll back to TEST_ACTIVITY_1, then move it into Desktop
                .apply { flingForward() }
                .moveTaskToDesktop(TEST_ACTIVITY_1)
                .switchToOverview()
                .currentTask

        // There should be two desktop thumbnail views in Overview.
        assertThat(desktopTaskView.getDesktopThumbnailViewCount()).isEqualTo(2)

        // Tap on the thumbnail of [TEST_ACTIVITY_2] to activate its window.
        desktopTaskView.tapOnDesktopThumbnailView("TestActivity$TEST_ACTIVITY_2")
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Tap on the thumbnail of [TEST_ACTIVITY_1] to activate its window.
        desktopTaskView = mLauncher.goHome().switchToOverview().currentTask
        desktopTaskView.tapOnDesktopThumbnailView("TestActivity$TEST_ACTIVITY_1")
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Tap on the empty space in overview should not bring back [TEST_ACTIVITY_2]
        desktopTaskView = mLauncher.goHome().switchToOverview().currentTask
        desktopTaskView.tapOnEmptySpaceInDesktopTaskView()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }
    }

    @Test
    @PortraitLandscape
    fun enterDesktopViaOverviewMenu() {
        // Move last launched TEST_ACTIVITY_2 into Desktop
        mLauncher.workspace.switchToOverview().moveTaskToDesktop(TEST_ACTIVITY_2)
        // Scroll back to TEST_ACTIVITY_1, then move it into Desktop
        mLauncher
            .goHome()
            .switchToOverview()
            .apply { flingForward() }
            .moveTaskToDesktop(TEST_ACTIVITY_1)
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Launch static DesktopTaskView without live tile in Overview
        val desktopTask =
            mLauncher.goHome().switchToOverview().getTestActivityTask(TEST_ACTIVITIES).open()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Launch live-tile DesktopTaskView
        desktopTask.switchToOverview().getTestActivityTask(TEST_ACTIVITIES).open()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }

        // Launch static DesktopTaskView with live tile in Overview
        mLauncher.goHome()
        startTestActivity(TEST_ACTIVITY_EXTRA)
        mLauncher.launchedAppState
            .switchToOverview()
            .apply { flingBackward() }
            .getTestActivityTask(TEST_ACTIVITIES)
            .open()
        TEST_ACTIVITIES.forEach { assertTestAppLaunched(it) }
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testCreateMultiDesktopsViaAddDesktopButton() {
        // Tap add desk button to create a desk.
        val overview = mLauncher.workspace.switchToOverview().createDeskViaClickAddDesktopButton()

        // Fling and tap add desk button again to create one more desk.
        overview.createDeskViaClickAddDesktopButton()
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testEmptyDesk() {
        mLauncher.workspace
            .switchToOverview()
            // Create an empty desk
            .createDeskViaClickAddDesktopButton()
            .apply { flingBackward() }
            .currentTask
            // Launch the empty desk
            .open()
            // Go back to Overview from the empty desk
            .switchToOverview()
            // Relaunch it
            .currentTask
            .open()
            // Launch an app from the taskbar
            .taskbar
            .openAllApps()
            .getAppIcon(CALCULATOR_APP_NAME)
            .launch(CALCULATOR_APP_PACKAGE)
            // Verify that the app is now running inside the desktop.
            .assertAppInDesktop(CALCULATOR_APP_PACKAGE)
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testSwitchMultiDesktopsViaOverview() {
        val overview =
            mLauncher.workspace
                .switchToOverview()
                // Create an empty desk
                .createDeskViaClickAddDesktopButton()
                // Create one non-empty desk
                .createAnNonEmptyDesk()
                // Create one more empty desk
                .createDeskViaClickAddDesktopButton()

        // From each desk, launch it and switch to Overview and then tap a different desktop tile
        // to switch between the desks, and verify the correct desk is launched.
        val deskCount = overview.desktopTasksCount
        // Fling to the right-most desk, and enumerate desks from it.
        var currentOverview = overview.apply { flingBackward() }
        for (i in 0 until deskCount) {
            val task = currentOverview.currentTask
            assertTrue("Current task should be a desktop", task.isDesktop)
            val launchedDesk = task.open()
            // Go back to overview and scroll the distance of one task for the next iteration
            if (i < deskCount - 1) {
                currentOverview = launchedDesk.switchToOverview().scrollForwardByOneTask()
            }
        }
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testQuickSwitchBetweenDesksForwardAndBackward() {
        // Create two empty desks and 1 non-empty desk, and keep record of deskId.
        var overview =
            mLauncher.workspace
                .switchToOverview()
                .createDeskViaClickAddDesktopButton()
                .apply { flingBackward() }
                .currentTask
                .open()
                .switchToOverview()
        val desk1Id = overview.currentTask.deskId

        overview =
            overview
                .createDeskViaClickAddDesktopButton()
                .apply { flingBackward() }
                .currentTask
                .open()
                .switchToOverview()
        val desk2Id = overview.currentTask.deskId

        overview = overview.createAnNonEmptyDesk()
        val desk3Id = overview.currentTask.deskId

        // Start from Desk 3
        overview = mLauncher.goHome().switchToOverview().apply { flingBackward() }
        var launchedDesk = overview.currentTask.open()
        assertWithMessage("The active desk should be Desk 3")
            .that(mLauncher.activeDeskId)
            .isEqualTo(desk3Id)

        // Quick switch backward
        launchedDesk = launchedDesk.quickSwitchToPreviousApp()
        assertWithMessage("The active desk should be Desk 2 after switching backward")
            .that(mLauncher.activeDeskId)
            .isEqualTo(desk2Id)
        launchedDesk = launchedDesk.quickSwitchToPreviousApp()
        assertWithMessage("The active desk should be Desk 1 after switching backward")
            .that(mLauncher.activeDeskId)
            .isEqualTo(desk1Id)

        // Quick switch forward
        launchedDesk = launchedDesk.quickSwitchToPreviousAppSwipeLeft()
        assertWithMessage("The active desk should be Desk 2 after switching forward")
            .that(mLauncher.activeDeskId)
            .isEqualTo(desk2Id)
        launchedDesk.quickSwitchToPreviousAppSwipeLeft()
        assertWithMessage("The active desk should be Desk 3 after switching forward")
            .that(mLauncher.activeDeskId)
            .isEqualTo(desk3Id)

        // Cleanup: Go to overview and dismiss all tasks to prevent memory leak.
        mLauncher.launchedAppState.switchToOverview().dismissAllTasks()
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDismissMultipleDesksViaSwipeUpGesture() {
        var overview =
            mLauncher.workspace
                .switchToOverview()
                // Create one non-empty desk
                .createAnNonEmptyDesk()
                // Create an empty desk
                .createDeskViaClickAddDesktopButton()

        // Fling to the right-most desk to start dismissing via swipe-up gesture
        overview = overview.apply { flingBackward() }
        for (i in 0 until 2) {
            val task = overview.currentTask
            assertThat(task.isDesktop).isTrue()
            task.dismiss()
        }
    }

    @Test
    @PortraitLandscape
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDismissDeskViaTaskMenuClearButton() {
        var overview =
            mLauncher.workspace
                .switchToOverview()
                // Create an empty desk
                .createDeskViaClickAddDesktopButton()
                // Create a non-empty desk
                .createAnNonEmptyDesk()

        // Fling to the right-most desk to start dismissing via task menu
        overview = overview.apply { flingBackward() }
        for (i in 0 until 2) {
            val task = overview.currentTask
            assertThat(task.isDesktop).isTrue()
            task.dismissViaMenu()
            overview = mLauncher.overview
        }
    }

    @Test
    @PortraitLandscape
    fun dismissFocusedTasks_thenDesktopIsCentered() {
        // Create DesktopTaskView
        mLauncher.goHome().switchToOverview().moveTaskToDesktop(TEST_ACTIVITY_2)

        // Create a new task activity to be the focused task
        mLauncher.goHome()
        startTestActivity(TEST_ACTIVITY_EXTRA)

        val overview = mLauncher.goHome().switchToOverview()

        // Dismiss focused task
        val focusedTask1 = overview.currentTask
        assertTaskContentDescription(focusedTask1, TEST_ACTIVITY_EXTRA)
        focusedTask1.dismiss()

        // Dismiss new focused task
        val focusedTask2 = overview.currentTask
        assertTaskContentDescription(focusedTask2, TEST_ACTIVITY_1)
        focusedTask2.dismiss()

        // Dismiss DesktopTaskView
        val desktopTask = overview.currentTask
        assertWithMessage("The current task is not a Desktop.").that(desktopTask.isDesktop).isTrue()
        desktopTask.dismiss()

        assertWithMessage("Still have tasks after dismissing all the tasks")
            .that(mLauncher.workspace.switchToOverview().hasTasks())
            .isFalse()
    }

    @Test
    @PortraitLandscape
    fun dismissTasks_whenDesktopTask_IsInTheCenter() {
        // Create extra activity to be DesktopTaskView
        startTestActivity(TEST_ACTIVITY_EXTRA)

        // Open first fullscreen task and go back to Overview to validate whether it has adjacent
        // tasks in its both sides (grid task on left and desktop tasks at its right side)
        val firstFullscreenTaskOpened =
            mLauncher
                .goHome()
                .switchToOverview()
                .moveTaskToDesktop(TEST_ACTIVITY_EXTRA)
                .switchToOverview()
                .getTestActivityTask(TEST_ACTIVITY_2)
                .open()

        // Fling to desktop task and dismiss the first fullscreen task to check repositioning of
        // grid tasks.
        val overview = firstFullscreenTaskOpened.switchToOverview().apply { flingBackward() }
        val desktopTask = overview.currentTask
        assertWithMessage("The current task is not a Desktop.").that(desktopTask.isDesktop).isTrue()

        // Get first fullscreen task (previously opened task) then dismiss this task
        val firstFullscreenTaskInOverview = overview.getTestActivityTask(TEST_ACTIVITY_2)
        assertTaskContentDescription(firstFullscreenTaskInOverview, TEST_ACTIVITY_2)
        firstFullscreenTaskInOverview.dismiss()

        // Dismiss DesktopTask to validate whether the new task will take its position
        desktopTask.dismiss()

        // Dismiss last fullscreen task
        val lastFocusedTask = overview.currentTask
        assertTaskContentDescription(lastFocusedTask, TEST_ACTIVITY_1)
        lastFocusedTask.dismiss()

        assertWithMessage("Still have tasks after dismissing all the tasks")
            .that(mLauncher.workspace.switchToOverview().hasTasks())
            .isFalse()
    }

    private fun assertTaskContentDescription(task: OverviewTask, activityIndex: Int) {
        assertWithMessage("The current task content description is not TestActivity$activityIndex.")
            .that(task.containsContentDescription("TestActivity$activityIndex"))
            .isTrue()
    }

    private fun startTestAppsWithCheck() {
        TEST_ACTIVITIES.forEach {
            startTestActivity(it)
            executeOnLauncher { launcher ->
                assertWithMessage(
                        "Launcher activity is the top activity; expecting TestActivity$it"
                    )
                    .that(isInLaunchedApp(launcher))
                    .isTrue()
            }
        }
    }

    private fun assertTestAppLaunched(index: Int) {
        assertWithMessage("TestActivity$index not opened in Desktop")
            .that(
                mDevice.wait(
                    Until.hasObject(By.pkg(getAppPackageName()).text("TestActivity$index")),
                    TestUtil.DEFAULT_UI_TIMEOUT,
                )
            )
            .isTrue()
    }

    private fun BaseOverview.moveTaskToDesktop(activityIndex: Int): LaunchedAppState {
        return mLauncher.overview
            .getTestActivityTask(activityIndex)
            .tapMenu()
            .tapDesktopMenuItem()
            .also { assertTestAppLaunched(activityIndex) }
    }

    private fun BaseOverview.createAnNonEmptyDesk(): BaseOverview {
        return this.createDeskViaClickAddDesktopButton()
            .apply { flingBackward() }
            .currentTask
            .open()
            .taskbar
            .openAllApps()
            .getAppIcon(CALCULATOR_APP_NAME)
            .launch(CALCULATOR_APP_PACKAGE)
            .switchToOverview()
    }

    companion object {
        const val TEST_ACTIVITY_1 = 2
        const val TEST_ACTIVITY_2 = 3
        const val TEST_ACTIVITY_EXTRA = 4
        val TEST_ACTIVITIES = listOf(TEST_ACTIVITY_1, TEST_ACTIVITY_2)
    }
}
