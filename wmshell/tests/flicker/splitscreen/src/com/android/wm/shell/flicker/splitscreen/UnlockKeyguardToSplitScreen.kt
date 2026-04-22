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

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import android.tools.NavBar
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.region.RegionSubject
import android.tools.traces.component.ComponentNameMatcher.Companion.WALLPAPER_BBQ_WRAPPER
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.splitscreen.benchmark.UnlockKeyguardToSplitScreenBenchmark
import com.android.wm.shell.flicker.utils.ICommonAssertions
import com.android.wm.shell.flicker.utils.SPLIT_SCREEN_DIVIDER_COMPONENT
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.layerIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.splitAppLayerBoundsIsVisibleAtEnd
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test unlocking insecure keyguard to back to split screen tasks and verify the transition
 * behavior.
 *
 * To run this test: `atest WMShellFlickerTestsSplitScreen:UnlockKeyguardToSplitScreen`
 */
@RequiresDevice
@FlakyTest(bugId = 293578017)
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UnlockKeyguardToSplitScreen(override val flicker: LegacyFlickerTest) :
    UnlockKeyguardToSplitScreenBenchmark(flicker), ICommonAssertions {
    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            defaultTeardown(this)
            thisTransition(this)
        }

    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() =
        super.visibleLayersShownMoreThanOneConsecutiveEntry()

    // TODO(b/293578017) remove once that bug is resolve
    @Test
    @Presubmit
    fun visibleLayersShownMoreThanOneConsecutiveEntry_withoutWallpaper() =
        flicker.assertLayers {
            this.visibleLayersShownMoreThanOneConsecutiveEntry(
                LayersTraceSubject.VISIBLE_FOR_MORE_THAN_ONE_ENTRY_IGNORE_LAYERS +
                    listOf(WALLPAPER_BBQ_WRAPPER)
            )
        }

    @Test
    fun splitScreenDividerIsVisibleAtEnd() {
        flicker.assertLayersEnd { this.isVisible(SPLIT_SCREEN_DIVIDER_COMPONENT) }
    }

    @Test fun primaryAppLayerIsVisibleAtEnd() = flicker.layerIsVisibleAtEnd(primaryApp)

    @Test
    fun primaryAppBoundsIsVisibleAtEnd() =
        flicker.splitAppLayerBoundsIsVisibleAtEnd(
            primaryApp,
            landscapePosLeft = false,
            portraitPosTop = false
        )

    @Test
    fun secondaryAppBoundsIsVisibleAtEnd() =
        flicker.splitAppLayerBoundsIsVisibleAtEnd(
            secondaryApp,
            landscapePosLeft = true,
            portraitPosTop = true
        )

    @Test fun primaryAppWindowIsVisibleAtEnd() = flicker.appWindowIsVisibleAtEnd(primaryApp)

    @Test fun secondaryAppWindowIsVisibleAtEnd() = flicker.appWindowIsVisibleAtEnd(secondaryApp)

    @Test
    fun notOverlapsForPrimaryAndSecondaryAppLayers() {
        flicker.assertLayers {
            this.invoke("notOverlapsForPrimaryAndSecondaryLayers") {
                val primaryAppRegions =
                    it.subjects
                        .filter { subject ->
                            subject.name.contains(primaryApp.toLayerName()) && subject.isVisible
                        }
                        .mapNotNull { primaryApp -> primaryApp.layer.visibleRegion }

                val primaryAppRegionArea = RegionSubject(primaryAppRegions, it.timestamp)
                it.visibleRegion(secondaryApp).notOverlaps(primaryAppRegionArea.region)
            }
        }
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
