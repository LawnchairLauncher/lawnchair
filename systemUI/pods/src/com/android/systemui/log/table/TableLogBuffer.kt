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

package com.android.systemui.log.table

import com.android.systemui.Dumpable

/**
 * A logger that logs changes in table format.
 *
 * Some parts of System UI maintain a lot of pieces of state at once.
 * [com.android.systemui.log.LogBuffer] allows us to easily log change events:
 * - 10-10 10:10:10.456: state2 updated to newVal2
 * - 10-10 10:11:00.000: stateN updated to StateN(val1=true, val2=1)
 * - 10-10 10:11:02.123: stateN updated to StateN(val1=true, val2=2)
 * - 10-10 10:11:05.123: state1 updated to newVal1
 * - 10-10 10:11:06.000: stateN updated to StateN(val1=false, val2=3)
 *
 * However, it can sometimes be more useful to view the state changes in table format:
 * - timestamp--------- | state1- | state2- | ... | stateN.val1 | stateN.val2
 * - -------------------------------------------------------------------------
 * - 10-10 10:10:10.123 | val1--- | val2--- | ... | false------ | 0-----------
 * - 10-10 10:10:10.456 | val1--- | newVal2 | ... | false------ | 0-----------
 * - 10-10 10:11:00.000 | val1--- | newVal2 | ... | true------- | 1-----------
 * - 10-10 10:11:02.123 | val1--- | newVal2 | ... | true------- | 2-----------
 * - 10-10 10:11:05.123 | newVal1 | newVal2 | ... | true------- | 2-----------
 * - 10-10 10:11:06.000 | newVal1 | newVal2 | ... | false------ | 3-----------
 *
 * This class enables easy logging of the state changes in both change event format and table
 * format.
 *
 * This class also enables easy logging of states that are a collection of fields. For example,
 * stateN in the above example consists of two fields -- val1 and val2. It's useful to put each
 * field into its own column so that ABT (Android Bug Tool) can easily highlight changes to
 * individual fields.
 *
 * How it works:
 * 1) Create an instance of this buffer via [TableLogBufferFactory].
 * 2) For any states being logged, implement [Diffable]. Implementing [Diffable] allows the state to
 *    only log the fields that have *changed* since the previous update, instead of always logging
 *    all fields.
 * 3) Each time a change in a state happens, call [logDiffs]. If your state is emitted using a
 *    [Flow], you should use the [logDiffsForTable] extension function to automatically log diffs
 *    any time your flow emits a new value.
 *
 * When a dump occurs, there will be two dumps:
 * 1) The change events under the dumpable name "$name-changes".
 * 2) This class will coalesce all the diffs into a table format and log them under the dumpable
 *    name "$name-table".
 *
 * @param maxSize the maximum size of the buffer. Must be > 0.
 */
public interface TableLogBuffer : Dumpable {
    /**
     * Logs a String? change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: String?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs a String? change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(
        prefix: String = "",
        columnName: String,
        value: String?,
        isInitial: Boolean,
    )

    /**
     * Logs a Boolean change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: Boolean) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs a Boolean change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(
        prefix: String = "",
        columnName: String,
        value: Boolean,
        isInitial: Boolean,
    )

    /**
     * Logs an Int? change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: Int?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs an Int? change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(prefix: String = "", columnName: String, value: Int?, isInitial: Boolean)

    /**
     * Log the differences between [prevVal] and [newVal].
     *
     * The [newVal] object's method [Diffable.logDiffs] will be used to fetch the diffs.
     *
     * @param columnPrefix a prefix that will be applied to every column name that gets logged. This
     *   ensures that all the columns related to the same state object will be grouped together in
     *   the table.
     * @throws IllegalArgumentException if [columnPrefix] or column name contain "|". "|" is used as
     *   the separator token for parsing, so it can't be present in any part of the column name.
     */
    public fun <T : Diffable<T>> logDiffs(columnPrefix: String = "", prevVal: T, newVal: T)

    /**
     * Logs change(s) to the buffer using [rowInitializer].
     *
     * @param columnPrefix see [logDiffs].
     * @param rowInitializer a function that will be called immediately to store relevant data on
     *   the row.
     * @param isInitial true if this change represents the starting value for a particular column
     *   (as opposed to a value that was updated after receiving new information). This is used to
     *   help us identify which values were just default starting values, and which values were
     *   derived from updated information. Most callers should use false for this value.
     */
    public fun logChange(
        columnPrefix: String = "",
        isInitial: Boolean = false,
        rowInitializer: (TableRowLogger) -> Unit,
    )
}
