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

package com.android.launcher3.widgetpicker.repository

import android.content.Context
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.pm.ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVO
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize
import com.android.launcher3.widget.util.WidgetSizes
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.datasource.FeaturedWidgetsDataSource
import com.android.launcher3.widgetpicker.datasource.WidgetsSearchAlgorithm
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An implementation of the [WidgetsRepository] that provides widgets for widget picker using the
 * [WidgetsModel], [FeaturedWidgetsDataSource] & enables search using the provided
 * [WidgetsSearchAlgorithm].
 */
class WidgetsRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    idp: InvariantDeviceProfile,
    private val widgetsModel: WidgetsModel,
    private val featuredWidgetsDataSource: FeaturedWidgetsDataSource,
    private val searchAlgorithm: WidgetsSearchAlgorithm,
    @BackgroundContext private val backgroundContext: CoroutineContext,
) : WidgetsRepository {
    private val deviceProfile = idp.getDeviceProfile(appContext)
    private val backgroundScope =
        CoroutineScope(SupervisorJob() + backgroundContext + CoroutineName("WidgetsRepository"))

    private val _widgetItemsByPackage = MutableStateFlow<List<WidgetApp>>(emptyList())
    private val databaseWidgetPreviewLoader = DatabaseWidgetPreviewLoader(appContext, deviceProfile)

    override fun initialize() {
        // TODO(b/419495339): Remove the model executor requirement from widgets model and replace
        // with scope.launch
        MODEL_EXECUTOR.execute {
            widgetsModel.update(/* packageUser= */ null)
            _widgetItemsByPackage.update {
                widgetsModel.widgetsByPackageItemForPicker.toPickableWidgets(deviceProfile)
            }
        }

        backgroundScope.launch { featuredWidgetsDataSource.initialize() }
    }

    override fun observeWidgets(): Flow<List<WidgetApp>> = _widgetItemsByPackage.asStateFlow()

    override suspend fun getWidgetPreview(id: WidgetId): WidgetPreview {
        val componentKey = ComponentKey(id.componentName, id.userHandle)
        val widgetItem =
            widgetsModel.widgetsByComponentKey[componentKey]
                ?: return WidgetPreview.PlaceholderWidgetPreview

        val previewSizePx =
            WidgetSizes.getWidgetSizePx(deviceProfile, widgetItem.spanX, widgetItem.spanY)
        val preview =
            withContext(backgroundContext) {
                val result =
                    databaseWidgetPreviewLoader.generatePreviewInfoBg(
                        widgetItem,
                        previewSizePx.width,
                        previewSizePx.height,
                    )
                when {
                    result.remoteViews != null ->
                        WidgetPreview.RemoteViewsWidgetPreview(result.remoteViews)

                    result.providerInfo != null ->
                        WidgetPreview.ProviderInfoWidgetPreview(result.providerInfo)

                    result.previewBitmap != null ->
                        WidgetPreview.BitmapWidgetPreview(result.previewBitmap)

                    else -> WidgetPreview.PlaceholderWidgetPreview
                }
            }

        return preview
    }

    override suspend fun searchWidgets(query: String): List<WidgetApp> =
        searchAlgorithm.searchWidgets(query, _widgetItemsByPackage.value)

    override fun cleanUp() {
        _widgetItemsByPackage.update { emptyList() }
        backgroundScope.apply {
            if (isActive) {
                cancel()
            }
        }
    }

    override fun getFeaturedWidgets(): Flow<List<PickableWidget>> {
        return _widgetItemsByPackage
            .map { widgets -> featuredWidgetsDataSource.getFeaturedWidgets(widgets) }
            .flowOn(backgroundContext)
    }

    companion object {
        private fun Map<PackageItemInfo, List<WidgetItem>>.toPickableWidgets(
            deviceProfile: DeviceProfile
        ) = map { (packageItemInfo, widgetItems) ->
            val widgetAppId =
                WidgetAppId(
                    packageName = packageItemInfo.packageName,
                    userHandle = packageItemInfo.user,
                    category = packageItemInfo.widgetCategory,
                )

            WidgetApp(
                id = widgetAppId,
                title = packageItemInfo.title,
                widgets =
                    widgetItems.map { widgetItem ->
                        val previewSize =
                            WidgetSizes.getWidgetSizePx(
                                deviceProfile,
                                widgetItem.spanX,
                                widgetItem.spanY,
                            )
                        val containerSpan =
                            WidgetPreviewContainerSize.forItem(widgetItem, deviceProfile)
                        val containerSize =
                            WidgetSizes.getWidgetSizePx(
                                deviceProfile,
                                containerSpan.spanX,
                                containerSpan.spanY,
                            )

                        PickableWidget(
                            id =
                                WidgetId(
                                    componentName = widgetItem.componentName,
                                    userHandle = widgetItem.user,
                                ),
                            appId = widgetAppId,
                            label = widgetItem.label,
                            description = widgetItem.description,
                            widgetInfo =
                                if (widgetItem.widgetInfo != null) {
                                    WidgetInfo.AppWidgetInfo(
                                        appWidgetProviderInfo = widgetItem.widgetInfo.clone()
                                    )
                                } else {
                                    check(widgetItem.activityInfo is ShortcutConfigActivityInfoVO)
                                    WidgetInfo.ShortcutInfo(
                                        launcherActivityInfo = widgetItem.activityInfo.mInfo
                                    )
                                },
                            sizeInfo =
                                WidgetSizeInfo(
                                    spanX = widgetItem.spanX,
                                    spanY = widgetItem.spanY,
                                    widthPx = previewSize.width,
                                    heightPx = previewSize.height,
                                    containerSpanX = containerSpan.spanX,
                                    containerSpanY = containerSpan.spanY,
                                    containerWidthPx = containerSize.width,
                                    containerHeightPx = containerSize.height,
                                ),
                        )
                    },
            )
        }
    }
}
