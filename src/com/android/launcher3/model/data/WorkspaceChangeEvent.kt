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

import java.lang.ref.WeakReference
import java.util.function.Predicate

/** Represents a change being made to the existing [WorkspaceData] */
sealed class WorkspaceChangeEvent(actualOwner: Any?) {

    private val ownerRef = WeakReference(actualOwner)

    // The source of the change. If its user driven, it will point to the UI component where
    // the user is interacting or null if the change was made as a result of some system
    // event. Clients can use this to exclude self-made changes.
    val owner: Any?
        get() = ownerRef.get()

    /** New items were added to the model */
    class AddEvent(val items: List<ItemInfo>, owner: Any?) : WorkspaceChangeEvent(owner)

    /** Some properties of existing items changed */
    class UpdateEvent(val items: List<ItemInfo>, owner: Any?) : WorkspaceChangeEvent(owner)

    /**
     * Some items were removed from the model. Note that the event uses a [Predicate] instead of
     * actual [ItemInfo] as the items may not exist anymore
     */
    class RemoveEvent(val items: Predicate<ItemInfo?>, owner: Any?) : WorkspaceChangeEvent(owner)
}
