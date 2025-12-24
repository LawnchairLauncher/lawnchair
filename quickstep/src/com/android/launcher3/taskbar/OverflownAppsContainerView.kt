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
package com.android.launcher3.taskbar

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.ArrowPopup

/** A container view for overflown apps in the taskbar. */
class OverflownAppsContainerView<T : TaskbarActivityContext>
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ArrowPopup<T>(context, attrs, defStyleAttr) {
    private lateinit var overflowIcon: TaskbarOverflowView
    private lateinit var viewCallbacks: TaskbarViewCallbacks
    private lateinit var content: LinearLayout
    private var overflownApps = emptyList<ItemInfo>()
    private val spacing: Int =
        resources.getDimensionPixelSize(R.dimen.overflown_apps_container_spacing)

    fun init(icon: TaskbarOverflowView, callbacks: TaskbarViewCallbacks) {
        overflowIcon = icon
        viewCallbacks = callbacks
        content = findViewById(R.id.overflown_content)
        // Set the horizontal padding to the half of the expected spacing for the children to
        // complement the other half
        content.updatePadding(
            left = spacing / 2,
            top = spacing,
            right = spacing / 2,
            bottom = spacing,
        )
    }

    fun setOverflownApps(list: List<ItemInfo>) {
        overflownApps = list
        inflateApps()
    }

    private fun inflateApps() {
        val iconSize = mActivityContext.deviceProfile.taskbarProfile.iconSize
        val iconSpacing = spacing / 2
        for (item in overflownApps) {
            val icon =
                mActivityContext.viewCache.getView<View>(
                    R.layout.taskbar_app_icon,
                    mActivityContext,
                    content,
                )

            if (icon is BubbleTextView && item is WorkspaceItemInfo) {
                icon.applyFromWorkspaceItem(item)
                icon.setOnClickListener(viewCallbacks.iconOnClickListener)
                icon.setOnLongClickListener(viewCallbacks.iconOnLongClickListener)

                val lp =
                    LayoutParams(iconSize, iconSize).apply {
                        marginStart = iconSpacing
                        marginEnd = iconSpacing
                    }
                content.addView(icon, lp)
            }
        }
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_TASKBAR_OVERFLOW != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        when (ev?.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!mActivityContext.dragLayer.isEventOverView(this, ev)) {
                    close(true)
                }
            }
        }
        return false
    }

    override fun getTargetObjectLocation(outPos: Rect?) {
        popupContainer.getDescendantRectRelativeToSelf(overflowIcon, outPos)
    }
}
