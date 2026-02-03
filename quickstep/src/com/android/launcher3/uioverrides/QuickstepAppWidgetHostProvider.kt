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

package com.android.launcher3.uioverrides

import android.app.ActivityThread
import android.content.Context
import android.content.ContextWrapper
import com.android.launcher3.BuildConfigs
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.ListenableAppWidgetHost

object QuickstepAppWidgetHostProvider {

    /** Static widget host which is always listening and is lazily created */
    @JvmStatic
    val staticQuickstepHost: ListenableAppWidgetHost by lazy {
        ListenableAppWidgetHost(
                LooperContext(
                    ActivityThread.currentApplication(),
                    ListenableAppWidgetHost.widgetHolderExecutor,
                ),
                LauncherWidgetHolder.APPWIDGET_HOST_ID,
            )
            .apply { if (BuildConfigs.WIDGETS_ENABLED) startListening() }
    }

    private class LooperContext(ctx: Context, val executor: LooperExecutor) : ContextWrapper(ctx) {

        override fun getMainLooper() = executor.looper

        override fun getMainExecutor() = executor
    }
}
