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
package com.android.launcher3.taskbar.customization

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.Reorderable
import com.android.launcher3.taskbar.TaskbarView.TaskbarLayoutParams
import com.android.launcher3.util.MultiTranslateDelegate

/** This manages a group of icons within [TaskbarView]. */
class TaskbarIconsContainer
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), Reorderable, TaskbarContainer {
    private var iconTouchSize = 0
    private var itemMarginLeftRight = 0
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = DEFAULT_BOUNCE_SCALE

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val iconTop = 0
        val iconBottom = iconTop + iconTouchSize

        val count = childCount
        var iconEnd = spaceNeeded + itemMarginLeftRight
        for (i in count downTo 1) {
            val child = getChildAt(i - 1)
            iconEnd -= itemMarginLeftRight
            val iconStart = iconEnd - iconTouchSize
            child.layout(iconStart, iconTop, iconEnd, iconBottom)
            iconEnd = iconStart - itemMarginLeftRight
        }
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        TaskbarLayoutParams(lp)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        TaskbarLayoutParams(context, attrs)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean = p is TaskbarLayoutParams

    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    override fun setReorderBounceScale(scale: Float) {
        reorderBounceScale = scale
    }

    override fun getReorderBounceScale(): Float = reorderBounceScale

    override val spaceNeeded: Int
        get() =
            if (childCount == 0) 0
            else (childCount * iconTouchSize) + ((childCount - 1) * itemMarginLeftRight * 2)

    companion object {
        // effectively a no-op since we do not scale this container.
        private const val DEFAULT_BOUNCE_SCALE = 1f

        /** @return a new instance of [TaskbarIconsContainer]. */
        @JvmStatic
        fun create(
            context: Context,
            iconTouchSize: Int,
            itemMarginLeftRight: Int,
        ): TaskbarIconsContainer {
            return TaskbarIconsContainer(context).apply {
                this.iconTouchSize = iconTouchSize
                this.itemMarginLeftRight = itemMarginLeftRight
                // App icon views draw running state indicators outside of the icon view bounds, and
                // thus outside the icons container bounds - don't clip the children so running
                // state indicators remain visible.
                this.clipChildren = false
            }
        }
    }
}
