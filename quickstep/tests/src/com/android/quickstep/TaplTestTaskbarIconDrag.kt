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

package com.android.quickstep

import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.tapl.BubbleBar
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME
import com.android.launcher3.util.TestUtil
import com.android.wm.shell.Flags
import org.junit.After
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_BUBBLE_ANYTHING)
class TaplTestTaskbarIconDrag : AbstractQuickStepTest() {

    private var mLauncherLayout: AutoCloseable? = null

    override fun setUp() {
        Assume.assumeTrue(mLauncher.isTablet)
        super.setUp()
        val layoutBuilder =
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putApp(
                    "com.google.android.apps.nexuslauncher.tests",
                    "com.android.launcher3.testcomponent.BaseTestingActivity",
                )
        mLauncherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, layoutBuilder)
        performInitialization()
        mLauncher.enableBlockTimeout(true)
    }

    @After
    fun tearDown() {
        mLauncher.enableBlockTimeout(false)
        mLauncherLayout?.close()
    }

    @Test
    fun testAppIconDragOnOverviewFromTaskBarToBubbleBar() {
        val overview = mLauncher.workspace.switchToOverview()
        // test left drop target
        overview.taskbar!!
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ true)
        dismissExpandedBubbleBar(overview.bubbleBar)
    }

    @Test
    fun testAppIconDragInRunningAppFromTaskBarToBubbleBar() {
        startAppFast(AbstractTaplTestsTaskbar.CALCULATOR_APP_PACKAGE)
        val launchedAppState = mLauncher.launchedAppState
        mLauncher.showTaskbarIfHidden()
        // test right drop target
        launchedAppState.taskbar
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ false)
        // close expanded bubble
        dismissExpandedBubbleBar(launchedAppState.bubbleBar)
    }

    private fun dismissExpandedBubbleBar(bubbleBar: BubbleBar) {
        // close expanded bubble bar
        mLauncher.pressBack()
        bubbleBar.verifyCollapsed()
        // at this moment the bubble bar will be hidden, so need to show it again
        mLauncher.showBubbleBarIfHidden()
        // dismiss bubble bar
        bubbleBar.dragToDismiss()
    }
}
