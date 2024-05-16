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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import com.android.launcher3.Utilities
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.system.QuickStepContract
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class TaskThumbnailView : View {
    // TODO(b/335649589): Ideally create and obtain this from DI. This ViewModel should be scoped
    //  to [TaskView], and also shared between [TaskView] and [TaskThumbnailView]
    //  This is using a lazy for now because the dependencies cannot be obtained without DI.
    val viewModel by lazy {
        TaskThumbnailViewModel(
            RecentsViewContainer.containerFromContext<RecentsViewContainer>(context)
                .getOverviewPanel<RecentsView<*, *>>()
                .mRecentsViewData,
            (parent as TaskView).taskViewData
        )
    }

    private var uiState: TaskThumbnailUiState = Uninitialized
    private var inheritedScale: Float = 1f

    private var cornerRadius: Float = TaskCornerRadius.get(context)
    private var fullscreenCornerRadius: Float = QuickStepContract.getWindowCornerRadius(context)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // TODO(b/335396935) replace MainScope with shorter lifecycle.
        MainScope().launch {
            viewModel.uiState.collect { viewModelUiState ->
                uiState = viewModelUiState
                invalidate()
            }
        }
        MainScope().launch { viewModel.recentsFullscreenProgress.collect { invalidateOutline() } }
        MainScope().launch {
            viewModel.inheritedScale.collect { viewModelInheritedScale ->
                inheritedScale = viewModelInheritedScale
                invalidateOutline()
            }
        }

        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.measuredWidth,
                        view.measuredHeight,
                        getCurrentCornerRadius()
                    )
                }
            }
    }

    override fun onDraw(canvas: Canvas) {
        when (uiState) {
            is Uninitialized -> {}
            is LiveTile -> drawTransparentUiState(canvas)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        cornerRadius = TaskCornerRadius.get(context)
        fullscreenCornerRadius = QuickStepContract.getWindowCornerRadius(context)
        invalidateOutline()
    }

    private fun drawTransparentUiState(canvas: Canvas) {
        canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), CLEAR_PAINT)
    }

    private fun getCurrentCornerRadius() =
        Utilities.mapRange(
            viewModel.recentsFullscreenProgress.value,
            cornerRadius,
            fullscreenCornerRadius
        ) / inheritedScale

    companion object {
        private val CLEAR_PAINT =
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    }
}
