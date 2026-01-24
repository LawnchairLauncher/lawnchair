/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.bubbles.animation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [AnimatableScaleMatrix] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AnimatableScaleMatrixTest {

    @Test
    fun test_equals_matricesWithSameValuesAreNotEqual() {
        val matrix1 = AnimatableScaleMatrix().apply { setScale(0.5f, 0.5f) }
        val matrix2 = AnimatableScaleMatrix().apply { setScale(0.5f, 0.5f) }
        assertThat(matrix1).isNotEqualTo(matrix2)
    }

    @Test
    fun test_hashCode_remainsSameIfMatrixUpdates() {
        val matrix = AnimatableScaleMatrix().apply { setScale(0.5f, 0.5f) }
        val hash1 = matrix.hashCode()
        matrix.setScale(0.75f, 0.75f)
        val hash2 = matrix.hashCode()

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun test_hashCode_matricesWithSameValuesHaveDiffHashCode() {
        val matrix1 = AnimatableScaleMatrix().apply { setScale(0.5f, 0.5f) }
        val matrix2 = AnimatableScaleMatrix().apply { setScale(0.5f, 0.5f) }
        assertThat(matrix1.hashCode()).isNotEqualTo(matrix2.hashCode())
    }
}
