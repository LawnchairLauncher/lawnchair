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

package com.android.wm.shell.flicker.splitscreen

import android.platform.test.annotations.Presubmit
import android.tools.NavBar
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.wm.shell.flicker.splitscreen.benchmark.SplitScreenBase
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.layerBecomesInvisible
import com.android.wm.shell.flicker.utils.splitAppLayerBoundsIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.splitAppLayerBoundsSnapToDivider
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test quick switch between two split pairs.
 *
 * To run this test: `atest WMShellFlickerTestsSplitScreen:SwitchBetweenSplitPairsNoPip`
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SwitchBetweenSplitPairsNoPip(override val flicker: LegacyFlickerTest) :
    SplitScreenBase(flicker) {

    val thirdApp = SplitScreenUtils.getSendNotification(instrumentation)
    val pipApp = PipAppHelper(instrumentation)

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            defaultSetup(this)
            defaultTeardown(this)
            thisTransition(this)
        }

    val thisTransition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                tapl.goHome()
                SplitScreenUtils.enterSplit(
                    wmHelper,
                    tapl,
                    device,
                    primaryApp,
                    secondaryApp,
                    flicker.scenario.startRotation
                )
                SplitScreenUtils.enterSplit(
                    wmHelper,
                    tapl,
                    device,
                    thirdApp,
                    pipApp,
                    flicker.scenario.startRotation
                )
                pipApp.enableAutoEnterForPipActivity()
                SplitScreenUtils.waitForSplitComplete(wmHelper, thirdApp, pipApp)
            }
            transitions {
                tapl.launchedAppState.quickSwitchToPreviousApp()
                SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
            }
            teardown {
                pipApp.exit(wmHelper)
                thirdApp.exit(wmHelper)
            }
        }

    /** Checks that [pipApp] window won't enter pip */
    @Presubmit
    @Test
    fun notEnterPip() {
        flicker.assertWm { isNotPinned(pipApp) }
    }

    /** Checks the [pipApp] task did not reshow during transition. */
    @Presubmit
    @Test
    fun app1WindowIsVisibleOnceApp2WindowIsInvisible() {
        flicker.assertLayers {
            this.isVisible(pipApp)
                .then()
                .isVisible(ComponentNameMatcher.LAUNCHER, isOptional = true)
                .then()
                .isVisible(ComponentNameMatcher.SNAPSHOT, isOptional = true)
                .then()
                .isInvisible(pipApp)
                .isVisible(secondaryApp)
        }
    }

    @Presubmit
    @Test
    fun primaryAppBoundsIsVisibleAtEnd() =
        flicker.splitAppLayerBoundsIsVisibleAtEnd(
            primaryApp,
            landscapePosLeft = tapl.isTablet,
            portraitPosTop = false
        )

    @Presubmit
    @Test
    fun secondaryAppBoundsIsVisibleAtEnd() =
        flicker.splitAppLayerBoundsIsVisibleAtEnd(
            secondaryApp,
            landscapePosLeft = !tapl.isTablet,
            portraitPosTop = true
        )

    /** Checks the [pipApp] task become invisible after transition finish. */
    @Presubmit @Test fun pipAppLayerBecomesInvisible() = flicker.layerBecomesInvisible(pipApp)

    /** Checks the [pipApp] task is in split screen bounds when transition start. */
    @Presubmit
    @Test
    fun pipAppBoundsIsVisibleAtBegin() =
        flicker.assertLayersStart {
            this.splitAppLayerBoundsSnapToDivider(
                pipApp,
                landscapePosLeft = !tapl.isTablet,
                portraitPosTop = true,
                flicker.scenario.startRotation
            )
        }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL)
            )
    }
}
