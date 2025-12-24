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

package com.android.quickstep.compose

import android.content.Context
import android.view.View
import com.android.launcher3.compose.ComposeFacade
import com.android.launcher3.compose.core.BaseComposeFacade
import com.android.quickstep.compose.core.QuickstepComposeFeatures
import com.android.quickstep.recents.ui.viewmodel.TaskViewModel
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.apptimer.ViewModel
import com.android.quickstep.views.TaskViewIcon

object QuickstepComposeFacade : BaseComposeFacade, QuickstepComposeFeatures {

    override fun isComposeAvailable() = ComposeFacade.isComposeAvailable()

    override fun initComposeView(appContext: Context) = ComposeFacade.initComposeView(appContext)

    override fun disposeComposition(view: View) = ComposeFacade.disposeComposition(view)

    override fun startIconAppChip(
        composeView: TaskViewIcon,
        viewModel: TaskViewModel,
        taskId: Int,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ): View {
        error(
            "Compose is not available. Make sure to check isComposeAvailable() before calling any" +
                " other function on ComposeFacade."
        )
    }

    override fun startTaskAppTimerToast(
        view: View,
        viewModel: ViewModel<TaskAppTimerUiState>,
    ): View {
        error(
            "Compose is not available. Make sure to check isComposeAvailable() before calling any" +
                " other function on ComposeFacade."
        )
    }
}
