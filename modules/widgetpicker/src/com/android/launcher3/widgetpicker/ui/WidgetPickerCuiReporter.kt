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

package com.android.launcher3.widgetpicker.ui

import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reports CUIs (critical user interactions) for widget picker's ui for instrumentation purposes.
 * e.g. an event reported when user clicks a button that begins an animation and then another event
 * when animation ends.
 *
 * NOTE: This is not meant to be used as callback to perform UI action / business logic on ui
 * interactions. Use [WidgetPickerEventListeners] instead and pass them down as callbacks (inputs
 * arguments) to low level ui components.
 */
interface WidgetPickerCuiReporter {
    fun report(event: WidgetPickerCui, view: View)
}

/** A default no-op [WidgetPickerCuiReporter]. */
class NoOpWidgetPickerCuiReporter : WidgetPickerCuiReporter {
    override fun report(event: WidgetPickerCui, view: View) {}
}

/**
 * A composition local available for widget picker's UI code to report CUIs for instrumentation.
 * e.g.
 *
 * ```
 * val localView = LocalView.current
 * val reporter = LocalWidgetPickerUiEventReporter.current
 * ...
 *
 * reporter.report(WidgetPickerUiEvent.MY_ANIMATION_BEGIN, LocalView.current)
 * ```
 */
val LocalWidgetPickerCuiReporter =
    staticCompositionLocalOf<WidgetPickerCuiReporter> { NoOpWidgetPickerCuiReporter() }

/** Enum holding types of CUIs (critical user interactions) in widget picker. */
enum class WidgetPickerCui {
    /**
     * Indicates that widget picker started animating from bottom (to open). Starting from this
     * event until [OPEN_ANIMATION_END] user should see a smooth open animation and expensive UI
     * work shouldn't happen during this time.
     */
    OPEN_ANIMATION_BEGIN,

    /**
     * Indicates that widget picker open animation ended (either because it fully opened or user
     * closed it.
     */
    OPEN_ANIMATION_END,

    /**
     * Indicates that user tapped on widget app in the single pane picker to expand it and see its
     * widgets. Starting from this event until [WIDGET_APP_EXPAND_END], the widget app header is
     * animating to expand its content.
     */
    WIDGET_APP_EXPAND_BEGIN,

    /**
     * Indicates that the expand animation for a specific widget app that user tapped on is fully
     * opened.
     */
    WIDGET_APP_EXPAND_END,
}
