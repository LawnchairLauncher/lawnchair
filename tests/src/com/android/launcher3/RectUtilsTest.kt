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

package com.android.launcher3

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RectUtilsTest {

    private val srcRect = Rect()
    private val destRect = Rect()
    private val letterBoxedRect = Rect()

    @Test
    fun letterBoxSelf_toSameRect_noScale() {
        srcRect.set(0, 0, 100, 100)
        destRect.set(0, 0, 100, 100)

        srcRect.letterBox(destRect)

        assertThat(srcRect).isEqualTo(Rect(0, 0, 100, 100))
    }

    @Test
    fun letterBox_toSameRect_noScale() {
        srcRect.set(0, 0, 100, 100)
        destRect.set(0, 0, 100, 100)

        srcRect.letterBox(destRect, letterBoxedRect)

        assertThat(letterBoxedRect).isEqualTo(Rect(0, 0, 100, 100))
        assertThat(srcRect).isEqualTo(Rect(0, 0, 100, 100))
    }

    @Test
    fun letterBoxSelf_toSmallHeight_scaleDownHorizontally() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(0, 0, 939, 520)

        srcRect.letterBox(destRect)

        assertThat(srcRect).isEqualTo(Rect(114, 0, 825, 520))
    }

    @Test
    fun letterBoxRect_toSmallHeight_scaleDownHorizontally() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(0, 0, 939, 520)

        srcRect.letterBox(destRect, letterBoxedRect)

        assertThat(letterBoxedRect).isEqualTo(Rect(114, 0, 825, 520))
        assertThat(srcRect).isEqualTo(Rect(0, 0, 2893, 2114))
    }

    @Test
    fun letterBoxSelf_toSmallHeightWithOffset_scaleDownHorizontally() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(10, 20, 949, 540)

        srcRect.letterBox(destRect)

        assertThat(srcRect).isEqualTo(Rect(124, 20, 835, 540))
    }

    @Test
    fun letterBoxRect_toSmallHeightWithOffset_scaleDownHorizontally() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(10, 20, 949, 540)

        srcRect.letterBox(destRect, letterBoxedRect)

        assertThat(letterBoxedRect).isEqualTo(Rect(124, 20, 835, 540))
        assertThat(srcRect).isEqualTo(Rect(0, 0, 2893, 2114))
    }

    @Test
    fun letterBoxSelf_toSmallWidth_scaleDownVertically() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(0, 0, 520, 939)

        srcRect.letterBox(destRect)

        assertThat(srcRect).isEqualTo(Rect(0, 280, 520, 659))
    }

    @Test
    fun letterBoxRect_toSmallWidth_scaleDownVertically() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(0, 0, 520, 939)

        srcRect.letterBox(destRect, letterBoxedRect)

        assertThat(letterBoxedRect).isEqualTo(Rect(0, 280, 520, 659))
        assertThat(srcRect).isEqualTo(Rect(0, 0, 2893, 2114))
    }

    @Test
    fun letterBoxSelf_toSmallWidthWithOffset_scaleDownVertically() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(40, 60, 560, 999)

        srcRect.letterBox(destRect)

        assertThat(srcRect).isEqualTo(Rect(40, 340, 560, 719))
    }

    @Test
    fun letterBoxRect_toSmallWidthWithOffset_scaleDownVertically() {
        srcRect.set(0, 0, 2893, 2114)
        destRect.set(40, 60, 560, 999)

        srcRect.letterBox(destRect, letterBoxedRect)

        assertThat(letterBoxedRect).isEqualTo(Rect(40, 340, 560, 719))
        assertThat(srcRect).isEqualTo(Rect(0, 0, 2893, 2114))
    }
}
