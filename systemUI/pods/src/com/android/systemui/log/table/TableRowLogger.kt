/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.log.table

/**
 * A class that logs a row to [TableLogBuffer].
 *
 * Objects that implement [Diffable] will receive an instance of this class, and can log any changes
 * to individual fields using the [logChange] methods. All logged changes will be associated with
 * the same timestamp.
 */
public interface TableRowLogger {
    /** Logs a change to a string value. */
    public fun logChange(columnName: String, value: String?)

    /** Logs a change to a boolean value. */
    public fun logChange(columnName: String, value: Boolean)

    /** Logs a change to an int value. */
    public fun logChange(columnName: String, value: Int)
}
