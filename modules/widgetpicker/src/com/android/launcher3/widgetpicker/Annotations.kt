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

import javax.inject.Qualifier
import javax.inject.Scope

/** Scope annotation for singleton items within the widget picker. */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Scope
annotation class WidgetPickerSingleton

/** Qualifier annotation for the background context used in interactors. */
@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class WidgetPickerBackground

/** Qualifier annotation for information about the widget host. */
@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class WidgetPickerHostInfo

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class WidgetPickerRepository
