/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3

import android.util.SparseIntArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspacePageReorderTest {

    @Test
    fun createPageGroupSwapMap_singlePanel_swapsBothScreens() {
        val screenOrder = IntArray.wrap(0, 1, 2, 3)

        val swapMap = Workspace.createPageGroupSwapMap(screenOrder, 1, 2, 1)

        assertSwap(swapMap, 1, 2)
        assertSwap(swapMap, 2, 1)
    }

    @Test
    fun createPageGroupSwapMap_twoPanel_swapsPagePairs() {
        val screenOrder = IntArray.wrap(0, 1, 2, 3, 4, 5)

        val swapMap = Workspace.createPageGroupSwapMap(screenOrder, 2, 4, 2)

        assertSwap(swapMap, 2, 4)
        assertSwap(swapMap, 3, 5)
        assertSwap(swapMap, 4, 2)
        assertSwap(swapMap, 5, 3)
    }

    @Test
    fun getPageGroupStarts_singlePanel_returnsEveryPageStart() {
        val starts = Workspace.getPageGroupStarts(IntArray.wrap(0, 1, 2, 3), 1)
        assertThat(starts.toArray().asList()).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun getPageGroupStarts_twoPanel_returnsPairStarts() {
        val starts = Workspace.getPageGroupStarts(IntArray.wrap(0, 1, 2, 3, 4, 5), 2)
        assertThat(starts.toArray().asList()).containsExactly(0, 2, 4).inOrder()
    }

    @Test
    fun getPersistedWorkspaceScreenIds_parsesCommaSeparatedOrder() {
        val ids = Workspace.getPersistedWorkspaceScreenIds("0,1,2")
        assertThat(ids.getArray().toArray().asList()).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun getPersistedWorkspaceScreenIds_ignoresInvalidEntries() {
        val ids = Workspace.getPersistedWorkspaceScreenIds("2, ,x,0")
        assertThat(ids.getArray().toArray().asList()).containsExactly(0, 2).inOrder()
    }

    @Test
    fun shouldPreserveEmptyScreenWhenStripping_skipsPersistedNonExtraScreens() {
        val persisted = IntSet.wrap(0, 1, 2)
        assertThat(
            Workspace.shouldPreserveEmptyScreenWhenStripping(2, persisted, false),
        ).isTrue()
        assertThat(
            Workspace.shouldPreserveEmptyScreenWhenStripping(
                Workspace.EXTRA_EMPTY_SCREEN_ID,
                persisted,
                true,
            ),
        ).isFalse()
        assertThat(
            Workspace.shouldPreserveEmptyScreenWhenStripping(3, persisted, false),
        ).isFalse()
    }

    private fun assertSwap(map: SparseIntArray, from: Int, to: Int) {
        assertThat(map.indexOfKey(from)).isAtLeast(0)
        assertThat(map.get(from)).isEqualTo(to)
    }
}
