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

/** Unit tests for [MultiAdditivePropertyFactory] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiAdditivePropertyTest {

    private val received = mutableListOf<Float>()

    private val factory =
        object : MultiAdditivePropertyFactory<View?>("Test", View.TRANSLATION_X) {
            override fun apply(obj: View?, value: Float) {
                received.add(value)
            }
        }

    private val p1 = factory.get(1)
    private val p2 = factory.get(2)
    private val p3 = factory.get(3)

    @Test
    fun set_sameIndexes_allApplied() {
        val v1 = 50f
        val v2 = 100f
        p1.set(null, v1)
        p1.set(null, v1)
        p1.set(null, v2)

        assertThat(received).containsExactly(v1, v1, v2)
    }

    @Test
    fun set_differentIndexes_aggregationApplied() {
        val v1 = 50f
        val v2 = 100f
        val v3 = 150f
        p1.set(null, v1)
        p2.set(null, v2)
        p3.set(null, v3)

        assertThat(received).containsExactly(v1, v1 + v2, v1 + v2 + v3)
    }
}
