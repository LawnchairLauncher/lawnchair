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
import android.util.Log
import android.view.IWallpaperVisibilityListener
import android.view.IWindowManager
import com.android.quickstep.recents.di.DisplayId
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class DesktopTileBackgroundDataSource(
    private val windowManager: IWindowManager?,
    private val displayId: DisplayId,
) {

    private var screenshotCache: Bitmap? = null

    suspend fun getWallpaperBackgroundBitmap(forceRefresh: Boolean = false): Bitmap? {
        windowManager ?: return null

        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested, clearing cache.")
            screenshotCache = null
        }

        if (isCacheValid()) {
            Log.d(TAG, "Returning cached screenshot: $screenshotCache")
            return screenshotCache
        } else {
            Log.d(TAG, "cache invalid")
            screenshotCache = null
        }

        return suspendCancellableCoroutine { continuation ->
            val wallpaperVisibilityListener =
                object : IWallpaperVisibilityListener.Stub() {
                    override fun onWallpaperVisibilityChanged(visible: Boolean, newDisplayId: Int) {
                        try {
                            if (visible) {
                                screenshotCache = windowManager.screenshotWallpaper()
                                continuation.resume(screenshotCache)
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "RemoteException in onWallpaperVisibilityChanged", e)
                            screenshotCache = null
                            continuation.resume(null)
                        }
                    }
                }

            try {
                if (
                    windowManager.registerWallpaperVisibilityListener(
                        wallpaperVisibilityListener,
                        displayId.displayId,
                    )
                ) {
                    Log.d(TAG, "Wallpaper initially visible. Taking screenshot.")
                    screenshotCache = windowManager.screenshotWallpaper()
                    continuation.resume(screenshotCache)
                }
            } catch (e: RemoteException) {
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                Log.d(TAG, "unRegister: ")
                windowManager.unregisterWallpaperVisibilityListener(
                    wallpaperVisibilityListener,
                    displayId.displayId,
                )
                screenshotCache = null
            }
        }
    }

    private fun isCacheValid() =
        screenshotCache?.let { !it.isRecycled && it.width > 0 && it.height > 0 } ?: false

    private companion object {
        const val TAG = "DesktopTileBackgroundDataSource"
    }
}
