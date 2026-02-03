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

package com.android.wm.shell.compatui.impl

import android.content.Context
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.compatui.api.CompatUIComponent
import com.android.wm.shell.compatui.api.CompatUIComponentFactory
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUISpec
import com.android.wm.shell.compatui.api.CompatUIState

/**
 * Default {@link CompatUIComponentFactory } implementation
 */
class DefaultCompatUIComponentFactory(
    private val context: Context,
    private val syncQueue: SyncTransactionQueue,
    private val displayController: DisplayController
) : CompatUIComponentFactory {
    override fun create(
        spec: CompatUISpec,
        compId: String,
        state: CompatUIState,
        compatUIInfo: CompatUIInfo
    ): CompatUIComponent =
        CompatUIComponent(
            spec,
            compId,
            context,
            state,
            compatUIInfo,
            syncQueue,
            displayController.getDisplayLayout(compatUIInfo.taskInfo.displayId)
        )
}
