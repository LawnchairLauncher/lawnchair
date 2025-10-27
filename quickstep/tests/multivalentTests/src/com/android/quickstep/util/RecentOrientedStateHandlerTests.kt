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

package com.android.quickstep.util

import android.view.Surface
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_180
import android.view.Surface.ROTATION_90
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.quickstep.FallbackActivityInterface
import com.android.quickstep.orientation.RecentsPagedOrientationHandler
import com.android.quickstep.orientation.RecentsPagedOrientationHandler.Companion.PORTRAIT
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Test all possible inputs to RecentsOrientedState.updateHandler. It tests all possible
 * combinations of rotations and relevant methods (two methods that return boolean values) but it
 * only provides the expected result when the final rotation is different from ROTATION_0 for
 * simplicity. So any case not shown in resultMap you can assume results in ROTATION_0.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class RecentOrientedStateHandlerTests {

    data class TestCase(
        val recentsRotation: Int,
        val displayRotation: Int,
        val touchRotation: Int,
        val isRotationAllowed: Boolean,
        val isFixedLandscape: Boolean,
    ) {
        override fun toString(): String {
            return "TestCase(recentsRotation=${Surface.rotationToString(recentsRotation)}, " +
                "displayRotation=${Surface.rotationToString(displayRotation)}, " +
                "touchRotation=${Surface.rotationToString(touchRotation)}, " +
                "isRotationAllowed=$isRotationAllowed, " +
                "isFixedLandscape=$isFixedLandscape)"
        }
    }

    private fun runTestCase(testCase: TestCase, expectedHandler: RecentsPagedOrientationHandler) {
        val recentOrientedState =
            spy(
                RecentsOrientedState(
                    ApplicationProvider.getApplicationContext(),
                    FallbackActivityInterface.INSTANCE,
                ) {}
            )
        whenever(recentOrientedState.isRecentsActivityRotationAllowed).thenAnswer {
            testCase.isRotationAllowed
        }
        whenever(recentOrientedState.isLauncherFixedLandscape).thenAnswer {
            testCase.isFixedLandscape
        }

        recentOrientedState.update(testCase.displayRotation, testCase.touchRotation)
        val rotation = recentOrientedState.orientationHandler.rotation
        assertWithMessage("$testCase to ${Surface.rotationToString(rotation)},")
            .that(rotation)
            .isEqualTo(expectedHandler.rotation)
    }

    @Test
    fun `test fixed landscape when device is portrait`() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_0,
                displayRotation = -1,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }

    @Test
    fun `test fixed landscape when device is landscape`() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_90,
                displayRotation = -1,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }

    @Test
    fun `test fixed landscape when device is seascape`() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_180,
                displayRotation = -1,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }

    @Test
    fun `test fixed landscape when device is portrait and display rotation is portrait`() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_0,
                displayRotation = ROTATION_0,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }

    @Test
    fun `test fixed landscape when device is landscape and display rotation is landscape `() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_90,
                displayRotation = ROTATION_90,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }

    @Test
    fun `test fixed landscape when device is seascape and display rotation is seascape`() {
        runTestCase(
            TestCase(
                recentsRotation = ROTATION_180,
                displayRotation = ROTATION_180,
                touchRotation = ROTATION_0,
                isRotationAllowed = false,
                isFixedLandscape = true,
            ),
            PORTRAIT,
        )
    }
}
