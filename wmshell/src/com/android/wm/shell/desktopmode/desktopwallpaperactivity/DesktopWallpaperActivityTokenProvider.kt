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

package com.android.wm.shell.desktopmode.desktopwallpaperactivity

import android.util.SparseArray
import android.view.Display.DEFAULT_DISPLAY
import android.window.WindowContainerToken
import androidx.core.util.keyIterator
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** Provides per display window container tokens for [DesktopWallpaperActivity]. */
class DesktopWallpaperActivityTokenProvider {

    private val wallpaperActivityTokenByDisplayId = SparseArray<WindowContainerToken>()

    fun setToken(token: WindowContainerToken, displayId: Int = DEFAULT_DISPLAY) {
        logV("Setting desktop wallpaper activity token for display %s", displayId)
        wallpaperActivityTokenByDisplayId[displayId] = token
    }

    fun getToken(displayId: Int = DEFAULT_DISPLAY): WindowContainerToken? {
        return wallpaperActivityTokenByDisplayId[displayId]
    }

    fun removeToken(displayId: Int = DEFAULT_DISPLAY) {
        logV("Remove desktop wallpaper activity token for display %s", displayId)
        wallpaperActivityTokenByDisplayId.delete(displayId)
    }

    fun removeToken(token: WindowContainerToken) {
        val displayId =
            wallpaperActivityTokenByDisplayId.keyIterator().asSequence().find {
                wallpaperActivityTokenByDisplayId[it] == token
            }
        if (displayId != null) {
            logV("Remove desktop wallpaper activity token for display %s", displayId)
            wallpaperActivityTokenByDisplayId.delete(displayId)
        }
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopWallpaperActivityTokenProvider"
    }
}
