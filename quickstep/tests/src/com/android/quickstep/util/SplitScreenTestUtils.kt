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

package com.android.quickstep.util

import androidx.test.uiautomator.By
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.launcher3.tapl.Overview
import com.android.launcher3.tapl.OverviewTask
import com.android.launcher3.ui.AbstractLauncherUiTest

object SplitScreenTestUtils {

    /** Creates 2 tasks and makes a split mode pair. Also asserts the accessibility labels. */
    @JvmStatic
    fun createAndLaunchASplitPairInOverview(launcher: LauncherInstrumentation): Overview {
        clearAllRecentTasks(launcher)

        AbstractLauncherUiTest.startTestActivity(2)
        AbstractLauncherUiTest.startTestActivity(3)

        val overView = launcher.goHome().switchToOverview()
        if (launcher.isTablet && !launcher.isGridOnlyOverviewEnabled) {
            overView.overviewActions.clickSplit().getTestActivityTask(2).open()
        } else {
            overView.currentTask.tapMenu().tapSplitMenuItem().currentTask.open()
        }

        val overviewWithSplitPair = launcher.goHome().switchToOverview()
        val currentTask = overviewWithSplitPair.currentTask
        currentTask.containsContentDescription(
            By.pkg(AbstractLauncherUiTest.getAppPackageName()).text("TestActivity3").toString(),
            OverviewTask.OverviewTaskContainer.SPLIT_TOP_OR_LEFT,
        )
        currentTask.containsContentDescription(
            By.pkg(AbstractLauncherUiTest.getAppPackageName()).text("TestActivity2").toString(),
            OverviewTask.OverviewTaskContainer.SPLIT_BOTTOM_OR_RIGHT,
        )
        return overviewWithSplitPair
    }

    private fun clearAllRecentTasks(launcher: LauncherInstrumentation) {
        if (launcher.recentTasks.isNotEmpty()) {
            launcher.goHome().switchToOverview().dismissAllTasks()
        }
    }
}
