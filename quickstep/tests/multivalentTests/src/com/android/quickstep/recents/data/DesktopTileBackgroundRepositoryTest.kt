/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.recents.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.DesktopTileBackgroundRepository.Companion.DESKTOP_BACKGROUND_FALLBACK_COLOR
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DesktopTileBackgroundRepositoryTest {
    private val desktopTileBackgroundDataSource = mock<DesktopTileBackgroundDataSource>()
    private val desktopTileBackgroundRepository =
        DesktopTileBackgroundRepository(desktopTileBackgroundDataSource)

    @Test
    fun getBackground_dataSourceReturnsScreenshot_returnsBitmapBackground() = runTest {
        val testBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        whenever(desktopTileBackgroundDataSource.getWallpaperBackgroundBitmap())
            .thenReturn(testBitmap)

        val expectedResult = desktopTileBackgroundRepository.getWallpaperBackground()

        assertThat(expectedResult)
            .isInstanceOf(DesktopBackgroundResult.WallpaperBackground::class.java)
        assertThat(testBitmap)
            .isEqualTo((expectedResult as DesktopBackgroundResult.WallpaperBackground).background)
    }

    @Test
    fun getBackground_dataSourceReturnsNull_returnsColorBackground() = runTest {
        whenever(desktopTileBackgroundDataSource.getWallpaperBackgroundBitmap()).thenReturn(null)

        val expectedResult = desktopTileBackgroundRepository.getWallpaperBackground()

        assertThat(expectedResult)
            .isInstanceOf(DesktopBackgroundResult.FallbackBackground::class.java)
        assertThat(DESKTOP_BACKGROUND_FALLBACK_COLOR)
            .isEqualTo((expectedResult as DesktopBackgroundResult.FallbackBackground).background)
    }
}
