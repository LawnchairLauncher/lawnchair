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

package com.android.launcher3.compose.core.widgetpicker

import dagger.Binds
import dagger.Module

/**
 * A module that provides a no-op [WidgetPickerComposeWrapper] for dagger graph that doesn't
 * involve widget picker e.g. launcher preview OR when compose is disabled via build flag.
 */
@Module
interface NoOpWidgetPickerModule {
    @Binds
    fun bindWidgetPickerWrapper(noOp: NoOpWidgetPickerComposeWrapper): WidgetPickerComposeWrapper
}