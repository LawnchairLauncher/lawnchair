/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles.storage

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import org.junit.Test
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidTestingRunner::class)
class BubbleVolatileRepositoryTest : ShellTestCase() {

    private val user0 = UserHandle.of(0)
    private val user10_managed = UserHandle.of(10) // In test, acts as workprofile of user0
    private val user11 = UserHandle.of(11)

    // user, package, shortcut, notification key, height, res-height, title, taskId, locusId
    private val bubble1 = BubbleEntity(user0.identifier,
            "com.example.messenger", "shortcut-1", "0key-1", 120, 0, null, 1, null)
    private val bubble2 = BubbleEntity(user10_managed.identifier,
            "com.example.chat", "alice and bob", "10key-2", 0, 16537428, "title", 2, null)
    private val bubble3 = BubbleEntity(user0.identifier,
            "com.example.messenger", "shortcut-2", "0key-3", 120, 0, null, INVALID_TASK_ID, null)

    private val bubble11 = BubbleEntity(user11.identifier,
            "com.example.messenger", "shortcut-1", "01key-1", 120, 0, null, 3)
    private val bubble12 = BubbleEntity(user11.identifier,
            "com.example.chat", "alice and bob", "11key-2", 0, 16537428, "title", INVALID_TASK_ID)

    private val user0bubbles = listOf(bubble1, bubble2, bubble3)
    private val user11bubbles = listOf(bubble11, bubble12)

    private lateinit var repository: BubbleVolatileRepository
    private lateinit var launcherApps: LauncherApps

    @Before
    fun setup() {
        launcherApps = mock<LauncherApps>()
        repository = BubbleVolatileRepository(launcherApps)
    }

