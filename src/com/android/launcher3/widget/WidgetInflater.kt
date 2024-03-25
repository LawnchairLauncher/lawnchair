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

package com.android.launcher3.widget

import android.content.Context
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.qsb.QsbContainerView

/** Utility class for handling widget inflation taking into account all the restore state updates */
class WidgetInflater(private val context: Context) {

    private val widgetHelper = WidgetManagerHelper(context)

    fun inflateAppWidget(
        item: LauncherAppWidgetInfo,
    ): InflationResult {
        if (item.hasOptionFlag(LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET)) {
            item.providerName = QsbContainerView.getSearchComponentName(context)
            if (item.providerName == null) {
                return InflationResult(
                    TYPE_DELETE,
                    reason = "search widget removed because search component cannot be found",
                    restoreErrorType = RestoreError.NO_SEARCH_WIDGET
                )
            }
        }
        if (LauncherAppState.INSTANCE.get(context).isSafeModeEnabled) {
            return InflationResult(TYPE_PENDING)
        }
        val appWidgetInfo: LauncherAppWidgetProviderInfo?
        var removalReason = ""
        @RestoreError var logReason = RestoreError.APP_NOT_INSTALLED
        var update = false

        if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
            // The widget id is not valid. Try to find the widget based on the provider info.
            appWidgetInfo = widgetHelper.findProvider(item.providerName, item.user)
            if (appWidgetInfo == null) {
                if (!BuildConfig.WIDGETS_ENABLED) {
                    removalReason = "widgets are disabled on go device."
                    logReason = RestoreError.WIDGETS_DISABLED
                } else {
                    removalReason = "WidgetManagerHelper cannot find a provider from provider info."
                    logReason = RestoreError.MISSING_WIDGET_PROVIDER
                }
            } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                // since appWidgetInfo is not null anymore, update the provider status
                item.restoreStatus =
                    item.restoreStatus and LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY.inv()
                update = true
            }
        } else {
            appWidgetInfo =
                widgetHelper.getLauncherAppWidgetInfo(item.appWidgetId, item.targetComponent)
            if (appWidgetInfo == null) {
                if (item.appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID) {
                    removalReason = "CustomWidgetManager cannot find provider from that widget id."
                    logReason = RestoreError.MISSING_INFO
                } else {
                    removalReason =
                        ("AppWidgetManager cannot find provider for that widget id." +
                            " It could be because AppWidgetService is not available, or the" +
                            " appWidgetId has not been bound to a the provider yet, or you" +
                            " don't have access to that appWidgetId.")
                    logReason = RestoreError.INVALID_WIDGET_ID
                }
            }
        }

        // If the provider is ready, but the widget is not yet restored, try to restore it.
        if (
            !item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) &&
                item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED
        ) {
            if (appWidgetInfo == null) {
                return InflationResult(
                    type = TYPE_DELETE,
                    reason =
                        "Removing restored widget: id=${item.appWidgetId} belongs to component ${item.providerName} user ${item.user}, as the provider is null and $removalReason",
                    restoreErrorType = logReason
                )
            }

            // If we do not have a valid id, try to bind an id.
            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                    // Id has not been allocated yet. Allocate a new id.
                    LauncherWidgetHolder.newInstance(context).let {
                        item.appWidgetId = it.allocateAppWidgetId()
                        it.destroy()
                    }
                    item.restoreStatus =
                        item.restoreStatus or LauncherAppWidgetInfo.FLAG_ID_ALLOCATED

                    // Also try to bind the widget. If the bind fails, the user will be shown
                    // a click to setup UI, which will ask for the bind permission.
                    val pendingInfo = PendingAddWidgetInfo(appWidgetInfo, item.sourceContainer)
                    pendingInfo.spanX = item.spanX
                    pendingInfo.spanY = item.spanY
                    pendingInfo.minSpanX = item.minSpanX
                    pendingInfo.minSpanY = item.minSpanY
                    var options = pendingInfo.getDefaultSizeOptions(context)
                    val isDirectConfig =
                        item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)
                    if (isDirectConfig && item.bindOptions != null) {
                        val newOptions = item.bindOptions.extras
                        if (options != null) {
                            newOptions!!.putAll(options)
                        }
                        options = newOptions
                    }
                    val success =
                        widgetHelper.bindAppWidgetIdIfAllowed(
                            item.appWidgetId,
                            appWidgetInfo,
                            options
                        )

                    // We tried to bind once. If we were not able to bind, we would need to
                    // go through the permission dialog, which means we cannot skip the config
                    // activity.
                    item.bindOptions = null
                    item.restoreStatus =
                        item.restoreStatus and LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG.inv()

                    // Bind succeeded
                    if (success) {
                        // If the widget has a configure activity, it is still needs to set it
                        // up, otherwise the widget is ready to go.
                        item.restoreStatus =
                            if ((appWidgetInfo.configure == null) || isDirectConfig)
                                LauncherAppWidgetInfo.RESTORE_COMPLETED
                            else LauncherAppWidgetInfo.FLAG_UI_NOT_READY
                    }
                    update = true
                }
            } else if (
                (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY) &&
                    (appWidgetInfo.configure == null))
            ) {
                // The widget was marked as UI not ready, but there is no configure activity to
                // update the UI.
                item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                update = true
            } else if (
                (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY) &&
                    appWidgetInfo.configure != null)
            ) {
                if (widgetHelper.isAppWidgetRestored(item.appWidgetId)) {
                    item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                    update = true
                }
            }
        }

        if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            // Verify that we own the widget
            if (appWidgetInfo == null) {
                FileLog.e(Launcher.TAG, "Removing invalid widget: id=" + item.appWidgetId)
                return InflationResult(TYPE_DELETE, reason = removalReason)
            }
            item.minSpanX = appWidgetInfo.minSpanX
            item.minSpanY = appWidgetInfo.minSpanY
            return InflationResult(TYPE_REAL, isUpdate = update, widgetInfo = appWidgetInfo)
        } else {
            return InflationResult(TYPE_PENDING, isUpdate = update, widgetInfo = appWidgetInfo)
        }
    }

    data class InflationResult(
        val type: Int,
        val reason: String? = null,
        @RestoreError val restoreErrorType: String = RestoreError.APP_NOT_INSTALLED,
        val isUpdate: Boolean = false,
        val widgetInfo: LauncherAppWidgetProviderInfo? = null
    )

    companion object {
        const val TYPE_DELETE = 0

        const val TYPE_PENDING = 1

        const val TYPE_REAL = 2
    }
}
