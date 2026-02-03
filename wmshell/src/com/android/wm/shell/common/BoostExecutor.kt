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
package com.android.wm.shell.common

import android.os.Looper
import java.util.concurrent.Executor

/** Executor implementation which can be boosted temporarily to a different thread priority.  */
interface BoostExecutor : Executor {
    /**
     * Requests that the executor is boosted until {@link #resetBoost()} is called.
     */
    fun setBoost() {}

    /**
     * Requests that the executor is not boosted (only resets if there are no other boost requests
     * in progress).
     */
    fun resetBoost() {}

    /**
     * Returns whether the executor is boosted.
     */
    fun isBoosted() : Boolean {
        return false
    }

    /**
     * Returns the looper for this executor.
     */
    fun getLooper() : Looper? {
        return Looper.myLooper()
    }
}
