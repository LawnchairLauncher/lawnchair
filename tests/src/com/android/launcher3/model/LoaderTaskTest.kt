package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.database.sqlite.SQLiteDatabase
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.LoaderTransaction
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.IS_FIRST_LOAD_AFTER_RESTORE
import com.android.launcher3.LauncherPrefs.Companion.RESTORE_DEVICE
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.IconCacheUpdateHandler
import com.android.launcher3.model.FirstScreenBroadcastHelper.Companion.DISABLE_INSTALLED_APPS_BROADCAST
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LooperIdleLock
import com.android.launcher3.util.ModelTestExtensions
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.launcher3.util.ui.TestViewHelpers
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import java.util.concurrent.CountDownLatch
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val INSERTION_STATEMENT_FILE = "databases/workspace_items.sql"

@SmallTest
@RunWith(AndroidJUnit4::class)
class LoaderTaskTest {
    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val context = spy(SandboxApplication().withModelDependency())
    @get:Rule val mockUsers = MockUsersRule(context)

    private val expectedBroadcastModel =
        FirstScreenBroadcastModel(
            installerPackage = "installerPackage",
            pendingCollectionItems = mutableSetOf("pendingCollectionItem"),
            pendingWidgetItems = mutableSetOf("pendingWidgetItem"),
            pendingHotseatItems = mutableSetOf("pendingHotseatItem"),
            pendingWorkspaceItems = mutableSetOf("pendingWorkspaceItem"),
            installedHotseatItems = mutableSetOf("installedHotseatItem"),
            installedWorkspaceItems = mutableSetOf("installedWorkspaceItem"),
            installedWidgets = linkedSetOf("installedFirstScreenWidget"),
        )

    @Mock private lateinit var bgAllAppsList: AllAppsList
    @Mock private lateinit var modelDelegate: ModelDelegate
    @Mock private lateinit var launcherModel: LauncherModel
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var modelDbController: ModelDbController
    @Mock private lateinit var broadcastHelper: FirstScreenBroadcastHelper

    @Mock private lateinit var launcherBinder: BaseLauncherBinder
    @Mock private lateinit var transaction: LoaderTransaction
    @Mock private lateinit var idleLock: LooperIdleLock
    @Mock private lateinit var iconCacheUpdateHandler: IconCacheUpdateHandler
    @Mock private lateinit var settingsCache: SettingsCache

    private val testComponent: TestComponent
        get() = context.appComponent as TestComponent

    private val bgDataModel: BgDataModel
        get() = testComponent.getDataModel()

    private val inMemoryDb: SQLiteDatabase by lazy {
        ModelTestExtensions.createInMemoryDb(context, INSERTION_STATEMENT_FILE)
    }

