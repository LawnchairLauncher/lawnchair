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
import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresDevice
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test quick switching to letterboxed app from launcher
 *
 * To run this test: `atest WMShellFlickerTestsOther:QuickSwitchLauncherToLetterboxAppTest`
 *
 * Actions:
 * ```
 *     Launch a letterboxed app
 *     Navigate home to show launcher
 *     Swipe right from the bottom of the screen to quick switch back to the app
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QuickSwitchLauncherToLetterboxAppTest(flicker: LegacyFlickerTest) : BaseAppCompat(flicker) {

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit = {
        setup {
            tapl.setExpectedRotationCheckEnabled(false)

            tapl.setExpectedRotation(flicker.scenario.startRotation.value)

            letterboxApp.launchViaIntent(wmHelper)
            tapl.goHome()
            wmHelper
                .StateSyncBuilder()
                .withHomeActivityVisible()
                .withWindowSurfaceDisappeared(letterboxApp)
                .waitForAndVerify()

            startDisplayBounds =
                wmHelper.currentState.layerState.physicalDisplayBounds ?: error("Display not found")
        }
        transitions {
            tapl.workspace.quickSwitchToPreviousApp()
            wmHelper
                .StateSyncBuilder()
                .withFullScreenApp(letterboxApp)
                .withNavOrTaskBarVisible()
                .withStatusBarVisible()
                .waitForAndVerify()
        }
        teardown { letterboxApp.exit(wmHelper) }
    }

    /**
     * Checks that [letterboxApp] is the top window at the end of the transition once we have fully
     * quick switched from the launcher back to the [letterboxApp].
     */
    @Postsubmit
    @Test
    fun endsWithAppBeingOnTop() {
        flicker.assertWmEnd { this.isAppWindowOnTop(letterboxApp) }
    }

    /** Checks that the transition starts with the home activity being tagged as visible. */
    @Postsubmit
    @Test
    fun startsWithHomeActivityFlaggedVisible() {
        flicker.assertWmStart { this.isHomeActivityVisible() }
    }

    /**
     * Checks that the transition starts with the [ComponentNameMatcher.LAUNCHER] windows
     * filling/covering exactly display size
     */
    @Postsubmit
    @Test
    fun startsWithLauncherWindowsCoverFullScreen() {
        flicker.assertWmStart {
            this.visibleRegion(ComponentNameMatcher.LAUNCHER).coversExactly(startDisplayBounds)
        }
    }

    /**
     * Checks that the transition starts with the [ComponentNameMatcher.LAUNCHER] layers
     * filling/covering exactly the display size.
     */
    @Postsubmit
    @Test
    fun startsWithLauncherLayersCoverFullScreen() {
        flicker.assertLayersStart {
            this.visibleRegion(ComponentNameMatcher.LAUNCHER).coversExactly(startDisplayBounds)
        }
    }

    /**
     * Checks that the transition starts with the [ComponentNameMatcher.LAUNCHER] being the top
     * window.
     */
    @Postsubmit
    @Test
    fun startsWithLauncherBeingOnTop() {
        flicker.assertWmStart { this.isAppWindowOnTop(ComponentNameMatcher.LAUNCHER) }
    }

    /**
     * Checks that the transition ends with the home activity being flagged as not visible. By this
     * point we should have quick switched away from the launcher back to the [letterboxApp].
     */
    @Postsubmit
    @Test
    fun endsWithHomeActivityFlaggedInvisible() {
        flicker.assertWmEnd { this.isHomeActivityInvisible() }
    }

    /**
     * Checks that [letterboxApp]'s window starts off invisible and becomes visible at some point
     * before the end of the transition and then stays visible until the end of the transition.
     */
    @Postsubmit
    @Test
    fun appWindowBecomesAndStaysVisible() {
        flicker.assertWm {
            this.isAppWindowInvisible(letterboxApp).then().isAppWindowVisible(letterboxApp)
        }
    }

    /**
     * Checks that [letterboxApp]'s layer starts off invisible and becomes visible at some point
     * before the end of the transition and then stays visible until the end of the transition.
     */
    @Postsubmit
    @Test
    fun appLayerBecomesAndStaysVisible() {
        flicker.assertLayers { this.isInvisible(letterboxApp).then().isVisible(letterboxApp) }
    }

    /**
     * Checks that the [ComponentNameMatcher.LAUNCHER] window starts off visible and becomes
     * invisible at some point before the end of the transition and then stays invisible until the
     * end of the transition.
     */
    @Postsubmit
    @Test
    fun launcherWindowBecomesAndStaysInvisible() {
        flicker.assertWm {
            this.isAppWindowOnTop(ComponentNameMatcher.LAUNCHER)
                .then()
                .isAppWindowNotOnTop(ComponentNameMatcher.LAUNCHER)
        }
    }

    /**
     * Checks that the [ComponentNameMatcher.LAUNCHER] layer starts off visible and becomes
     * invisible at some point before the end of the transition and then stays invisible until the
     * end of the transition.
     */
    @Postsubmit
    @Test
    fun launcherLayerBecomesAndStaysInvisible() {
        flicker.assertLayers {
            this.isVisible(ComponentNameMatcher.LAUNCHER)
                .then()
                .isInvisible(ComponentNameMatcher.LAUNCHER)
        }
    }

    /**
     * Checks that the [ComponentNameMatcher.LAUNCHER] window is visible at least until the app
     * window is visible. Ensures that at any point, either the launcher or [letterboxApp] windows
     * are at least partially visible.
     */
    @Postsubmit
    @Test
    fun appWindowIsVisibleOnceLauncherWindowIsInvisible() {
        flicker.assertWm {
            this.isAppWindowOnTop(ComponentNameMatcher.LAUNCHER)
                .then()
                .isAppWindowVisible(ComponentNameMatcher.SNAPSHOT, isOptional = true)
                .then()
                .isAppWindowVisible(letterboxApp)
        }
    }

    /**
     * Checks that the [ComponentNameMatcher.LAUNCHER] layer is visible at least until the app layer
     * is visible. Ensures that at any point, either the launcher or [letterboxApp] layers are at
     * least partially visible.
     */
    @Postsubmit
    @Test
    fun appLayerIsVisibleOnceLauncherLayerIsInvisible() {
        flicker.assertLayers {
            this.isVisible(ComponentNameMatcher.LAUNCHER)
                .then()
                .isVisible(ComponentNameMatcher.SNAPSHOT, isOptional = true)
                .then()
                .isVisible(letterboxApp)
        }
    }

    /**
     * Checks that the [ComponentNameMatcher.LETTERBOX] layer is visible as soon as the
     * [letterboxApp] layer is visible at the end of the transition once we have fully quick
     * switched from the launcher back to the [letterboxApp].
     */
    @Postsubmit
    @Test
    fun appAndLetterboxLayersBothVisibleOnceLauncherIsInvisible() {
        flicker.assertLayers {
            this.isVisible(ComponentNameMatcher.LAUNCHER)
                .then()
                .isVisible(ComponentNameMatcher.SNAPSHOT, isOptional = true)
                .then()
                .isVisible(letterboxApp)
                .isVisible(ComponentNameMatcher.LETTERBOX)
        }
    }

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun taskBarLayerIsVisibleAtStartAndEnd() = super.taskBarLayerIsVisibleAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

    companion object {
        /** {@inheritDoc} */
        private var startDisplayBounds = Rect()

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL),
                supportedRotations = listOf(Rotation.ROTATION_90)
            )
        }
    }
}
