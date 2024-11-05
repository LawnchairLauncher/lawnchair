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
package com.android.wm.shell.bubbles

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
import android.content.pm.UserInfo
import android.os.UserHandle
import android.util.Log
import android.util.SparseArray
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.bubbles.Bubbles.BubbleMetadataFlagListener
import com.android.wm.shell.bubbles.storage.BubbleEntity
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.bubbles.storage.BubbleVolatileRepository
import com.android.wm.shell.common.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class BubbleDataRepository(
    private val launcherApps: LauncherApps,
    private val mainExecutor: ShellExecutor,
    private val persistentRepository: BubblePersistentRepository,
) {
    private val volatileRepository = BubbleVolatileRepository(launcherApps)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    // For use in Bubble construction.
    private lateinit var bubbleMetadataFlagListener: BubbleMetadataFlagListener

    fun setSuppressionChangedListener(listener: BubbleMetadataFlagListener) {
        bubbleMetadataFlagListener = listener
    }

    /**
     * Adds the bubble in memory, then persists the snapshot after adding the bubble to disk
     * asynchronously.
     */
    fun addBubble(@UserIdInt userId: Int, bubble: Bubble) = addBubbles(userId, listOf(bubble))

    /**
     * Adds the bubble in memory, then persists the snapshot after adding the bubble to disk
     * asynchronously.
     */
    fun addBubbles(@UserIdInt userId: Int, bubbles: List<Bubble>) {
        if (DEBUG) Log.d(TAG, "adding ${bubbles.size} bubbles")
        val entities = transform(bubbles).also {
            b -> volatileRepository.addBubbles(userId, b) }
        if (entities.isNotEmpty()) persistToDisk()
    }

    /**
     * Removes the bubbles from memory, then persists the snapshot to disk asynchronously.
     */
    fun removeBubbles(@UserIdInt userId: Int, bubbles: List<Bubble>) {
        if (DEBUG) Log.d(TAG, "removing ${bubbles.size} bubbles")
        val entities = transform(bubbles).also {
            b -> volatileRepository.removeBubbles(userId, b) }
        if (entities.isNotEmpty()) persistToDisk()
    }

    /**
     * Removes all the bubbles associated with the provided user from memory. Then persists the
     * snapshot to disk asynchronously.
     */
    fun removeBubblesForUser(@UserIdInt userId: Int, @UserIdInt parentId: Int) {
        if (volatileRepository.removeBubblesForUser(userId, parentId)) persistToDisk()
    }

    /**
     * Remove any bubbles that don't have a user id from the provided list of users.
     */
    fun sanitizeBubbles(users: List<UserInfo>) {
        val userIds = users.map { u -> u.id }
        if (volatileRepository.sanitizeBubbles(userIds)) persistToDisk()
    }

    /**
     * Removes all entities that don't have a user in the activeUsers list, if any entities were
     * removed it persists the new list to disk.
     */
    @VisibleForTesting
    fun filterForActiveUsersAndPersist(
            activeUsers: List<Int>,
            entitiesByUser: SparseArray<List<BubbleEntity>>
    ): SparseArray<List<BubbleEntity>> {
        val validEntitiesByUser = SparseArray<List<BubbleEntity>>()
        var entitiesChanged = false
        for (i in 0 until entitiesByUser.size()) {
            val parentUserId = entitiesByUser.keyAt(i)
            if (activeUsers.contains(parentUserId)) {
                val validEntities = mutableListOf<BubbleEntity>()
                // Check if each of the bubbles in the top-level user still has a valid user
                // as it could belong to a profile and have a different id from the parent.
                for (entity in entitiesByUser.get(parentUserId)) {
                    if (activeUsers.contains(entity.userId)) {
                        validEntities.add(entity)
                    } else {
                        entitiesChanged = true
                    }
                }
                if (validEntities.isNotEmpty()) {
                    validEntitiesByUser.put(parentUserId, validEntities)
                }
            } else {
                entitiesChanged = true
            }
        }
        if (entitiesChanged) {
            persistToDisk(validEntitiesByUser)
            return validEntitiesByUser
        }
        return entitiesByUser
    }

    private fun transform(bubbles: List<Bubble>): List<BubbleEntity> {
        return bubbles.mapNotNull { b ->
            BubbleEntity(
                    b.user.identifier,
                    b.packageName,
                    b.metadataShortcutId ?: return@mapNotNull null,
                    b.key,
                    b.rawDesiredHeight,
                    b.rawDesiredHeightResId,
                    b.title,
                    b.taskId,
                    b.locusId?.id,
                    b.isDismissable
            )
        }
    }

    /**
     * Persists the bubbles to disk. When being called multiple times, it waits for first ongoing
     * write operation to finish then run another write operation exactly once.
     *
     * e.g.
     * Job A started -> blocking I/O
     * Job B started, cancels A, wait for blocking I/O in A finishes
     * Job C started, cancels B, wait for job B to finish
     * Job D started, cancels C, wait for job C to finish
     * Job A completed
     * Job B resumes and reaches yield() and is then cancelled
     * Job C resumes and reaches yield() and is then cancelled
     * Job D resumes and performs another blocking I/O
     */
    @VisibleForTesting
    fun persistToDisk(
            entitiesByUser: SparseArray<List<BubbleEntity>> = volatileRepository.bubbles
    ) {
        val prev = job
        job = coroutineScope.launch {
            // if there was an ongoing disk I/O operation, they can be cancelled
            prev?.cancelAndJoin()
            // check for cancellation before disk I/O
            yield()
            // save to disk
            persistentRepository.persistsToDisk(entitiesByUser)
        }
    }

    /**
     * Load bubbles from disk.
     * @param cb The callback to be run after the bubbles are loaded.  This callback is always made
     *           on the main thread of the hosting process. The callback is only run if there are
     *           bubbles.
     */
    @SuppressLint("WrongConstant")
    fun loadBubbles(
            userId: Int,
            currentUsers: List<Int>,
            cb: (List<Bubble>) -> Unit
    ) = coroutineScope.launch {
        /**
         * Load BubbleEntity from disk.
         * e.g.
         * [
         *     BubbleEntity(0, "com.example.messenger", "id-2"),
         *     BubbleEntity(10, "com.example.chat", "my-id1")
         *     BubbleEntity(0, "com.example.messenger", "id-1")
         * ]
         */
        val entitiesByUser = persistentRepository.readFromDisk()

        // Before doing anything, validate that the entities we loaded are valid & have an existing
        // user.
        val validEntitiesByUser = filterForActiveUsersAndPersist(currentUsers, entitiesByUser)

        val entities = validEntitiesByUser.get(userId) ?: return@launch
        volatileRepository.addBubbles(userId, entities)
        /**
         * Extract userId/packageName from these entities.
         * e.g.
         * [
         *     ShortcutKey(0, "com.example.messenger"), ShortcutKey(0, "com.example.chat")
         * ]
         */
        val shortcutKeys = entities.map { ShortcutKey(it.userId, it.packageName) }.toSet()

        /**
         * Retrieve shortcuts with given userId/packageName combination, then construct a
         * mapping from the userId/packageName pair to a list of associated ShortcutInfo.
         * e.g.
         * {
         *     ShortcutKey(0, "com.example.messenger") -> [
         *         ShortcutInfo(userId=0, pkg="com.example.messenger", id="id-0"),
         *         ShortcutInfo(userId=0, pkg="com.example.messenger", id="id-2")
         *     ]
         *     ShortcutKey(10, "com.example.chat") -> [
         *         ShortcutInfo(userId=10, pkg="com.example.chat", id="id-1"),
         *         ShortcutInfo(userId=10, pkg="com.example.chat", id="id-3")
         *     ]
         * }
         */
        val shortcutMap = shortcutKeys.flatMap { key ->
            launcherApps.getShortcuts(
                    LauncherApps.ShortcutQuery()
                            .setPackage(key.pkg)
                            .setQueryFlags(SHORTCUT_QUERY_FLAG), UserHandle.of(key.userId))
                    ?: emptyList()
        }.groupBy { ShortcutKey(it.userId, it.`package`) }
        // For each entity loaded from xml, find the corresponding ShortcutInfo then convert
        // them into Bubble.
        val bubbles = entities.mapNotNull { entity ->
            shortcutMap[ShortcutKey(entity.userId, entity.packageName)]
                    ?.firstOrNull { shortcutInfo -> entity.shortcutId == shortcutInfo.id }
                    ?.let { shortcutInfo ->
                        Bubble(
                                entity.key,
                                shortcutInfo,
                                entity.desiredHeight,
                                entity.desiredHeightResId,
                                entity.title,
                                entity.taskId,
                                entity.locus,
                                entity.isDismissable,
                                mainExecutor,
                                bubbleMetadataFlagListener
                        )
                    }
        }
        mainExecutor.execute { cb(bubbles) }
    }
}

data class ShortcutKey(val userId: Int, val pkg: String)

private const val TAG = "BubbleDataRepository"
private const val DEBUG = false
private const val SHORTCUT_QUERY_FLAG =
        FLAG_MATCH_DYNAMIC or FLAG_MATCH_PINNED_BY_ANY_LAUNCHER or FLAG_MATCH_CACHED