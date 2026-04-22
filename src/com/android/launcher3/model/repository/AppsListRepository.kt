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

package com.android.launcher3.model.repository

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.MutableListenableStream
import javax.inject.Inject

/** Repository for app-list daya. */
@LauncherAppSingleton
class AppsListRepository @Inject constructor() {

    private val mutableStateRef = MutableListenableRef(AppsListData(emptyArray(), 0))

    /** Represents the current home screen data model. There are two ways this can change: */
    val appsListStateRef = mutableStateRef.asListenable()

    /** sets a new value to [appsListStateRef] */
    fun dispatchChange(appsListData: AppsListData) {
        mutableStateRef.dispatchValue(appsListData)
    }

    private val mutableIncrementalUpdate = MutableListenableStream<AppInfo>()
    /** Represents incremental download apps to apps list items */
    val incrementalUpdates = mutableIncrementalUpdate.asListenable()

    /** Dispatches an incremental download update to [incrementalUpdates] */
    fun dispatchIncrementationUpdate(appInfo: AppInfo) {
        mutableIncrementalUpdate.dispatchValue(appInfo)
    }
}
