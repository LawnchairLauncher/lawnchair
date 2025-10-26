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

package com.android.launcher3.model

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_INSTALLED
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.google.common.truth.Truth.assertThat
import java.util.function.Predicate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutsChangedTaskTest {
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var shortcutsChangedTask: ShortcutsChangedTask
    private lateinit var modelHelper: LauncherModelHelper
    private lateinit var context: SandboxModelContext
    private lateinit var launcherApps: LauncherApps
    private var shortcuts: List<ShortcutInfo> = emptyList()

    private val expectedPackage: String = "expected"
    private val expectedShortcutId: String = "shortcut_id"
    private val user: UserHandle = myUserHandle()
    private val mockTaskController: ModelTaskController = mock()
    private val mockAllApps: AllAppsList = mock()
    private val mockIconCache: IconCache = mock()

    private val expectedWai =
        WorkspaceItemInfo().apply {
            id = 1
            itemType = ITEM_TYPE_DEEP_SHORTCUT
            intent =
                Intent().apply {
                    `package` = expectedPackage
                    putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, expectedShortcutId)
                }
        }

    @Before
    fun setup() {
        modelHelper = LauncherModelHelper()
        modelHelper.loadModelSync()
        context = modelHelper.sandboxContext
        launcherApps = context.spyService(LauncherApps::class.java)
        whenever(mockTaskController.context).thenReturn(context)
        whenever(mockTaskController.iconCache).thenReturn(mockIconCache)
        whenever(mockIconCache.getShortcutIcon(eq(expectedWai), any<CacheableShortcutInfo>()))
            .then { _ -> { expectedWai.bitmap = BitmapInfo.LOW_RES_INFO } }
        shortcuts = emptyList()
        shortcutsChangedTask = ShortcutsChangedTask(expectedPackage, shortcuts, user, false)
    }

    @After
    fun teardown() {
        modelHelper.destroy()
    }

    @Test
    fun `When installed pinned shortcut is found then keep in workspace`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(true)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = false
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockIconCache).getShortcutIcon(eq(expectedWai), any<CacheableShortcutInfo>())
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWai))
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When installed unpinned shortcut is found with Flag off then remove from workspace`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(false)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = false
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockTaskController)
            .deleteAndBindComponentsRemoved(
                any<Predicate<ItemInfo?>>(),
                eq("removed because the shortcut is no longer available in shortcut service"),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When installed unpinned shortcut is found with Flag on then keep in workspace`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(false)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = false
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockIconCache).getShortcutIcon(eq(expectedWai), any<CacheableShortcutInfo>())
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWai))
    }

    @Test
    fun `When shortcut app is uninstalled then skip handling`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(true)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags and FLAG_INSTALLED.inv()
                    isArchived = false
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockTaskController, times(0)).deleteAndBindComponentsRemoved(any(), any())
        verify(mockTaskController, times(0)).bindUpdatedWorkspaceItems(any())
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When archived pinned shortcut is found with flag off then keep in workspace`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(true)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = true
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockIconCache).getShortcutIcon(eq(expectedWai), any<CacheableShortcutInfo>())
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWai))
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When archived unpinned shortcut is found with flag off then keep in workspace`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(true)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = true
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockIconCache).getShortcutIcon(eq(expectedWai), any<CacheableShortcutInfo>())
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWai))
    }

    @Test
    fun `When updateIdMap true then trigger deep shortcut binding`() {
        // Given
        val expectedShortcut =
            mock<ShortcutInfo>().apply {
                whenever(isEnabled).thenReturn(true)
                whenever(isDeclaredInManifest).thenReturn(true)
                whenever(activity).thenReturn(ComponentName(expectedPackage, "expectedClass"))
                whenever(id).thenReturn(expectedShortcutId)
                whenever(userHandle).thenReturn(user)
            }
        shortcuts = listOf(expectedShortcut)
        val expectedKey = ComponentKey(expectedShortcut.activity, expectedShortcut.userHandle)
        doReturn(ApplicationInfo())
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        shortcutsChangedTask =
            ShortcutsChangedTask(
                packageName = expectedPackage,
                shortcuts = shortcuts,
                user = user,
                shouldUpdateIdMap = true,
            )
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        assertThat(modelHelper.bgDataModel.deepShortcutMap).containsEntry(expectedKey, 1)
        verify(mockTaskController).bindDeepShortcuts(eq(modelHelper.bgDataModel))
    }

    @Test
    fun `When updateIdMap false then do not trigger deep shortcut binding`() {
        // Given
        val expectedShortcut =
            mock<ShortcutInfo>().apply {
                whenever(isEnabled).thenReturn(true)
                whenever(isDeclaredInManifest).thenReturn(true)
                whenever(activity).thenReturn(ComponentName(expectedPackage, "expectedClass"))
                whenever(id).thenReturn(expectedShortcutId)
                whenever(userHandle).thenReturn(user)
            }
        shortcuts = listOf(expectedShortcut)
        val expectedKey = ComponentKey(expectedShortcut.activity, expectedShortcut.userHandle)
        doReturn(ApplicationInfo())
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        shortcutsChangedTask =
            ShortcutsChangedTask(
                packageName = expectedPackage,
                shortcuts = shortcuts,
                user = user,
                shouldUpdateIdMap = false,
            )
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        assertThat(modelHelper.bgDataModel.deepShortcutMap).doesNotContainKey(expectedKey)
        verify(mockTaskController, times(0)).bindDeepShortcuts(eq(modelHelper.bgDataModel))
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When restoring archived shortcut with flag on then skip handling`() {
        // Given
        shortcuts =
            listOf(
                mock<ShortcutInfo>().apply {
                    whenever(isPinned).thenReturn(true)
                    whenever(id).thenReturn(expectedShortcutId)
                }
            )
        val items: IntSparseArrayMap<ItemInfo> = modelHelper.bgDataModel.itemsIdMap
        items.put(expectedWai.id, expectedWai)
        doReturn(
                ApplicationInfo().apply {
                    enabled = true
                    flags = flags or FLAG_INSTALLED
                    isArchived = true
                }
            )
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedPackage), any(), eq(user))
        doReturn(shortcuts).whenever(launcherApps).getShortcuts(any(), eq(user))
        // When
        shortcutsChangedTask.execute(mockTaskController, modelHelper.bgDataModel, mockAllApps)
        // Then
        verify(mockTaskController, times(0)).deleteAndBindComponentsRemoved(any(), any())
        verify(mockTaskController, times(0)).bindUpdatedWorkspaceItems(any())
    }
}
