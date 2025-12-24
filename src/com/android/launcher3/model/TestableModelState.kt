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

package com.android.launcher3.model

import com.android.launcher3.LauncherModel
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.model.repository.HomeScreenRepository
import javax.inject.Inject

/**
 * Data class which exposes various Launcher model internals for testing. This should NOT be used in
 * production.
 */
data class TestableModelState
@Inject
constructor(
    @JvmField val model: LauncherModel,
    @JvmField val dataModel: BgDataModel,
    @JvmField val appsList: AllAppsList,
    @JvmField val homeRepo: HomeScreenRepository,
    @JvmField val appsRepo: AppsListRepository,
    @JvmField val dbController: ModelDbController,
    @JvmField val iconCache: IconCache,
)
