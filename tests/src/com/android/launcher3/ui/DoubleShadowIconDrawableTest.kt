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

package com.android.launcher3.ui

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.views.DoubleShadowIconDrawable
import com.android.launcher3.views.ShadowInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DoubleShadowIconDrawableTest {

    @Test
    fun `DoubleShadowIconDrawable is setup correctly from given ShadowInfo`() {
        // Given
        val shadowInfo: ShadowInfo = mock()
        val originalDrawable: Drawable = mock()
        val iconSize = 2
        val iconInsetSize = 1
        // When
        val drawableUnderTest =
            DoubleShadowIconDrawable(shadowInfo, originalDrawable, iconSize, iconInsetSize)
        // Then
        assertThat(drawableUnderTest.intrinsicHeight).isEqualTo(iconSize)
        assertThat(drawableUnderTest.intrinsicWidth).isEqualTo(iconSize)
    }

    @Test
    fun `createShadowRenderNode creates RenderNode for shadow effects`() {
        // Given
        val shadowInfo =
            ShadowInfo(
                ambientShadowBlur = 1f,
                ambientShadowColor = 2,
                keyShadowBlur = 3f,
                keyShadowOffsetX = 4f,
                keyShadowOffsetY = 5f,
                keyShadowColor = 6
            )
        val originalDrawable: Drawable = mock()
        val iconSize = 2
        val iconInsetSize = 1
        // When
        val shadowDrawableUnderTest =
            spy(DoubleShadowIconDrawable(shadowInfo, originalDrawable, iconSize, iconInsetSize))
        shadowDrawableUnderTest.createShadowRenderNode()
        // Then
        verify(shadowDrawableUnderTest)
            .createShadowRenderEffect(
                shadowInfo.ambientShadowBlur,
                0f,
                0f,
                Color.alpha(shadowInfo.ambientShadowColor).toFloat()
            )
        verify(shadowDrawableUnderTest)
            .createShadowRenderEffect(
                shadowInfo.keyShadowBlur,
                shadowInfo.keyShadowOffsetX,
                shadowInfo.keyShadowOffsetY,
                Color.alpha(shadowInfo.keyShadowColor).toFloat()
            )
    }
}
