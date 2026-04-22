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

package com.android.wm.shell.repository

/**
 * In memory [GenericRepository] implementation.
 */
class MemoryRepositoryImpl<Key, Item>(private val logger: (String) -> Unit = { _ -> }) :
    GenericRepository<Key, Item> {

    private var memoryStore = mutableMapOf<Key, Item>()

    override fun find(key: Key): Item? = memoryStore[key]

    override fun insert(
        key: Key,
        item: Item,
        overrideIfPresent: Boolean
    ): Boolean {
        if (find(key) != null && !overrideIfPresent) {
            return false
        }
        memoryStore[key] = item
        return true
    }

    override fun insert(
        key: Key,
        itemFactory: () -> Item,
        overrideIfPresent: Boolean
    ): Boolean {
        if (find(key) != null && !overrideIfPresent) {
            return false
        }
        memoryStore[key] = itemFactory()
        return true
    }

    override fun delete(key: Key): Boolean = memoryStore.remove(key) != null

    override fun find(predicate: (Key, Item) -> Boolean): List<Item> =
        memoryStore.entries
            .filter { element -> predicate(element.key, element.value) }
            .map { element -> element.value }

    override fun update(key: Key, updateItem: (Item) -> Item): Boolean {
        find(key)?.let { item ->
            return insert(key, updateItem(item))
        }
        return false
    }

    override fun dump() {
        logger("Repository dump: $memoryStore")
    }
}