    @Test
    fun testAddBubbles() {
        repository.addBubbles(user0.identifier, user0bubbles)
        repository.addBubbles(user11.identifier, user11bubbles)

        assertEquals(user0bubbles, repository.getEntities(user0.identifier).toList())
        assertEquals(user11bubbles, repository.getEntities(user11.identifier).toList())

        verify(launcherApps).cacheShortcuts(eq(PKG_MESSENGER),
                eq(listOf("shortcut-1", "shortcut-2")), eq(user0),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))
        verify(launcherApps).cacheShortcuts(eq(PKG_CHAT),
                eq(listOf("alice and bob")), eq(user10_managed),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))

        verify(launcherApps).cacheShortcuts(eq(PKG_MESSENGER),
                eq(listOf("shortcut-1")), eq(user11),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))
        verify(launcherApps).cacheShortcuts(eq(PKG_CHAT),
                eq(listOf("alice and bob")), eq(user11),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))

        repository.addBubbles(user0.identifier, listOf(bubble1))
        assertEquals(listOf(bubble2, bubble3, bubble1), repository.getEntities(user0.identifier))

        repository.addBubbles(user11.identifier, listOf(bubble12))
        assertEquals(listOf(bubble11, bubble12), repository.getEntities(user11.identifier))

        verifyNoMoreInteractions(launcherApps)
    }

    @Test
    fun testRemoveBubbles() {
        repository.addBubbles(user0.identifier, user0bubbles)
        repository.addBubbles(user11.identifier, user11bubbles)

        repository.removeBubbles(user0.identifier, listOf(bubble3))
        assertEquals(listOf(bubble1, bubble2), repository.getEntities(user0.identifier).toList())
        verify(launcherApps).uncacheShortcuts(eq(PKG_MESSENGER),
                eq(listOf("shortcut-2")), eq(user0),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))

        reset(launcherApps)

        repository.removeBubbles(user11.identifier, listOf(bubble12))
        assertEquals(listOf(bubble11), repository.getEntities(user11.identifier).toList())
        verify(launcherApps).uncacheShortcuts(eq(PKG_CHAT),
                eq(listOf("alice and bob")), eq(user11),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))
    }

    @Test
    fun testAddAndRemoveBubblesWhenExceedingCapacity() {
        repository.capacity = 2
        // push bubbles beyond capacity
        repository.addBubbles(user0.identifier, user0bubbles)
        // verify it is trim down to capacity
        assertEquals(listOf(bubble2, bubble3), repository.getEntities(user0.identifier).toList())
        verify(launcherApps).cacheShortcuts(eq(PKG_MESSENGER),
                eq(listOf("shortcut-2")), eq(user0),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))
        verify(launcherApps).cacheShortcuts(eq(PKG_CHAT),
                eq(listOf("alice and bob")), eq(user10_managed),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))

        repository.addBubbles(user0.identifier, listOf(bubble1))
        // verify the oldest bubble is popped 2, 3
        assertEquals(listOf(bubble3, bubble1), repository.getEntities(user0.identifier).toList())
        verify(launcherApps).uncacheShortcuts(eq(PKG_CHAT),
                eq(listOf("alice and bob")), eq(user10_managed),
                eq(LauncherApps.FLAG_CACHE_BUBBLE_SHORTCUTS))
    }

    @Test
    fun testAddBubbleMatchesByKey() {
        val bubble = BubbleEntity(0, "com.example.pkg", "shortcut-id", "key", 120, 0, "title",
                1, null)
        repository.addBubbles(user0.identifier, listOf(bubble))
        assertEquals(bubble, repository.getEntities(user0.identifier).get(0))

        // Same key as first bubble but different entry
        val bubbleModified = BubbleEntity(0, "com.example.pkg", "shortcut-id", "key", 120, 0,
                "different title", 2)
        repository.addBubbles(user0.identifier, listOf(bubbleModified))
        assertEquals(bubbleModified, repository.getEntities(user0.identifier).get(0))
    }

    @Test
    fun testRemoveBubblesForUser() {
        repository.addBubbles(user0.identifier, user0bubbles)
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))

        val ret = repository.removeBubblesForUser(user0.identifier, -1)
        assertThat(ret).isTrue() // bubbles were removed

        assertThat(repository.getEntities(user0.identifier).toList()).isEmpty()
        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())
    }

    @Test
    fun testRemoveBubblesForUser_parentUserRemoved() {
        repository.addBubbles(user0.identifier, user0bubbles)
        // bubble2 is the work profile bubble
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))

        val ret = repository.removeBubblesForUser(user10_managed.identifier, user0.identifier)
        assertThat(ret).isTrue() // bubbles were removed

        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble3))
        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())
    }

    @Test
    fun testRemoveBubblesForUser_withoutBubbles() {
        repository.addBubbles(user0.identifier, user0bubbles)
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))

        val ret = repository.removeBubblesForUser(user11.identifier, -1)
        assertThat(ret).isFalse() // bubbles were NOT removed

        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))
        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())
    }

    @Test
    fun testSanitizeBubbles_noChanges() {
        repository.addBubbles(user0.identifier, user0bubbles)
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))
        repository.addBubbles(user11.identifier, user11bubbles)
        assertThat(repository.getEntities(user11.identifier).toList())
                .isEqualTo(listOf(bubble11, bubble12))

        val ret = repository.sanitizeBubbles(listOf(user0.identifier,
                user10_managed.identifier,
                user11.identifier))
        assertThat(ret).isFalse() // bubbles were NOT removed

        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())
    }

    @Test
    fun testSanitizeBubbles_userRemoved() {
        repository.addBubbles(user0.identifier, user0bubbles)
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))
        repository.addBubbles(user11.identifier, user11bubbles)
        assertThat(repository.getEntities(user11.identifier).toList())
                .isEqualTo(listOf(bubble11, bubble12))

        val ret = repository.sanitizeBubbles(listOf(user11.identifier))
        assertThat(ret).isTrue() // bubbles were removed

        assertThat(repository.getEntities(user0.identifier).toList()).isEmpty()
        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())

        // User 11 bubbles should still be here
        assertThat(repository.getEntities(user11.identifier).toList())
                .isEqualTo(listOf(bubble11, bubble12))
    }

    @Test
    fun testSanitizeBubbles_userParentRemoved() {
        repository.addBubbles(user0.identifier, user0bubbles)
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble2, bubble3))

        repository.addBubbles(user11.identifier, user11bubbles)
        assertThat(repository.getEntities(user11.identifier).toList())
                .isEqualTo(listOf(bubble11, bubble12))

        val ret = repository.sanitizeBubbles(listOf(user0.identifier, user11.identifier))
        assertThat(ret).isTrue() // bubbles were removed
        // bubble2 is the work profile bubble and should be removed
        assertThat(repository.getEntities(user0.identifier).toList())
                .isEqualTo(listOf(bubble1, bubble3))
        verify(launcherApps, never())
                .uncacheShortcuts(any<String>(), any(), any<UserHandle>(), any<Int>())

        // User 11 bubbles should still be here
        assertThat(repository.getEntities(user11.identifier).toList())
                .isEqualTo(listOf(bubble11, bubble12))
    }

    @Test
    fun testRemoveBubbleForUser_invalidInputDoesntCrash() {
        repository.removeBubblesForUser(-1, 0)
        repository.removeBubblesForUser(-1, -1)
    }
}

private const val PKG_MESSENGER = "com.example.messenger"
private const val PKG_CHAT = "com.example.chat"
