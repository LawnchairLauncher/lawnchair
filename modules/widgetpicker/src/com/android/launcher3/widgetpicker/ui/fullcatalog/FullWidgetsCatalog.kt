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

package com.android.launcher3.widgetpicker.ui.fullcatalog

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.ModalBottomSheetHeightStyle
import com.android.launcher3.widgetpicker.ui.components.TitledBottomSheet
import com.android.launcher3.widgetpicker.ui.components.TitledBottomSheetDefaults
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTagContainer
import com.android.launcher3.widgetpicker.ui.fullcatalog.FullWidgetsCatalogViewModel.Screen
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreen
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.search.SearchScreen
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.launcher3.widgetpicker.ui.windowsizeclass.calculateWindowInfo
import javax.inject.Inject

/**
 * A catalog of all widgets available on device.
 *
 * When opened, first shows a landing page that comprises of the featured widgets and the list of
 * apps hosting widgets. User can enter search mode by tapping the search bar and see matching
 * results.
 *
 */
class FullWidgetsCatalog @Inject constructor(
    private val viewModelFactory: FullWidgetsCatalogViewModel.Factory,
) {
    @Composable
    fun Content(
        eventListeners: WidgetPickerEventListeners,
    ) {
        val viewModel: FullWidgetsCatalogViewModel =
            rememberViewModel(
                animationDelay = TitledBottomSheetDefaults.SLIDE_IN_ANIMATION_DURATION
            ) {
                viewModelFactory.create()
            }
        val windowInfo = LocalConfiguration.current.calculateWindowInfo()
        val isCompactWidth =
            windowInfo.windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        val isCompactHeight =
            windowInfo.windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

        TitledBottomSheet(
            title = viewModel.title.takeIf { !isCompactHeight },
            modifier =
                Modifier
                .widgetPickerTestTagContainer()
                .widgetPickerTestTag(WIDGET_CATALOG_TEST_TAG),
            description = viewModel.description,
            heightStyle = ModalBottomSheetHeightStyle.FILL_HEIGHT,
            showDragHandle = true,
            onDismissRequest = { eventListeners.onClose() },
        ) {
            when (viewModel.activeScreen) {
                Screen.LANDING -> {
                    LandingScreen(
                        isCompact = isCompactWidth,
                        onEnterSearchMode = { viewModel.onActiveScreenChange(Screen.SEARCH) },
                        onWidgetInteraction = eventListeners::onWidgetInteraction,
                        showDragShadow = viewModel.showDragShadow,
                        viewModel = viewModel.landingScreenViewModel,
                    )
                }

                Screen.SEARCH -> {
                    SearchScreen(
                        isCompact = isCompactWidth,
                        onExitSearchMode = { viewModel.onActiveScreenChange(Screen.LANDING) },
                        onWidgetInteraction = eventListeners::onWidgetInteraction,
                        showDragShadow = viewModel.showDragShadow,
                        viewModel = viewModel.searchScreenViewModel,
                    )
                }
            }
        }
    }
}

private const val WIDGET_CATALOG_TEST_TAG = "widgets_catalog"
