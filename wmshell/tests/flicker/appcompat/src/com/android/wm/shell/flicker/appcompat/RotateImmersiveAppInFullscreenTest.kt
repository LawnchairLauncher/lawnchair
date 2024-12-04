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

package com.android.wm.shell.flicker.appcompat

import android.graphics.Rect
import android.os.Build
import android.platform.test.annotations.Postsubmit
import android.system.helpers.CommandsHelper
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.FIND_TIMEOUT
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.LetterboxAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test rotating an immersive app in fullscreen.
 *
 * To run this test: `atest WMShellFlickerTestsOther:RotateImmersiveAppInFullscreenTest`
 *
 * Actions:
 * ```
 *     Rotate the device by 90 degrees to trigger a rotation through sensors
 *     Verify that the button exists
 * ```
 *
 * Notes:
 * ```
 *     Some default assertions that are inherited from
 *     the `BaseTest` are ignored due to the nature of the immersive apps.
 *
 *     This test only works with Cuttlefish devices.
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class RotateImmersiveAppInFullscreenTest(flicker: LegacyFlickerTest) : BaseAppCompat(flicker) {

    private val immersiveApp =
        LetterboxAppHelper(
            instrumentation,
            launcherName = ActivityOptions.PortraitImmersiveActivity.LABEL,
            component = ActivityOptions.PortraitImmersiveActivity.COMPONENT.toFlickerComponent()
        )

    private val cmdHelper: CommandsHelper = CommandsHelper.getInstance(instrumentation)
    private val execAdb: (String) -> String = { cmd -> cmdHelper.executeShellCommand(cmd) }

    protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)

    private val isCuttlefishDevice: Boolean = Build.MODEL.contains("Cuttlefish")

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                setStartRotation()
                immersiveApp.launchViaIntent(wmHelper)
                startDisplayBounds =
                    wmHelper.currentState.layerState.physicalDisplayBounds
                        ?: error("Display not found")
            }
            transitions {
                if (isCuttlefishDevice) {
                    // Simulates a device rotation through sensors because the rotation button
                    // only appears in a rotation event through sensors
                    execAdb("/vendor/bin/cuttlefish_sensor_injection rotate 0")
                    // verify rotation button existence
                    val rotationButtonSelector = By.res(LAUNCHER_PACKAGE, "rotate_suggestion")
                    uiDevice.wait(Until.hasObject(rotationButtonSelector), FIND_TIMEOUT)
                    uiDevice.findObject(rotationButtonSelector)
                        ?: error("rotation button not found")
                }
            }
            teardown { immersiveApp.exit(wmHelper) }
        }

    @Before
    fun setUpForImmersiveAppTests() {
        Assume.assumeTrue(isCuttlefishDevice)
    }

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun taskBarLayerIsVisibleAtStartAndEnd() {}

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun navBarLayerIsVisibleAtStartAndEnd() {}

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun statusBarLayerIsVisibleAtStartAndEnd() {}

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun taskBarWindowIsAlwaysVisible() {}

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun navBarWindowIsAlwaysVisible() {}

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun statusBarWindowIsAlwaysVisible() {}

    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun statusBarLayerPositionAtStartAndEnd() {}

    @Test
    @Ignore("Not applicable to this CUJ. App is in immersive mode.")
    override fun visibleWindowsShownMoreThanOneConsecutiveEntry() {}

    /** Test that app is fullscreen by checking status bar and task bar visibility. */
    @Postsubmit
    @Test
    fun appWindowFullScreen() {
        flicker.assertWmEnd {
            this.isAppWindowInvisible(ComponentNameMatcher.STATUS_BAR)
                .isAppWindowInvisible(ComponentNameMatcher.TASK_BAR)
                .visibleRegion(immersiveApp)
                .coversExactly(startDisplayBounds)
        }
    }

    /** Test that app is in the original rotation we have set up. */
    @Postsubmit
    @Test
    fun appInOriginalRotation() {
        flicker.assertWmEnd { this.hasRotation(Rotation.ROTATION_90) }
    }

    companion object {
        private var startDisplayBounds = Rect()
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_90),
                // TODO(b/292403378): 3 button mode not added as rotation button is hidden in
                // taskbar
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL)
            )
        }
    }
}
