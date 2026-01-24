/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.util

import com.android.launcher3.BuildConfig
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.DeviceConfigWrapper.Companion.configHelper
import com.android.quickstep.util.DeviceConfigHelper.Companion.prefs
import java.util.concurrent.CountDownLatch
import java.util.function.BooleanSupplier
import org.junit.Assert
import org.junit.Assume

/** Helper methods for testing */
object TestExtensions {

    @JvmStatic
    fun overrideNavConfigFlag(
        key: String,
        value: Boolean,
        targetValue: BooleanSupplier
    ): AutoCloseable {
        Assume.assumeTrue(BuildConfig.IS_DEBUG_DEVICE)
        if (targetValue.asBoolean == value) {
            return AutoCloseable {}
        }

        navConfigEditWatcher().let {
            prefs.edit().putBoolean(key, value).commit()
            it.close()
        }
        Assert.assertEquals(value, targetValue.asBoolean)

        val watcher = navConfigEditWatcher()
        return AutoCloseable {
            prefs.edit().remove(key).commit()
            watcher.close()
        }
    }

    private fun navConfigEditWatcher(): SafeCloseable {
        val wait = CountDownLatch(1)
        val listener = Runnable { wait.countDown() }
        configHelper.addChangeListener(listener)

        return SafeCloseable {
            wait.await()
            configHelper.removeChangeListener(listener)
        }
    }
}
