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

package com.google.android.apps.nexuslauncher.integration.FixedLandscape

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.FIXED_LANDSCAPE_MODE
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.debug.TestEventEmitter.TestEvent
import com.android.launcher3.integration.events.EventsRule
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.util.rule.ScreenRecordRule
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FixedLandscapeIntegrationTest {

    private val targetContext: Context = getInstrumentation().targetContext

    private val eventsRule = EventsRule(targetContext)

    private val launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext, false)

    private val idp = InvariantDeviceProfile.INSTANCE.get(targetContext)

    @get:Rule val mScreenRecordRule: ScreenRecordRule = ScreenRecordRule()

    @get:Rule val chainRule: RuleChain = RuleChain.outerRule(eventsRule).around(launcherActivity)

    private fun getTotalItems(): Int =
        launcherActivity.getFromLauncher {
            it.workspace.mWorkspaceScreens.sumOf { it.shortcutsAndWidgets.childCount }
        }!!

    private fun switchFixedLandscape(isFixedLandscape: Boolean) {
        val currentIsFixedLandscape =
            launcherActivity.getFromLauncher { it.deviceProfile.inv.isFixedLandscape }
        if (currentIsFixedLandscape != isFixedLandscape) {
            val workspaceLoadedEvent =
                eventsRule.createEventWaiter(TestEvent.WORKSPACE_FINISH_LOADING)
            LauncherPrefs.get(targetContext).putSync(FIXED_LANDSCAPE_MODE.to(isFixedLandscape))
            workspaceLoadedEvent.waitForSignal()
        }
        assert(idp.isFixedLandscape == isFixedLandscape) {
            "Did not switch Fixed Landscape IDP value ${idp.isFixedLandscape} expected $isFixedLandscape"
        }
        if (isFixedLandscape) {
            assert(idp.numRows == 3) { "Fixed Landscape should have 3 columns" }
        }
    }

    @Test
    fun `can switch to fixed landscape and back`() {
        switchFixedLandscape(true)
        val countItemsInFixedLandscape = getTotalItems()
        switchFixedLandscape(false)
        val countItemsInLauncher = getTotalItems()
        assert(countItemsInFixedLandscape == countItemsInLauncher) {
            "The number of items should be the same in both orientations, the values " +
                "are $countItemsInFixedLandscape in Fixed Landscape and" +
                "$countItemsInLauncher in the regular Launcher "
        }
    }

    @Test
    fun `can switch to fixed landscape and back from non-default grid`() {
        val gridName = "small"
        val fixedLandscapeGridName = "fixed_landscape_mode"
        idp.setCurrentGrid(targetContext, gridName)
        switchFixedLandscape(true)
        var currentGridName = get(targetContext!!).get(LauncherPrefs.GRID_NAME)
        assert(currentGridName.equals(fixedLandscapeGridName)) {
            "When we switch to fixed landscape mode we should go to $fixedLandscapeGridName. " +
                "Instead, we went to $currentGridName"
        }
        switchFixedLandscape(false)
        currentGridName = get(targetContext!!).get(LauncherPrefs.GRID_NAME)
        assert(currentGridName.equals(gridName)) {
            "The grid that we go back to should be $gridName. Instead, we went to $currentGridName"
        }
    }

    @Test
    fun `stress test fixed landscape`() {
        // The number should be bigger than 1 but also not too big so that the test doesn't take too
        // long to run
        for (i in 0..7) {
            switchFixedLandscape(true)
            val countItemsInFixedLandscape = getTotalItems()
            switchFixedLandscape(false)
            val countItemsInLauncher = getTotalItems()
            assert(countItemsInFixedLandscape == countItemsInLauncher) {
                "The number of items should be the same in both orientations, the values " +
                    "are $countItemsInFixedLandscape in fixed landscape and" +
                    "$countItemsInLauncher in the regular Launcher "
            }
        }
    }

    @Before
    fun setup() {
        launcherActivity.initializeActivity()
        assumeTrue(
            "Fixed landscape is only supported on phones, skip test for none phone",
            launcherActivity.getFromLauncher { it.deviceProfile.inv.deviceType } == TYPE_PHONE,
        )
    }

    @After
    fun cleanup() {
        LauncherPrefs.get(targetContext).putSync(FIXED_LANDSCAPE_MODE.to(false))
    }
}
