package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.UserHandle
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.LoaderTransaction
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.IconCacheUpdateHandler
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.ui.TestViewHelpers
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.LooperIdleLock
import com.android.launcher3.util.UserIconInfo
import com.google.common.truth.Truth
import java.util.concurrent.CountDownLatch
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.Spy
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

private const val INSERTION_STATEMENT_FILE = "databases/workspace_items.sql"

@SmallTest
@RunWith(AndroidJUnit4::class)
class LoaderTaskTest {
    private var context = SandboxModelContext()
    private val expectedBroadcastModel =
        FirstScreenBroadcastModel(
            installerPackage = "installerPackage",
            pendingCollectionItems = mutableSetOf("pendingCollectionItem"),
            pendingWidgetItems = mutableSetOf("pendingWidgetItem"),
            pendingHotseatItems = mutableSetOf("pendingHotseatItem"),
            pendingWorkspaceItems = mutableSetOf("pendingWorkspaceItem"),
            installedHotseatItems = mutableSetOf("installedHotseatItem"),
            installedWorkspaceItems = mutableSetOf("installedWorkspaceItem"),
            firstScreenInstalledWidgets = mutableSetOf("installedFirstScreenWidget"),
            secondaryScreenInstalledWidgets = mutableSetOf("installedSecondaryScreenWidget")
        )
    private lateinit var mockitoSession: MockitoSession

    @Mock private lateinit var app: LauncherAppState
    @Mock private lateinit var bgAllAppsList: AllAppsList
    @Mock private lateinit var modelDelegate: ModelDelegate
    @Mock private lateinit var launcherBinder: BaseLauncherBinder
    @Mock private lateinit var launcherModel: LauncherModel
    @Mock private lateinit var transaction: LoaderTransaction
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var idleLock: LooperIdleLock
    @Mock private lateinit var iconCacheUpdateHandler: IconCacheUpdateHandler
    @Mock private lateinit var userCache: UserCache

    @Spy private var userManagerState: UserManagerState? = UserManagerState()

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(FirstScreenBroadcastHelper::class.java)
                .startMocking()
        val idp =
            InvariantDeviceProfile().apply {
                numRows = 5
                numColumns = 6
                numDatabaseHotseatIcons = 5
            }
        context.putObject(InvariantDeviceProfile.INSTANCE, idp)
        context.putObject(LauncherAppState.INSTANCE, app)

