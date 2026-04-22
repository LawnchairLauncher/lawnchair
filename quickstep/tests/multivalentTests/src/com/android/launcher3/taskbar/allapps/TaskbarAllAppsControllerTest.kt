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

import android.animation.AnimatorTestRule
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarAllAppsControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)
    @get:Rule(order = 2) val animatorTestRule = AnimatorTestRule(this)

    @InjectController lateinit var allAppsController: TaskbarAllAppsController
    @InjectController lateinit var overlayController: TaskbarOverlayController

    @Test
    fun testToggle_once_showsAllApps() {
        runOnMainSync { allAppsController.toggle() }
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    fun testToggle_twice_closesAllApps() {
        runOnMainSync {
            allAppsController.toggle()
            allAppsController.toggle()
        }
        assertThat(allAppsController.isOpen).isFalse()
    }

    @Test
    fun testToggle_taskbarRecreated_allAppsReopened() {
        runOnMainSync { allAppsController.toggle() }
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    fun testSetApps_beforeOpened_cachesInfo() {
        val overlayContext =
            TestUtil.getOnUiThread {
                allAppsController.setApps(TEST_APPS, 0, emptyMap())
                allAppsController.toggle()
                overlayController.requestWindow()
            }

        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    fun testSetApps_afterOpened_updatesStore() {
        val overlayContext =
            TestUtil.getOnUiThread {
                allAppsController.toggle()
                allAppsController.setApps(TEST_APPS, 0, emptyMap())
                overlayController.requestWindow()
            }

        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    fun testSetPredictedApps_beforeOpened_cachesInfo() {
        val predictedApps =
            TestUtil.getOnUiThread {
                allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
                allAppsController.toggle()

                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .predictedApps
            }

        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    fun testSetPredictedApps_afterOpened_cachesInfo() {
        val predictedApps =
            TestUtil.getOnUiThread {
                allAppsController.toggle()
                allAppsController.setPredictedApps(TEST_PREDICTED_APPS)

                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .predictedApps
            }

        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    fun testUpdateNotificationDots_appInfo_hasDot() {
        runOnMainSync {
            allAppsController.setApps(TEST_APPS, 0, emptyMap())
            allAppsController.toggle()
            taskbarUnitTestRule.activityContext.popupDataProvider.onNotificationPosted(
                PackageUserKey.fromItemInfo(TEST_APPS[0]),
                NotificationKeyData("key"),
            )
        }

        // Ensure the recycler view fully inflates before trying to grab an icon.
        val btv =
            TestUtil.getOnUiThread {
                overlayController
                    .requestWindow()
                    .appsView
                    .activeRecyclerView
                    .findViewHolderForAdapterPosition(0)
                    ?.itemView as? BubbleTextView
            }
        assertThat(btv?.hasDot()).isTrue()
    }

    @Test
    fun testUpdateNotificationDots_predictedApp_hasDot() {
        runOnMainSync {
            allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
            allAppsController.toggle()
            taskbarUnitTestRule.activityContext.popupDataProvider.onNotificationPosted(
                PackageUserKey.fromItemInfo(TEST_PREDICTED_APPS[0]),
                NotificationKeyData("key"),
            )
        }

        val btv =
            TestUtil.getOnUiThread {
                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .getChildAt(0) as BubbleTextView
            }
        assertThat(btv.hasDot()).isTrue()
    }

    @Test
    fun testToggleSearch_searchEditTextFocused() {
        runOnMainSync { allAppsController.toggleSearch() }
        runOnMainSync {
            // All Apps is now attached to window. Open animation is posted but not started.
        }

        runOnMainSync {
            // Animation has started. Advance to end of animation.
            animatorTestRule.advanceTimeBy(overlayController.openDuration.toLong())
        }
        val editText = overlayController.requestWindow().appsView.searchUiManager.editText
        assertThat(editText?.hasFocus()).isTrue()
    }

    companion object {
        val TEST_APPS =
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

        val TEST_PREDICTED_APPS = TEST_APPS.take(4).map { WorkspaceItemInfo(it) }
    }
}
