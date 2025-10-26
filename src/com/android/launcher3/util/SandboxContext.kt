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

package com.android.launcher3.util

import android.content.Context
import com.android.launcher3.LauncherApplication

/** Abstract Context which allows custom implementations for dagger components. */
open class SandboxContext(base: Context?) : LauncherApplication() {
    init {
        base?.let { attachBaseContext(it) }
    }

    override fun getApplicationContext(): Context {
        return this
    }

    /**
     * Returns whether this sandbox should cleanup all objects when its destroyed or leave it to the
     * GC. These objects can have listeners attached to the system server and mey not be able to get
     * GCed themselves when running on a device. Some environments like Robolectric tear down the
     * whole system at the end of the test, so manual cleanup may not be required.
     */
    open fun shouldCleanUpOnDestroy(): Boolean {
        return (getBaseContext().getApplicationContext() as? SandboxContext)
            ?.shouldCleanUpOnDestroy() ?: true
    }

    fun onDestroy() {
        if (shouldCleanUpOnDestroy()) {
            cleanUpObjects()
        }
    }

    open protected fun cleanUpObjects() {
        appComponent.daggerSingletonTracker.close()
    }

    companion object {
        private const val TAG = "SandboxContext"
    }
}
