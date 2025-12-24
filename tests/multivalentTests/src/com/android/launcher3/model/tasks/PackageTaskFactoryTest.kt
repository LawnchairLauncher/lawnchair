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

package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.Process.myUserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AppFilter
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.model.tasks.ModelRepoTestEx.trackUpdate
import com.android.launcher3.model.tasks.ModelRepoTestEx.trackUpdateAndChanges
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyAndGetItemsUpdated
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyDelete
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PackageTaskFactoryTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val mockito = MockitoJUnit.rule()

    private val myUser = myUserHandle()

    @Mock lateinit var expectedActivityInfo: LauncherActivityInfo
    @Mock lateinit var mockIconCache: IconCache
    @Mock lateinit var mockAppFilter: AppFilter

    @Mock lateinit var mockApplicationInfo: ApplicationInfo
    @Mock lateinit var mockActivityInfo: ActivityInfo

    private val expectedPackage = "Test.Package"
    private val expectedComponent = ComponentName(expectedPackage, "TestClass")
    private val expectedWorkspaceItem =
        WorkspaceItemInfo().apply {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
            container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            user = myUser
            intent = AppInfo.makeLaunchIntent(expectedComponent)
        }

    private lateinit var mockTaskController: ModelTaskController

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    @Before
    fun setup() {
        val appsListRepo = AppsListRepository()
        context.initDaggerComponent(
            DaggerPackageTaskFactoryTest_TestComponent.builder()
                .bindAppsRepository(appsListRepo)
                .bindAllAppsList(spy(AllAppsList(mockIconCache, mockAppFilter) { appsListRepo }))
                .bindAppFilter(mockAppFilter)
                .bindIconCache(mockIconCache)
        )

        mockTaskController = spy((context.appComponent as TestComponent).getTaskController())

        whenever(mockAppFilter.shouldShowApp(expectedComponent)).thenReturn(true)
        mockApplicationInfo.apply {
            uid = 1
            isArchived = false
        }
        mockActivityInfo.isArchived = false
        expectedActivityInfo.apply {
            whenever(applicationInfo).thenReturn(mockApplicationInfo)
            whenever(activityInfo).thenReturn(mockActivityInfo)
            whenever(componentName).thenReturn(expectedComponent)
        }
    }

    private fun ModelUpdateTask.executeSync() =
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            execute(mockTaskController, modelState.dataModel, modelState.appsList)
        }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun remove_triggers_icon_and_package_removal() {
        modelState.appsList.add(
            AppInfo(context, expectedActivityInfo, myUser),
            expectedActivityInfo,
        )
        modelState.appsList.getAndResetChangeFlag()
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val appUpdates = modelState.appsRepo.appsListStateRef.trackUpdate()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdateAndChanges()

        PackageTaskFactory.appsRemoved(myUser, setOf(expectedPackage)).executeSync()

        verify(mockIconCache).removeIconsForPkg(expectedPackage, myUser)
        assertThat(appUpdates).hasSize(2)
        workspaceUpdates.verifyDelete(deleteIndex = 1, totalUpdates = 2)

        verify(modelState.appsList).removePackage(expectedPackage, myUser)
        verify(mockTaskController, never()).bindUpdatedWorkspaceItems(any())

        assertThat(modelState.appsList.data).isEmpty()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun unavailability_triggers_package_removal_from_all_apps_list() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdateAndChanges()

        PackageTaskFactory.appsUnavailable(myUser, setOf(expectedPackage)).executeSync()

        workspaceUpdates.verifyItemUpdated()
        verify(modelState.appsList).removePackage(expectedPackage, myUser)
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        assertThat(modelState.appsList.data).isEmpty()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun suspend_triggers_flag_update_in_apps_list() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.appsList.add(
            AppInfo(context, expectedActivityInfo, myUser),
            expectedActivityInfo,
        )
        modelState.appsList.getAndResetChangeFlag()
        doAnswer {}.whenever(mockTaskController).bindApplicationsIfNeeded()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdateAndChanges()

        PackageTaskFactory.appsSuspended(myUser, setOf(expectedPackage)).executeSync()

        workspaceUpdates.verifyItemUpdated()
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        verify(modelState.appsList).updateDisabledFlags(any(), any())
        assertThat(modelState.appsList.getAndResetChangeFlag()).isTrue()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isNotEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun unsuspend_triggers_no_update_when_app_not_suspended() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.appsList.getAndResetChangeFlag()
        doAnswer {}.whenever(mockTaskController).bindApplicationsIfNeeded()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdateAndChanges()

        PackageTaskFactory.appsUnsuspended(myUser, setOf(expectedPackage)).executeSync()

        verify(mockTaskController).bindUpdatedWorkspaceItems(emptyList())
        assertThat(workspaceUpdates.changes).isEmpty()

        verify(modelState.appsList).updateDisabledFlags(any(), any())
        assertThat(modelState.appsList.getAndResetChangeFlag()).isFalse()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    private fun TrackedWorkspaceUpdates.verifyItemUpdated(
        updateIndex: Int = 1,
        totalUpdates: Int = 2,
    ) =
        assertThat(verifyAndGetItemsUpdated(updateIndex, totalUpdates))
            .containsExactly(expectedWorkspaceItem)

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getTaskController(): ModelTaskController

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindAppsRepository(appsListRepo: AppsListRepository): Builder

            @BindsInstance fun bindAppFilter(appFilter: AppFilter): Builder

            @BindsInstance fun bindIconCache(iconCache: IconCache): Builder

            @BindsInstance fun bindAllAppsList(list: AllAppsList): Builder

            override fun build(): TestComponent
        }
    }
}
