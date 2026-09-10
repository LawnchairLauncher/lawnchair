/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.integration.rotation

import android.content.Context
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.ALLOW_ROTATION
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.util.Wait.atMost
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class LauncherRotationIntegrationTest {

    private val targetContext: Context = getInstrumentation().targetContext
    private val launcherActivity = LauncherActivityScenarioRule<Launcher>(targetContext)
    private val originalAllowRotation = LauncherPrefs.get(targetContext).get(ALLOW_ROTATION)

    @get:Rule val activityRule: LauncherActivityScenarioRule<Launcher> = launcherActivity

    @Before
    fun setup() {
        assumeTrue(
            "Wallpaper transition rotation lock is only needed on phones",
            launcherActivity.getFromLauncher { it.deviceProfile.inv.deviceType } == TYPE_PHONE,
        )
    }

    @After
    fun cleanup() {
        launcherActivity.executeOnLauncher { it.rotationHelper.setFixedLandscape(false) }
        setAllowRotation(originalAllowRotation)
    }

    @Test
    fun `stop locks rotation until home entry with rotation disabled`() {
        setAllowRotation(false)
        waitForOrientation(SCREEN_ORIENTATION_NOSENSOR)

        stopAndResumeLauncher(SCREEN_ORIENTATION_LOCKED)

        completeHomeEntry()
        waitForOrientation(SCREEN_ORIENTATION_NOSENSOR)
    }

    @Test
    fun `stop lock restores sensor rotation after home entry`() {
        setAllowRotation(true)
        waitForOrientation(SCREEN_ORIENTATION_UNSPECIFIED)

        stopAndResumeLauncher(SCREEN_ORIENTATION_LOCKED)

        completeHomeEntry()
        waitForOrientation(SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun `fixed landscape remains authoritative across home entry`() {
        launcherActivity.executeOnLauncher { it.rotationHelper.setFixedLandscape(true) }
        waitForOrientation(SCREEN_ORIENTATION_USER_LANDSCAPE)

        stopAndResumeLauncher(SCREEN_ORIENTATION_USER_LANDSCAPE)

        completeHomeEntry()
        waitForOrientation(SCREEN_ORIENTATION_USER_LANDSCAPE)
    }

    @Test
    fun `repeated stop and home entry does not strand rotation lock`() {
        setAllowRotation(false)
        waitForOrientation(SCREEN_ORIENTATION_NOSENSOR)

        repeat(2) {
            stopAndResumeLauncher(SCREEN_ORIENTATION_LOCKED)
            completeHomeEntry()
            waitForOrientation(SCREEN_ORIENTATION_NOSENSOR)
        }
    }

    private fun stopAndResumeLauncher(expectedOrientation: Int) {
        launcherActivity.activity.moveToState(Lifecycle.State.CREATED)
        waitForOrientation(expectedOrientation)
        launcherActivity.activity.moveToState(Lifecycle.State.RESUMED)
        waitForOrientation(expectedOrientation)
    }

    private fun completeHomeEntry() {
        launcherActivity.executeOnLauncher { it.onEnterAnimationComplete() }
    }

    private fun setAllowRotation(allowRotation: Boolean) {
        LauncherPrefs.get(targetContext).putSync(ALLOW_ROTATION.to(allowRotation))
    }

    private fun waitForOrientation(expectedOrientation: Int) {
        atMost(
            "Launcher did not request orientation $expectedOrientation",
            { launcherActivity.getFromLauncher { it.requestedOrientation } == expectedOrientation },
        )
    }
}
