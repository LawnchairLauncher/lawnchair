/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.content.Context
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.wm.shell.ShellTestCase
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times

/**
 * Tests for [LetterboxSurfaceBuilder].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxSurfaceBuilderTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxSurfaceBuilderTest : ShellTestCase() {

    @Test
    fun `When surface is created mandatory methods are invoked`() {
        runTestScenario { r ->
            r.invokeBuilder()

            r.checkNameIsSet(expected = true)
            r.checkCallSiteIsSet(expected = true)
            r.checkSurfaceIsHidden(invoked = true, isHidden = true)
            r.checkColorLayerIsSet(expected = true)
            r.checkParentLeashIsSet(expected = true)
            r.checkSetLayerIsInvoked(expected = true)
            r.checkColorSpaceAgnosticIsSet(expected = true, value = true)
            r.checkColorIsSetFromLetterboxConfiguration(expected = true)
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxSurfaceBuilderRobotTest>) {
        val robot = LetterboxSurfaceBuilderRobotTest(mContext)
        consumer.accept(robot)
    }

    class LetterboxSurfaceBuilderRobotTest(val ctx: Context) {

        private val letterboxConfiguration: LetterboxConfiguration
        private val letterboxSurfaceBuilder: LetterboxSurfaceBuilder
        private val tx: SurfaceControl.Transaction
        private val parentLeash: SurfaceControl
        private val surfaceBuilder: SurfaceControl.Builder

        companion object {
            @JvmStatic
            val TEST_SURFACE_NAME = "SurfaceForTest"

            @JvmStatic
            val TEST_SURFACE_CALL_SITE = "CallSiteForTest"
        }

        init {
            letterboxConfiguration = LetterboxConfiguration(ctx)
            letterboxSurfaceBuilder = LetterboxSurfaceBuilder(letterboxConfiguration)
            tx = getTransactionMock()
            parentLeash = org.mockito.kotlin.mock<SurfaceControl>()
            surfaceBuilder = SurfaceControl.Builder()
            spyOn(surfaceBuilder)
        }

        fun invokeBuilder() {
            letterboxSurfaceBuilder.createSurface(
                tx,
                parentLeash,
                TEST_SURFACE_NAME,
                TEST_SURFACE_CALL_SITE,
                surfaceBuilder
            )
        }

        fun checkNameIsSet(expected: Boolean) {
            verify(surfaceBuilder, expected.asMode())
                .setName(TEST_SURFACE_NAME)
        }

        fun checkCallSiteIsSet(expected: Boolean) {
            verify(surfaceBuilder, expected.asMode())
                .setCallsite(TEST_SURFACE_CALL_SITE)
        }

        fun checkSurfaceIsHidden(invoked: Boolean, isHidden: Boolean) {
            verify(surfaceBuilder, invoked.asMode())
                .setHidden(isHidden)
        }

        fun checkColorLayerIsSet(expected: Boolean) {
            verify(surfaceBuilder, expected.asMode()).setColorLayer()
        }

        fun checkParentLeashIsSet(expected: Boolean) {
            verify(surfaceBuilder, expected.asMode()).setParent(parentLeash)
        }

        fun checkSetLayerIsInvoked(expected: Boolean) {
            verify(tx, expected.asMode()).setLayer(anyOrNull(), ArgumentMatchers.anyInt())
        }

        fun checkColorSpaceAgnosticIsSet(expected: Boolean, value: Boolean) {
            verify(tx, expected.asMode()).setColorSpaceAgnostic(anyOrNull(), eq(value))
        }

        fun checkColorIsSetFromLetterboxConfiguration(expected: Boolean) {
            val components = letterboxConfiguration.getBackgroundColorRgbArray()
            verify(tx, expected.asMode()).setColor(anyOrNull(), eq(components))
        }
    }
}
