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
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.LongSparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.Utilities.EMPTY_PERSON_ARRAY
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_UI_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORED_ICON
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORE_STARTED
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetInflater
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WorkspaceItemProcessorTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @Mock private lateinit var mockIconRequestInfo: IconRequestInfo<WorkspaceItemInfo>
    @Mock private lateinit var mockWorkspaceInfo: WorkspaceItemInfo
    @Mock private lateinit var mockBgDataModel: BgDataModel
    @Mock private lateinit var mockPmHelper: PackageManagerHelper
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private lateinit var mockCursor: LoaderCursor
    @Mock private lateinit var mockUserCache: UserCache
    @Mock private lateinit var mockUserManagerState: UserManagerState
    @Mock private lateinit var mockWidgetInflater: WidgetInflater
    @Mock private lateinit var mockIconCache: IconCache

    lateinit var mModelHelper: LauncherModelHelper
    lateinit var mContext: SandboxModelContext
    lateinit var mLauncherApps: LauncherApps
    private var mIntent: Intent = Intent()
    private var mUserHandle: UserHandle = Process.myUserHandle()
    private var mIconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mutableListOf()
    private var mComponentName: ComponentName = ComponentName("package", "class")
    private var mUnlockedUsersArray: LongSparseArray<Boolean> = LongSparseArray()
    private var mKeyToPinnedShortcutsMap: MutableMap<ShortcutKey, ShortcutInfo> = mutableMapOf()
    private var mInstallingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = hashMapOf()
    private var mAllDeepShortcuts: MutableList<CacheableShortcutInfo> = mutableListOf()
    private var mWidgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> =
        mutableMapOf()
    private var mPendingPackages: MutableSet<PackageUserKey> = mutableSetOf()

    private lateinit var itemProcessorUnderTest: WorkspaceItemProcessor

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mModelHelper = LauncherModelHelper()
        mContext = mModelHelper.sandboxContext
        mLauncherApps =
            mContext.spyService(LauncherApps::class.java).apply {
                doReturn(true).whenever(this).isPackageEnabled("package", mUserHandle)
                doReturn(true).whenever(this).isActivityEnabled(mComponentName, mUserHandle)
            }
        mUserHandle = Process.myUserHandle()
        mComponentName = ComponentName("package", "class")
        mUnlockedUsersArray = LongSparseArray<Boolean>(1).apply { put(101, true) }
        mIntent =
            Intent().apply {
                component = mComponentName
                `package` = "pkg"
                putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "")
            }
        whenever(mockIconCache.getShortcutIcon(any(), any(), any())).then {}
        whenever(mockPmHelper.getAppLaunchIntent(mComponentName.packageName, mUserHandle))
            .thenReturn(mIntent)
        mockCursor.apply {
            user = mUserHandle
            itemType = ITEM_TYPE_APPLICATION
            id = 1
            restoreFlag = 1
            serialNumber = 101
            whenever(parseIntent()).thenReturn(mIntent)
            whenever(markRestored()).doAnswer { restoreFlag = 0 }
            whenever(updater().put(Favorites.INTENT, mIntent.toUri(0)).commit()).thenReturn(1)
            whenever(getAppShortcutInfo(any(), any(), any(), any())).thenReturn(mockWorkspaceInfo)
            whenever(createIconRequestInfo(any(), any())).thenReturn(mockIconRequestInfo)
        }
        mockUserCache.apply {
            val userIconInfo = mock<UserIconInfo>().apply { whenever(isPrivate).thenReturn(false) }
            whenever(getUserInfo(any())).thenReturn(userIconInfo)
        }

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
        userCache: UserCache = mockUserCache,
        userManagerState: UserManagerState = mockUserManagerState,
        launcherApps: LauncherApps = mLauncherApps,
        shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo> = mKeyToPinnedShortcutsMap,
        bgDataModel: BgDataModel = mockBgDataModel,
        widgetProvidersMap: MutableMap<ComponentKey, AppWidgetProviderInfo?> = mWidgetProvidersMap,
        widgetInflater: WidgetInflater = mockWidgetInflater,
        pmHelper: PackageManagerHelper = mockPmHelper,
        iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>> = mIconRequestInfos,
        isSdCardReady: Boolean = false,
        pendingPackages: MutableSet<PackageUserKey> = mPendingPackages,
        unlockedUsers: LongSparseArray<Boolean> = mUnlockedUsersArray,
        installingPkgs: HashMap<PackageUserKey, PackageInstaller.SessionInfo> = mInstallingPkgs,
        allDeepShortcuts: MutableList<CacheableShortcutInfo> = mAllDeepShortcuts,
    ) =
        WorkspaceItemProcessor(
            c = cursor,
            memoryLogger = memoryLogger,
            userCache = userCache,
            userManagerState = userManagerState,
            launcherApps = launcherApps,
            context = mContext,
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
            allDeepShortcuts = allDeepShortcuts,
            iconCache = mockIconCache,
            idp = InvariantDeviceProfile.INSTANCE.get(mContext),
            isSafeMode = false,
        )

    @Test
    fun `When user is null then mark item deleted`() {
        // Given
        mockCursor = mock<LoaderCursor>().apply { id = 1 }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted("User has been deleted for item id=1", RestoreError.PROFILE_DELETED)
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
        verify(mockCursor)
            .markDeleted("Null intent from db for item id=1", RestoreError.APP_NO_DB_INTENT)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has null target package then mark deleted`() {

        // Given
        mIntent.apply {
            component = null
            `package` = null
        }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted("No target package for item id=1", RestoreError.APP_NO_TARGET_PACKAGE)
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When app has empty String target package then mark deleted`() {

        // Given
        mComponentName = ComponentName("", "")
        mIntent.component = mComponentName
        mIntent.`package` = ""

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted("No target package for item id=1", RestoreError.APP_NO_TARGET_PACKAGE)
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
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When fallback Activity found for app then mark restored`() {

        // Given
        mLauncherApps.apply {
            whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
            whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
        }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(mIntent)
            }

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor.updater().put(Favorites.INTENT, mIntent.toUri(0))).commit()
        assertThat(mIconRequestInfos).containsExactly(mockIconRequestInfo)
        verify(mockCursor).checkAndAddItem(mockWorkspaceInfo, mockBgDataModel, null)
    }

    @Test
    fun `When app with disabled activity and no fallback found then mark deleted`() {

        // Given
        mLauncherApps.apply {
            whenever(isPackageEnabled("package", mUserHandle)).thenReturn(true)
            whenever(isActivityEnabled(mComponentName, mUserHandle)).thenReturn(false)
        }
        mockPmHelper =
            mock<PackageManagerHelper>().apply {
                whenever(getAppLaunchIntent(mComponentName.packageName, mUserHandle))
                    .thenReturn(null)
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
                RestoreError.APP_NO_LAUNCH_INTENT,
            )
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When valid Pinned Deep Shortcut then mark restored`() {
        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        val expectedShortcutInfo =
            mock<ShortcutInfo>().apply {
                whenever(userHandle).thenReturn(mUserHandle)
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
            }
        val shortcutKey = ShortcutKey.fromIntent(mIntent, mockCursor.user)
        mKeyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts.size).isEqualTo(1)
        assertThat(mAllDeepShortcuts[0].shortcutInfo).isEqualTo(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When Archived Deep Shortcut with flag on then mark restored`() {
        // Given
        val mockContentWriter: ContentWriter = mock()
        val mockAppInfo: ApplicationInfo =
            mock<ApplicationInfo>().apply {
                isArchived = true
                enabled = true
            }
        val expectedRestoreFlag = FLAG_RESTORED_ICON or FLAG_RESTORE_STARTED
        doReturn(mockAppInfo).whenever(mLauncherApps).getApplicationInfo(any(), any(), any())
        whenever(mockContentWriter.put(Favorites.RESTORED, expectedRestoreFlag))
            .thenReturn(mockContentWriter)
        whenever(mockContentWriter.commit()).thenReturn(1)
        mockCursor.apply {
            itemType = ITEM_TYPE_DEEP_SHORTCUT
            restoreFlag = restoreFlag or FLAG_RESTORED_ICON
            whenever(updater()).thenReturn(mockContentWriter)
        }
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertThat(mockCursor.restoreFlag and FLAG_RESTORED_ICON).isEqualTo(FLAG_RESTORED_ICON)
        assertThat(mockCursor.restoreFlag and FLAG_RESTORE_STARTED).isEqualTo(FLAG_RESTORE_STARTED)
        assertThat(mIconRequestInfos).isNotEmpty()
        assertThat(mAllDeepShortcuts).isEmpty()
        verify(mockContentWriter).put(Favorites.RESTORED, expectedRestoreFlag)
        verify(mockCursor).checkAndAddItem(any(), eq(mockBgDataModel), eq(null))
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    fun `When Archived Deep Shortcut with flag off then remove`() {
        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts).isEmpty()
        verify(mockCursor)
            .markDeleted(
                "Pinned shortcut not found from request. package=pkg, user=$mUserHandle",
                "shortcut_not_found",
            )
    }

    @Test
    fun `When Pinned Deep Shortcut is not stored in ShortcutManager re-query by Shortcut ID`() {
        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        val si =
            mock<ShortcutInfo>().apply {
                whenever(id).thenReturn("")
                whenever(`package`).thenReturn("")
                whenever(activity).thenReturn(mock())
                whenever(longLabel).thenReturn("")
                whenever(isEnabled).thenReturn(true)
                whenever(disabledMessage).thenReturn("")
                whenever(disabledReason).thenReturn(0)
                whenever(persons).thenReturn(EMPTY_PERSON_ARRAY)
                whenever(userHandle).thenReturn(mUserHandle)
            }
        doReturn(listOf(si)).whenever(mLauncherApps).getShortcuts(any(), any())
        mKeyToPinnedShortcutsMap.clear()
        mIconRequestInfos = mutableListOf()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        verify(mLauncherApps).getShortcuts(any(), any())
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), eq(null))
    }

    @Test
    fun `When Pinned Deep Shortcut not found then mark deleted`() {

        // Given
        mockCursor.itemType = ITEM_TYPE_DEEP_SHORTCUT
        mIconRequestInfos = mutableListOf()
        mKeyToPinnedShortcutsMap = hashMapOf()

        // When
        itemProcessorUnderTest = createWorkspaceItemProcessorUnderTest()
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        verify(mockCursor, times(0)).checkAndAddItem(any(), any(), anyOrNull())
        verify(mockCursor)
            .markDeleted(
                "Pinned shortcut not found from request. package=pkg, user=$mUserHandle",
                "shortcut_not_found",
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
                whenever(userHandle).thenReturn(mUserHandle)
            }
        mIconRequestInfos = mutableListOf()
        // Make sure shortcuts map has expected key from expected package
        mIntent.`package` = mComponentName.packageName
        val shortcutKey = ShortcutKey.fromIntent(mIntent, mockCursor.user)
        mKeyToPinnedShortcutsMap[shortcutKey] = expectedShortcutInfo
        // set intent package back to null to test scenario
        mIntent.`package` = null

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(allDeepShortcuts = mAllDeepShortcuts)
        itemProcessorUnderTest.processItem()

        // Then
        assertWithMessage("item restoreFlag should be set to 0")
            .that(mockCursor.restoreFlag)
            .isEqualTo(0)
        assertThat(mIconRequestInfos).isEmpty()
        assertThat(mAllDeepShortcuts.size).isEqualTo(1)
        assertThat(mAllDeepShortcuts[0].shortcutInfo).isEqualTo(expectedShortcutInfo)
        verify(mockCursor).markRestored()
        verify(mockCursor).checkAndAddItem(any(), any(), anyOrNull())
    }

    @Test
    fun `When processing Folder then create FolderInfo and mark restored`() {
        val actualFolderInfo = FolderInfo()
        mockBgDataModel = mock<BgDataModel>()
        mockCursor =
            mock<LoaderCursor>().apply {
                user = mUserHandle
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
                whenever(findOrMakeFolder(eq(1), any())).thenReturn(actualFolderInfo)
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
    fun `When valid TYPE_REAL App Widget then add item`() {

        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        val expectedComponentName =
            ComponentName.unflattenFromString(expectedProvider)!!.flattenToString()
        val expectedRestoreStatus = FLAG_UI_NOT_READY
        val expectedAppWidgetId = 0
        mockCursor.apply {
            itemType = ITEM_TYPE_APPWIDGET
            user = mUserHandle
            restoreFlag = FLAG_UI_NOT_READY
            container = CONTAINER_DESKTOP
            whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
            whenever(appWidgetProvider).thenReturn(expectedProvider)
            whenever(appWidgetId).thenReturn(expectedAppWidgetId)
            whenever(spanX).thenReturn(2)
            whenever(spanY).thenReturn(1)
            whenever(options).thenReturn(0)
            whenever(appWidgetSource).thenReturn(20)
            whenever(applyCommonProperties(any())).thenCallRealMethod()
            whenever(
                    updater()
                        .put(Favorites.APPWIDGET_PROVIDER, expectedComponentName)
                        .put(Favorites.APPWIDGET_ID, expectedAppWidgetId)
                        .put(Favorites.RESTORED, expectedRestoreStatus)
                        .commit()
                )
                .thenReturn(1)
        }
        val expectedWidgetInfo =
            LauncherAppWidgetInfo().apply {
                appWidgetId = expectedAppWidgetId
                providerName = ComponentName.unflattenFromString(expectedProvider)
                restoreStatus = expectedRestoreStatus
            }
        val expectedWidgetProviderInfo =
            mock<LauncherAppWidgetProviderInfo>().apply {
                provider = ComponentName.unflattenFromString(expectedProvider)
                whenever(user).thenReturn(mUserHandle)
            }
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_REAL,
                widgetInfo = expectedWidgetProviderInfo,
            )
        mockWidgetInflater =
            mock<WidgetInflater>().apply {
                whenever(inflateAppWidget(any())).thenReturn(inflationResult)
            }
        val packageUserKey = PackageUserKey("com.google.android.testApp", mUserHandle)
        mInstallingPkgs[packageUserKey] = PackageInstaller.SessionInfo()

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)
        itemProcessorUnderTest.processItem()

        // Then
        val widgetInfoCaptor = ArgumentCaptor.forClass(LauncherAppWidgetInfo::class.java)
        verify(mockCursor)
            .checkAndAddItem(widgetInfoCaptor.capture(), eq(mockBgDataModel), anyOrNull())
        val actualWidgetInfo = widgetInfoCaptor.value
        with(actualWidgetInfo) {
            assertThat(providerName).isEqualTo(expectedWidgetInfo.providerName)
            assertThat(restoreStatus).isEqualTo(expectedWidgetInfo.restoreStatus)
            assertThat(targetComponent).isEqualTo(expectedWidgetInfo.targetComponent)
            assertThat(appWidgetId).isEqualTo(expectedWidgetInfo.appWidgetId)
        }
        val expectedComponentKey =
            ComponentKey(expectedWidgetProviderInfo.provider, expectedWidgetProviderInfo.user)
        assertThat(mWidgetProvidersMap[expectedComponentKey]).isEqualTo(expectedWidgetProviderInfo)
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
                widgetInfo = mockProviderInfo,
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
        verify(mockCursor).checkAndAddItem(any(), eq(mockBgDataModel), eq(null))
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
        val expectedComponentName = ComponentName.unflattenFromString(expectedProvider)

        // When
        itemProcessorUnderTest =
            createWorkspaceItemProcessorUnderTest(widgetProvidersMap = mWidgetProvidersMap)
        itemProcessorUnderTest.processItem()

        // Then
        verify(mockCursor)
            .markDeleted(
                "processWidget: Unrestored Pending widget removed: id=1, appWidgetId=0, component=$expectedComponentName, restoreFlag:=4",
                RestoreError.UNRESTORED_PENDING_WIDGET,
            )
    }

    @Test
    fun `When widget inflation result is TYPE_DELETE then mark deleted`() {
        // Given
        val expectedProvider = "com.google.android.testApp/com.android.testApp.testAppProvider"
        mockCursor =
            mock<LoaderCursor>().apply {
                itemType = ITEM_TYPE_APPWIDGET
                id = 1
                user = UserHandle(1)
                container = CONTAINER_DESKTOP
                whenever(spanX).thenReturn(2)
                whenever(spanY).thenReturn(1)
                whenever(appWidgetProvider).thenReturn(expectedProvider)
                whenever(isOnWorkspaceOrHotseat).thenCallRealMethod()
                whenever(applyCommonProperties(any())).thenCallRealMethod()
            }
        mInstallingPkgs = hashMapOf()
        val inflationResult =
            WidgetInflater.InflationResult(
                type = WidgetInflater.TYPE_DELETE,
                widgetInfo = null,
                reason = "test_delete_reason",
                restoreErrorType = RestoreError.MISSING_WIDGET_PROVIDER,
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
        verify(mockCursor).markDeleted(inflationResult.reason, inflationResult.restoreErrorType)
    }
}
