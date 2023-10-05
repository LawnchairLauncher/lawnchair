package com.android.launcher3.model

import android.appwidget.AppWidgetManager
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.LoaderTransaction
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.IconCacheUpdateHandler
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LooperIdleLock
import com.google.common.truth.Truth
import java.util.concurrent.CountDownLatch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class LoaderTaskTest {
    @Mock private lateinit var app: LauncherAppState
    @Mock private lateinit var bgAllAppsList: AllAppsList
    @Mock private lateinit var modelDelegate: ModelDelegate
    @Mock private lateinit var launcherBinder: LauncherBinder
    @Mock private lateinit var launcherModel: LauncherModel
    @Mock private lateinit var transaction: LoaderTransaction
    @Mock private lateinit var iconCache: IconCache
    @Mock private lateinit var idleLock: LooperIdleLock
    @Mock private lateinit var iconCacheUpdateHandler: IconCacheUpdateHandler
    @Mock private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val idp =
            InvariantDeviceProfile.INSTANCE[context].apply {
                numRows = 5
                numColumns = 6
                numDatabaseHotseatIcons = 5
            }

        MockitoAnnotations.initMocks(this)
        `when`(app.context).thenReturn(context)
        `when`(app.model).thenReturn(launcherModel)
        `when`(launcherModel.beginLoader(any(LoaderTask::class.java))).thenReturn(transaction)
        `when`(app.iconCache).thenReturn(iconCache)
        `when`(launcherModel.modelDbController).thenReturn(FactitiousDbController(context))
        `when`(app.invariantDeviceProfile).thenReturn(idp)
        `when`(launcherBinder.newIdleLock(any(LoaderTask::class.java))).thenReturn(idleLock)
        `when`(idleLock.awaitLocked(1000)).thenReturn(false)
        `when`(iconCache.updateHandler).thenReturn(iconCacheUpdateHandler)
        `when`(appWidgetManager.getInstalledProvidersForProfile(any(UserHandle::class.java)))
            .thenReturn(emptyList())
    }

    @Test
    fun loadsDataProperly() =
        with(BgDataModel()) {
            LoaderTask(app, bgAllAppsList, this, modelDelegate, launcherBinder)
                .runSyncOnBackgroundThread()
            Truth.assertThat(workspaceItems.size).isAtLeast(25)
            Truth.assertThat(appWidgets.size).isAtLeast(7)
            Truth.assertThat(folders.size()).isAtLeast(8)
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
}

private fun LoaderTask.runSyncOnBackgroundThread() {
    val latch = CountDownLatch(1)
    MODEL_EXECUTOR.execute {
        run()
        latch.countDown()
    }
    latch.await()
}
