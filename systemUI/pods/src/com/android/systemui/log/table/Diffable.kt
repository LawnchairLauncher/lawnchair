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
 * An interface that enables logging the difference between values in table format.
 *
 * Many objects that we want to log are data-y objects with a collection of fields. When logging
 * these objects, we want to log each field separately. This allows ABT (Android Bug Tool) to easily
 * highlight changes in individual fields.
 *
 * See [TableLogBuffer].
 */
public interface Diffable<T> {
    /**
     * Finds the differences between [prevVal] and this object and logs those diffs to [row].
     *
     * Each implementer should determine which individual fields have changed between [prevVal] and
     * this object, and only log the fields that have actually changed. This helps save buffer
     * space.
     *
     * For example, if:
     * - prevVal = Object(val1=100, val2=200, val3=300)
     * - this = Object(val1=100, val2=200, val3=333)
     *
     * Then only the val3 change should be logged.
     */
    public fun logDiffs(prevVal: T, row: TableRowLogger)

    /**
     * Logs all the relevant fields of this object to [row].
     *
     * As opposed to [logDiffs], this method should log *all* fields.
     *
     * Implementation is optional. This method will only be used with [logDiffsForTable] in order to
     * fully log the initial value of the flow.
     */
    public fun logFull(row: TableRowLogger) {}
}
