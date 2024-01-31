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

package com.android.launcher3.model

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.LongSparseArray
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.MISSING_INFO
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.PROFILE_DELETED
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetInflater
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WorkspaceItemProcessorTest {
    private var itemProcessor = createTestWorkspaceItemProcessor()

    @Before
    fun setup() {
        itemProcessor = createTestWorkspaceItemProcessor()
    }

    @Test
    fun `When user is null then mark item deleted`() {
        // Given
        val mockCursor = mock<LoaderCursor>().apply { id = 1 }
        val itemProcessor = createTestWorkspaceItemProcessor(cursor = mockCursor)
        // When
        itemProcessor.processItem()
        // Then
        verify(mockCursor).markDeleted("User has been deleted for item id=1", PROFILE_DELETED)
    }

    @Test
    fun `When app has null intent then mark deleted`() {
        // Given
        val mockCursor =
            mock<LoaderCursor>().apply {
                user = UserHandle(0)
                id = 1
                itemType = ITEM_TYPE_APPLICATION
            }
        val itemProcessor = createTestWorkspaceItemProcessor(cursor = mockCursor)
        // When
        itemProcessor.processItem()
        // Then
        verify(mockCursor).markDeleted("Null intent for item id=1", MISSING_INFO)
    }

    @Test
    fun `When app has null target package then mark deleted`() {
        // Given
        val mockCursor =
            mock<LoaderCursor>().apply {
                user = UserHandle(0)
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                whenever(parseIntent()).thenReturn(Intent())
            }
        val itemProcessor = createTestWorkspaceItemProcessor(cursor = mockCursor)
        // When
        itemProcessor.processItem()
        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
    }

    @Test
    fun `When app has empty String target package then mark deleted`() {
        // Given
        val mockIntent =
            mock<Intent>().apply {
                whenever(component).thenReturn(null)
                whenever(`package`).thenReturn("")
            }
        val mockCursor =
            mock<LoaderCursor>().apply {
                user = UserHandle(0)
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                whenever(parseIntent()).thenReturn(mockIntent)
            }
        val itemProcessor = createTestWorkspaceItemProcessor(cursor = mockCursor)
        // When
        itemProcessor.processItem()
        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
    }

    @Test
    fun `When valid app then mark restored`() {
        // Given
        val userHandle = UserHandle(0)
        val componentName = ComponentName("package", "class")
        val mockIntent =
            mock<Intent>().apply {
                whenever(component).thenReturn(componentName)
                whenever(`package`).thenReturn("")
            }
        val mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(true)
            }
        val mockCursor =
            mock<LoaderCursor>().apply {
                user = userHandle
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                restoreFlag = 1
                whenever(parseIntent()).thenReturn(mockIntent)
                whenever(markRestored()).doAnswer { restoreFlag = 0 }
            }
        val itemProcessor =
            createTestWorkspaceItemProcessor(cursor = mockCursor, launcherApps = mockLauncherApps)
        // When
        itemProcessor.processItem()
        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        // currently gets marked restored twice, although markRestore() has check for restoreFlag
        verify(mockCursor, times(2)).markRestored()
    }

    @Test
    fun `When fallback Activity found for app then mark restored`() {
        // Given
        val userHandle = UserHandle(0)
        val componentName = ComponentName("package", "class")
        val mockIntent =
            mock<Intent>().apply {
                whenever(component).thenReturn(componentName)
                whenever(`package`).thenReturn("")
                whenever(toUri(0)).thenReturn("")
            }
        val mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(false)
            }
        val mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(componentName.packageName, userHandle))
                    .thenReturn(mockIntent)
            }
        val mockCursor =
            mock(LoaderCursor::class.java, RETURNS_DEEP_STUBS).apply {
                user = userHandle
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                restoreFlag = 1
                whenever(parseIntent()).thenReturn(mockIntent)
                whenever(markRestored()).doAnswer { restoreFlag = 0 }
                whenever(updater().put(Favorites.INTENT, mockIntent.toUri(0)).commit())
                    .thenReturn(1)
            }
        val itemProcessor =
            createTestWorkspaceItemProcessor(
                cursor = mockCursor,
                launcherApps = mockLauncherApps,
                pmHelper = mockPmHelper
            )
        // When
        itemProcessor.processItem()
        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor.updater().put(Favorites.INTENT, mockIntent.toUri(0))).commit()
    }

    @Test
    fun `When app with disabled activity and no fallback found then mark deleted`() {
        // Given
        val userHandle = UserHandle(0)
        val componentName = ComponentName("package", "class")
        val mockIntent =
            mock<Intent>().apply {
                whenever(component).thenReturn(componentName)
                whenever(`package`).thenReturn("")
            }
        val mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(false)
            }
        val mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(componentName.packageName, userHandle)).thenReturn(null)
            }
        val mockCursor =
            mock<LoaderCursor>().apply {
                user = userHandle
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                restoreFlag = 1
                whenever(parseIntent()).thenReturn(mockIntent)
            }
        val itemProcessor =
            createTestWorkspaceItemProcessor(
                cursor = mockCursor,
                launcherApps = mockLauncherApps,
                pmHelper = mockPmHelper
            )
        // When
        itemProcessor.processItem()
        // Then
        assertWithMessage("item restoreFlag should be unchanged")
            .that(mockCursor.restoreFlag)
            .isEqualTo(1)
        verify(mockCursor).markDeleted("Intent null, unable to find a launch target", MISSING_INFO)
    }

    /**
     * Helper to create WorkspaceItemProcessor with defaults. WorkspaceItemProcessor has a lot of
     * dependencies, so this method can be used to inject concrete arguments while keeping the rest
     * as mocks/defaults.
     */
    private fun createTestWorkspaceItemProcessor(
        cursor: LoaderCursor = mock(),
        memoryLogger: LoaderMemoryLogger? = null,
        userManagerState: UserManagerState = mock(),
        launcherApps: LauncherApps = mock(),
        shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo> = mapOf(),
        app: LauncherAppState = mock(),
        bgDataModel: BgDataModel = mock(),
        widgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> = mutableMapOf(),
        widgetInflater: WidgetInflater = mock(),
        pmHelper: PackageManagerHelper = mock(),
        iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mutableListOf(),
        isSdCardReady: Boolean = false,
        pendingPackages: MutableSet<PackageUserKey> = mutableSetOf(),
        unlockedUsers: LongSparseArray<Boolean> = LongSparseArray(),
        installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = hashMapOf(),
        allDeepShortcuts: MutableList<ShortcutInfo> = mutableListOf()
    ) =
        WorkspaceItemProcessor(
            c = cursor,
            memoryLogger = memoryLogger,
            userManagerState = userManagerState,
            launcherApps = launcherApps,
            app = app,
            bgDataModel = bgDataModel,
            widgetProvidersMap = widgetProvidersMap,
            widgetInflater = widgetInflater,
            pmHelper = pmHelper,
            unlockedUsers = unlockedUsers,
            iconRequestInfos = iconRequestInfos,
            pendingPackages = pendingPackages,
            isSdCardReady = isSdCardReady,
            shortcutKeyToPinnedShortcuts = shortcutKeyToPinnedShortcuts,
            installingPkgs = installingPkgs,
            allDeepShortcuts = allDeepShortcuts
        )
}
