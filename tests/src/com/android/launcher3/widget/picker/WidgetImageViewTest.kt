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

package com.android.launcher3.widget.picker

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.widget.WidgetImageView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@MediumTest
@RunWith(AndroidJUnit4::class)
class WidgetImageViewTest {
    private lateinit var context: Context
    private lateinit var widgetImageView: WidgetImageView

    @Mock private lateinit var testDrawable: Drawable

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        context = ActivityContextWrapper(ApplicationProvider.getApplicationContext())
        widgetImageView = spy(WidgetImageView(context))
    }

    @Test
    fun getBitmapBounds_aspectRatioLargerThanView_scaledByWidth() {
        // view - 100 x 100
        whenever(widgetImageView.width).thenReturn(100)
        whenever(widgetImageView.height).thenReturn(100)
        // bitmap - 200 x 100
        whenever(testDrawable.intrinsicWidth).thenReturn(200)
        whenever(testDrawable.intrinsicHeight).thenReturn(100)

        widgetImageView.drawable = testDrawable
        val bitmapBounds = widgetImageView.bitmapBounds

        // new scaled width of bitmap is = 100, and height is scaled to 1/2 = 50
        assertThat(bitmapBounds).isEqualTo(Rect(0, 25, 100, 75))
    }

    @Test
    fun getBitmapBounds_aspectRatioSmallerThanView_scaledByHeight() {
        // view - 100 x 100
        whenever(widgetImageView.width).thenReturn(100)
        whenever(widgetImageView.height).thenReturn(100)
        // bitmap - 100 x 200
        whenever(testDrawable.intrinsicWidth).thenReturn(100)
        whenever(testDrawable.intrinsicHeight).thenReturn(200)
        widgetImageView.drawable = testDrawable

        val bitmapBounds = widgetImageView.bitmapBounds

        // new scaled height of bitmap is = 100, and width is scaled to 1/2 = 50
        assertThat(bitmapBounds).isEqualTo(Rect(25, 0, 75, 100))
    }

    @Test
    fun getBitmapBounds_noScale_returnsOriginalDrawableBounds() {
        // view - 200 x 100
        whenever(widgetImageView.width).thenReturn(200)
        whenever(widgetImageView.height).thenReturn(100)
        // bitmap - 200 x 100
        whenever(testDrawable.intrinsicWidth).thenReturn(200)
        whenever(testDrawable.intrinsicHeight).thenReturn(100)

        widgetImageView.drawable = testDrawable
        val bitmapBounds = widgetImageView.bitmapBounds

        // no scaling
        assertThat(bitmapBounds).isEqualTo(Rect(0, 0, 200, 100))
    }
}
