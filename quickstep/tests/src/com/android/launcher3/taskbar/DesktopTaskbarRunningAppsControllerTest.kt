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

package com.android.launcher3.taskbar

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import android.testing.AndroidTestingRunner
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.quickstep.RecentsModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.whenever

@RunWith(AndroidTestingRunner::class)
class DesktopTaskbarRunningAppsControllerTest : TaskbarBaseTestCase() {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mockRecentsModel: RecentsModel
    @Mock private lateinit var mockDesktopVisibilityController: DesktopVisibilityController

    private var nextTaskId: Int = 500

    private lateinit var taskbarRunningAppsController: DesktopTaskbarRunningAppsController
    private lateinit var userHandle: UserHandle

    @Before
    fun setUp() {
        super.setup()
        userHandle = Process.myUserHandle()
        taskbarRunningAppsController =
            DesktopTaskbarRunningAppsController(mockRecentsModel) {
                mockDesktopVisibilityController
            }
        taskbarRunningAppsController.init(taskbarControllers)
        taskbarRunningAppsController.setApps(
            ALL_APP_PACKAGES.map { createTestAppInfo(packageName = it) }.toTypedArray()
        )
    }

    @Test
    fun updateHotseatItemInfos_notInDesktopMode_returnsExistingHotseatItems() {
        setInDesktopMode(false)
        val hotseatItems =
            createHotseatItemsFromPackageNames(listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2))

        assertThat(taskbarRunningAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray()))
            .isEqualTo(hotseatItems.toTypedArray())
    }

    @Test
    fun updateHotseatItemInfos_notInDesktopMode_runningApps_returnsExistingHotseatItems() {
        setInDesktopMode(false)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        val hotseatItems = createHotseatItemsFromPackageNames(hotseatPackages)
        val runningTasks =
            createDesktopTasksFromPackageNames(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
        whenever(mockRecentsModel.runningTasks).thenReturn(runningTasks)
        taskbarRunningAppsController.updateRunningApps()

        val newHotseatItems =
            taskbarRunningAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())

        assertThat(newHotseatItems.map { it?.targetPackage }).isEqualTo(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_noRunningApps_returnsExistingHotseatItems() {
        setInDesktopMode(true)
        val hotseatItems =
            createHotseatItemsFromPackageNames(listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2))

        assertThat(taskbarRunningAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray()))
            .isEqualTo(hotseatItems.toTypedArray())
    }

    @Test
    fun updateHotseatItemInfos_returnsExistingHotseatItemsAndRunningApps() {
        setInDesktopMode(true)
        val hotseatItems =
            createHotseatItemsFromPackageNames(listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2))
        val runningTasks =
            createDesktopTasksFromPackageNames(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
        whenever(mockRecentsModel.runningTasks).thenReturn(runningTasks)
        taskbarRunningAppsController.updateRunningApps()

        val newHotseatItems =
            taskbarRunningAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())

        val expectedPackages =
            listOf(
                HOTSEAT_PACKAGE_1,
                HOTSEAT_PACKAGE_2,
                RUNNING_APP_PACKAGE_1,
                RUNNING_APP_PACKAGE_2,
            )
        assertThat(newHotseatItems.map { it?.targetPackage }).isEqualTo(expectedPackages)
    }

    @Test
    fun updateHotseatItemInfos_runningAppIsHotseatItem_returnsDistinctItems() {
        setInDesktopMode(true)
        val hotseatItems =
            createHotseatItemsFromPackageNames(listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2))
        val runningTasks =
            createDesktopTasksFromPackageNames(
                listOf(HOTSEAT_PACKAGE_1, RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
            )
        whenever(mockRecentsModel.runningTasks).thenReturn(runningTasks)
        taskbarRunningAppsController.updateRunningApps()

        val newHotseatItems =
            taskbarRunningAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())

        val expectedPackages =
            listOf(
                HOTSEAT_PACKAGE_1,
                HOTSEAT_PACKAGE_2,
                RUNNING_APP_PACKAGE_1,
                RUNNING_APP_PACKAGE_2,
            )
        assertThat(newHotseatItems.map { it?.targetPackage }).isEqualTo(expectedPackages)
    }

    @Test
    fun getRunningApps_notInDesktopMode_returnsEmptySet() {
        setInDesktopMode(false)
        val runningTasks =
            createDesktopTasksFromPackageNames(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
        whenever(mockRecentsModel.runningTasks).thenReturn(runningTasks)
        taskbarRunningAppsController.updateRunningApps()

        assertThat(taskbarRunningAppsController.runningApps).isEqualTo(emptySet<String>())
    }

    @Test
    fun getRunningApps_inDesktopMode_returnsRunningApps() {
        setInDesktopMode(true)
        val runningTasks =
            createDesktopTasksFromPackageNames(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
        whenever(mockRecentsModel.runningTasks).thenReturn(runningTasks)
        taskbarRunningAppsController.updateRunningApps()

        assertThat(taskbarRunningAppsController.runningApps)
            .isEqualTo(setOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
    }

    private fun createHotseatItemsFromPackageNames(packageNames: List<String>): List<ItemInfo> {
        return packageNames.map { createTestAppInfo(packageName = it) }
    }

    private fun createDesktopTasksFromPackageNames(
        packageNames: List<String>
    ): ArrayList<RunningTaskInfo> {
        return ArrayList(packageNames.map { createDesktopTaskInfo(packageName = it) })
    }

    private fun createDesktopTaskInfo(packageName: String): RunningTaskInfo {
        return RunningTaskInfo().apply {
            taskId = nextTaskId++
            configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
            realActivity = ComponentName(packageName, "TestActivity")
        }
    }

    private fun createTestAppInfo(
        packageName: String = "testPackageName",
        className: String = "testClassName"
    ) = AppInfo(ComponentName(packageName, className), className /* title */, userHandle, Intent())

    private fun setInDesktopMode(inDesktopMode: Boolean) {
        whenever(mockDesktopVisibilityController.areDesktopTasksVisible()).thenReturn(inDesktopMode)
    }

    private companion object {
        const val HOTSEAT_PACKAGE_1 = "hotseat1"
        const val HOTSEAT_PACKAGE_2 = "hotseat2"
        const val RUNNING_APP_PACKAGE_1 = "running1"
        const val RUNNING_APP_PACKAGE_2 = "running2"
        const val RUNNING_APP_PACKAGE_3 = "running3"
        val ALL_APP_PACKAGES =
            listOf(
                HOTSEAT_PACKAGE_1,
                HOTSEAT_PACKAGE_2,
                RUNNING_APP_PACKAGE_1,
                RUNNING_APP_PACKAGE_2,
                RUNNING_APP_PACKAGE_3,
            )
    }
}
