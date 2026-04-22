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

package com.android.launcher3.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.WorkspaceItemInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutUtilTest {

    @Test
    fun `supportsShortcuts returns true if the item is active and it is an app`() {
        // A blank workspace item info should be active and should be ITEM_TYPE_APPLICATION
        val itemInfo = WorkspaceItemInfo()
        // Action
        val result = ShortcutUtil.supportsShortcuts(itemInfo)
        // Verify
        assertEquals(true, result)
    }

    @Test
    fun `supportsDeepShortcuts returns true if the app is active and an app and widgets are enabled`() {
        // Setup
        val itemInfo = WorkspaceItemInfo()
        // Action
        val result = ShortcutUtil.supportsDeepShortcuts(itemInfo)
        // Verify
        assertEquals(true, result)
    }

    @Test
    fun `getShortcutIdIfPinnedShortcut returns null if the item is an app`() {
        // Setup
        val itemInfo = WorkspaceItemInfo()
        // Action
        val result = ShortcutUtil.getShortcutIdIfPinnedShortcut(itemInfo)
        // Verify
        assertNull(result)
    }

    @Test
    fun `getPersonKeysIfPinnedShortcut returns empty string array if item type is an app`() {
        // Setup
        val itemInfo = WorkspaceItemInfo()
        // Action
        val result = ShortcutUtil.getPersonKeysIfPinnedShortcut(itemInfo)
        // Verify
        assertArrayEquals(Utilities.EMPTY_STRING_ARRAY, result)
    }
}
