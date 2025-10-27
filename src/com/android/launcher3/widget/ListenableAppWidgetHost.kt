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

package com.android.launcher3.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LooperExecutor

open class ListenableAppWidgetHost(private val ctx: Context, hostId: Int) :
    AppWidgetHost(ctx, hostId) {

    protected val holders = mutableListOf<LauncherWidgetHolder>()

    override fun onProvidersChanged() {
        MAIN_EXECUTOR.execute {
            holders.forEach { holder ->
                // Listeners might remove themselves from the list during the iteration.
                // Creating a copy of the list to avoid exceptions for concurrent modification.
                holder.mProviderChangedListeners.toList().forEach {
                    it.notifyWidgetProvidersChanged()
                }
            }
        }
    }

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        // Route the call via model thread, in case it comes while a loader-bind is in progress
        MODEL_EXECUTOR.execute {
            MAIN_EXECUTOR.execute {
                holders.forEach { it.mAppWidgetRemovedCallback?.accept(appWidgetId) }
            }
        }
    }

    override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo) {
        val info = LauncherAppWidgetProviderInfo.fromProviderInfo(ctx, appWidget)
        super.onProviderChanged(appWidgetId, info)
        // The super method updates the dimensions of the providerInfo. Update the
        // launcher spans accordingly.
        info.initSpans(ctx, InvariantDeviceProfile.INSTANCE.get(ctx))
    }

    /** Listener for getting notifications on provider changes. */
    fun interface ProviderChangedListener {
        /** Notify the listener that the providers have changed */
        fun notifyWidgetProvidersChanged()
    }

    companion object {

        @JvmStatic val widgetHolderExecutor: LooperExecutor = Executors.UI_HELPER_EXECUTOR
    }
}
