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
 * Simple repository abstraction for common use.
 * @param Key     The generic type for the key.
 * @param Item    The generic type for the value stored in the repository.
 */
interface GenericRepository<Key, Item> {

    /**
     * @return The Item for a given [key] if present.
     */
    fun find(key: Key): Item?

    /**
     * Inserts the given [item] for the given [key].
     *
     * @param key   The Key for the new item
     * @param item  The Item for the given key.
     * @param overrideIfPresent If {@code true} the value is updated when already present.
     * @return `true` if the value has been updated and `false` otherwise.
     */
    fun insert(key: Key, item: Item, overrideIfPresent: Boolean = true): Boolean

    /**
     * Inserts the given [item] for the given [key]. The [item] is created only in the case
     * when [overrideIfPresent] is [true] or it's not present. This prevents the [item] from
     * being created if not necessary.
     *
     * @param key   The Key for the new item
     * @param item  The Item for the given key.
     * @param overrideIfPresent If {@code true} the value is updated when already present.
     * @return `true` if the value has been updated and `false` otherwise.
     */
    fun insert(key: Key, itemFactory: () -> Item, overrideIfPresent: Boolean = true): Boolean

    /**
     * Deletes the Item for the given [key].
     *
     * @param key   The Key of the item to delete.
     * @return `true` if the item has been removed and `false` otherwise.
     */
    fun delete(key: Key): Boolean

    /**
     * Search for the Item for the given [key] and invoked the [onItem] on it if
     * present.
     *
     * @param key   The Key for the Item in the repository.
     * @param defaultResult The value to return in case there's no value for the key
     * @param onItem    The function to invoke in case the Item is present.
     * @return The result of [onItem] if the item is present or [defaultResult] otherwise.
     */
    fun executeOn(
        key: Key,
        defaultResult: Boolean = false,
        onItem: (Item) -> Boolean
    ): Boolean = find(key)?.let { onItem(it) } ?: defaultResult

    /**
     * Updates the value if present.
     *
     * @param key   The Key for the Item in the repository.
     * @param updateItem The function which updated the Item if present.
     * @return [true] if the item has been updated and [false] if not present.
     */
    fun update(key: Key, updateItem: (Item) -> Item): Boolean

    /**
     * Finds the items for a given [predicate].
     */
    fun find(predicate: (Key, Item) -> Boolean): List<Item>

    /**
     * Shows what's in the repository for debugging purpose.
     */
    fun dump()
}
