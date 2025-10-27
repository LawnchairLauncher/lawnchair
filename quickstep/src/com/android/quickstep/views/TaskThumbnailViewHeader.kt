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

package com.android.quickstep.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.android.launcher3.R
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.ThumbnailHeader

class TaskThumbnailViewHeader
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val headerTitleView: TextView by lazy { findViewById(R.id.header_app_title) }
    private val headerIconView: ImageView by lazy { findViewById(R.id.header_app_icon) }
    private val headerCloseButton: ImageButton by lazy { findViewById(R.id.header_close_button) }

    fun setHeader(header: ThumbnailHeader) {
        headerTitleView.setText(header.title)
        headerIconView.setImageDrawable(header.icon)
        headerCloseButton.setOnClickListener(header.clickCloseListener)
    }
}
