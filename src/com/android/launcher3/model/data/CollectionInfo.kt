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

package com.android.launcher3.model.data

import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.ContentWriter
import java.util.function.Predicate

abstract class CollectionInfo : ItemInfo() {
    /** Adds an ItemInfo to the collection. Throws if given an illegal type. */
    abstract fun add(item: ItemInfo)

    /** Returns the collection's contents as an ArrayList of [ItemInfo]. */
    abstract fun getContents(): ArrayList<ItemInfo>

    /**
     * Returns the collection's contents as an ArrayList of [WorkspaceItemInfo]. Does not include
     * other collection [ItemInfo]s that are inside this collection; rather, it should collect
     * *their* contents and adds them to the ArrayList.
     */
    abstract fun getAppContents(): ArrayList<WorkspaceItemInfo>

    /** Convenience function. Checks contents to see if any match a given predicate. */
    fun anyMatch(matcher: Predicate<ItemInfo>) = getContents().stream().anyMatch(matcher)

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(LauncherSettings.Favorites.TITLE, title)
    }
}
