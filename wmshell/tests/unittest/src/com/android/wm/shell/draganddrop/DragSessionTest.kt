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
package com.android.wm.shell.draganddrop

import android.app.ActivityTaskManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ClipDescription.EXTRA_HIDE_DRAG_SOURCE_TASK_ID
import android.content.ClipDescription.MIMETYPE_APPLICATION_ACTIVITY
import android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT
import android.os.PersistableBundle
import android.testing.AndroidTestingRunner
import android.view.View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.draganddrop.DragTestUtils.createTaskInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Tests for DragSession.
 *
 * Usage: atest WMShellUnitTests:DragSessionTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DragSessionTest : ShellTestCase() {

    private val activityTaskManager = mock<ActivityTaskManager>()
    private val displayLayout = mock<DisplayLayout>()

    @Test
    fun testNullClipData() {
        // Start a new drag session with null data
        val session = DragSession(activityTaskManager, displayLayout, null, 0)

        assertThat(session.hideDragSourceTaskId).isEqualTo(-1)
    }

    @Test
    fun testGetRunningTask() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }

        // Set up for dragging an app
        val data = DragTestUtils.createAppClipData(MIMETYPE_APPLICATION_SHORTCUT)

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data, 0)
        session.updateRunningTask()

        assertThat(session.runningTaskInfo).isEqualTo(runningTasks.first())
        assertThat(session.runningTaskWinMode).isEqualTo(runningTasks.first().windowingMode)
        assertThat(session.runningTaskActType).isEqualTo(runningTasks.first().activityType)
    }

    @Test
    fun testGetRunningTaskWithFloatingTasks() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, alwaysOnTop=true),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }

        // Set up for dragging an app
        val data = DragTestUtils.createAppClipData(MIMETYPE_APPLICATION_SHORTCUT)

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data, 0)
        session.updateRunningTask()

        // Ensure that we find the first non-floating task
        assertThat(session.runningTaskInfo).isEqualTo(runningTasks.first())
        assertThat(session.runningTaskWinMode).isEqualTo(runningTasks.first().windowingMode)
        assertThat(session.runningTaskActType).isEqualTo(runningTasks.first().activityType)
    }

    @Test
    fun testHideDragSource_readDragFlag() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }

        // Set up for dragging an app
        val data = DragTestUtils.createAppClipData(MIMETYPE_APPLICATION_SHORTCUT)
        data.description.extras =
            PersistableBundle().apply {
                putInt(
                    EXTRA_HIDE_DRAG_SOURCE_TASK_ID,
                    runningTasks.last().taskId
                )
            }

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data, 0)
        session.updateRunningTask()

        assertThat(session.hideDragSourceTaskId).isEqualTo(runningTasks.last().taskId)
    }

    @Test
    fun testAppData() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }

        // Set up for dragging with app data
        val data = DragTestUtils.createAppClipData(MIMETYPE_APPLICATION_ACTIVITY)

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data, 0)
        session.initialize(true /* skipUpdateRunningTask */)

        assertThat(session.appData).isNotNull()
        assertThat(session.launchableIntent).isNull()
    }

    @Test
    fun testLaunchableIntent() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }
        val pendingIntent = DragTestUtils.createLaunchableIntent(mContext)

        // Set up for dragging with a launchable intent
        val data = DragTestUtils.createIntentClipData(pendingIntent)

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data,
            DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG)
        session.initialize(true /* skipUpdateRunningTask */)

        assertThat(session.appData).isNull()
        assertThat(session.launchableIntent).isNotNull()
    }

    @Test
    fun testBothValidAppDataAndLaunchableIntent_launchableIntentIsNull() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }
        val pendingIntent = DragTestUtils.createLaunchableIntent(mContext)

        // Set up for dragging data with both and app-data intent and a laucnhable intent
        val launchData = DragTestUtils.createIntentClipData(pendingIntent)
        val data = DragTestUtils.createAppClipData(MIMETYPE_APPLICATION_ACTIVITY)
        data.addItem(launchData.getItemAt(0))

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data,
            DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG)
        session.initialize(true /* skipUpdateRunningTask */)

        // Since the app data is valid, we prioritize using that
        assertThat(session.appData).isNotNull()
        assertThat(session.launchableIntent).isNull()
    }

    @Test
    fun testInvalidAppDataWithValidLaunchableIntent_appDataIsNull() {
        // Set up running tasks
        val runningTasks = listOf(
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
            createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD),
        )
        activityTaskManager.stub {
            on { getTasks(any(), any()) } doReturn runningTasks
        }
        val pendingIntent = DragTestUtils.createLaunchableIntent(mContext)

        // Set up for dragging data with an unknown mime type, this should not be treated as a valid
        // app drag
        val launchData = DragTestUtils.createIntentClipData(pendingIntent)
        val data = DragTestUtils.createAppClipData("unknown_mime_type")
        data.addItem(launchData.getItemAt(0))

        // Start a new drag session
        val session = DragSession(activityTaskManager, displayLayout, data,
            DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG)
        session.initialize(true /* skipUpdateRunningTask */)

        // Since the app data is invalid, we use the launchable intent
        assertThat(session.appData).isNull()
        assertThat(session.launchableIntent).isNotNull()
    }
}
