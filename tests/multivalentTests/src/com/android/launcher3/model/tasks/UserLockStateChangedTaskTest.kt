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
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process.myUserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.tasks.ModelRepoTestEx.trackUpdateAndChanges
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyAndGetItemsUpdated
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyDelete
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.SHORTCUT_ID
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.LayoutResource
import com.android.launcher3.util.ModelTestExtensions.countPersistedModelItems
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class UserLockStateChangedTaskTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule var layout = LayoutResource(context)
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val shortcutAccessRule = RoboApiWrapper.grantShortcutsPermissionRule()

    private val user = myUserHandle()
    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    private lateinit var launcherApps: LauncherApps

    @Mock lateinit var mockShortcut: ShortcutInfo

    @Before
    fun setup() {
        launcherApps = context.spyService(LauncherApps::class.java)
        layout.set(
            LauncherLayoutBuilder()
                .atHotseat(1)
                .putShortcut(TEST_PACKAGE, SHORTCUT_ID)
                .atHotseat(2)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY)
        )

        assertEquals(2, modelState.dataModel.itemsIdMap.countPersistedModelItems())

        whenever(mockShortcut.id).thenReturn(SHORTCUT_ID)
        whenever(mockShortcut.`package`).thenReturn(TEST_PACKAGE)
        whenever(mockShortcut.userHandle).thenReturn(user)
        whenever(mockShortcut.activity).thenReturn(ComponentName(TEST_PACKAGE, TEST_ACTIVITY))
    }

    private fun executeTask(isUserUnlocked: Boolean, hasShortcuts: Boolean = true) {
        if (hasShortcuts) {
            doReturn(listOf(mockShortcut)).whenever(launcherApps).getShortcuts(any(), eq(user))
        } else {
            doReturn(emptyList<ShortcutInfo>()).whenever(launcherApps).getShortcuts(any(), eq(user))
        }
        modelState.model.enqueueModelUpdateTask(UserLockStateChangedTask(user, isUserUnlocked))
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `items updated on user enabled`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val workspaceUpdate = modelState.homeRepo.workspaceState.trackUpdateAndChanges()
            executeTask(true)
            workspaceUpdate.verifyAndGetItemsUpdated()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `items removed on user enabled and shortcut missing`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val workspaceUpdate = modelState.homeRepo.workspaceState.trackUpdateAndChanges()
            executeTask(isUserUnlocked = true, hasShortcuts = false)
            workspaceUpdate.verifyDelete()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `items updated on user disabled`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val workspaceUpdate = modelState.homeRepo.workspaceState.trackUpdateAndChanges()
            executeTask(false)
            workspaceUpdate.verifyAndGetItemsUpdated()
        }
    }
}
