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

import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.ui.fullcatalog.FullWidgetsCatalog
import dagger.BindsInstance
import dagger.Subcomponent
import kotlin.coroutines.CoroutineContext

/**
 * A sub-component that apps can include in their dagger module to bootstrap & interact with apis
 * provided by widget picker.
 *
 * Steps:
 * 1. Include this component as a sub-component on your app's main dagger module (and remember to
 *    update Android.bp & gradle files).
 * 2. Host a singleton class in your app to create an instance of widget picker component on-demand
 *    e.g. when user wants to open widget picker. The component factory takes in a few dependencies
 *    that should be implemented e.g. the repository implementations.
 *
 * Example of using the component and opening the picker.
 *
 * ```
 * @MyAppSingleton
 * public class WidgetPickerProvider @Inject constructor(
 *    private val Provider<WidgetPickerComponent.Factory> widgetPickerComponentProvider
 * ) {
 *     public boolean show(...) {
 *         WidgetPickerComponent component = widgetPickerComponentProvider.get().build(...);
 *         FullCatalog catalog = component.getFullWidgetsCatalog();
 *         return catalog.show(...);
 *     }
 * }
 * ```
 */
@WidgetPickerSingleton
@Subcomponent
interface WidgetPickerComponent {
    @Subcomponent.Factory
    interface Factory {
        fun build(
            @WidgetPickerRepository @BindsInstance
            widgetsRepository: WidgetsRepository,
            @WidgetPickerRepository @BindsInstance
            widgetUsersRepository: WidgetUsersRepository,
            @WidgetPickerRepository @BindsInstance
            widgetAppIconsRepository: WidgetAppIconsRepository,
            @WidgetPickerHostInfo @BindsInstance
            widgetHostInfo: WidgetHostInfo,
            @WidgetPickerBackground @BindsInstance
            backgroundContext: CoroutineContext
        ): WidgetPickerComponent
    }

    /** Provides UI for the catalog of all widgets on device. */
    fun getFullWidgetsCatalog(): FullWidgetsCatalog
}
