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

import android.os.Process.myUserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.SETTINGS_COMPONENT
import com.android.launcher3.util.LauncherModelHelper.SETTINGS_PACKAGE
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.LayoutResource
import com.android.launcher3.util.ModelTestExtensions.countPersistedModelItems
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PackageIncrementalDownloadUpdatedTaskTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val layout = LayoutResource(context)

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    @Before
    @Throws(Exception::class)
    fun setup() {
        layout.set(
            LauncherLayoutBuilder()
                .atWorkspace(0, 0, 1)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY) // 1
                .atWorkspace(0, 0, 2)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY2) // 2
                .atWorkspace(0, 0, 3)
                .putApp(SETTINGS_PACKAGE, SETTINGS_COMPONENT) // 3
                .atWorkspace(0, 0, 4)
                .putApp(SETTINGS_PACKAGE, SETTINGS_COMPONENT) // 4
                .atWorkspace(0, 0, 5)
                .putApp(SETTINGS_PACKAGE, SETTINGS_COMPONENT) // 5
                .atWorkspace(0, 0, 6)
                .putApp(SETTINGS_PACKAGE, SETTINGS_COMPONENT) // 6
        )

        Assert.assertTrue(modelState.model.isModelLoaded())
        assertEquals(6, modelState.dataModel.itemsIdMap.countPersistedModelItems())
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun test_incremental_download_updates_repositories() {
        // Run on model executor so that no other task runs in the middle.
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val incrementalUpdates = mutableListOf<AppInfo>()
            modelState.appsRepo.incrementalUpdates.forEach(MODEL_EXECUTOR) {
                incrementalUpdates.add(it)
            }

            val workspaceUpdates = mutableListOf<WorkspaceChangeEvent?>()
            modelState.homeRepo.workspaceState.changes.forEach(MODEL_EXECUTOR) {
                workspaceUpdates.add(it)
            }

            modelState.model.enqueueModelUpdateTask(
                PackageIncrementalDownloadUpdatedTask(TEST_PACKAGE, myUserHandle(), 30f)
            )

            // Incremental update received only for TEST_PACKAGE
            assertThat(incrementalUpdates).isNotEmpty()
            incrementalUpdates.forEach { assertEquals(TEST_PACKAGE, it.targetPackage) }

            // Workspace update received only once
            assertThat(workspaceUpdates).hasSize(1)

            // Only 2 items corresponding to test package got updated
            val update = workspaceUpdates[0] as UpdateEvent
            assertThat(update.items).hasSize(2)
            assertThat(update.items[0].targetPackage).isEqualTo(TEST_PACKAGE)
            assertThat(update.items[1].targetPackage).isEqualTo(TEST_PACKAGE)
        }
    }
}
