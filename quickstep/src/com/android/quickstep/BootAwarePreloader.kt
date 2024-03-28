/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep

import android.content.Context
import android.util.Log
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.moveStartupDataToDeviceProtectedStorageIsEnabled
import com.android.launcher3.util.LockedUserState

/**
 * Loads expensive objects in memory before the user is unlocked. This decreases experienced latency
 * when starting the launcher for the first time after a reboot.
 */
object BootAwarePreloader {
    private const val TAG = "BootAwarePreloader"

    @JvmStatic
    fun start(context: Context) {
        val lp = LauncherPrefs.get(context)
        when {
            LockedUserState.get(context).isUserUnlocked ||
                !moveStartupDataToDeviceProtectedStorageIsEnabled -> {
                /* No-Op */
            }
            lp.isStartupDataMigrated -> {
                Log.d(TAG, "preloading start up data")
                LauncherAppState.INSTANCE.get(context)
            }
            else -> {
                Log.d(TAG, "queuing start up data migration to boot aware prefs")
                LockedUserState.get(context).runOnUserUnlocked {
                    lp.migrateStartupDataToDeviceProtectedStorage()
                }
            }
        }
    }
}
