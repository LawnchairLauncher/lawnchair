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

package com.android.launcher3.widgetpicker.ui.windowsizeclass

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.WindowMetricsCalculator

/**
 * Provides window size for the current configuration.
 *
 * Unlike "calculateWindowSizeClass" provided by windowSizeClass material library, this works
 * independent of whether you are running with activity (real app) or not (android studio preview).
 * And the "currentWindowAdaptiveInfo" provided by material adaptive library seems deprecated.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
// Suppress: Configuration is used as receiver to indicate recomposition on config changes.
@Suppress("UnusedReceiverParameter")
fun Configuration.calculateWindowInfo(): WindowInfo {
    val density = LocalDensity.current
    val context = LocalContext.current
    val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(context)
    val size = with(density) { metrics.bounds.toComposeRect().size.toDpSize() }
    return WindowInfo(WindowSizeClass.calculateFromSize(size), size)
}

/** Information about the window's size. */
data class WindowInfo(val windowSizeClass: WindowSizeClass, val size: DpSize)

/** Returns true if the window is extra tall e.g. portrait on a tablet. */
fun WindowInfo.isExtraTall() = size.height > 1200.dp
