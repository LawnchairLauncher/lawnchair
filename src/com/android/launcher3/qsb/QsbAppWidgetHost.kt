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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.widget.RemoteViews
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.qsb.OSEManager.Companion.OSE_LOOPER
import javax.inject.Inject

/** AppWidgetHost used for QSB */
class QsbAppWidgetHost @Inject constructor(@ApplicationContext private val ctx: Context) :
    AppWidgetHost(WrappedContext(ctx), HOST_ID) {

    private var callbacks: Callbacks? = null
    private var activeWidgetId = INVALID_APPWIDGET_ID

    fun setCallbacks(c: Callbacks) {
        callbacks = c
    }

    /** Starts listening for any updates for the provided widget id */
    fun setActiveWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        if (activeWidgetId == appWidgetId) return
        if (activeWidgetId != INVALID_APPWIDGET_ID) deleteAppWidgetId(activeWidgetId)

        activeWidgetId = appWidgetId
        if (appWidgetId != INVALID_APPWIDGET_ID) {
            createView(ctx, appWidgetId, info)
        }
    }

    fun getActiveWidgetId() = activeWidgetId

    /**
     * Returns the currently bound widget id to this host or [INVALID_APPWIDGET_ID] if none are
     * bound. In multiple widgets are bounds, it deletes all except the last one.
     */
    fun getBoundWidgetId(): Int {
        val currentWidgets = appWidgetIds
        if (currentWidgets.isNotEmpty()) {
            // Delete all widgets except the last
            for (i in 0..(currentWidgets.size - 2)) deleteAppWidgetId(currentWidgets[i])
            return currentWidgets.last()
        } else {
            return INVALID_APPWIDGET_ID
        }
    }

    override fun onCreateView(
        context: Context?,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = DelegateHostView(appWidgetId)

    private inner class DelegateHostView(val widgetId: Int) : AppWidgetHostView(ctx) {

        override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
            if (activeWidgetId == widgetId) callbacks?.onProviderChanged(info)
        }

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            if (activeWidgetId == widgetId) callbacks?.onViewsChanged(remoteViews)
        }
    }

    interface Callbacks {

        fun onProviderChanged(appWidget: AppWidgetProviderInfo?)

        fun onViewsChanged(views: RemoteViews?)
    }

    private class WrappedContext(ctx: Context) : ContextWrapper(ctx) {

        override fun getMainLooper() = OSE_LOOPER.looper
    }

    companion object {
        // Any fixed integer as long as it doesn't conflict with other widget hosts
        const val HOST_ID = 1025
    }
}
