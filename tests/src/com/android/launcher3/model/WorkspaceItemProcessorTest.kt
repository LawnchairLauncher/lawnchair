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
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.LongSparseArray
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.Utilities.EMPTY_PERSON_ARRAY
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.MISSING_INFO
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.PROFILE_DELETED
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.widget.WidgetInflater
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WorkspaceItemProcessorTest {

    @Mock private lateinit var mockIconRequestInfo: IconRequestInfo<WorkspaceItemInfo>
    @Mock private lateinit var mockWorkspaceInfo: WorkspaceItemInfo
    @Mock private lateinit var mockBgDataModel: BgDataModel
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockAppState: LauncherAppState
    @Mock private lateinit var mockPmHelper: PackageManagerHelper
    @Mock private lateinit var mockLauncherApps: LauncherApps
    @Mock private lateinit var mockCursor: LoaderCursor
    @Mock private lateinit var mockUserCache: UserCache
    @Mock private lateinit var mockUserManagerState: UserManagerState
    @Mock private lateinit var mockWidgetInflater: WidgetInflater

    private lateinit var intent: Intent
    private lateinit var userHandle: UserHandle
    private lateinit var iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>>
    private lateinit var componentName: ComponentName
    private lateinit var unlockedUsersArray: LongSparseArray<Boolean>
    private lateinit var keyToPinnedShortcutsMap: MutableMap<ShortcutKey, ShortcutInfo>
    private lateinit var installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo>
    private lateinit var allDeepShortcuts: MutableList<ShortcutInfo>

    private lateinit var itemProcessorUnderTest: WorkspaceItemProcessor

    @Before
    fun setup() {
        userHandle = UserHandle(0)
        mockIconRequestInfo = mock<IconRequestInfo<WorkspaceItemInfo>>()
        iconRequestInfos = mutableListOf(mockIconRequestInfo)
        mockWorkspaceInfo = mock<WorkspaceItemInfo>()
        mockBgDataModel = mock<BgDataModel>()
        componentName = ComponentName("package", "class")
        unlockedUsersArray = LongSparseArray<Boolean>(1).apply { put(101, true) }
        intent =
            Intent().apply {
                component = componentName
                `package` = "pkg"
                putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "")
            }
        mockContext =
            mock<Context>().apply {
                whenever(packageManager).thenReturn(mock())
                whenever(packageManager.getUserBadgedLabel(any(), any())).thenReturn("")
            }
        mockAppState =
            mock<LauncherAppState>().apply {
                whenever(context).thenReturn(mockContext)
                whenever(iconCache).thenReturn(mock())
                whenever(iconCache.getShortcutIcon(any(), any(), any())).then {}
            }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(componentName.packageName, userHandle))
                    .thenReturn(intent)
            }
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(true)
            }
        mockCursor =
            mock(LoaderCursor::class.java, RETURNS_DEEP_STUBS).apply {
                user = userHandle
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                restoreFlag = 1
                serialNumber = 101
                whenever(parseIntent()).thenReturn(intent)
                whenever(markRestored()).doAnswer { restoreFlag = 0 }
                whenever(updater().put(Favorites.INTENT, intent.toUri(0)).commit()).thenReturn(1)
                whenever(getAppShortcutInfo(any(), any(), any(), any()))
                    .thenReturn(mockWorkspaceInfo)
                whenever(createIconRequestInfo(any(), any())).thenReturn(mockIconRequestInfo)
            }
        mockUserCache =
            mock<UserCache>().apply {
                val userIconInfo =
                    mock<UserIconInfo>().apply() { whenever(isPrivate).thenReturn(false) }
                whenever(getUserInfo(any())).thenReturn(userIconInfo)
            }

        mockUserManagerState = mock<UserManagerState>()
        mockWidgetInflater = mock<WidgetInflater>()
        keyToPinnedShortcutsMap = mutableMapOf()
        installingPkgs = hashMapOf()
        allDeepShortcuts = mutableListOf()
    }

    /**
     * Helper to create WorkspaceItemProcessor with defaults. WorkspaceItemProcessor has a lot of
     * dependencies, so this method can be used to inject concrete arguments while keeping the rest
     * as mocks/defaults, or to recreate it after modifying the default vars.
     */
    private fun createWorkspaceItemProcessorUnderTest(
        cursor: LoaderCursor = mockCursor,
        memoryLogger: LoaderMemoryLogger? = null,
        userCache: UserCache = mockUserCache,
        userManagerState: UserManagerState = mockUserManagerState,
        launcherApps: LauncherApps = mockLauncherApps,
        shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo> = keyToPinnedShortcutsMap,
        app: LauncherAppState = mockAppState,
        bgDataModel: BgDataModel = mockBgDataModel,
        widgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> = mutableMapOf(),
        widgetInflater: WidgetInflater = mockWidgetInflater,
        pmHelper: PackageManagerHelper = mockPmHelper,
        iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mutableListOf(),
        isSdCardReady: Boolean = false,
        pendingPackages: MutableSet<PackageUserKey> = mutableSetOf(),
        unlockedUsers: LongSparseArray<Boolean> = unlockedUsersArray,
        installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = hashMapOf(),
        allDeepShortcuts: MutableList<ShortcutInfo> = mutableListOf()
    ) =
        WorkspaceItemProcessor(
            c = cursor,
            memoryLogger = memoryLogger,
            userCache = userCache,
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

    @Test
    fun `When user is null then mark item deleted`() {
        // Given
        mockCursor = mock<LoaderCursor>().apply { id = 1 }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted("User has been deleted for item id=1", PROFILE_DELETED)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null intent then mark deleted`() {
        // Given
        mockCursor.apply { whenever(parseIntent()).thenReturn(null) }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()
        // Then
        verify(mockCursor).markDeleted("Null intent from db for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null target package then mark deleted`() {

        // Given
        intent.apply {
            component = null
            `package` = null
        }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has empty String target package then mark deleted`() {

        // Given
        componentName = ComponentName("", "")
        intent.component = componentName
        intent.`package` = ""

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid app then mark restored`() {

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        // currently gets marked restored twice, although markRestore() has check for restoreFlag
        verify(mockCursor, times(2)).markRestored()
        assertThat(iconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When fallback Activity found for app then mark restored`() {

        // Given
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(false)
            }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(componentName.packageName, userHandle))
                    .thenReturn(intent)
            }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor.updater().put(Favorites.INTENT, intent.toUri(0))).commit()
        assertThat(iconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When app with disabled activity and no fallback found then mark deleted`() {

        // Given
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", userHandle)).thenReturn(true)
                whenever(isActivityEnabled(componentName, userHandle)).thenReturn(false)
            }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(componentName.packageName, userHandle)).thenReturn(null)
            }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be unchanged")
            .that(mockCursor.restoreFlag)
            .isEqualTo(1)
        verify(mockCursor)
            .markDeleted(
                "No Activities found for id=1," +
                    " targetPkg=package," +
                    " component=ComponentInfo{package/class}." +
                    " Unable to create launch Intent.",
                MISSING_INFO
            )
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid Pinned Deep Shortcut then mark restored`() {

        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        val expectedShortcutInfo =
            mock<ShortcutInfo>().apply {
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
            }
        val shortcutKey = ShortcutKey.fromIntent(intent, mockCursor.user)
        keyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        iconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = allDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(iconRequestInfos).isEmpty()
        assertThat(allDeepShortcuts).containsExactly(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When Pinned Deep Shortcut not found then mark deleted`() {

        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        iconRequestInfos = mutableListOf()
        keyToPinnedShortcutsMap = hashMapOf()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(iconRequestInfos).isEmpty()
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
        verify(mockCursor)
            .markDeleted(
                "Pinned shortcut not found from request. package=pkg, user=UserHandle{0}",
                "shortcut_not_found"
            )
    }

    @Test
    fun `When valid Pinned Deep Shortcut with null intent package then use targetPkg`() {

        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        val expectedShortcutInfo =
            mock<ShortcutInfo>().apply {
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
            }
        iconRequestInfos = mutableListOf()
        // Make sure shortcuts map has expected key from expected package
        intent.`package` = componentName.packageName
        val shortcutKey = ShortcutKey.fromIntent(intent, mockCursor.user)
        keyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        // set intent package back to null to test scenario
        intent.`package` = null

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = allDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(iconRequestInfos).isEmpty()
        assertThat(allDeepShortcuts).containsExactly(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When processing Folder then create FolderInfo and mark restored`() {
        val actualFolderInfo = FolderInfo()
        mockBgDataModel =
            mock<BgDataModel>().apply { whenever(findOrMakeFolder(1)).thenReturn(actualFolderInfo) }
        mockCursor =
            mock<LoaderCursor>().apply {
                user = UserHandle(0)
                itemType = ITEM_TYPE_FOLDER
                id = 1
                container = 100
                restoreFlag = 1
                serialNumber = 101
                whenever(applyCommonProperties(any<ItemInfo>())).then {}
                whenever(markRestored()).doAnswer { restoreFlag = 0 }
                whenever(getColumnIndex(Favorites.TITLE)).thenReturn(4)
                whenever(getString(4)).thenReturn("title")
                whenever(options).thenReturn(5)
            }
        val expectedFolderInfo =
            FolderInfo().apply {
                itemType = ITEM_TYPE_FOLDER
                spanX = 1
                spanY = 1
                options = 5
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor).markRestored()
        assertThat(actualFolderInfo.id).isEqualTo(expectedFolderInfo.id)
        assertThat(actualFolderInfo.container).isEqualTo(expectedFolderInfo.container)
        assertThat(actualFolderInfo.itemType).isEqualTo(expectedFolderInfo.itemType)
        assertThat(actualFolderInfo.screenId).isEqualTo(expectedFolderInfo.screenId)
        assertThat(actualFolderInfo.cellX).isEqualTo(expectedFolderInfo.cellX)
        assertThat(actualFolderInfo.cellY).isEqualTo(expectedFolderInfo.cellY)
        assertThat(actualFolderInfo.spanX).isEqualTo(expectedFolderInfo.spanX)
        assertThat(actualFolderInfo.spanY).isEqualTo(expectedFolderInfo.spanY)
        assertThat(actualFolderInfo.options).isEqualTo(expectedFolderInfo.options)
        verify(mockCursor).checkAndAddItem(actualFolderInfo, mockBgDataModel, null)
    }
}
