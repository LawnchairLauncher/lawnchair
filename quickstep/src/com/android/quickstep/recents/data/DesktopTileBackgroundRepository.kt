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
import com.android.internal.R

class DesktopTileBackgroundRepository(
    private val desktopTileBackgroundDataSource: DesktopTileBackgroundDataSource
) {
    suspend fun getWallpaperBackground(forceRefresh: Boolean = false): DesktopBackgroundResult {
        val wallpaperBackground =
            desktopTileBackgroundDataSource.getWallpaperBackgroundBitmap(forceRefresh)
        return if (wallpaperBackground != null) {
            DesktopBackgroundResult.WallpaperBackground(wallpaperBackground)
        } else {
            DesktopBackgroundResult.FallbackBackground(DESKTOP_BACKGROUND_FALLBACK_COLOR)
        }
    }

    companion object {
        const val DESKTOP_BACKGROUND_FALLBACK_COLOR = R.color.system_neutral2_300
    }
}

sealed class DesktopBackgroundResult {
    data class WallpaperBackground(val background: Bitmap) : DesktopBackgroundResult()

    data class FallbackBackground(val background: Int) : DesktopBackgroundResult()
}
