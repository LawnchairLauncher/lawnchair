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

package com.android.launcher3.widgetpicker.datasource

import android.content.Context
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import java.util.Arrays
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * An implementation of [FeaturedWidgetsDataSource] that provides featured widgets based on a static
 * configuration from resources and pre-defined size templates.
 *
 * Only appwidgets; no shortcuts
 */
@LauncherAppSingleton
class ConfigResourceFeaturedWidgetsDataSource
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val idp: InvariantDeviceProfile,
) : FeaturedWidgetsDataSource {
    // the package part in component name e.g. "com.example" in {com.example/widget.Provider}
    private var eligiblePackages: Set<String> = emptySet()

    override suspend fun initialize() {
        if (eligiblePackages.isEmpty()) {
            eligiblePackages =
                Arrays.stream(
                        appContext.resources.getStringArray(R.array.default_featured_widget_apps)
                    )
                    .collect(Collectors.toSet())
        }
    }

    override suspend fun getFeaturedWidgets(widgetApps: List<WidgetApp>): List<PickableWidget> {
        val widgetsByContainerSize =
            widgetApps
                .shuffled()
                // pick only one of user profiles
                .distinctBy { Pair(it.id.packageName, it.id.category) }
                .flatMap { it.widgets }
                .filter {
                    eligiblePackages.isEmpty() ||
                        eligiblePackages.contains(it.id.componentName.packageName)
                }
                .groupBy {
                    WidgetPreviewContainerSize(
                        it.sizeInfo.containerSpanX,
                        it.sizeInfo.containerSpanY,
                    )
                }

        val selected: MutableList<PickableWidget> = mutableListOf()
        val usedAppIds: MutableSet<String> = mutableSetOf()

        val sizesToPick =
            WidgetPreviewContainerSize.pickTemplateForFeaturedWidgets(
                idp.getDeviceProfile(appContext)
            )
        for (sizeToPick in sizesToPick) {
            widgetsByContainerSize[sizeToPick]?.shuffled()?.let { items ->
                for (item in items) {
                    if (
                        item.widgetInfo.isAppWidget() &&
                            !usedAppIds.contains(item.appId.packageName)
                    ) {
                        selected.add(item)
                        usedAppIds.add(item.appId.packageName)
                        break
                    }
                }
            }
        }

        return selected
    }

    override fun cleanup() {
        eligiblePackages = emptySet()
    }
}
