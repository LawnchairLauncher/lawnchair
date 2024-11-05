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
package com.android.launcher3.icons

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.icons.UserBadgeDrawable.SHADOW_COLOR
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [UserBadgeDrawable] */
@RunWith(AndroidJUnit4::class)
class UserBadgeDrawableTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val canvas = mock<Canvas>()
    private val systemUnderTest =
        UserBadgeDrawable(context, R.drawable.ic_work_app_badge, R.color.badge_tint_work, false)

    @Test
    fun draw_opaque() {
        val colorList = mutableListOf<Int>()
        whenever(
            canvas.drawCircle(
                any(),
                any(),
                any(),
                any()
            )
        ).then { colorList.add(it.getArgument<Paint>(3).color) }

        systemUnderTest.alpha = 255
        systemUnderTest.draw(canvas)

        assertThat(colorList).containsExactly(SHADOW_COLOR, Color.WHITE)
    }

    @Test
    fun draw_transparent() {
        val colorList = mutableListOf<Int>()
        whenever(
            canvas.drawCircle(
                any(),
                any(),
                any(),
                any()
            )
        ).then { colorList.add(it.getArgument<Paint>(3).color) }

        systemUnderTest.alpha = 0
        systemUnderTest.draw(canvas)

        assertThat(colorList).hasSize(2)
        assertThat(Color.valueOf(colorList[0]).alpha()).isEqualTo(0)
        assertThat(Color.valueOf(colorList[1]).alpha()).isEqualTo(0)
    }
}
