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

package com.android.launcher3.widgetpicker.listeners

import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.pm.ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVO
import com.android.launcher3.util.ContextTracker.SchedulerCallback
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo

/**
 * A callback listener (for tap-to-add flow) that handles adding a widget from a separate widget
 * picker activity. Invoked once widget picker is closed and home screen is showing / ready.
 *
 * Also logs to stats logger once widget is added.
 */
class WidgetPickerAddItemListener(private val widgetInfo: WidgetInfo) :
    SchedulerCallback<Launcher> {
    override fun init(launcher: Launcher?, isHomeStarted: Boolean): Boolean {
        checkNotNull(launcher)

        val pendingAddItemInfo: PendingAddItemInfo =
            when (widgetInfo) {
                is WidgetInfo.AppWidgetInfo -> {
                    val launcherProviderInfo =
                        LauncherAppWidgetProviderInfo.fromProviderInfo(
                            launcher,
                            widgetInfo.appWidgetProviderInfo,
                        )
                    PendingAddWidgetInfo(launcherProviderInfo, CONTAINER_WIDGETS_TRAY)
                }

                is WidgetInfo.ShortcutInfo ->
                    PendingAddShortcutInfo(
                        ShortcutConfigActivityInfoVO(widgetInfo.launcherActivityInfo)
                    )
            }

        val view = View(launcher)
        view.tag = pendingAddItemInfo

        launcher.accessibilityDelegate?.addToWorkspace(
            /*item=*/ pendingAddItemInfo,
            /*accessibility=*/ false,
        ) {
            launcher.statsLogManager
                .logger()
                .withItemInfo(pendingAddItemInfo)
                .log(LauncherEvent.LAUNCHER_WIDGET_ADD_BUTTON_TAP)
        }
        return false // don't receive any more callbacks as we got launcher and handled it
    }
}
