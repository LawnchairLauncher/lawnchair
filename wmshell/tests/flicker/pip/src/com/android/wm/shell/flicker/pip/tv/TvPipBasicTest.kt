/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.tv

import android.graphics.Rect
import android.util.Rational
import androidx.test.filters.RequiresDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Test Pip Menu on TV. To run this test: `atest WMShellFlickerTests:TvPipBasicTest` */
@RequiresDevice
@RunWith(Parameterized::class)
class TvPipBasicTest(private val radioButtonId: String, private val pipWindowRatio: Rational?) :
    TvPipTestBase() {

    @Test
    fun enterPip_openMenu_pressBack_closePip() {
        // Launch test app
        testApp.launchViaIntent()

        // Set up ratio and enter Pip
        testApp.clickObject(radioButtonId)
        testApp.clickEnterPipButton(wmHelper)

        val actualRatio: Float =
            testApp.ui?.visibleBounds?.ratio ?: fail("Application UI not found")
        pipWindowRatio?.let { expectedRatio ->
            assertEquals("Wrong Pip window ratio", expectedRatio.toFloat(), actualRatio)
        }

        // Pressing the Window key should bring up Pip menu
        uiDevice.pressWindowKey()
        uiDevice.waitForTvPipMenu() ?: fail("Pip menu should have been shown")

        // Pressing the Back key should close the Pip menu
        uiDevice.pressBack()
        assertTrue("Pip menu should have closed", uiDevice.waitForTvPipMenuToClose())

        // Make sure Pip Window ration remained the same after Pip menu was closed
        testApp.ui?.visibleBounds?.let { newBounds ->
            assertEquals("Pip window ratio has changed", actualRatio, newBounds.ratio)
        }
            ?: fail("Application UI not found")

        // Close Pip
        testApp.closePipWindow()
    }

    private val Rect.ratio: Float
        get() = width().toFloat() / height()

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<Array<Any?>> {
            infix fun Int.to(denominator: Int) = Rational(this, denominator)
            return listOf(
                arrayOf("ratio_default", null),
                arrayOf("ratio_square", 1 to 1),
                arrayOf("ratio_wide", 2 to 1),
                arrayOf("ratio_tall", 1 to 2)
            )
        }
    }
}
