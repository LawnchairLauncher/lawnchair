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

package com.android.launcher3.taskbar.allapps

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.taskbar.TaskbarUnitTestRule
import com.android.launcher3.taskbar.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.PackageUserKey
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarAllAppsControllerTest {

    @get:Rule val taskbarUnitTestRule = TaskbarUnitTestRule()

    @InjectController lateinit var allAppsController: TaskbarAllAppsController
    @InjectController lateinit var overlayController: TaskbarOverlayController

    @Test
    @UiThreadTest
    fun testToggle_once_showsAllApps() {
        allAppsController.toggle()
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    @UiThreadTest
    fun testToggle_twice_closesAllApps() {
        allAppsController.toggle()
        allAppsController.toggle()
        assertThat(allAppsController.isOpen).isFalse()
    }

    @Test
    @UiThreadTest
    fun testToggle_taskbarRecreated_allAppsReopened() {
        allAppsController.toggle()
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    @UiThreadTest
    fun testSetApps_beforeOpened_cachesInfo() {
        allAppsController.setApps(TEST_APPS, 0, emptyMap())
        allAppsController.toggle()

        val overlayContext = overlayController.requestWindow()
        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    @UiThreadTest
    fun testSetApps_afterOpened_updatesStore() {
        allAppsController.toggle()
        allAppsController.setApps(TEST_APPS, 0, emptyMap())

        val overlayContext = overlayController.requestWindow()
        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    @UiThreadTest
    fun testSetPredictedApps_beforeOpened_cachesInfo() {
        allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
        allAppsController.toggle()

        val predictedApps =
            overlayController
                .requestWindow()
                .appsView
                .floatingHeaderView
                .findFixedRowByType(PredictionRowView::class.java)
                .predictedApps
        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    @UiThreadTest
    fun testSetPredictedApps_afterOpened_cachesInfo() {
        allAppsController.toggle()
        allAppsController.setPredictedApps(TEST_PREDICTED_APPS)

        val predictedApps =
            overlayController
                .requestWindow()
                .appsView
                .floatingHeaderView
                .findFixedRowByType(PredictionRowView::class.java)
                .predictedApps
        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    fun testUpdateNotificationDots_appInfo_hasDot() {
        getInstrumentation().runOnMainSync {
            allAppsController.setApps(TEST_APPS, 0, emptyMap())
            allAppsController.toggle()
            taskbarUnitTestRule.activityContext.popupDataProvider.onNotificationPosted(
                PackageUserKey.fromItemInfo(TEST_APPS[0]),
                NotificationKeyData("key"),
            )
        }

        // Ensure the recycler view fully inflates before trying to grab an icon.
        getInstrumentation().runOnMainSync {
            val btv =
                overlayController
                    .requestWindow()
                    .appsView
                    .activeRecyclerView
                    .findViewHolderForAdapterPosition(0)
                    ?.itemView as? BubbleTextView
            assertThat(btv?.hasDot()).isTrue()
        }
    }

    @Test
    @UiThreadTest
    fun testUpdateNotificationDots_predictedApp_hasDot() {
        allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
        allAppsController.toggle()

        taskbarUnitTestRule.activityContext.popupDataProvider.onNotificationPosted(
            PackageUserKey.fromItemInfo(TEST_PREDICTED_APPS[0]),
            NotificationKeyData("key"),
        )

        val predictionRowView =
            overlayController
                .requestWindow()
                .appsView
                .floatingHeaderView
                .findFixedRowByType(PredictionRowView::class.java)
        val btv = predictionRowView.getChildAt(0) as BubbleTextView
        assertThat(btv.hasDot()).isTrue()
    }

    private companion object {
        private val TEST_APPS =
            Array(16) {
                AppInfo(
                    ComponentName(
                        getInstrumentation().context,
                        "com.android.launcher3.tests.Activity$it",
                    ),
                    "Test App $it",
                    Process.myUserHandle(),
                    Intent(),
                )
            }

        private val TEST_PREDICTED_APPS = TEST_APPS.take(4).map { WorkspaceItemInfo(it) }
    }
}
