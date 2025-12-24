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

package com.android.quickstep.recents.data

import android.app.KeyguardManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class UserLockedRepository(private val keyguardManager: KeyguardManager) :
    UserLockedStateRepository {
    private val cache: ConcurrentMap<Int, Boolean> = ConcurrentHashMap()

    override fun invalidateCachedValues() {
        cache.clear()
    }

    override fun getIsUserLocked(userId: Int) =
        cache.getOrPut(userId) { keyguardManager.isDeviceLocked(userId) }
}
