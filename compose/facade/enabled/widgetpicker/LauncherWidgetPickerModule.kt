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

package com.android.launcher3.compose.widgetpicker

import com.android.launcher3.compose.core.widgetpicker.WidgetPickerComposeWrapper
import com.android.launcher3.widgetpicker.WidgetPickerComponent
import com.android.launcher3.widgetpicker.WidgetPickerComposeWrapperImpl
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.datasource.ConfigResourceFeaturedWidgetsDataSource
import com.android.launcher3.widgetpicker.datasource.FeaturedWidgetsDataSource
import com.android.launcher3.widgetpicker.datasource.InMemoryWidgetSearchAlgorithm
import com.android.launcher3.widgetpicker.datasource.WidgetsSearchAlgorithm
import com.android.launcher3.widgetpicker.repository.WidgetsRepositoryImpl
import com.android.launcher3.widgetpicker.repository.WidgetAppIconsRepositoryImpl
import com.android.launcher3.widgetpicker.repository.WidgetUsersRepositoryImpl
import dagger.Binds
import dagger.Module

/**
 * A module that installs widget picker for launcher.
 */
@Module(subcomponents = [WidgetPickerComponent::class])
interface LauncherWidgetPickerModule {
    @Binds
    fun bindWidgetPickerComposeWrapper(
        impl: WidgetPickerComposeWrapperImpl
    ): WidgetPickerComposeWrapper

    @Binds
    fun bindWidgetUsersRepository(impl: WidgetUsersRepositoryImpl): WidgetUsersRepository

    @Binds
    fun bindWidgetsRepository(impl: WidgetsRepositoryImpl): WidgetsRepository

    @Binds
    fun bindWidgetAppIconsRepository(impl: WidgetAppIconsRepositoryImpl): WidgetAppIconsRepository

    @Binds
    fun bindFeaturedWidgetsDataSource(
        impl: ConfigResourceFeaturedWidgetsDataSource
    ): FeaturedWidgetsDataSource

    @Binds
    fun bindWidgetsSearchAlgorithm(
        impl: InMemoryWidgetSearchAlgorithm
    ): WidgetsSearchAlgorithm
}
