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

package com.android.launcher3.model.data

import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.model.data.WorkspaceChangeEvent.AddEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.model.data.WorkspaceData.Companion.MAX_HISTORY_SIZE
import com.android.launcher3.model.data.WorkspaceData.MutableWorkspaceData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceDataTest {

    @Test
    fun two_mutable_data_are_different() {
        assertThat(MutableWorkspaceData()).isNotEqualTo(MutableWorkspaceData())
    }

    @Test
    fun copied_data_is_same_as_source() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(2))
        assertThat(data.copy()).isEqualTo(data)
        assertThat(data.copy()).isEqualTo(data.copy())
    }

    @Test
    fun copied_data_is_not_same_after_modification() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(3))
        val firstCopy = data.copy()
        data.removeItems(listOf(createItemInfo(1)), null)

        assertThat(firstCopy).isNotEqualTo(data)
        assertThat(data.copy()).isEqualTo(data)
    }

    @Test
    fun null_diff_if_data_replaced() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(3))

        val firstCopy = data.copy()
        data.replaceDataMap(createItemSparseArray(4))
        assertThat(data.diff(firstCopy)).isNull()
    }

    @Test
    fun empty_diff_for_same_data() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(3))
        data.removeItems(listOf(createItemInfo(1)), null)
        assertThat(data.diff(data.copy())).isEmpty()
    }

    @Test
    fun diff_same_with_copied_instance() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(100))
        val start = data.copy()
        data.removeItems(listOf(createItemInfo(99)), null)
        data.addItems(listOf(createItemInfo(0)), null)

        assertThat(data.diff(start)).isNotNull()
        assertThat(data.diff(start)).isEqualTo(data.copy().diff(start))
    }

    @Test
    fun diff_contains_valid_changes() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(100))
        for (i in 0..50) data.removeItems(listOf(createItemInfo(i)), null)

        val start = data.copy()
        data.notifyItemsUpdated(listOf(createItemInfo(0)), null)
        data.removeItems(listOf(createItemInfo(99)), null)
        data.addItems(listOf(createItemInfo(0)), null)

        val diff = data.diff(start)!!
        assertThat(diff).hasSize(3)
        assertThat(diff[0]).isInstanceOf(UpdateEvent::class.java)
        assertThat(diff[1]).isInstanceOf(RemoveEvent::class.java)
        assertThat(diff[2]).isInstanceOf(AddEvent::class.java)
    }

    @Test
    fun diff_fails_in_reverse_order() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(100))
        val start = data.copy()
        data.removeItems(listOf(createItemInfo(99)), null)
        assertThat(start.diff(data)).isNull()
    }

    @Test
    fun diff_fails_when_history_is_lost() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(100))
        val start = data.copy()

        // History is preserved for up to MAX_HISTORY_SIZE items
        for (i in 1..MAX_HISTORY_SIZE) data.removeItems(listOf(createItemInfo(i)), null)
        assertThat(data.diff(start)).isNotNull()

        data.removeItems(listOf(createItemInfo(0)), null)
        assertThat(data.diff(start)).isNull()
    }

    private fun createItemSparseArray(size: Int) =
        SparseArray<ItemInfo>().apply { for (i in 0..<size) this[i] = createItemInfo(i) }

    private fun createItemInfo(id: Int) = ItemInfo().apply { this.id = id }
}
