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

@file:JvmName("NavigationBarColorVerifier")

package com.android.wm.shell.flicker.bubbles.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Insets
import android.hardware.display.DisplayManager
import android.tools.helpers.WindowUtils
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileSystems

private const val TAG = "NavigationBarColorVerifier"

private val DUMP_PATH = FileSystems
    .getDefault()
    .getPath("/data/user/0/com.android.wm.shell.flicker.bubbles/cache")

private const val MIN_DOMINANT_COLOR_COVERAGE_RATIO = 0.3

/**
 * This assertion verifies if the IME can actually update the nav bar color.
 */
fun assertImeCanChangeNavBarColor(bitmap: Bitmap) {
    var success = false
    val navBarBitmapAtEnd = getNavBarBitmap(bitmap)
    try {
        val pixelsAtEnd = IntArray(navBarBitmapAtEnd.width * navBarBitmapAtEnd.height)
        navBarBitmapAtEnd.getPixels(
            pixelsAtEnd,
            0,
            navBarBitmapAtEnd.width,
            0,
            0,
            navBarBitmapAtEnd.width,
            navBarBitmapAtEnd.height
        )

        val maxColorCount = pixelsAtEnd.toList()
            .groupingBy { it }
            .eachCount()
            .values.maxOrNull() ?: 0
        assertWithMessage("IME must change the nav bar color.")
            .that(maxColorCount).isAtLeast(
                (MIN_DOMINANT_COLOR_COVERAGE_RATIO * pixelsAtEnd.size).toInt()
            )

        success = true
    } finally {
        if (!success) {
            dumpBitmap(navBarBitmapAtEnd, "navBarBitmap")
        }
    }
}

private fun getNavBarBitmap(bitmap: Bitmap): Bitmap {
    val displayBounds = WindowUtils.displayBounds
    val navBarInsets = getNavBarInset()
    return Bitmap.createBitmap(
        bitmap,
        displayBounds.left,
        displayBounds.bottom - navBarInsets.bottom,
        displayBounds.width(),
        navBarInsets.bottom
    )
}

private fun getNavBarInset(): Insets {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val defaultDisplay = context.getSystemService(DisplayManager::class.java)
        .getDisplay(DEFAULT_DISPLAY)
    val windowContext = context.createWindowContext(
        defaultDisplay,
        TYPE_APPLICATION,
        null /* options */
    )
    val windowInsets = windowContext
        .getSystemService(WindowManager::class.java)
        .currentWindowMetrics
        .windowInsets

    return windowInsets.getInsets(WindowInsets.Type.navigationBars())
}

private fun dumpBitmap(bitmap: Bitmap, name: String) {
    val dumpDir = DUMP_PATH.toFile()
    if (!dumpDir.exists()) {
        dumpDir.mkdirs()
    }

    val filePath = DUMP_PATH.resolve("$name.png")
    Log.e(TAG, "Dumping failed bitmap to $filePath")

    try {
        FileOutputStream(filePath.toFile()).use { stream ->
            // The quality is ignored for PNG format bitmap.
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
    } catch (e: IOException) {
        Log.e(TAG, "Failed to close FileOutputStream", e)
    } catch (e: Exception) {
        Log.e(TAG, "Dumping bitmap failed.", e)
    }
}
