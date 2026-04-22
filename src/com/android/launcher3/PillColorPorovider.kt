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

package com.android.launcher3

import android.content.Context
import android.database.ContentObserver
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR

class PillColorProvider private constructor(c: Context) {
    private val context = c.applicationContext

    private val matchaUri by lazy { Settings.Secure.getUriFor(MATCHA_SETTING) }
    var appTitlePillPaint = Paint()
        private set

    var appTitleTextPaint = Paint()
        private set

    private var isMatchaEnabledInternal = 0

    var isMatchaEnabled = isMatchaEnabledInternal != 0

    private val pillColorObserver =
        object : ContentObserver(ORDERED_BG_EXECUTOR.handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri == matchaUri) {
                    isMatchaEnabledInternal =
                        Settings.Secure.getInt(context.contentResolver, MATCHA_SETTING, 0)
                    isMatchaEnabled = isMatchaEnabledInternal != 0
                }
            }
        }

    fun registerObserver() {
        context.contentResolver.registerContentObserver(matchaUri, false, pillColorObserver)
        setup()
    }

    fun unregisterObserver() {
        context.contentResolver.unregisterContentObserver(pillColorObserver)
    }

    fun setup() {
        appTitlePillPaint.color = context.getColor(R.color.materialColorSurfaceContainer)
        appTitleTextPaint.color = context.getColor(R.color.materialColorOnSurface)
        isMatchaEnabledInternal = Settings.Secure.getInt(context.contentResolver, MATCHA_SETTING, 0)
        isMatchaEnabled = isMatchaEnabledInternal != 0
    }

    companion object {
        private var INSTANCE: PillColorProvider? = null
        private const val MATCHA_SETTING = "matcha_enable"

        // TODO: Replace with a Dagger injection that is a singleton.
        @JvmStatic
        fun getInstance(context: Context): PillColorProvider {
            if (INSTANCE == null) {
                INSTANCE = PillColorProvider(context)
            }
            return INSTANCE!!
        }
    }
}
