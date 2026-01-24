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

package com.android.launcher3.popup

import android.content.Intent
import com.android.launcher3.model.data.ItemInfo
import java.util.stream.Stream

/**
 * Enum for the category of popup we have, as we handle different categories of shortcuts
 * differently depending on the category.
 */
enum class PopupCategory {
    SYSTEM_SHORTCUT,
    SYSTEM_SHORTCUT_FIXED,
}

/** Data class which stores all the values we need to create a long press menu shortcut. */
data class PopupData(
    val iconResId: Int,
    val labelResId: Int,
    val intent: Intent,
    val category: PopupCategory,
)

/** Repository to get all the popup data needed for the long press menu. */
interface PopupDataRepository {
    /**
     * @return a map where we the key is the type of poppable and the value is a stream of popup
     *   data belonging to that type.
     */
    fun getAllPopupData(): Map<PoppableType, Stream<PopupData>>

    /**
     * @param type of PoppableType is what we use to filter shortcuts to only show the ones for that
     *   type of shortcut (e.g: only show long press shortcuts that belong to Folder type).
     * @return a stream of popup data belonging to that type.
     */
    fun getPopupDataByType(type: PoppableType): Stream<PopupData>

    /** Factory for creating a popup data repository */
    companion object PopupDataRepositoryFactory {
        /**
         * Creates a popup data repository.
         *
         * @param itemInfo is all the items for which we want to aggregate their popup data.
         * @return a new PopupDataRepository.
         */
        fun createRepository(vararg itemInfo: ItemInfo): PopupDataRepository {
            return TODO("Provide the return value")
        }
    }
}
