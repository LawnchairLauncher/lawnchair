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

package com.android.launcher3.model.data

import android.util.Log
import com.android.launcher3.util.ContentWriter

/** Info representing a set of predicted items belonging to a particular container */
class PredictedContainerInfo(containerId: Int, private val items: List<ItemInfo>) :
    CollectionInfo() {

    init {
        id = containerId
        container = containerId
    }

    override fun add(item: ItemInfo) {
        Log.e("PredictedContainerInfo", "Trying to add $item to immutable prediction container")
    }

    override fun getContents(): List<ItemInfo> = items

    override fun getAppContents(): List<WorkspaceItemInfo> =
        items.mapNotNull { if (it is WorkspaceItemInfo) it else null }

    override fun onAddToDatabase(writer: ContentWriter) =
        throw RuntimeException("Persisting predicted items not supported")

    override fun dumpProperties() = "${super.dumpProperties()}, items: [${items.joinToString()}]"
}
