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

import android.util.SparseArray
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceData.ImmutableWorkspaceData
import com.android.launcher3.util.MutableListenableRef
import javax.inject.Inject

/**
 * Repository for the home screen data.
 *
 * This class is responsible for holding the current state of the home screen and providing a way to
 * listen for changes to the data.
 */
@LauncherAppSingleton
class HomeScreenRepository @Inject constructor() {

    private val mutableStateRef: MutableListenableRef<WorkspaceData> =
        MutableListenableRef(ImmutableWorkspaceData(0, 0, emptyList(), SparseArray()))

    /** Represents the current home screen data model */
    val workspaceStateRef = mutableStateRef.asListenable()

    /** sets a new value to [workspaceStateRef] */
    fun dispatchChange(workspaceData: WorkspaceData) {
        mutableStateRef.dispatchValue(workspaceData)
    }
}
