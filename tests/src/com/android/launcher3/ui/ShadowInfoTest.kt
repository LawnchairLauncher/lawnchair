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

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.views.ShadowInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadowInfoTest {

    @Test
    fun `ShadowInfo is created correctly from context`() {
        // Given
        val mockContext: Context = mock()
        val mockAttrs: AttributeSet = mock()
        val styledAttrs: TypedArray = mock()
        val expectedShadowInfo =
            ShadowInfo(
                ambientShadowBlur = 1f,
                ambientShadowColor = 2,
                keyShadowBlur = 3f,
                keyShadowOffsetX = 4f,
                keyShadowOffsetY = 5f,
                keyShadowColor = 6
            )
        doReturn(styledAttrs)
            .whenever(mockContext)
            .obtainStyledAttributes(mockAttrs, R.styleable.ShadowInfo, 0, 0)
        doReturn(1)
            .whenever(styledAttrs)
            .getDimensionPixelSize(R.styleable.ShadowInfo_ambientShadowBlur, 0)
        doReturn(2).whenever(styledAttrs).getColor(R.styleable.ShadowInfo_ambientShadowColor, 0)
        doReturn(3)
            .whenever(styledAttrs)
            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowBlur, 0)
        doReturn(4)
            .whenever(styledAttrs)
            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowOffsetX, 0)
        doReturn(5)
            .whenever(styledAttrs)
            .getDimensionPixelSize(R.styleable.ShadowInfo_keyShadowOffsetY, 0)
        doReturn(6).whenever(styledAttrs).getColor(R.styleable.ShadowInfo_keyShadowColor, 0)
        // When
        val actualShadowInfo = ShadowInfo.fromContext(mockContext, mockAttrs, 0)
        // Then
        assertThat(actualShadowInfo.ambientShadowBlur).isEqualTo(1)
        assertThat(actualShadowInfo.ambientShadowColor).isEqualTo(2)
        assertThat(actualShadowInfo.keyShadowBlur).isEqualTo(3)
        assertThat(actualShadowInfo.keyShadowOffsetX).isEqualTo(4)
        assertThat(actualShadowInfo.keyShadowOffsetY).isEqualTo(5)
        assertThat(actualShadowInfo.keyShadowColor).isEqualTo(6)
        assertThat(actualShadowInfo).isEqualTo(expectedShadowInfo)
    }
}
