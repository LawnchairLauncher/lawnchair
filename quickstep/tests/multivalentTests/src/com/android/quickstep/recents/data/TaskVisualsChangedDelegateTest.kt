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

package com.android.quickstep.recents.data

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskIconChangedCallback
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskThumbnailChangedCallback
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class TaskVisualsChangedDelegateTest {
    private val taskVisualsChangeNotifier = FakeTaskVisualsChangeNotifier()
    private val highResLoadingStateNotifier = FakeHighResLoadingStateNotifier()

    val systemUnderTest =
        TaskVisualsChangedDelegateImpl(taskVisualsChangeNotifier, highResLoadingStateNotifier)

    @Test
    fun addingFirstListener_addsListenerToNotifiers() {
        systemUnderTest.registerTaskThumbnailChangedCallback(createTaskKey(id = 1), mock())

        assertThat(taskVisualsChangeNotifier.listeners.single()).isEqualTo(systemUnderTest)
        assertThat(highResLoadingStateNotifier.listeners.single()).isEqualTo(systemUnderTest)
    }

    @Test
    fun addingAndRemovingListener_removesListenerFromNotifiers() {
        systemUnderTest.registerTaskThumbnailChangedCallback(createTaskKey(id = 1), mock())
        systemUnderTest.unregisterTaskThumbnailChangedCallback(createTaskKey(id = 1))

        assertThat(taskVisualsChangeNotifier.listeners).isEmpty()
        assertThat(highResLoadingStateNotifier.listeners).isEmpty()
    }

    @Test
    fun addingTwoAndRemovingOneListener_doesNotRemoveListenerFromNotifiers() {
        systemUnderTest.registerTaskThumbnailChangedCallback(createTaskKey(id = 1), mock())
        systemUnderTest.registerTaskThumbnailChangedCallback(createTaskKey(id = 2), mock())
        systemUnderTest.unregisterTaskThumbnailChangedCallback(createTaskKey(id = 1))

        assertThat(taskVisualsChangeNotifier.listeners.single()).isEqualTo(systemUnderTest)
        assertThat(highResLoadingStateNotifier.listeners.single()).isEqualTo(systemUnderTest)
    }

    @Test
    fun onTaskIconChangedWithTaskId_notifiesCorrectListenerOnly() {
        val expectedListener = mock<TaskIconChangedCallback>()
        val additionalListener = mock<TaskIconChangedCallback>()
        systemUnderTest.registerTaskIconChangedCallback(createTaskKey(id = 1), expectedListener)
        systemUnderTest.registerTaskIconChangedCallback(createTaskKey(id = 2), additionalListener)

        systemUnderTest.onTaskIconChanged(1)

        verify(expectedListener).onTaskIconChanged()
        verifyNoMoreInteractions(additionalListener)
    }

    @Test
    fun onTaskIconChangedWithoutTaskId_notifiesCorrectListenerOnly() {
        val expectedListener = mock<TaskIconChangedCallback>()
        val listener = mock<TaskIconChangedCallback>()
        // Correct match
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 1, pkg = ALTERNATIVE_PACKAGE_NAME, userId = 1),
            expectedListener,
        )
        // 1 out of 2 match
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 2, pkg = PACKAGE_NAME, userId = 1),
            listener,
        )
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 3, pkg = ALTERNATIVE_PACKAGE_NAME, userId = 2),
            listener,
        )
        // 0 out of 2 match
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 4, pkg = PACKAGE_NAME, userId = 2),
            listener,
        )

        systemUnderTest.onTaskIconChanged(ALTERNATIVE_PACKAGE_NAME, UserHandle(1))

        verify(expectedListener).onTaskIconChanged()
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun replacedTaskIconChangedCallbacks_notCalled() {
        val replacedListener = mock<TaskIconChangedCallback>()
        val newListener = mock<TaskIconChangedCallback>()
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 1, pkg = ALTERNATIVE_PACKAGE_NAME, userId = 1),
            replacedListener,
        )
        systemUnderTest.registerTaskIconChangedCallback(
            createTaskKey(id = 1, pkg = ALTERNATIVE_PACKAGE_NAME, userId = 1),
            newListener,
        )

        systemUnderTest.onTaskIconChanged(ALTERNATIVE_PACKAGE_NAME, UserHandle(1))

        verifyNoMoreInteractions(replacedListener)
        verify(newListener).onTaskIconChanged()
    }

    @Test
    fun onTaskThumbnailChanged_notifiesCorrectListenerOnly() {
        val expectedListener = mock<TaskThumbnailChangedCallback>()
        val additionalListener = mock<TaskThumbnailChangedCallback>()
        val expectedThumbnailData = ThumbnailData(snapshotId = 12345)
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 1),
            expectedListener,
        )
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 2),
            additionalListener,
        )

        systemUnderTest.onTaskThumbnailChanged(1, expectedThumbnailData)

        verify(expectedListener).onTaskThumbnailChanged(expectedThumbnailData)
        verifyNoMoreInteractions(additionalListener)
    }

    @Test
    fun onHighResLoadingStateChanged_toEnabled_notifiesAllListeners() {
        val expectedListener = mock<TaskThumbnailChangedCallback>()
        val additionalListener = mock<TaskThumbnailChangedCallback>()
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 1),
            expectedListener,
        )
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 2),
            additionalListener,
        )

        systemUnderTest.onHighResLoadingStateChanged(true)

        verify(expectedListener).onHighResLoadingStateChanged(true)
        verify(additionalListener).onHighResLoadingStateChanged(true)
    }

    @Test
    fun onHighResLoadingStateChanged_toDisabled_notifiesAllListeners() {
        val expectedListener = mock<TaskThumbnailChangedCallback>()
        val additionalListener = mock<TaskThumbnailChangedCallback>()
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 1),
            expectedListener,
        )
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 2),
            additionalListener,
        )

        systemUnderTest.onHighResLoadingStateChanged(false)

        verify(expectedListener).onHighResLoadingStateChanged(false)
        verify(additionalListener).onHighResLoadingStateChanged(false)
    }

    @Test
    fun replacedTaskThumbnailChangedCallbacks_notCalled() {
        val replacedListener1 = mock<TaskThumbnailChangedCallback>()
        val newListener1 = mock<TaskThumbnailChangedCallback>()
        val expectedThumbnailData = ThumbnailData(snapshotId = 12345)
        systemUnderTest.registerTaskThumbnailChangedCallback(
            createTaskKey(id = 1),
            replacedListener1,
        )
        systemUnderTest.registerTaskThumbnailChangedCallback(createTaskKey(id = 1), newListener1)

        systemUnderTest.onTaskThumbnailChanged(1, expectedThumbnailData)

        verifyNoMoreInteractions(replacedListener1)
        verify(newListener1).onTaskThumbnailChanged(expectedThumbnailData)
    }

    private fun createTaskKey(id: Int = 1, pkg: String = PACKAGE_NAME, userId: Int = 1) =
        TaskKey(id, 0, Intent().setPackage(pkg), ComponentName("", ""), userId, 0)

    private companion object {
        const val PACKAGE_NAME = "com.test.test"
        const val ALTERNATIVE_PACKAGE_NAME = "com.test.test2"
    }
}
