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
        data.modifyItems {}

        assertThat(firstCopy).isNotEqualTo(data)
        assertThat(data.copy()).isEqualTo(data)
    }

    @Test
    fun copied_data_is_same_after_changes() {
        val data = MutableWorkspaceData()
        data.replaceDataMap(createItemSparseArray(3))
        data.modifyItems {}
        assertThat(data.copy()).isEqualTo(data)
        assertThat(data.copy()).isEqualTo(data.copy())
    }

    private fun createItemSparseArray(size: Int) =
        SparseArray<ItemInfo>().apply { for (i in 0..<size) this[i] = createItemInfo(i) }

    private fun createItemInfo(id: Int) = ItemInfo().apply { this.id = id }
}
