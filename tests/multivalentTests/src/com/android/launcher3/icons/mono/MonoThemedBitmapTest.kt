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

package com.android.launcher3.icons.mono

import android.graphics.Bitmap
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.mono.MonoIconThemeControllerTest.Companion.ensureBitmapSerializationSupported
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MonoThemedBitmapTest {

    @Test
    fun `newDrawable returns valid drawable`() {
        val bitmap =
            MonoThemedBitmap(
                Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8),
                Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            )
        val d = bitmap.newDrawable(BitmapInfo.LOW_RES_INFO, context)
        assertTrue(d is ThemedIconDrawable)
    }

    @Test
    fun `serialize returns valid bytes`() {
        ensureBitmapSerializationSupported()
        val bitmap =
            MonoThemedBitmap(
                Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8),
                Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            )
        assertTrue(bitmap.serialize().isNotEmpty())
    }
}
