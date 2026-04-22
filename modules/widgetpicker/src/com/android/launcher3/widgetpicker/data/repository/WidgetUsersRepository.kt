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

package com.android.launcher3.widgetpicker.data.repository

import android.os.UserHandle
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import kotlinx.coroutines.flow.Flow

/**
 * A repository that provides information about user profiles for which widgets need to be shown in
 * the widget picker.
 */
interface WidgetUsersRepository {
    /**
     * A hook to setup the repository so clients can observe the widgets available on device.
     * This serves as a place to start listening to the backing caches / data sources.
     */
    fun initialize()

    /** Get and listen to the changes in available user profiles. */
    fun observeUserProfiles(): Flow<WidgetUserProfiles?>

    /** Indicates if a user is a work profile user. */
    fun getWorkProfileUser(): UserHandle?

    /** Clean up any external listeners or state (if necessary). */
    fun cleanUp()
}
