/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.splitscreen.benchmark.DragDividerToResizeBenchmark
import com.android.wm.shell.flicker.utils.ICommonAssertions
import com.android.wm.shell.flicker.utils.SPLIT_SCREEN_DIVIDER_COMPONENT
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.layerKeepVisible
import com.android.wm.shell.flicker.utils.splitAppLayerBoundsChanges
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test resize split by dragging the divider bar.
 *
 * To run this test: `atest WMShellFlickerTestsSplitScreen:DragDividerToResize`
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DragDividerToResize(override val flicker: LegacyFlickerTest) :
    DragDividerToResizeBenchmark(flicker), ICommonAssertions {
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            defaultSetup(this)
            defaultTeardown(this)
            thisTransition(this)
        }

    @Presubmit
    @Test
    fun splitScreenDividerKeepVisible() = flicker.layerKeepVisible(SPLIT_SCREEN_DIVIDER_COMPONENT)

    @FlakyTest(bugId = 291678271)
    @Test
    fun primaryAppLayerVisibilityChanges() {
        flicker.assertLayers {
            this.isVisible(secondaryApp)
                .then()
                .isInvisible(secondaryApp)
                .then()
                .isVisible(secondaryApp)
        }
    }

    @FlakyTest(bugId = 291678271)
    @Test
    fun secondaryAppLayerVisibilityChanges() {
        flicker.assertLayers {
            this.isVisible(secondaryApp)
                .then()
                .isInvisible(secondaryApp)
                .then()
                .isVisible(secondaryApp)
        }
    }

    @Presubmit @Test fun primaryAppWindowKeepVisible() = flicker.appWindowKeepVisible(primaryApp)

    @Presubmit
    @Test
    fun secondaryAppWindowKeepVisible() = flicker.appWindowKeepVisible(secondaryApp)

    @FlakyTest(bugId = 291678271)
    @Test
    fun primaryAppBoundsChanges() {
        flicker.splitAppLayerBoundsChanges(
            primaryApp,
            landscapePosLeft = true,
            portraitPosTop = false
        )
    }

    @FlakyTest(bugId = 291678271)
    @Test
    fun secondaryAppBoundsChanges() =
        flicker.splitAppLayerBoundsChanges(
            secondaryApp,
            landscapePosLeft = false,
            portraitPosTop = true
        )

    @FlakyTest(bugId = 291678271)
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() = LegacyFlickerTestFactory.nonRotationTests()
    }
}