        doReturn(TestViewHelpers.findWidgetProvider(false))
            .`when`(context.spyService(AppWidgetManager::class.java))
            .getAppWidgetInfo(anyInt())
        `when`(app.context).thenReturn(context)
        `when`(app.model).thenReturn(launcherModel)
        `when`(launcherModel.beginLoader(any(LoaderTask::class.java))).thenReturn(transaction)
        `when`(app.iconCache).thenReturn(iconCache)
        `when`(launcherModel.modelDbController)
            .thenReturn(FactitiousDbController(context, INSERTION_STATEMENT_FILE))
        `when`(app.invariantDeviceProfile).thenReturn(idp)
        `when`(launcherBinder.newIdleLock(any(LoaderTask::class.java))).thenReturn(idleLock)
        `when`(idleLock.awaitLocked(1000)).thenReturn(false)
        `when`(iconCache.updateHandler).thenReturn(iconCacheUpdateHandler)
        context.putObject(UserCache.INSTANCE, userCache)
    }

    @After
    fun tearDown() {
        context.onDestroy()
        mockitoSession.finishMocking()
    }

    @Test
    fun loadsDataProperly() =
        with(BgDataModel()) {
            val MAIN_HANDLE = UserHandle.of(0)
            val mockUserHandles = arrayListOf<UserHandle>(MAIN_HANDLE)
            `when`(userCache.userProfiles).thenReturn(mockUserHandles)
            `when`(userCache.getUserInfo(MAIN_HANDLE)).thenReturn(UserIconInfo(MAIN_HANDLE, 1))
            LoaderTask(app, bgAllAppsList, this, modelDelegate, launcherBinder)
                .runSyncOnBackgroundThread()
            Truth.assertThat(workspaceItems.size).isAtLeast(25)
            Truth.assertThat(appWidgets.size).isAtLeast(7)
            Truth.assertThat(collections.size()).isAtLeast(8)
            Truth.assertThat(itemsIdMap.size()).isAtLeast(40)
        }

    @Test
    fun bindsLoadedDataCorrectly() {
        LoaderTask(app, bgAllAppsList, BgDataModel(), modelDelegate, launcherBinder)
            .runSyncOnBackgroundThread()

        verify(launcherBinder).bindWorkspace(true, false)
        verify(modelDelegate).workspaceLoadComplete()
        verify(modelDelegate).loadAndBindAllAppsItems(any(), any(), any())
        verify(launcherBinder).bindAllApps()
        verify(iconCacheUpdateHandler, times(4)).updateIcons(any(), any<CachingLogic<Any>>(), any())
        verify(launcherBinder).bindDeepShortcuts()
        verify(launcherBinder).bindWidgets()
        verify(modelDelegate).loadAndBindOtherItems(any())
        verify(iconCacheUpdateHandler).finish()
        verify(modelDelegate).modelLoadComplete()
        verify(transaction).commit()
    }

    @Test
    fun setsQuietModeFlagCorrectlyForWorkProfile() =
        with(BgDataModel()) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)
            val MAIN_HANDLE = UserHandle.of(0)
            val mockUserHandles = arrayListOf<UserHandle>(MAIN_HANDLE)
            `when`(userCache.userProfiles).thenReturn(mockUserHandles)
            `when`(userManagerState?.isUserQuiet(MAIN_HANDLE)).thenReturn(true)
            `when`(userCache.getUserInfo(MAIN_HANDLE)).thenReturn(UserIconInfo(MAIN_HANDLE, 1))

            LoaderTask(app, bgAllAppsList, this, modelDelegate, launcherBinder, userManagerState)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList)
                .setFlags(BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList)
                .setFlags(BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList, Mockito.never())
                .setFlags(BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    fun setsQuietModeFlagCorrectlyForPrivateProfile() =
        with(BgDataModel()) {
            setFlagsRule.enableFlags(Flags.FLAG_ENABLE_PRIVATE_SPACE)
            val MAIN_HANDLE = UserHandle.of(0)
            val mockUserHandles = arrayListOf<UserHandle>(MAIN_HANDLE)
            `when`(userCache.userProfiles).thenReturn(mockUserHandles)
            `when`(userManagerState?.isUserQuiet(MAIN_HANDLE)).thenReturn(true)
            `when`(userCache.getUserInfo(MAIN_HANDLE)).thenReturn(UserIconInfo(MAIN_HANDLE, 3))

            LoaderTask(app, bgAllAppsList, this, modelDelegate, launcherBinder, userManagerState)
                .runSyncOnBackgroundThread()

            verify(bgAllAppsList)
                .setFlags(BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, false)
            verify(bgAllAppsList)
                .setFlags(BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, true)
            verify(bgAllAppsList, Mockito.never())
                .setFlags(BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED, true)
        }

    @Test
    fun `When launcher_broadcast_installed_apps and is restore then send installed item broadcast`() {
        // Given
        val spyContext = spy(context)
        `when`(app.context).thenReturn(spyContext)
        whenever(
                FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                    anyOrNull(),
                    anyList(),
                    anyMap(),
                    anyList()
                )
            )
            .thenReturn(listOf(expectedBroadcastModel))

        whenever(
                FirstScreenBroadcastHelper.sendBroadcastsForModels(
                    spyContext,
                    listOf(expectedBroadcastModel)
                )
            )
            .thenCallRealMethod()

        Settings.Secure.putInt(spyContext.contentResolver, "launcher_broadcast_installed_apps", 1)
        RestoreDbTask.setPending(spyContext)

        // When
        LoaderTask(app, bgAllAppsList, BgDataModel(), modelDelegate, launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        val argumentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(spyContext).sendBroadcast(argumentCaptor.capture())
        val actualBroadcastIntent = argumentCaptor.value
        assertEquals(expectedBroadcastModel.installerPackage, actualBroadcastIntent.`package`)
        assertEquals(
            ArrayList(expectedBroadcastModel.installedWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceInstalledItems")
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.installedHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatInstalledItems")
        )
        assertEquals(
            ArrayList(
                expectedBroadcastModel.firstScreenInstalledWidgets +
                    expectedBroadcastModel.secondaryScreenInstalledWidgets
            ),
            actualBroadcastIntent.getStringArrayListExtra("widgetInstalledItems")
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingCollectionItems),
            actualBroadcastIntent.getStringArrayListExtra("folderItem")
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWorkspaceItems),
            actualBroadcastIntent.getStringArrayListExtra("workspaceItem")
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingHotseatItems),
            actualBroadcastIntent.getStringArrayListExtra("hotseatItem")
        )
        assertEquals(
            ArrayList(expectedBroadcastModel.pendingWidgetItems),
            actualBroadcastIntent.getStringArrayListExtra("widgetItem")
        )
    }

    @Test
    fun `When not a restore then installed item broadcast not sent`() {
        // Given
        val spyContext = spy(context)
        `when`(app.context).thenReturn(spyContext)
        whenever(
                FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                    anyOrNull(),
                    anyList(),
                    anyMap(),
                    anyList()
                )
            )
            .thenReturn(listOf(expectedBroadcastModel))

        whenever(
                FirstScreenBroadcastHelper.sendBroadcastsForModels(
                    spyContext,
                    listOf(expectedBroadcastModel)
                )
            )
            .thenCallRealMethod()

        Settings.Secure.putInt(spyContext.contentResolver, "launcher_broadcast_installed_apps", 1)

        // When
        LoaderTask(app, bgAllAppsList, BgDataModel(), modelDelegate, launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(spyContext, times(0)).sendBroadcast(any(Intent::class.java))
    }

    @Test
    fun `When launcher_broadcast_installed_apps false then installed item broadcast not sent`() {
        // Given
        val spyContext = spy(context)
        `when`(app.context).thenReturn(spyContext)
        whenever(
                FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                    anyOrNull(),
                    anyList(),
                    anyMap(),
                    anyList()
                )
            )
            .thenReturn(listOf(expectedBroadcastModel))

        whenever(
                FirstScreenBroadcastHelper.sendBroadcastsForModels(
                    spyContext,
                    listOf(expectedBroadcastModel)
                )
            )
            .thenCallRealMethod()

        Settings.Secure.putInt(spyContext.contentResolver, "launcher_broadcast_installed_apps", 0)
        RestoreDbTask.setPending(spyContext)

        // When
        LoaderTask(app, bgAllAppsList, BgDataModel(), modelDelegate, launcherBinder)
            .runSyncOnBackgroundThread()

        // Then
        verify(spyContext, times(0)).sendBroadcast(any(Intent::class.java))
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
