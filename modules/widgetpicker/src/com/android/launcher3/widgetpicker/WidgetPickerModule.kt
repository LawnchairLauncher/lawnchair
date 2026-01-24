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

package com.android.launcher3.widgetpicker

import dagger.Module

/**
 * Module that contains the bindings necessary for the widget picker library.
 *
 * See [WidgetPickerComponent] on how to include this module in your app. Dependencies in this
 * module will need to be provided by the host application.
 * 1. In following example, we depend on host for providing the `WidgetsRepository`
 *
 * ```
 * @Provides
 * @WidgetPickerSingleton
 * fun provideWidgetsInteractor(widgetsRepository: WidgetsRepository): WidgetsInteractor
 * = WidgetsInteractorImpl(widgetsRepository)
 * ```
 * 2. Following shows an example of the host module providing concrete implementation for the
 *    dependency.
 *
 * ```
 * @Module(includes = [WidgetPickerModule::class]
 * abstract MyAppSpecificWidgetPickerModule {
 *    @Binds fun bindWidgetsRepository(impl: MyAppSpecificWidgetsRepositoryImpl): WidgetsRepository
 * }
 * ```
 */
@Module
interface WidgetPickerModule {
    companion object {
        // TODO(b/408284734): Add the necessary bindings for widget picker
    }
}
