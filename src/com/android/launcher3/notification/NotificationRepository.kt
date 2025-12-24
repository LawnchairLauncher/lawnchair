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

package com.android.launcher3.notification

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dot.DotInfo
import com.android.launcher3.util.MutableListenableStream
import com.android.launcher3.util.PackageUserKey
import java.util.function.Predicate
import javax.inject.Inject

@LauncherAppSingleton
class NotificationRepository @Inject constructor() {

    /** Maps packages to their DotInfo's . */
    var packageUserToDotInfos: Map<PackageUserKey, DotInfo> = emptyMap()
        private set

    private val _updateStream = MutableListenableStream<Predicate<PackageUserKey>>()
    /** Update events on [packageUserToDotInfos] */
    val updateStream = _updateStream.asListenable()

    /** Dispatches a new notifation data update */
    fun dispatchUpdate(newValue: Map<PackageUserKey, DotInfo>, update: Predicate<PackageUserKey>) {
        packageUserToDotInfos = newValue
        _updateStream.dispatchValue(update)
    }
}
