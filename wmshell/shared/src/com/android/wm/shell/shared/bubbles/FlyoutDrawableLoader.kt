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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.Log

object FlyoutDrawableLoader {

    private const val TAG = "FlyoutDrawableLoader"

    /** Loads the flyout icon as a [Drawable]. */
    @JvmStatic
    fun Icon?.loadFlyoutDrawable(context: Context): Drawable? {
        if (this == null) return null
        try {
            if (this.type == Icon.TYPE_URI || this.type == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                context.grantUriPermission(
                    context.packageName,
                    this.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            return loadDrawable(context)
        } catch (e: Exception) {
            Log.w(TAG, "loadFlyoutDrawable failed: ${e.message}")
            return null
        }
    }
}
