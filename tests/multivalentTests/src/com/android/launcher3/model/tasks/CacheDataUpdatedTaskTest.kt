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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.icons.BitmapInfo.Companion.fromBitmap
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LayoutResource
import com.android.launcher3.util.ModelTestExtensions.countPersistedModelItems
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.InstallerSessionRule
import com.android.launcher3.util.rule.TestStabilityRule
import com.google.common.truth.Truth
import java.util.function.Consumer
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Tests for [CacheDataUpdatedTask] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class CacheDataUpdatedTaskTest {

    @get:Rule var testStabilityRule: TestRule = TestStabilityRule()
    @get:Rule var context: SandboxApplication = SandboxApplication().withModelDependency()
    @get:Rule var layout: LayoutResource = LayoutResource(context)
    @get:Rule var installerSessionRule: InstallerSessionRule = InstallerSessionRule()

    private val fixedBitmapInfo = fromBitmap(Bitmap.createBitmap(30, 30, ARGB_8888))

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    private var session1 = 0

    @Before
    fun setup() {
        session1 = installerSessionRule.createInstallerSession(PENDING_APP_1)
        installerSessionRule.createInstallerSession(PENDING_APP_2)

        val builder =
            LauncherLayoutBuilder()
                .atHotseat(1)
                .putFolder("MyFolder")
                .addApp(LauncherModelHelper.TEST_PACKAGE, LauncherModelHelper.TEST_ACTIVITY) // 2
                .addApp(LauncherModelHelper.TEST_PACKAGE, LauncherModelHelper.TEST_ACTIVITY2) // 3
                .addApp(LauncherModelHelper.TEST_PACKAGE, LauncherModelHelper.TEST_ACTIVITY3) // 4
                // Pending App 1

                .addApp(PENDING_APP_1, LauncherModelHelper.TEST_ACTIVITY) // 5
                .addApp(PENDING_APP_1, LauncherModelHelper.TEST_ACTIVITY2) // 6
                .addApp(PENDING_APP_1, LauncherModelHelper.TEST_ACTIVITY3) // 7
                // Pending App 2

                .addApp(PENDING_APP_2, LauncherModelHelper.TEST_ACTIVITY) // 8
                .addApp(PENDING_APP_2, LauncherModelHelper.TEST_ACTIVITY2) // 9
                .addApp(PENDING_APP_2, LauncherModelHelper.TEST_ACTIVITY3) // 10
                .build()
        layout.set(builder)
        // Items on homescreen and folders:
        Assert.assertEquals(10, modelState.dataModel.itemsIdMap.countPersistedModelItems())
    }

    private fun executeTask(op: Int, vararg pkg: String) {
        modelState.model.enqueueModelUpdateTask(
            CacheDataUpdatedTask(op, Process.myUserHandle(), HashSet(pkg.asList()))
        )
    }

    @Test
    fun testCacheUpdate_update_apps() {
        // Run on model executor so that no other task runs in the middle.
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(Consumer { wi: WorkspaceItemInfo -> wi.bitmap = fixedBitmapInfo })

            executeTask(CacheDataUpdatedTask.OP_CACHE_UPDATE, LauncherModelHelper.TEST_PACKAGE)

            // Verify that only the app icons of TEST_PACKAGE (id 2, 3, 4) are updated.
            verifyUpdate(2, 3, 4)
        }
    }

    @Test
    fun testSessionUpdate_ignores_normal_apps() {
        // Run on model executor so that no other task runs in the middle.
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(Consumer { wi: WorkspaceItemInfo -> wi.bitmap = fixedBitmapInfo })

            executeTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, LauncherModelHelper.TEST_PACKAGE)

            // TEST_PACKAGE has no restored shortcuts. Verify that nothing was updated.
            verifyUpdate()
        }
    }

    @Test
    fun testSessionUpdate_updates_pending_apps() {
        // Run on model executor so that no other task runs in the middle.
        val sessionInfo =
            ApplicationProvider.getApplicationContext<Context>()
                .packageManager
                .packageInstaller
                .getSessionInfo(session1)
        Assert.assertNotNull(sessionInfo)
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            getInstance(context)
                .iconCache
                .updateSessionCache(
                    PackageUserKey(PENDING_APP_1, Process.myUserHandle()),
                    sessionInfo,
                )
            // Clear all icons from apps list so that its easy to check what was updated
            allItems().forEach(Consumer { wi: WorkspaceItemInfo -> wi.bitmap = fixedBitmapInfo })

            executeTask(CacheDataUpdatedTask.OP_SESSION_UPDATE, PENDING_APP_1)

            // Only restored apps from PENDING_APP_1 (id 5, 6, 7) are updated
            verifyUpdate(5, 6, 7)
        }
    }

    private fun verifyUpdate(vararg idsUpdated: Int) {
        val updates = IntSet.wrap(*idsUpdated)
        for (info in allItems()) {
            if (updates.contains(info.id)) {
                Truth.assertThat(info.bitmap.icon).isNotSameInstanceAs(fixedBitmapInfo.icon)
                Assert.assertFalse(info.bitmap.isLowRes)
            } else {
                Truth.assertThat(info.bitmap.icon).isSameInstanceAs(fixedBitmapInfo.icon)
            }
        }
    }

    private fun allItems(): List<WorkspaceItemInfo> =
        (modelState.dataModel.itemsIdMap[1] as FolderInfo).getAppContents()

    companion object {
        private val PENDING_APP_1 = LauncherModelHelper.TEST_PACKAGE + ".pending1"
        private val PENDING_APP_2 = LauncherModelHelper.TEST_PACKAGE + ".pending2"
    }
}
