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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.flicker.subject.exceptions.IncorrectRegionException
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.pip.common.PipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/** Test minimizing a pip window via pinch in gesture. */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipPinchInTest(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions { pipApp.pinchInPipWindow(wmHelper, 0.4f, 30) }
    }

    /**
     * Checks that the visible region area of [pipApp] decreases and then increases during the
     * animation.
     */
    @Presubmit
    @Test
    fun pipLayerAreaDecreasesThenIncreases() {
        val isAreaDecreasing = arrayOf(true)
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                if (isAreaDecreasing[0]) {
                    try {
                        current.visibleRegion.notBiggerThan(previous.visibleRegion.region)
                    } catch (e: IncorrectRegionException) {
                        isAreaDecreasing[0] = false
                    }
                } else {
                    previous.visibleRegion.notBiggerThan(current.visibleRegion.region)
                }
            }
        }
    }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
    }
}
