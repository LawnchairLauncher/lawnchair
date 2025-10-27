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

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.IgnoreLimit
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.launcher3.BuildConfig
import com.android.launcher3.tapl.LaunchedAppState
import com.android.launcher3.tapl.OverviewTask
import com.android.launcher3.ui.AbstractLauncherUiTest
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test

/** Test Desktop windowing in Overview. */
@AllowedDevices(allowed = [DeviceProduct.CF_TABLET, DeviceProduct.TANGORPRO])
@IgnoreLimit(ignoreLimit = BuildConfig.IS_STUDIO_BUILD)
class TaplTestsOverviewDesktop : AbstractLauncherUiTest<QuickstepLauncher?>() {
    @Before
    fun setup() {
        val overview = mLauncher.goHome().switchToOverview()
        if (overview.hasTasks()) {
            overview.dismissAllTasks()
        }
        startTestAppsWithCheck()
        mLauncher.goHome()
    }

    @Test
    @PortraitLandscape
    fun enterDesktopViaOverviewMenu() {
        mLauncher.workspace.switchToOverview()
        moveTaskToDesktop(TEST_ACTIVITY_2) // Move last launched TEST_ACTIVITY_2 into Desktop

        // Scroll back to TEST_ACTIVITY_1, then move it into Desktop
        mLauncher
            .goHome()
            .switchToOverview()
            .apply { flingForward() }
            .also { moveTaskToDesktop(TEST_ACTIVITY_1) }
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
    fun dismissFocusedTasks_thenDesktopIsCentered() {
        // Create DesktopTaskView
        mLauncher.goHome().switchToOverview()
        moveTaskToDesktop(TEST_ACTIVITY_2)

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
        mLauncher.goHome().switchToOverview()

        val desktop = moveTaskToDesktop(TEST_ACTIVITY_EXTRA)
        var overview = desktop.switchToOverview()

        // Open first fullscreen task and go back to Overview to validate whether it has adjacent
        // tasks in its both sides (grid task on left and desktop tasks at its right side)
        val firstFullscreenTaskOpened = overview.getTestActivityTask(TEST_ACTIVITY_2).open()

        // Fling to desktop task and dismiss the first fullscreen task to check repositioning of
        // grid tasks.
        overview = firstFullscreenTaskOpened.switchToOverview().apply { flingBackward() }
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

    private fun moveTaskToDesktop(activityIndex: Int): LaunchedAppState {
        return mLauncher.overview
            .getTestActivityTask(activityIndex)
            .tapMenu()
            .tapDesktopMenuItem()
            .also { assertTestAppLaunched(activityIndex) }
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

    companion object {
        const val TEST_ACTIVITY_1 = 2
        const val TEST_ACTIVITY_2 = 3
        const val TEST_ACTIVITY_EXTRA = 4
        val TEST_ACTIVITIES = listOf(TEST_ACTIVITY_1, TEST_ACTIVITY_2)
    }
}
