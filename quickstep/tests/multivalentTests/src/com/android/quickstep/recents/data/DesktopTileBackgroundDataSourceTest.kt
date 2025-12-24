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
import android.os.RemoteException
import android.view.IWallpaperVisibilityListener
import android.view.IWindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.di.DisplayId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class DesktopTileBackgroundDataSourceTest {

    @Mock private lateinit var mockWindowManager: IWindowManager

    private val testBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Captor
    private lateinit var wallpaperVisibilityListenerCaptor:
        ArgumentCaptor<IWallpaperVisibilityListener>

    private lateinit var dataSource: DesktopTileBackgroundDataSource
    private val displayId = DisplayId(0)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dataSource = DesktopTileBackgroundDataSource(mockWindowManager, displayId)
    }

    @Test
    fun getWallpaperBackgroundBitmap_windowManagerIsNull_returnsNull() = runTest {
        dataSource = DesktopTileBackgroundDataSource(null, displayId)

        val result = dataSource.getWallpaperBackgroundBitmap(false)

        assertThat(result).isNull()
    }

    @Test
    fun getWallpaperBackgroundBitmap_cacheAvailable_returnsCache() = runTest {
        `when`(
                mockWindowManager.registerWallpaperVisibilityListener(
                    wallpaperVisibilityListenerCaptor.capture(),
                    eq(displayId.displayId),
                )
            )
            .thenReturn(true)
        `when`(mockWindowManager.screenshotWallpaper()).thenReturn(testBitmap)

        dataSource.getWallpaperBackgroundBitmap(false)

        val result = dataSource.getWallpaperBackgroundBitmap(false)
        assertThat(testBitmap).isEqualTo(result)
        verify(mockWindowManager, times(1)).screenshotWallpaper()
    }

    @Test
    fun getWallpaperBackgroundBitmap_forceRefreshRequested_capturesScreenshot() = runTest {
        val recapturedBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        `when`(
                mockWindowManager.registerWallpaperVisibilityListener(
                    wallpaperVisibilityListenerCaptor.capture(),
                    eq(displayId.displayId),
                )
            )
            .thenReturn(true)
        `when`(mockWindowManager.screenshotWallpaper()).thenReturn(testBitmap)
        dataSource.getWallpaperBackgroundBitmap(false)
        `when`(mockWindowManager.screenshotWallpaper()).thenReturn(recapturedBitmap)

        val result = dataSource.getWallpaperBackgroundBitmap(true)

        assertThat(recapturedBitmap).isEqualTo(result)
        verify(mockWindowManager, times(2)).screenshotWallpaper()
    }

    @Test
    fun getWallpaperBackgroundBitmap_wallpaperInitiallyVisible_capturesScreenshot() = runTest {
        `when`(
                mockWindowManager.registerWallpaperVisibilityListener(
                    wallpaperVisibilityListenerCaptor.capture(),
                    eq(displayId.displayId),
                )
            )
            .thenReturn(true)
        `when`(mockWindowManager.screenshotWallpaper()).thenReturn(testBitmap)

        val result = dataSource.getWallpaperBackgroundBitmap(false)

        assertThat(testBitmap).isEqualTo(result)
        verify(mockWindowManager)
            .registerWallpaperVisibilityListener(
                wallpaperVisibilityListenerCaptor.value,
                displayId.displayId,
            )
        verify(mockWindowManager).screenshotWallpaper()
    }

    @Test
    fun getWallpaperBackgroundBitmap_remoteExceptionDuringRegistration_returnsNull() = runTest {
        `when`(
                mockWindowManager.registerWallpaperVisibilityListener(
                    wallpaperVisibilityListenerCaptor.capture(),
                    eq(displayId.displayId),
                )
            )
            .thenThrow(RemoteException("Test Exception"))

        val result = dataSource.getWallpaperBackgroundBitmap(false)
        assertThat(result).isNull()
    }
}
