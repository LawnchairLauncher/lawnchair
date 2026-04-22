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

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ItemInfoMatcher.ofShortcutKeys
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ItemInfoMatcherTest {

    @Test
    fun `ofUser returns Predicate for ItemInfo containing given UserHandle`() {
        val expectedItemInfo = ItemInfo().apply { user = UserHandle(11) }
        val unexpectedItemInfo = ItemInfo().apply { user = UserHandle(0) }
        val itemInfoStream = listOf(expectedItemInfo, unexpectedItemInfo).stream()

        val predicate = ItemInfoMatcher.ofUser(UserHandle(11))
        val actualResults = itemInfoStream.filter(predicate).toList()

        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `ofComponents returns Predicate for ItemInfo containing target Component and UserHandle`() {
        // Given
        val expectedUserHandle = UserHandle(0)
        val expectedComponentName = ComponentName("expectedPackage", "expectedClass")
        val expectedItemInfo = spy(ItemInfo())
        expectedItemInfo.user = expectedUserHandle
        whenever(expectedItemInfo.targetComponent).thenReturn(expectedComponentName)

        val unexpectedComponentName = ComponentName("unexpectedPackage", "unexpectedClass")
        val unexpectedItemInfo1 = spy(ItemInfo())
        unexpectedItemInfo1.user = expectedUserHandle
        whenever(unexpectedItemInfo1.targetComponent).thenReturn(unexpectedComponentName)

        val unexpectedItemInfo2 = spy(ItemInfo())
        unexpectedItemInfo2.user = UserHandle(10)
        whenever(unexpectedItemInfo2.targetComponent).thenReturn(expectedComponentName)

        val itemInfoStream =
            listOf(expectedItemInfo, unexpectedItemInfo1, unexpectedItemInfo2).stream()

        // When
        val predicate =
            ItemInfoMatcher.ofComponents(hashSetOf(expectedComponentName), expectedUserHandle)
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `ofPackages returns Predicate for ItemInfo containing UserHandle and target package`() {
        // Given
        val expectedUserHandle = UserHandle(0)
        val expectedPackage = "expectedPackage"
        val expectedComponentName = ComponentName(expectedPackage, "expectedClass")
        val expectedItemInfo = spy(ItemInfo())
        expectedItemInfo.user = expectedUserHandle
        whenever(expectedItemInfo.targetComponent).thenReturn(expectedComponentName)

        val unexpectedPackage = "unexpectedPackage"
        val unexpectedComponentName = ComponentName(unexpectedPackage, "unexpectedClass")
        val unexpectedItemInfo1 = spy(ItemInfo())
        unexpectedItemInfo1.user = expectedUserHandle
        whenever(unexpectedItemInfo1.targetComponent).thenReturn(unexpectedComponentName)

        val unexpectedItemInfo2 = spy(ItemInfo())
        unexpectedItemInfo2.user = UserHandle(10)
        whenever(unexpectedItemInfo2.targetComponent).thenReturn(expectedComponentName)

        val itemInfoStream =
            listOf(expectedItemInfo, unexpectedItemInfo1, unexpectedItemInfo2).stream()

        // When
        val predicate = ItemInfoMatcher.ofPackages(setOf(expectedPackage), expectedUserHandle)
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `ofShortcutKeys returns Predicate for Deep Shortcut Info containing given ShortcutKey`() {
        // Given
        val expectedItemInfo = spy(ItemInfo())
        expectedItemInfo.itemType = ITEM_TYPE_DEEP_SHORTCUT
        val expectedIntent =
            Intent().apply {
                putExtra("shortcut_id", "expectedShortcut")
                `package` = "expectedPackage"
            }
        whenever(expectedItemInfo.intent).thenReturn(expectedIntent)

        val unexpectedIntent =
            Intent().apply {
                putExtra("shortcut_id", "unexpectedShortcut")
                `package` = "unexpectedPackage"
            }
        val unexpectedItemInfo = spy(ItemInfo())
        unexpectedItemInfo.itemType = ITEM_TYPE_DEEP_SHORTCUT
        whenever(unexpectedItemInfo.intent).thenReturn(unexpectedIntent)

        val itemInfoStream = listOf(expectedItemInfo, unexpectedItemInfo).stream()
        val expectedShortcutKey = ShortcutKey.fromItemInfo(expectedItemInfo)

        // When
        val predicate = ItemInfoMatcher.ofShortcutKeys(setOf(expectedShortcutKey))
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `forFolderMatch returns Predicate to match against children within Folder ItemInfo`() {
        // Given
        val expectedItemInfo = spy(FolderInfo())
        expectedItemInfo.itemType = ITEM_TYPE_FOLDER
        val expectedIntent =
            Intent().apply {
                putExtra("shortcut_id", "expectedShortcut")
                `package` = "expectedPackage"
            }
        val expectedChildInfo = spy(ItemInfo())
        expectedChildInfo.itemType = ITEM_TYPE_DEEP_SHORTCUT
        whenever(expectedChildInfo.intent).thenReturn(expectedIntent)
        whenever(expectedItemInfo.getContents()).thenReturn(arrayListOf(expectedChildInfo))

        val unexpectedItemInfo = spy(FolderInfo())
        unexpectedItemInfo.itemType = ITEM_TYPE_FOLDER

        val itemInfoStream = listOf(expectedItemInfo, unexpectedItemInfo).stream()
        val expectedShortcutKey = ShortcutKey.fromItemInfo(expectedChildInfo)

        // When
        val predicate = ItemInfoMatcher.forFolderMatch(ofShortcutKeys(setOf(expectedShortcutKey)))
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `ofItemIds returns Predicate to match ItemInfo that contains given ids`() {
        // Given
        val expectedItemInfo = spy(ItemInfo())
        expectedItemInfo.id = 1

        val unexpectedItemInfo = spy(ItemInfo())
        unexpectedItemInfo.id = 2

        val itemInfoStream = listOf(expectedItemInfo, unexpectedItemInfo).stream()

        // When
        val expectedIds = IntSet().apply { add(1) }
        val predicate = ItemInfoMatcher.ofItemIds(expectedIds)
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }

    @Test
    fun `ofItems returns Predicate matching against provided ItemInfo`() {
        // Given
        val expectedItemInfo = spy(ItemInfo())
        expectedItemInfo.id = 1

        val unexpectedItemInfo = spy(ItemInfo())
        unexpectedItemInfo.id = 2

        val itemInfoStream = listOf(expectedItemInfo, unexpectedItemInfo).stream()

        // When
        val expectedItems = setOf(expectedItemInfo)
        val predicate = ItemInfoMatcher.ofItems(expectedItems)
        val actualResults = itemInfoStream.filter(predicate).toList()

        // Then
        assertThat(actualResults).containsExactly(expectedItemInfo)
    }
}
