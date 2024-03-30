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
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.util.ContentWriter
import java.util.function.Predicate

abstract class CollectionInfo : ItemInfo() {
    var contents: ArrayList<WorkspaceItemInfo> = ArrayList()

    abstract fun add(item: WorkspaceItemInfo)

    /** Convenience function. Checks contents to see if any match a given predicate. */
    fun anyMatch(matcher: Predicate<in WorkspaceItemInfo>): Boolean {
        return contents.stream().anyMatch(matcher)
    }

    /** Convenience function. Returns true if none of the contents match a given predicate. */
    fun noneMatch(matcher: Predicate<in WorkspaceItemInfo>): Boolean {
        return contents.stream().noneMatch(matcher)
    }

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(LauncherSettings.Favorites.TITLE, title)
    }

    /** Returns the collection wrapped as {@link LauncherAtom.ItemInfo} for logging. */
    override fun buildProto(): LauncherAtom.ItemInfo {
        return buildProto(null)
    }
}
