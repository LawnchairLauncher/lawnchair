/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.bubbles

import android.app.ActivityTaskManager
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.storage.BubbleEntity
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.common.HandlerExecutor
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

class BubbleDataRepositoryTest : ShellTestCase() {

    private val user0BubbleEntities = listOf(
        BubbleEntity(
            userId = 0,
            packageName = "com.example.messenger",
            shortcutId = "shortcut-1",
            key = "0k1",
            desiredHeight = 120,
            desiredHeightResId = 0,
            title = null,
            taskId = 1,
            locus = null,
            isDismissable = true
        ),
        BubbleEntity(
            userId = 10,
            packageName = "com.example.chat",
            shortcutId = "alice and bob",
            key = "0k2",
            desiredHeight = 0,
            desiredHeightResId = 16537428,
            title = "title",
            taskId = 2,
            locus = null
        ),
        BubbleEntity(
            userId = 0,
            packageName = "com.example.messenger",
            shortcutId = "shortcut-2",
            key = "0k3",
            desiredHeight = 120,
            desiredHeightResId = 0,
            title = null,
            taskId = ActivityTaskManager.INVALID_TASK_ID,
            locus = null
        )
    )

    private val user1BubbleEntities = listOf(
        BubbleEntity(
            userId = 1,
            packageName = "com.example.messenger",
            shortcutId = "shortcut-1",
            key = "1k1",
            desiredHeight = 120,
            desiredHeightResId = 0,
            title = null,
            taskId = 3,
            locus = null,
            isDismissable = true
        ),
        BubbleEntity(
            userId = 12,
            packageName = "com.example.chat",
            shortcutId = "alice and bob",
            key = "1k2",
            desiredHeight = 0,
            desiredHeightResId = 16537428,
            title = "title",
            taskId = 4,
            locus = null
        ),
        BubbleEntity(
            userId = 1,
            packageName = "com.example.messenger",
            shortcutId = "shortcut-2",
            key = "1k3",
            desiredHeight = 120,
            desiredHeightResId = 0,
            title = null,
            taskId = ActivityTaskManager.INVALID_TASK_ID,
            locus = null
        ),
        BubbleEntity(
            userId = 12,
            packageName = "com.example.chat",
            shortcutId = "alice",
            key = "1k4",
            desiredHeight = 0,
            desiredHeightResId = 16537428,
            title = "title",
            taskId = 5,
            locus = null
        )
    )

    private val testHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = HandlerExecutor(testHandler)
    private val bgExecutor = HandlerExecutor(testHandler)
    private val launcherApps = mock<LauncherApps>()

    private val persistedBubbles = SparseArray<List<BubbleEntity>>()

    private lateinit var dataRepository: BubbleDataRepository
    private lateinit var persistentRepository: BubblePersistentRepository

    @Before
    fun setup() {
        persistentRepository = BubblePersistentRepository(mContext)
        dataRepository =
            spy(BubbleDataRepository(launcherApps, mainExecutor, bgExecutor, persistentRepository))

        persistedBubbles.put(0, user0BubbleEntities)
        persistedBubbles.put(1, user1BubbleEntities)
    }

    @After
    fun teardown() {
        // Clean up any persisted bubbles for the next run
        persistentRepository.persistsToDisk(SparseArray())
    }

    @Test
    fun testFilterForActiveUsersAndPersist_allValid() {
        // Matches all the users in user0BubbleEntities & user1BubbleEntities
        val activeUserIds = listOf(0, 10, 1, 12)

        val validEntitiesByUser = dataRepository.filterForActiveUsersAndPersist(
            activeUserIds, persistedBubbles)

        // No invalid users, so no changes
        assertThat(persistedBubbles).isEqualTo(validEntitiesByUser)

        // No invalid users, so no persist to disk happened
        verify(dataRepository, never()).persistToDisk(any())
    }

    @Test
    fun testFilterForActiveUsersAndPersist_invalidParent() {
        // When we start, we do have user 0 bubbles.
        assertThat(persistedBubbles.get(0)).isNotEmpty()

        val activeUserIds = listOf(10, 1, 12) // Missing user 0
        val validEntitiesByUser = dataRepository.filterForActiveUsersAndPersist(
            activeUserIds, persistedBubbles)

        // We no longer have any user 0 bubbles.
        assertThat(validEntitiesByUser.get(0)).isNull()
        // User 1 bubbles should be the same.
        assertThat(validEntitiesByUser.get(1)).isEqualTo(user1BubbleEntities)

        // Verify that persist to disk happened with the new valid entities list.
        verify(dataRepository).persistToDisk(validEntitiesByUser)
    }

    @Test
    fun testFilterForActiveUsersAndPersist_invalidChild() {
        // Build a list to compare against (remove all user 12 bubbles)
        val (user1EntitiesWithUser12, user1EntitiesWithoutUser12) =
            user1BubbleEntities.partition { it.userId == 12 }

        // Verify we start with user 12 bubbles
        assertThat(persistedBubbles.get(1).containsAll(user1EntitiesWithUser12)).isTrue()

        val activeUserIds = listOf(0, 10, 1) // Missing user 1's child user 12
        val validEntitiesByUser = dataRepository.filterForActiveUsersAndPersist(
            activeUserIds, persistedBubbles)

        // We no longer have any user 12 bubbles.
        assertThat(validEntitiesByUser.get(1)).isEqualTo(user1EntitiesWithoutUser12)

        // Verify that persist to disk happened with the new valid entities list.
        verify(dataRepository).persistToDisk(validEntitiesByUser)
    }
}