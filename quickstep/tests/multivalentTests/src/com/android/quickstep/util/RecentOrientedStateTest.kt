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
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

data class HideActionsTestCase(
    val recentsRotation: Int,
    val touchRotation: Int,
    val isRotationAllowed: Boolean,
    val isFixedLandscape: Boolean,
)

data class RotationHandlerTestCase(
    val displayRotation: Int,
    val touchRotation: Int,
    val isRotationAllowed: Boolean,
    val isFixedLandscape: Boolean,
) {
    override fun toString(): String {
        return "TestCase(displayRotation=${Surface.rotationToString(displayRotation)}, " +
            "touchRotation=${Surface.rotationToString(touchRotation)}, " +
            "isRotationAllowed=$isRotationAllowed, " +
            "isFixedLandscape=$isFixedLandscape)"
    }
}

/**
 * Test all possible inputs to RecentsOrientedState.updateHandler. It tests all possible
 * combinations of rotations and relevant methods (two methods that return boolean values) but it
 * only provides the expected result when the final rotation is different from ROTATION_0 for
 * simplicity. So any case not shown in resultMap you can assume results in ROTATION_0.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class RecentOrientedStateTest {

    companion object {
        const val INVALID_ROTATION = -1
    }

    private fun createRecentOrientedState() =
        spy(
            RecentsOrientedState(
                ApplicationProvider.getApplicationContext(),
                FallbackActivityInterface.INSTANCE,
            ) {}
        )

    private fun rotationHandlerTest(
        testCase: RotationHandlerTestCase,
        expectedHandler: RecentsPagedOrientationHandler,
    ) {
        val recentOrientedState = createRecentOrientedState()
        whenever(recentOrientedState.isRecentsActivityRotationAllowed)
            .thenReturn(testCase.isRotationAllowed)
        whenever(recentOrientedState.isLauncherFixedLandscape).thenReturn(testCase.isFixedLandscape)

        recentOrientedState.update(testCase.displayRotation, testCase.touchRotation)
        val rotation = recentOrientedState.orientationHandler.rotation
        assertWithMessage("$testCase to ${Surface.rotationToString(rotation)},")
            .that(rotation)
            .isEqualTo(expectedHandler.rotation)
    }

    private fun shouldHideActionButtonTest(
        testCase: HideActionsTestCase,
        hideActionButtonsExpected: Boolean,
    ) {
        val recentOrientedState = createRecentOrientedState()
        whenever(recentOrientedState.recentsActivityRotation).thenReturn(testCase.recentsRotation)
        whenever(recentOrientedState.touchRotation).thenReturn(testCase.touchRotation)
        whenever(recentOrientedState.isRecentsActivityRotationAllowed)
            .thenReturn(testCase.isRotationAllowed)
        whenever(recentOrientedState.isLauncherFixedLandscape).thenReturn(testCase.isFixedLandscape)
        val res = recentOrientedState.shouldHideActionButtons()
        assertWithMessage(
                "Test case $testCase generated $res but should be $hideActionButtonsExpected"
            )
            .that(res)
            .isEqualTo(hideActionButtonsExpected)
    }

    @Test
    fun `stateId changes with flags`() {
        val recentOrientedState1 = createRecentOrientedState()
        val recentOrientedState2 = createRecentOrientedState()
        assertEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState1.setGestureActive(true)
        recentOrientedState2.setGestureActive(false)
        assertNotEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState2.setGestureActive(true)
        assertEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)
    }

    @Test
    fun `stateId changes with recents rotation`() {
        val recentOrientedState1 = createRecentOrientedState()
        val recentOrientedState2 = createRecentOrientedState()
        recentOrientedState1.setRecentsRotation(ROTATION_90)
        recentOrientedState2.setRecentsRotation(ROTATION_180)
        assertNotEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState2.setRecentsRotation(ROTATION_90)
        assertEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)
    }

    @Test
    fun `stateId changes with display rotation`() {
        val recentOrientedState1 = createRecentOrientedState()
        val recentOrientedState2 = createRecentOrientedState()
        recentOrientedState1.update(ROTATION_0, ROTATION_90)
        recentOrientedState2.update(ROTATION_0, ROTATION_180)
        assertNotEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState2.update(ROTATION_90, ROTATION_90)
        assertNotEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState2.update(ROTATION_90, ROTATION_0)
        assertNotEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)

        recentOrientedState2.update(ROTATION_0, ROTATION_90)
        assertEquals(recentOrientedState1.stateId, recentOrientedState2.stateId)
    }

    @Test
    fun `rotation handler test fixed landscape when device is portrait`() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = INVALID_ROTATION,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `rotation handler test fixed landscape when device is landscape`() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = INVALID_ROTATION,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `rotation handler test fixed landscape when device is seascape`() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = INVALID_ROTATION,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `rotation handler test fixed landscape when device is portrait and display rotation is portrait`() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = ROTATION_0,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `rotation handler test fixed landscape when device is landscape and display rotation is landscape `() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = ROTATION_90,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `rotation handler test fixed landscape when device is seascape and display rotation is seascape`() {
        rotationHandlerTest(
            testCase =
                RotationHandlerTestCase(
                    displayRotation = ROTATION_180,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            expectedHandler = PORTRAIT,
        )
    }

    @Test
    fun `should hide actions fixed landscape no rotation`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_0,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            hideActionButtonsExpected = false,
        )
    }

    @Test
    fun `should hide actions fixed landscape rotation 90`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_90,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            hideActionButtonsExpected = false,
        )
    }

    @Test
    fun `should hide actions recent rotation 180`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_180,
                    touchRotation = ROTATION_0,
                    isRotationAllowed = false,
                    isFixedLandscape = false,
                ),
            hideActionButtonsExpected = true,
        )
    }

    @Test
    fun `should hide actions touch rotation 180`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_0,
                    touchRotation = ROTATION_180,
                    isRotationAllowed = false,
                    isFixedLandscape = false,
                ),
            hideActionButtonsExpected = true,
        )
    }

    @Test
    fun `should hide actions rotation allowed`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_90,
                    touchRotation = ROTATION_180,
                    isRotationAllowed = true,
                    isFixedLandscape = false,
                ),
            hideActionButtonsExpected = false,
        )
    }

    @Test
    fun `should hide actions fixed landscape rotations and not allowed`() {
        shouldHideActionButtonTest(
            testCase =
                HideActionsTestCase(
                    recentsRotation = ROTATION_180,
                    touchRotation = ROTATION_90,
                    isRotationAllowed = false,
                    isFixedLandscape = true,
                ),
            hideActionButtonsExpected = false,
        )
    }
}
