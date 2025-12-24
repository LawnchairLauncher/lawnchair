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

import com.android.systemui.util.kotlin.pairwiseBy
import kotlinx.coroutines.flow.Flow

/**
 * Each time the flow is updated with a new value, logs the differences between the previous value
 * and the new value to the given [tableLogBuffer].
 *
 * The new value's [Diffable.logDiffs] method will be used to log the differences to the table.
 *
 * @param columnPrefix a prefix that will be applied to every column name that gets logged.
 */
public fun <T : Diffable<T>> Flow<T>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    initialValue: T,
): Flow<T> {
    // Fully log the initial value to the table.
    val getInitialValue = {
        tableLogBuffer.logChange(columnPrefix, isInitial = true) { row ->
            initialValue.logFull(row)
        }
        initialValue
    }
    return this.pairwiseBy(getInitialValue) { prevVal: T, newVal: T ->
        tableLogBuffer.logDiffs(columnPrefix, prevVal, newVal)
        newVal
    }
}

// Here and below: Various Flow<SomeType> extension functions that are effectively equivalent to the
// above [logDiffsForTable] method.

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
public fun Flow<Boolean>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
    initialValue: Boolean,
): Flow<Boolean> {
    val initialValueFun = {
        tableLogBuffer.logChange(columnPrefix, columnName, initialValue, isInitial = true)
        initialValue
    }
    return this.pairwiseBy(initialValueFun) { prevVal: Boolean, newVal: Boolean ->
        if (prevVal != newVal) {
            tableLogBuffer.logChange(columnPrefix, columnName, newVal)
        }
        newVal
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
public fun Flow<Int>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
    initialValue: Int,
): Flow<Int> {
    val initialValueFun = {
        tableLogBuffer.logChange(columnPrefix, columnName, initialValue, isInitial = true)
        initialValue
    }
    return this.pairwiseBy(initialValueFun) { prevVal: Int, newVal: Int ->
        if (prevVal != newVal) {
            tableLogBuffer.logChange(columnPrefix, columnName, newVal)
        }
        newVal
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
public fun Flow<Int?>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
    initialValue: Int?,
): Flow<Int?> {
    val initialValueFun = {
        tableLogBuffer.logChange(columnPrefix, columnName, initialValue, isInitial = true)
        initialValue
    }
    return this.pairwiseBy(initialValueFun) { prevVal: Int?, newVal: Int? ->
        if (prevVal != newVal) {
            tableLogBuffer.logChange(columnPrefix, columnName, newVal)
        }
        newVal
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
public fun Flow<String?>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
    initialValue: String?,
): Flow<String?> {
    val initialValueFun = {
        tableLogBuffer.logChange(columnPrefix, columnName, initialValue, isInitial = true)
        initialValue
    }
    return this.pairwiseBy(initialValueFun) { prevVal: String?, newVal: String? ->
        if (prevVal != newVal) {
            tableLogBuffer.logChange(columnPrefix, columnName, newVal)
        }
        newVal
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
public fun <T> Flow<List<T>>.logDiffsForTable(
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
    initialValue: List<T>,
): Flow<List<T>> {
    val initialValueFun = {
        tableLogBuffer.logChange(
            columnPrefix,
            columnName,
            initialValue.toString(),
            isInitial = true,
        )
        initialValue
    }
    return this.pairwiseBy(initialValueFun) { prevVal: List<T>, newVal: List<T> ->
        if (prevVal != newVal) {
            // TODO(b/267761156): Can we log list changes without using toString?
            tableLogBuffer.logChange(columnPrefix, columnName, newVal.toString())
        }
        newVal
    }
}

// See [com.android.systemui.log.table.DiffableExtensionsKairos] for similar extension functions for
// Kairos states.
