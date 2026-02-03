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

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.R
import com.android.launcher3.appprediction.AppsDividerView
import com.android.launcher3.appprediction.AppsDividerView.DividerType
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.TaskbarStashController
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_AUTO
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsControllerTest.Companion.TEST_PREDICTED_APPS
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023"])
class TaskbarAllAppsViewControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var overlayController: TaskbarOverlayController
    @InjectController lateinit var stashController: TaskbarStashController

    private var allAppsVisitedCount by ALL_APPS_VISITED_COUNT.prefItem.asProperty(context)
    private val searchSessionController =
        TestUtil.getOnUiThread { TaskbarSearchSessionController.newInstance(context) }

    @After
    fun cleanUpSearchSessionController() {
        getInstrumentation().runOnMainSync { searchSessionController.onDestroy() }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testShow_transientMode_stashesTaskbar() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO.toLong(), false)
            stashController.applyState(0)
        }

        val viewController = createViewController()
        getInstrumentation().runOnMainSync { viewController.show(false) }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testShow_pinnedMode_taskbarDoesNotStash() {
        val viewController = createViewController()
        getInstrumentation().runOnMainSync { viewController.show(false) }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHide_transientMode_unstashesTaskbar() {
        getInstrumentation().runOnMainSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO.toLong(), false)
            stashController.applyState(0)
        }

        val viewController = createViewController()
        getInstrumentation().runOnMainSync { viewController.show(false) }
        getInstrumentation().runOnMainSync { viewController.close(false) }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    fun testShow_firstAllAppsVisit_hasAllAppsTextDivider() {
        allAppsVisitedCount = 0
        val viewController = createViewController()
        getInstrumentation().runOnMainSync { viewController.show(false) }

        val appsView = overlayController.requestWindow().appsView
        getInstrumentation().runOnMainSync {
            appsView.floatingHeaderView
                .findFixedRowByType(PredictionRowView::class.java)
                .setPredictedApps(TEST_PREDICTED_APPS)
        }

        val dividerView =
            appsView.floatingHeaderView.findFixedRowByType(AppsDividerView::class.java)
        assertThat(dividerView.dividerType).isEqualTo(DividerType.ALL_APPS_LABEL)
    }

    @Test
    fun testShow_maxAllAppsVisitedCount_hasLineDivider() {
        allAppsVisitedCount = ALL_APPS_VISITED_COUNT.maxCount
        val viewController = createViewController()
        getInstrumentation().runOnMainSync { viewController.show(false) }

        val appsView = overlayController.requestWindow().appsView
        getInstrumentation().runOnMainSync {
            appsView.floatingHeaderView
                .findFixedRowByType(PredictionRowView::class.java)
                .setPredictedApps(TEST_PREDICTED_APPS)
        }

        val dividerView =
            appsView.floatingHeaderView.findFixedRowByType(AppsDividerView::class.java)
        assertThat(dividerView.dividerType).isEqualTo(DividerType.LINE)
    }

    private fun createViewController(): TaskbarAllAppsViewController {
        return TestUtil.getOnUiThread {
            val overlayContext = overlayController.requestWindow()
            TaskbarAllAppsViewController(
                overlayContext,
                overlayContext.layoutInflater.inflate(
                    R.layout.taskbar_all_apps_sheet,
                    overlayContext.dragLayer,
                    false,
                ) as TaskbarAllAppsSlideInView,
                taskbarUnitTestRule.activityContext.controllers,
                searchSessionController,
                /* showKeyboard= */ false, // Covered in TaskbarAllAppsControllerTest.
            )
        }
    }
}
