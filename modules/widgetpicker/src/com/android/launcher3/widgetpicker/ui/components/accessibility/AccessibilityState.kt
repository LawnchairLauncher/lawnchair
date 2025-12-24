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

package com.android.launcher3.widgetpicker.ui.components.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.core.content.getSystemService

/** Provides the [AccessibilityState] as a composition local for use in UI components. */
val LocalAccessibilityState = compositionLocalOf { AccessibilityState(isEnabled = false) }

/**
 * State about accessibility services.
 *
 * @property isEnabled indicates if any of the accessibility services are enabled.
 */
data class AccessibilityState(val isEnabled: Boolean)

/**
 * Backing state for [LocalAccessibilityState] as looked up from [AccessibilityManager].
 *
 * Use `LocalAccessibilityState.current` when you want to read the state.
 */
@Composable
fun produceAccessibilityState(context: Context): State<AccessibilityState> =
    produceState(initialValue = AccessibilityState(isEnabled = false)) {
        val accessibilityManager = context.getSystemService<AccessibilityManager>()
        value = value.copy(isEnabled = accessibilityManager?.isEnabled ?: false)

        val listener =
            AccessibilityManager.AccessibilityStateChangeListener { isEnabled ->
                value = value.copy(isEnabled = isEnabled)
            }

        accessibilityManager?.addAccessibilityStateChangeListener(listener)
        awaitDispose { accessibilityManager?.removeAccessibilityStateChangeListener(listener) }
    }
