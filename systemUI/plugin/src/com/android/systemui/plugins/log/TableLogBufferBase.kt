/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.log

/**
 * Base interface for a logger that logs changes in table format.
 *
 * This is a plugin interface for classes outside of SystemUI core.
 */
interface TableLogBufferBase {
    /**
     * Logs a String? change.
     *
     * For Java overloading.
     */
    fun logChange(prefix: String, columnName: String, value: String?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /** Logs a String? change. */
    fun logChange(prefix: String, columnName: String, value: String?, isInitial: Boolean)

    /**
     * Logs a Boolean change.
     *
     * For Java overloading.
     */
    fun logChange(prefix: String, columnName: String, value: Boolean) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /** Logs a Boolean change. */
    fun logChange(prefix: String, columnName: String, value: Boolean, isInitial: Boolean)

    /**
     * Logs an Int? change.
     *
     * For Java overloading.
     */
    fun logChange(prefix: String, columnName: String, value: Int?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /** Logs an Int? change. */
    fun logChange(prefix: String, columnName: String, value: Int?, isInitial: Boolean)
}
