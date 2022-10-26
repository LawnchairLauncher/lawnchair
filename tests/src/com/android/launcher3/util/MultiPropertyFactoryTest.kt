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

import android.util.FloatProperty
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [MultiPropertyFactory] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiPropertyFactoryTest {

    private val received = mutableListOf<Float>()

    private val receiveProperty: FloatProperty<Any> = object : FloatProperty<Any>("receive") {
        override fun setValue(obj: Any?, value: Float) {
            received.add(value)
        }
        override fun get(o: Any): Float {
            return 0f
        }
    }

    private val factory = MultiPropertyFactory(null, receiveProperty, 3) {
        x: Float, y: Float -> x + y
    }

    private val p1 = factory.get(0)
    private val p2 = factory.get(1)
    private val p3 = factory.get(2)

    @Test
    fun set_sameIndexes_allApplied() {
        val v1 = 50f
        val v2 = 100f
        p1.value = v1
        p1.value = v1
        p1.value = v2

        assertThat(received).containsExactly(v1, v1, v2)
    }

    @Test
    fun set_differentIndexes_aggregationApplied() {
        val v1 = 50f
        val v2 = 100f
        val v3 = 150f
        p1.value = v1
        p2.value = v2
        p3.value = v3

        assertThat(received).containsExactly(v1, v1 + v2, v1 + v2 + v3)
    }
}
