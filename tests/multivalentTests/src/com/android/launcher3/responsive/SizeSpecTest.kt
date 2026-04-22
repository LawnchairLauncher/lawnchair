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

package com.android.launcher3.responsive

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractDeviceProfileTest
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SizeSpecTest : AbstractDeviceProfileTest() {
    override val runningContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        initializeVarsForPhone(deviceSpecs["phone"]!!)
    }

    @Test
    fun valid_values() {
        val combinations =
            listOf(
                SizeSpec(100f, 0f, 0f, false),
                SizeSpec(0f, 1f, 0f, false),
                SizeSpec(0f, 0f, 1f, false),
                SizeSpec(0f, 0f, 0f, false),
                SizeSpec(0f, 0f, 0f, true),
                SizeSpec(100f, 0f, 0f, false, 100),
                SizeSpec(0f, 1f, 0f, false, 100),
                SizeSpec(0f, 0f, 1f, false, 100),
                SizeSpec(0f, 0f, 0f, false, 100),
                SizeSpec(0f, 0f, 0f, true, 100)
            )

        for (instance in combinations) {
            assertThat(instance.isValid()).isEqualTo(true)
        }
    }

    @Test
    fun validate_getCalculatedValue() {
        val availableSpace = 100
        val matchWorkspaceValue = 101
        val combinations =
            listOf(
                SizeSpec(100f) to 100,
                SizeSpec(ofAvailableSpace = .5f) to (availableSpace * .5f).roundToInt(),
                SizeSpec(ofRemainderSpace = .5f) to 0,
                SizeSpec(matchWorkspace = true) to matchWorkspaceValue,
                // Restricts max size up to 10 (calculated value > 10)
                SizeSpec(100f, maxSize = 10) to 10,
                SizeSpec(ofAvailableSpace = .5f, maxSize = 10) to 10,
                SizeSpec(ofRemainderSpace = .5f, maxSize = 10) to 0,
                SizeSpec(matchWorkspace = true, maxSize = 10) to 10
            )

        for ((sizeSpec, expectedValue) in combinations) {
            val value = sizeSpec.getCalculatedValue(availableSpace, matchWorkspaceValue)
            assertThat(value).isEqualTo(expectedValue)
        }
    }

    @Test
    fun validate_getRemainderSpaceValue() {
        val remainderSpace = 100
        val defaultValue = 50
        val combinations =
            listOf(
                SizeSpec(100f) to defaultValue,
                SizeSpec(ofAvailableSpace = .5f) to defaultValue,
                SizeSpec(ofRemainderSpace = .5f) to (remainderSpace * .5f).roundToInt(),
                SizeSpec(matchWorkspace = true) to defaultValue,
                // Restricts max size up to 10 (defaultValue > 10)
                SizeSpec(100f, maxSize = 10) to 10,
                SizeSpec(ofAvailableSpace = .5f, maxSize = 10) to 10,
                SizeSpec(ofRemainderSpace = .5f, maxSize = 10) to 10,
                SizeSpec(matchWorkspace = true, maxSize = 10) to 10,
            )

        for ((sizeSpec, expectedValue) in combinations) {
            val value = sizeSpec.getRemainderSpaceValue(remainderSpace, defaultValue)
            assertThat(value).isEqualTo(expectedValue)
        }
    }

    @Test
    fun multiple_values_assigned() {
        val combinations =
            listOf(
                SizeSpec(1f, 1f, 0f, false),
                SizeSpec(1f, 0f, 1f, false),
                SizeSpec(1f, 0f, 0f, true),
                SizeSpec(0f, 1f, 1f, false),
                SizeSpec(0f, 1f, 0f, true),
                SizeSpec(0f, 0f, 1f, true),
                SizeSpec(1f, 1f, 1f, true)
            )

        for (instance in combinations) {
            assertThat(instance.isValid()).isEqualTo(false)
        }
    }

    @Test
    fun invalid_values() {
        val combinations =
            listOf(
                SizeSpec(-1f, 0f, 0f, false),
                SizeSpec(0f, 1.1f, 0f, false),
                SizeSpec(0f, -0.1f, 0f, false),
                SizeSpec(0f, 0f, 1.1f, false),
                SizeSpec(0f, 0f, -0.1f, false),
                SizeSpec(0f, 0f, 0f, false, -10),
                SizeSpec(50f, 0f, 0f, false, 10)
            )

        for (instance in combinations) {
            assertThat(instance.isValid()).isEqualTo(false)
        }
    }

    @Test
    fun onlyFixedSize() {
        assertThat(SizeSpec(fixedSize = 16f).onlyFixedSize()).isEqualTo(true)

        val combinations =
            listOf(
                SizeSpec(0f, 1.1f, 0f, false),
                SizeSpec(0f, 0f, 1.1f, false),
                SizeSpec(0f, 0f, 0f, true)
            )

        for (instance in combinations) {
            assertThat(instance.onlyFixedSize()).isEqualTo(false)
        }
    }
}
