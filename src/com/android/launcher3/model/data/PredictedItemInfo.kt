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

import android.content.Context

/**
 * Represents predictive icons, used for example in Hotseat prediction icons and in AllApps
 * prediction row. PredictedItemInfo implements WorkspaceItemFactory because PredictedItemInfo can
 * be used to create new WorkspaceItemInfo refer [ItemInflater].
 */
class PredictedItemInfo(src: WorkspaceItemInfo) : WorkspaceItemInfo(src), WorkspaceItemFactory {

    override fun makeWorkspaceItem(context: Context) = WorkspaceItemInfo(this)
}
