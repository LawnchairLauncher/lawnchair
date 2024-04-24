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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class TaskThumbnailView : View {
    // TODO(b/335649589): Ideally create and obtain this from DI. This ViewModel should be scoped
    //  to [TaskView], and also shared between [TaskView] and [TaskThumbnailView]
    val viewModel = TaskThumbnailViewModel()

    private var uiState: TaskThumbnailUiState = Uninitialized

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
    }

    override fun onDraw(canvas: Canvas) {
        when (uiState) {
            is Uninitialized -> {}
            is LiveTile -> drawTransparentUiState(canvas)
        }
    }

    private fun drawTransparentUiState(canvas: Canvas) {
        canvas.drawRoundRect(
            0f,
            0f,
            measuredWidth.toFloat(),
            measuredHeight.toFloat(),
            // TODO(b/334826840) add rounded corners
            0f,
            0f,
            CLEAR_PAINT
        )
    }

    companion object {
        private val CLEAR_PAINT =
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    }
}
