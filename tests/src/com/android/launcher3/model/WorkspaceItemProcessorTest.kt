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
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.EMPTY_PERSON_ARRAY
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.MISSING_INFO
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.PROFILE_DELETED
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_UI_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
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
    @Mock private lateinit var mockIntent: Intent
    @Mock private lateinit var mockPmHelper: PackageManagerHelper
    @Mock private lateinit var mockLauncherApps: LauncherApps
    @Mock private lateinit var mockCursor: LoaderCursor
    @Mock private lateinit var mockUserManagerState: UserManagerState
    @Mock private lateinit var mockWidgetInflater: WidgetInflater

    private var mUserHandle: UserHandle = UserHandle(0)
    private var mIconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mutableListOf()
    private var mComponentName: ComponentName = ComponentName("package", "class")
    private var mUnlockedUsersArray: LongSparseArray<Boolean> = LongSparseArray()
    private var mKeyToPinnedShortcutsMap: MutableMap<ShortcutKey, ShortcutInfo> = mutableMapOf()
    private var mInstallingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = hashMapOf()
    private var mAllDeepShortcuts: MutableList<ShortcutInfo> = mutableListOf()
    private var mWidgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> =
        mutableMapOf()
    private var mPendingPackages: MutableSet<PackageUserKey> = mutableSetOf()

    private lateinit var itemProcessorUnderTest: WorkspaceItemProcessor

    @Before
    fun setup() {
        mUserHandle = UserHandle(0)
        mockIconRequestInfo = mock<IconRequestInfo<WorkspaceItemInfo>>()
        mockWorkspaceInfo = mock<WorkspaceItemInfo>()
        mockBgDataModel = mock<BgDataModel>()
        mComponentName = ComponentName("package", "class")
        mUnlockedUsersArray = LongSparseArray<Boolean>(1).apply { put(101, true) }
        mockIntent =
            mock<Intent>().apply {
                whenever(component).thenReturn(mComponentName)
                whenever(`package`).thenReturn("pkg")
                whenever(getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID)).thenReturn("")
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
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(mockIntent)
            }
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
                whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(true)
            }
        mockCursor =
            mock(LoaderCursor::class.java, RETURNS_DEEP_STUBS).apply {
                user = mUserHandle
                itemType = ITEM_TYPE_APPLICATION
                id = 1
                restoreFlag = 1
                serialNumber = 101
                whenever(parseIntent()).thenReturn(mockIntent)
                whenever(markRestored()).doAnswer { restoreFlag = 0 }
                whenever(updater().put(Favorites.INTENT, mockIntent.toUri(0)).commit())
                    .thenReturn(1)
                whenever(getAppShortcutInfo(any(), any(), any(), any()))
                    .thenReturn(mockWorkspaceInfo)
                whenever(createIconRequestInfo(any(), any())).thenReturn(mockIconRequestInfo)
            }
        mockUserManagerState = mock<UserManagerState>()
        mockWidgetInflater = mock<WidgetInflater>()
        mKeyToPinnedShortcutsMap = mutableMapOf()
        mInstallingPkgs = hashMapOf()
        mAllDeepShortcuts = mutableListOf()
        mWidgetProvidersMap = mutableMapOf()
        mIconRequestInfos = mutableListOf()
        mPendingPackages = mutableSetOf()
    }

    /**
     * Helper to create WorkspaceItemProcessor with defaults. WorkspaceItemProcessor has a lot of
     * dependencies, so this method can be used to inject concrete arguments while keeping the rest
     * as mocks/defaults, or to recreate it after modifying the default vars.
     */
    private fun createWorkspaceItemProcessorUnderTest(
        cursor: LoaderCursor = mockCursor,
        memoryLogger: LoaderMemoryLogger? = null,
        userManagerState: UserManagerState = mockUserManagerState,
        launcherApps: LauncherApps = mockLauncherApps,
        shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo> = mKeyToPinnedShortcutsMap,
        app: LauncherAppState = mockAppState,
        bgDataModel: BgDataModel = mockBgDataModel,
        widgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> = mWidgetProvidersMap,
        widgetInflater: WidgetInflater = mockWidgetInflater,
        pmHelper: PackageManagerHelper = mockPmHelper,
        iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mIconRequestInfos,
        isSdCardReady: Boolean = false,
        pendingPackages: MutableSet<PackageUserKey> = mPendingPackages,
        unlockedUsers: LongSparseArray<Boolean> = mUnlockedUsersArray,
        installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = mInstallingPkgs,
        allDeepShortcuts: MutableList<ShortcutInfo> = mAllDeepShortcuts
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

    @Test
    fun `When user is null then mark item deleted`() {
        // Given
        mockCursor = mock<LoaderCursor>().apply { id = 1 }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        // When
        itemProcessorUnderTest.processItem()
        // Then
        verify(mockCursor).markDeleted("User has been deleted for item id=1", PROFILE_DELETED)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null intent then mark deleted`() {
        // Given
        mockCursor.apply { whenever(parseIntent()).thenReturn(null) }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        // When
        itemProcessorUnderTest.processItem()
        // Then
        verify(mockCursor).markDeleted("Null intent from db for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null target package then mark deleted`() {

        // Given
        mockIntent.apply {
            whenever(component).thenReturn(null)
            whenever(`package`).thenReturn(null)
        }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has empty String target package then mark deleted`() {

        // Given
        mComponentName = ComponentName("", "")
        whenever(mockIntent.component).thenReturn(mComponentName)
        whenever(mockCursor.parseIntent()).thenReturn(mockIntent)
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).markDeleted("No target package for item id=1", MISSING_INFO)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid app then mark restored`() {

        // Given
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        // currently gets marked restored twice, although markRestore() has check for restoreFlag
        verify(mockCursor, times(2)).markRestored()
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When fallback Activity found for app then mark restored`() {

        // Given
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
                whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
            }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(mockIntent)
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor.updater().put(Favorites.INTENT, mockIntent.toUri(0))).commit()
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When app with disabled activity and no fallback found then mark deleted`() {

        // Given
        mockLauncherApps =
            mock<LauncherApps>().apply {
                whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
                whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
            }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(null)
            }
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
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
        val shortcutKey = ShortcutKey.fromIntent(mockIntent, mockCursor.user)
        mKeyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts).containsExactly(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When Pinned Deep Shortcut not found then mark deleted`() {

        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
        verify(mockCursor)
            .markDeleted(
                "Pinned shortcut not found from request. package=pkg, user=UserHandle{0}",
                "shortcut_not_found"
            )
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

    @Test
    fun `When valid Widget then checkAndAddItem`() {

        // Given
        mockCursor =
            mock<LoaderCursor>().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 1
                user = UserHandle(1)
                restoreFlag = FLAG_DIRECT_CONFIG
                container = CONTAINER_DESKTOP
                whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
                whenever(appWidgetProvider)
                    .thenReturn("com.google.android.testApp/com.android.testApp.testAppProvider")
                whenever(appWidgetId).thenReturn(0)
                whenever(spanX).thenReturn(2)
                whenever(spanY).thenReturn(1)
                whenever(options).thenReturn(0)
                whenever(appWidgetSource).thenReturn(20)
                whenever(applyCommonProperties(any())).thenCallRealMethod()
            }
        val mockProviderInfo =
            mock<LauncherAppWidgetProviderInfo>().apply {
                provider = mock()
                whenever(user).thenReturn(UserHandle(1))
            }
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_REAL,
                widgetInfo = mockProviderInfo
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)

        // When
        itemProcessorUnderTest.processItem()

        // Then
        assertThat(
                mWidgetProvidersMap[ComponentKey(mockProviderInfo.provider, mockProviderInfo.user)]
            )
            .isEqualTo(inflationResult.widgetInfo)
        verify(mockCursor).checkAndAddItem(any(), any())
    }

    @Test
    fun `When valid Pending Widget then checkAndAddItem`() {

        // Given
        mockCursor =
            mock<LoaderCursor>().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 1
                user = UserHandle(1)
                restoreFlag = FLAG_UI_NOT_READY
                container = CONTAINER_DESKTOP
                whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
                whenever(appWidgetProvider)
                    .thenReturn("com.google.android.testApp/com.android.testApp.testAppProvider")
                whenever(appWidgetId).thenReturn(0)
                whenever(spanX).thenReturn(2)
                whenever(spanY).thenReturn(1)
                whenever(options).thenReturn(0)
                whenever(appWidgetSource).thenReturn(20)
                whenever(applyCommonProperties(any())).thenCallRealMethod()
            }
        val mockProviderInfo =
            mock<LauncherAppWidgetProviderInfo>().apply {
                provider = mock()
                whenever(user).thenReturn(UserHandle(1))
            }
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_PENDING,
                widgetInfo = mockProviderInfo
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor).checkAndAddItem(any(), any())
    }

    @Test
    fun `When Unrestored Pending App Widget then mark deleted`() {

        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        mockCursor =
            mock<LoaderCursor>().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 1
                user = UserHandle(1)
                restoreFlag = FLAG_UI_NOT_READY
                container = CONTAINER_DESKTOP
                whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
                whenever(appWidgetProvider).thenReturn(expectedProvider)
                whenever(appWidgetId).thenReturn(0)
                whenever(spanX).thenReturn(2)
                whenever(spanY).thenReturn(1)
                whenever(options).thenReturn(0)
                whenever(appWidgetSource).thenReturn(20)
                whenever(applyCommonProperties(any())).thenCallRealMethod()
            }
        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(type = WidgetInflater.TYPE_PENDING, widgetInfo = null)
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)
        val expectedComponentName = ComponentName.unflattenFromString(expectedProvider)

        // When
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted(
                "Unrestored widget removed: $expectedComponentName",
                LauncherRestoreEventLogger.RestoreError.APP_NOT_INSTALLED
            )
    }

    @Test
    fun `When Archived Pending App Widget then checkAndAddItem`() {

        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        val expectedComponentName = ComponentName.unflattenFromString(expectedProvider)
        val expectedPackage = expectedComponentName!!.packageName
        val mockitoSession =
            ExtendedMockito.mockitoSession().mockStatic(Utilities::class.java).startMocking()
        whenever(Utilities.enableSupportForArchiving()).thenReturn(true)
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(isAppArchived(expectedPackage)).thenReturn(true)
            }
        mockCursor =
            mock<LoaderCursor>().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 1
                user = UserHandle(1)
                restoreFlag = FLAG_UI_NOT_READY
                container = CONTAINER_DESKTOP
                whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
                whenever(appWidgetProvider).thenReturn(expectedProvider)
                whenever(appWidgetId).thenReturn(0)
                whenever(spanX).thenReturn(2)
                whenever(spanY).thenReturn(1)
                whenever(options).thenReturn(0)
                whenever(appWidgetSource).thenReturn(20)
                whenever(applyCommonProperties(any())).thenCallRealMethod()
            }
        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(type = WidgetInflater.TYPE_PENDING, widgetInfo = null)
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)

        // When
        itemProcessorUnderTest.processItem()

        // Then
        mockitoSession.finishMocking()
        verify(mockCursor).checkAndAddItem(any(), any())
    }
}
