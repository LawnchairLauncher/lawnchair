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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.os.Process
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.OVERFLOW
import com.android.launcher3.taskbar.TaskbarIconType.RECENT
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat

/** Common utilities for testing [TaskbarView]. */
object TaskbarViewTestUtil {

    /** Begins an assertion about a [TaskbarView]. */
    fun assertThat(view: TaskbarView): TaskbarViewSubject {
        return assertAbout(::TaskbarViewSubject).that(view)
    }

    /** Creates an array of fake hotseat items. */
    fun createHotseatItems(size: Int): Array<ItemInfo> {
        return Array(size) { createHotseatWorkspaceItem(it) }
    }

    fun createHotseatWorkspaceItem(id: Int = 0): WorkspaceItemInfo {
        return WorkspaceItemInfo(
                AppInfo(TEST_COMPONENT, "Test App $id", Process.myUserHandle(), Intent())
            )
            .apply {
                this.id = id
                // Create a placeholder icon so that the test  doesn't try to load a high-res icon.
                this.bitmap = BitmapInfo.fromBitmap(createBitmap(1, 1, Bitmap.Config.ALPHA_8))
            }
    }

    fun createHotseatAppPairsItem(): AppPairInfo {
        return AppPairInfo().apply {
            add(createHotseatWorkspaceItem(1))
            add(createHotseatWorkspaceItem(2))
        }
    }

    fun createHotseatFolderItem(): FolderInfo {
        return FolderInfo().apply {
            title = "Test Folder"
            add(createHotseatWorkspaceItem(1))
            add(createHotseatWorkspaceItem(2))
            add(createHotseatWorkspaceItem(3))
        }
    }

    /** Creates a list of fake recent tasks. */
    fun createRecents(size: Int): List<GroupTask> {
        return List(size) {
            SingleTask(
                Task().apply {
                    key =
                        TaskKey(
                            it,
                            5,
                            TEST_INTENT,
                            TEST_COMPONENT,
                            Process.myUserHandle().identifier,
                            System.currentTimeMillis(),
                        )
                }
            )
        }
    }
}

/** A `Truth` [Subject] with extensions for verifying [TaskbarView]. */
class TaskbarViewSubject(failureMetadata: FailureMetadata, private val view: TaskbarView?) :
    Subject(failureMetadata, view) {

    /** Verifies that the types of icons match [expectedTypes] in order. */
    fun hasIconTypes(vararg expectedTypes: TaskbarIconType) {
        val actualTypes =
            view?.iconViews?.map {
                when (it) {
                    view.allAppsButtonContainer -> ALL_APPS
                    view.taskbarDividerViewContainer -> DIVIDER
                    view.taskbarOverflowView -> OVERFLOW
                    else ->
                        when (it.tag) {
                            is ItemInfo -> HOTSEAT
                            is GroupTask -> RECENT
                            else -> throw IllegalStateException("Unknown type for $it")
                        }
                }
            }
        assertThat(actualTypes).containsExactly(*expectedTypes).inOrder()
    }

    /** Verifies that recents from [startIndex] have IDs that match [expectedIds] in order. */
    fun hasRecentsOrder(startIndex: Int, expectedIds: List<Int>) {
        val actualIds =
            view?.iconViews?.slice(startIndex..<startIndex + expectedIds.size)?.flatMap {
                assertThat(it.tag).isInstanceOf(GroupTask::class.java)
                (it.tag as GroupTask).tasks.map { task -> task.key.id }
            }
        assertThat(actualIds).containsExactlyElementsIn(expectedIds).inOrder()
    }
}

/** Types of icons in the [TaskbarView]. */
enum class TaskbarIconType {
    ALL_APPS,
    DIVIDER,
    HOTSEAT,
    RECENT,
    OVERFLOW;

    operator fun times(size: Int) = Array(size) { this }
}

private const val TEST_PACKAGE = "com.android.launcher3.taskbar"
private val TEST_COMPONENT = ComponentName(TEST_PACKAGE, "Activity")
private val TEST_INTENT = Intent().apply { `package` = TEST_PACKAGE }
