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

package com.android.launcher3.folder

import android.content.Context
import androidx.core.util.Consumer
import com.android.launcher3.LauncherModel
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import javax.inject.Inject
import javax.inject.Provider

class FolderNameSuggestionLoader
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val folderNameProviderFactory: Provider<FolderNameProvider>,
    private val model: LauncherModel,
) {

    var folderNameProvider: FolderNameProvider? = null

    init {
        MODEL_EXECUTOR.execute {
            folderNameProvider = folderNameProviderFactory.get()
            model.enqueueModelUpdateTask { _, dataModel, appList ->
                folderNameProvider?.load(
                    appList.copyData().asList(),
                    FolderNameProvider.getCollectionForSuggestions(dataModel),
                )
            }
        }
    }

    fun getSuggestedFolderName(
        workspaceItemInfos: ArrayList<WorkspaceItemInfo>,
        callback: Consumer<FolderNameInfos>,
    ) {
        MODEL_EXECUTOR.execute {
            val nameInfos = FolderNameInfos()
            folderNameProvider?.getSuggestedFolderName(context, workspaceItemInfos, nameInfos)
            callback.accept(nameInfos)
        }
    }
}
