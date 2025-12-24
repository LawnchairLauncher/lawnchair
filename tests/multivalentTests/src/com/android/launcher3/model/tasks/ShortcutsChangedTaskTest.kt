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
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_INSTALLED
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags
import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.WorkspaceChangeEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
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
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutsChangedTaskTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule var layout = LayoutResource(context)
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val shortcutAccessRule = RoboApiWrapper.grantShortcutsPermissionRule()

    private lateinit var launcherApps: LauncherApps

    private val user: UserHandle = myUserHandle()

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    @Mock lateinit var mockShortcut: ShortcutInfo
    @Mock lateinit var mockCallbacks: Callbacks

    private val workspaceUpdates = mutableListOf<WorkspaceChangeEvent?>()

    @Before
    fun setup() {
        launcherApps = context.spyService(LauncherApps::class.java)
        layout
            .withCallbacks(mockCallbacks)
            .set(
                LauncherLayoutBuilder()
                    .atHotseat(1)
                    .putShortcut(TEST_PACKAGE, SHORTCUT_ID)
                    .atHotseat(2)
                    .putApp(TEST_PACKAGE, TEST_ACTIVITY)
            )

        assertEquals(2, modelState.dataModel.itemsIdMap.countPersistedModelItems())
    }

    private fun setupMockLauncherApps(callback: (ApplicationInfo, ShortcutInfo) -> Unit) {
        val appInfo = ApplicationInfo(getInstrumentation().context.applicationInfo)

        whenever(mockShortcut.id).thenReturn(SHORTCUT_ID)
        whenever(mockShortcut.`package`).thenReturn(TEST_PACKAGE)
        whenever(mockShortcut.userHandle).thenReturn(user)
        whenever(mockShortcut.activity).thenReturn(ComponentName(TEST_PACKAGE, TEST_ACTIVITY))

        callback.invoke(appInfo, mockShortcut)

        doReturn(appInfo)
            .whenever(launcherApps)
            .getApplicationInfo(eq(TEST_PACKAGE), any(), eq(user))

        doReturn(listOf(mockShortcut)).whenever(launcherApps).getShortcuts(any(), eq(user))

        // Clear any previous callback updates
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        reset(mockCallbacks)

        modelState.homeRepo.workspaceState.changes.forEach(MODEL_EXECUTOR) {
            workspaceUpdates.add(it)
        }
    }

    private fun executeTask(
        shortcuts: List<ShortcutInfo> = emptyList(),
        shouldUpdateIdMap: Boolean = false,
    ) {
        modelState.model.enqueueModelUpdateTask(
            ShortcutsChangedTask(TEST_PACKAGE, shortcuts, user, shouldUpdateIdMap)
        )
    }

    private fun verifyCallbacks(itemUpdated: Boolean, itemRemoved: Boolean) {
        // Verify repository update
        if (!itemRemoved && !itemUpdated) {
            assertThat(workspaceUpdates).isEmpty()
        } else {
            assertThat(workspaceUpdates).hasSize(1)

            if (itemUpdated) {
                val updateEvent = workspaceUpdates[0] as UpdateEvent
                assertThat(updateEvent.items).hasSize(1)
                updateEvent.items.forEach { assertThat(it.targetPackage).isEqualTo(TEST_PACKAGE) }
            } else {
                assertThat(workspaceUpdates[0]).isInstanceOf(RemoveEvent::class.java)
            }
        }

        // Verify legacy callbacks
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        if (itemUpdated) {
            verify(mockCallbacks)
                .bindItemsUpdated(
                    argThat { items ->
                        assertThat(items).hasSize(1)
                        items.forEach { assertThat(it.targetPackage).isEqualTo(TEST_PACKAGE) }
                        true
                    }
                )
        } else {
            verify(mockCallbacks, never()).bindItemsUpdated(any())
        }

        if (itemRemoved) {
            verify(mockCallbacks).bindWorkspaceComponentsRemoved(any())
        } else {
            verify(mockCallbacks, never()).bindWorkspaceComponentsRemoved(any())
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When installed pinned shortcut is found then keep in workspace`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = false
                whenever(si.isPinned).thenReturn(true)
            }
            executeTask()
            verifyCallbacks(itemUpdated = true, itemRemoved = false)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When installed unpinned shortcut is found with Flag off then remove from workspace`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = false
                whenever(si.isPinned).thenReturn(false)
            }
            executeTask()
            verifyCallbacks(itemUpdated = false, itemRemoved = true)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS, Flags.FLAG_MODEL_REPOSITORY)
    fun `When installed unpinned shortcut is found with Flag on then keep in workspace`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            // Given
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = false
                whenever(si.isPinned).thenReturn(false)
            }
            executeTask()
            verifyCallbacks(itemUpdated = true, itemRemoved = false)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When shortcut app is uninstalled then skip handling`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags and FLAG_INSTALLED.inv()
                ai.isArchived = false
                whenever(si.isPinned).thenReturn(true)
            }
            executeTask()
            verifyCallbacks(itemUpdated = false, itemRemoved = false)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When archived pinned shortcut is found with flag off then keep in workspace`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = true
                whenever(si.isPinned).thenReturn(true)
            }
            executeTask()
            verifyCallbacks(itemUpdated = true, itemRemoved = false)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS)
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When archived unpinned shortcut is found with flag off then keep in workspace`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = true
                whenever(si.isPinned).thenReturn(true)
                whenever(si.id).thenReturn(SHORTCUT_ID)
            }
            executeTask()
            verifyCallbacks(itemUpdated = true, itemRemoved = false)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When updateIdMap true then trigger deep shortcut binding`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val expectedKey = ComponentKey(ComponentName(TEST_PACKAGE, "expectedClass"), user)

            setupMockLauncherApps { _, si ->
                whenever(si.isEnabled).thenReturn(true)
                whenever(si.isDeclaredInManifest).thenReturn(true)
                whenever(si.activity).thenReturn(expectedKey.componentName)
            }

            executeTask(listOf(mockShortcut), true)

            // Verify that repository was updated
            assertThat(modelState.dataModel.deepShortcutMap).containsEntry(expectedKey, 1)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `When updateIdMap false then do not trigger deep shortcut binding`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val expectedKey = ComponentKey(ComponentName(TEST_PACKAGE, "expectedClass"), user)

            setupMockLauncherApps { _, si ->
                whenever(si.isEnabled).thenReturn(true)
                whenever(si.isDeclaredInManifest).thenReturn(true)
                whenever(si.activity).thenReturn(expectedKey.componentName)
                whenever(si.userHandle).thenReturn(user)
            }
            executeTask(listOf(mockShortcut), false)

            // Verify that repository was not updated
            assertThat(modelState.dataModel.deepShortcutMap).doesNotContainKey(expectedKey)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS, Flags.FLAG_MODEL_REPOSITORY)
    fun `When restoring archived shortcut with flag on then skip handling`() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            setupMockLauncherApps { ai, si ->
                ai.enabled = true
                ai.flags = ai.flags or FLAG_INSTALLED
                ai.isArchived = true
                whenever(si.isPinned).thenReturn(true)
            }
            executeTask()
            verifyCallbacks(itemUpdated = false, itemRemoved = false)
        }
    }
}
