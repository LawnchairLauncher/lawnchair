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

package com.android.launcher3.taskbar.handoff

import android.companion.datatransfer.continuity.RemoteTask
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class HandoffSuggestionListTest {

    private val suggestionList = HandoffSuggestionList()

    @Test
    fun getSuggestions_initiallyEmpty() {
        assertThat(suggestionList.suggestions).isEmpty()
    }

    @Test
    fun updateSuggestions_addFirstSuggestion_returnsTrue() {
        val remoteTasks = listOf(createRemoteTask(1, "Task 1"))

        val result = suggestionList.updateSuggestions(remoteTasks)

        assertThat(result).isTrue()
        assertThat(suggestionList.suggestions).hasSize(1)
        assertThat(suggestionList.suggestions.first().deviceId).isEqualTo(1)
    }

    @Test
    fun updateSuggestions_addMultipleSuggestions_returnsTrue() {
        val remoteTasks = listOf(createRemoteTask(1, "Task 1"), createRemoteTask(2, "Task 2"))

        val result = suggestionList.updateSuggestions(remoteTasks)

        assertThat(result).isTrue()
        assertThat(suggestionList.suggestions).hasSize(2)
        assertThat(suggestionList.suggestions.map { it.deviceId }).containsExactly(1, 2)
    }

    @Test
    fun updateSuggestions_removeSuggestion_returnsTrue() {
        val initialTasks = listOf(createRemoteTask(1, "Task 1"), createRemoteTask(2, "Task 2"))
        suggestionList.updateSuggestions(initialTasks)

        val updatedTasks = listOf(createRemoteTask(1, "Task 1"))
        val result = suggestionList.updateSuggestions(updatedTasks)

        assertThat(result).isTrue()
        assertThat(suggestionList.suggestions).hasSize(1)
        assertThat(suggestionList.suggestions.first().deviceId).isEqualTo(1)
    }

    @Test
    fun updateSuggestions_updateExistingSuggestion_returnsFalse() {
        val initialTasks = listOf(createRemoteTask(1, "Task 1"))
        suggestionList.updateSuggestions(initialTasks)

        val updatedTasks = listOf(createRemoteTask(1, "Task 1 updated"))
        val result = suggestionList.updateSuggestions(updatedTasks)

        assertThat(result).isFalse()
        val suggestions = suggestionList.suggestions
        assertThat(suggestions).hasSize(1)
        assertThat(suggestions.first().deviceId).isEqualTo(1)
    }

    @Test
    fun updateSuggestions_noChange_returnsFalse() {
        val remoteTask = createRemoteTask(1, "Task 1")
        val initialTasks = listOf(remoteTask)
        suggestionList.updateSuggestions(initialTasks)

        val result = suggestionList.updateSuggestions(listOf(remoteTask))

        assertThat(result).isFalse()
        assertThat(suggestionList.suggestions).hasSize(1)
        assertThat(suggestionList.suggestions.first().deviceId).isEqualTo(1)
    }

    @Test
    fun clear_removesAllSuggestions() {
        val remoteTasks = listOf(createRemoteTask(1, "Task 1"))
        suggestionList.updateSuggestions(remoteTasks)

        suggestionList.clear()

        assertThat(suggestionList.suggestions).isEmpty()
    }

    private fun createRemoteTask(deviceId: Int, label: String): RemoteTask =
        RemoteTask.Builder(1).setDeviceId(deviceId).setLabel(label).build()
}
