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

package com.android.launcher3.util

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [MultiScalePropertyFactory] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiScalePropertyTest {

    private val received = mutableListOf<Float>()

    private val factory =
        object : MultiScalePropertyFactory<View?>("Test") {
            override fun apply(obj: View?, value: Float) {
                received.add(value)
            }
        }

    private val p1 = factory.get(1)
    private val p2 = factory.get(2)
    private val p3 = factory.get(3)

    @Test
    fun set_multipleSame_bothAppliedd() {
        p1.set(null, 0.5f)
        p1.set(null, 0.5f)

        assertThat(received).containsExactly(0.5f, 0.5f)
    }

    @Test
    fun set_differentIndexes_oneValuesNotCounted() {
        val v1 = 0.5f
        val v2 = 1.0f
        p1.set(null, v1)
        p2.set(null, v2)

        assertThat(received).containsExactly(v1, v1)
    }

    @Test
    fun set_onlyOneSetToOne_oneApplied() {
        p1.set(null, 1.0f)

        assertThat(received).containsExactly(1.0f)
    }

    @Test
    fun set_onlyOneLessThanOne_applied() {
        p1.set(null, 0.5f)

        assertThat(received).containsExactly(0.5f)
    }

    @Test
    fun set_differentIndexes_boundToMin() {
        val v1 = 0.5f
        val v2 = 0.6f
        p1.set(null, v1)
        p2.set(null, v2)

        assertThat(received).containsExactly(v1, v1)
    }

    @Test
    fun set_allHigherThanOne_boundToMax() {
        val v1 = 3.0f
        val v2 = 2.0f
        val v3 = 1.0f
        p1.set(null, v1)
        p2.set(null, v2)
        p3.set(null, v3)

        assertThat(received).containsExactly(v1, v1, v1)
    }

    @Test
    fun set_differentIndexes_firstModified_aggregationApplied() {
        val v1 = 0.5f
        val v2 = 0.6f
        val v3 = 4f
        p1.set(null, v1)
        p2.set(null, v2)
        p3.set(null, v3)

        assertThat(received).containsExactly(v1, v1, v1 * v2 * v3)
    }
}
