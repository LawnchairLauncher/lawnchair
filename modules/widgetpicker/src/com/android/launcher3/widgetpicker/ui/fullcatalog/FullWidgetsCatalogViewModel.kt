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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.widgetpicker.WidgetPickerHostInfo
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenViewModel
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.search.SearchScreenViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Top-level view model for the widget picker view that shows a full catalog of widgets available
 * on the device.
 */
class FullWidgetsCatalogViewModel @AssistedInject constructor(
    landingScreenViewModelFactory: LandingScreenViewModel.Factory,
    searchScreenViewModelFactory: SearchScreenViewModel.Factory,
    @WidgetPickerHostInfo private val hostInfo: WidgetHostInfo,
) : ViewModel {
    val landingScreenViewModel = landingScreenViewModelFactory.create()
    val searchScreenViewModel = searchScreenViewModelFactory.create()

    val title: String? = hostInfo.title
    val description: String? = hostInfo.description
    val showDragShadow: Boolean = hostInfo.showDragShadow
    var activeScreen by mutableStateOf(Screen.LANDING)
        private set

    override suspend fun onInit() {
        coroutineScope {
            launch { landingScreenViewModel.onInit() }
            launch { searchScreenViewModel.onInit() }

            awaitCancellation()
        }
    }

    fun onActiveScreenChange(screen: Screen) {
        activeScreen = screen
    }

    @AssistedFactory
    @WidgetPickerSingleton
    interface Factory {
        fun create(): FullWidgetsCatalogViewModel
    }

    enum class Screen {
        LANDING,
        SEARCH
    }
}