    @Before
    fun setup() {
        val allWidgetManager = context.spyService(AppWidgetManager::class.java)
        doReturn(TestViewHelpers.findWidgetProvider(false))
            .whenever(allWidgetManager)
            .getAppWidgetInfo(any())

        `when`(launcherModel.beginLoader(any())).thenReturn(transaction)

        `when`(launcherModel.modelDbController).thenReturn(modelDbController)
        doReturn(BitmapInfo.LOW_RES_INFO).whenever(iconCache).getDefaultIcon(any())
        doAnswer {}.whenever(modelDbController).loadDefaultFavoritesIfNecessary()
        doAnswer { i ->
                inMemoryDb.query(
                    TABLE_NAME,
                    i.getArgument(0),
                    i.getArgument(1),
                    i.getArgument(2),
                    null,
                    null,
                    i.getArgument(3),
                )
            }
            .whenever(modelDbController)
            .query(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

        `when`(launcherModel.modelDelegate).thenReturn(modelDelegate)
        `when`(launcherBinder.newIdleLock(any())).thenReturn(idleLock)
        `when`(idleLock.awaitLocked(1000)).thenReturn(false)
        `when`(iconCache.getUpdateHandler()).thenReturn(iconCacheUpdateHandler)

        context.initDaggerComponent(
            DaggerLoaderTaskTest_TestComponent.builder()
                .bindIconCache(iconCache)
                .bindLauncherModel(launcherModel)
                .bindAllAppsList(bgAllAppsList)
                .bindSettingsCache(settingsCache)
                .bindBroadcastHelper(broadcastHelper)
        )
        context.appComponent.idp.apply {
            numRows = 5
            numColumns = 6
            numDatabaseHotseatIcons = 5
        }
        TestUtil.grantWriteSecurePermission()
    }

    @After
    fun tearDown() {
        LauncherPrefs.get(context).removeSync(RESTORE_DEVICE)
        LauncherPrefs.get(context).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false))
        inMemoryDb.close()
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_WORK)
    fun loadsDataProperly() =
        with(bgDataModel) {
            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask(launcherBinder)
                .runSyncOnBackgroundThread()
            assertThat(
                    itemsIdMap
                        .filter {
                            it.container == CONTAINER_DESKTOP || it.container == CONTAINER_HOTSEAT
                        }
                        .size
                )
                .isAtLeast(32)
            assertThat(itemsIdMap.filter { ModelUtils.WIDGET_FILTER.test(it) }.size).isAtLeast(7)
            assertThat(
                    itemsIdMap
                        .filter {
                            it.itemType == ITEM_TYPE_FOLDER || it.itemType == ITEM_TYPE_APP_PAIR
                        }
                        .size
                )
                .isAtLeast(8)
            assertThat(itemsIdMap.count()).isAtLeast(40)
        }

    @Test
    fun bindsLoadedDataCorrectly() {
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask(launcherBinder)
            .runSyncOnBackgroundThread()

        verify(launcherBinder).bindWorkspace(true, false)
        verify(modelDelegate).workspaceLoadComplete()
        verify(modelDelegate).loadAndAddExtraModelItems(any())
        verify(launcherBinder).bindAllApps()
        verify(iconCacheUpdateHandler, times(4)).updateIcons(any(), any<CachingLogic<Any>>(), any())
        verify(launcherBinder).bindWidgets()
        verify(iconCacheUpdateHandler).finish()
        verify(modelDelegate).modelLoadComplete()
        verify(transaction).commit()
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_WORK, isQuietModeEnabled = true)
    fun setsQuietModeFlagCorrectlyForWorkProfile() =
        with(bgDataModel) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)

            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask(launcherBinder)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList).setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList).setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList, Mockito.never()).setFlags(FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE, isQuietModeEnabled = true)
    fun setsQuietModeFlagCorrectlyForPrivateProfile() =
        with(bgDataModel) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)

            testComponent
                .getLoaderTaskFactory()
                .newLoaderTask(launcherBinder)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList).setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList).setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList, Mockito.never()).setFlags(FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS)
    fun `When broadcast flag on and is restore and secure setting off then send new broadcast`() {
        // Given
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())

        RestoreDbTask.setPending(context)

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask(launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        val argumentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).sendBroadcast(argumentCaptor.capture())
        val actualBroadcastIntent = argumentCaptor.value
        assertEquals(expectedBroadcastModel.installerPackage, actualBroadcastIntent.`package`)
        assertEquals(
            ArrayList(expectedBroadcastModel.installedWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.installedHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.installedWidgets),
            actualBroadcastIntent.getStringArrayListExtra("widgetInstalledItems"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingCollectionItems),
            actualBroadcastIntent.getStringArrayListExtra("folderItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatItem"),
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWidgetItems),
            actualBroadcastIntent.getStringArrayListExtra("widgetItem"),
        )
    }

    @Test
    fun `When not a restore then archiving extras are not present`() {
        // Given
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask(launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(broadcastHelper).createModelsForFirstScreenBroadcast(any(), any(), any(), eq(false))
    }

    @Test
    fun `When failsafe secure setting on then installed item broadcast not sent`() {
        // Given
        doReturn(true).whenever(settingsCache).getValue(DISABLE_INSTALLED_APPS_BROADCAST)
        doReturn(listOf(expectedBroadcastModel))
            .whenever(broadcastHelper)
            .createModelsForFirstScreenBroadcast(any(), any(), any(), any())
        RestoreDbTask.setPending(context)

        // When
        testComponent
            .getLoaderTaskFactory()
            .newLoaderTask(launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(broadcastHelper).createModelsForFirstScreenBroadcast(any(), any(), any(), eq(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then archived AllApps icons on Workspace load from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask(launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isEqualTo(expectedIconBlob)
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and not restore then archived AllApps icons do not load from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask(launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ false,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then unarchived AllApps icons not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = false }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo().apply { componentName = expectedComponent }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask(launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag on and restore then all apps icon not on workspace is not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val expectedComponent = ComponentName("package", "class")
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo().apply {
                        intent = Intent().apply { component = expectedComponent }
                    },
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo =
            AppInfo().apply { componentName = ComponentName("differentPkg", "differentClass") }
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask(launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB)
    fun `When flag off and restore then archived AllApps icons not loaded from db`() {
        // Given
        val activityInfo: LauncherActivityInfo = mock()
        val applicationInfo: ApplicationInfo = mock<ApplicationInfo>().apply { isArchived = true }
        whenever(activityInfo.applicationInfo).thenReturn(applicationInfo)
        val expectedIconBlob = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val workspaceIconRequests =
            listOf(
                IconRequestInfo<WorkspaceItemInfo>(
                    WorkspaceItemInfo(),
                    activityInfo,
                    expectedIconBlob,
                    /* isBlobFullBleed **/ false,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(false),
                )
            )
        val expectedAppInfo = AppInfo()
        // When
        val loader = testComponent.getLoaderTaskFactory().newLoaderTask(launcherBinder)
        val actualIconRequest =
            loader.getAppInfoIconRequestInfo(
                expectedAppInfo,
                activityInfo,
                workspaceIconRequests,
                /* isRestoreFromBackup */ true,
            )
        // Then
        assertThat(actualIconRequest.iconBlob).isNull()
        assertThat(actualIconRequest.itemInfo).isEqualTo(expectedAppInfo)
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getLoaderTaskFactory(): LoaderTaskFactory

        fun getDataModel(): BgDataModel

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindLauncherModel(model: LauncherModel): Builder

            @BindsInstance fun bindIconCache(iconCache: IconCache): Builder

            @BindsInstance fun bindAllAppsList(list: AllAppsList): Builder

            @BindsInstance fun bindSettingsCache(cache: SettingsCache): Builder

            @BindsInstance fun bindBroadcastHelper(helper: FirstScreenBroadcastHelper): Builder

            override fun build(): TestComponent
        }
    }
}

private fun LoaderTask.runSyncOnBackgroundThread() {
    val latch = CountDownLatch(1)
    MODEL_EXECUTOR.execute {
        run()
        latch.countDown()
    }
    latch.await()
}
