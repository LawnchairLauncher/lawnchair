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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.TouchDelegate
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import com.android.launcher3.R
import com.android.quickstep.task.thumbnail.TaskHeaderUiState

class TaskHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {

    private val headerTitleView: TextView by lazy { findViewById(R.id.header_app_title) }
    private val headerIconView: ImageView by lazy { findViewById(R.id.header_app_icon) }
    private val headerCloseButton: ImageButton by lazy { findViewById(R.id.header_close_button) }

    var closeButtonAccessibilityAction: AccessibilityAction? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Post to ensure the button has been laid out and has its dimensions.
        headerCloseButton.post {
            val delegateArea = Rect()
            headerCloseButton.getHitRect(delegateArea)

            // Calculate the desired touch area size in pixels.
            val touchTargetSize =
                resources.getDimensionPixelSize(
                    R.dimen.task_thumbnail_header_close_button_hit_rect_size
                )

            // Expand the hit rect to the desired touch target size, centered on the button.
            val sizeDifferenceX = (touchTargetSize - delegateArea.width()) / 2
            val sizeDifferenceY = (touchTargetSize - delegateArea.height()) / 2
            delegateArea.inset(-sizeDifferenceX, -sizeDifferenceY)

            // The TouchDelegate is set on the parent `TaskHeaderView`.
            touchDelegate = TouchDelegate(delegateArea, headerCloseButton)
        }
    }

    fun setState(taskHeaderState: TaskHeaderUiState) {
        when (taskHeaderState) {
            is TaskHeaderUiState.ShowHeader -> {
                setHeader(taskHeaderState.header)
                isGone = false
                closeButtonAccessibilityAction =
                    createCloseButtonAccessibilityAction(context, taskHeaderState.header.title)
            }
            is TaskHeaderUiState.HideHeader -> {
                isGone = true
                closeButtonAccessibilityAction = null
            }
        }
    }

    fun getSupportedAccessibilityActions(): List<AccessibilityAction> =
        listOfNotNull(closeButtonAccessibilityAction)

    fun handleAccessibilityAction(action: Int): Boolean {
        closeButtonAccessibilityAction?.let {
            if (action == it.id) {
                return headerCloseButton.callOnClick() ?: false
            }
        }
        return false
    }

    private fun setHeader(header: TaskHeaderUiState.ThumbnailHeader) {
        headerTitleView.text = header.title
        headerIconView.setImageDrawable(header.icon)
        headerCloseButton.setOnClickListener(header.clickCloseListener)
    }

    companion object {
        const val TAG = "TaskHeaderView"

        private fun createCloseButtonAccessibilityAction(
            context: Context,
            taskDescription: String?,
        ) =
            AccessibilityAction(
                R.id.action_close,
                context.getString(R.string.close_task_accessibility, taskDescription),
            )
    }
}
