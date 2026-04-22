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

package com.android.launcher3.dagger

import com.android.launcher3.model.ModelDelegate
import com.android.launcher3.model.QuickstepModelDelegate
import dagger.Binds
import dagger.Module

/**
 * Module containing bindings for the final derivative app, an implementation of this module should
 * be included in the final app code.
 */
@Module
abstract class AppModule {

    @Binds abstract fun bindModelDelegate(impl: QuickstepModelDelegate): ModelDelegate
}
