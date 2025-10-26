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

package com.android.launcher3.taskbar.customization

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.views.ActivityContext

/** TaskbarView that is customizeable via Taskbar containers. */
class CustomizableTaskbarView(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs), Insettable {
    private val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)

    init {
        inflate(context, R.layout.customizable_taskbar_view, this)
    }

    override fun setInsets(insets: Rect?) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }
}
