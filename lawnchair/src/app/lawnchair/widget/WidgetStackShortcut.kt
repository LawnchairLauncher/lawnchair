/*
 * Copyright (C) 2025 Lawnchair
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

package app.lawnchair.widget

import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext

/**
 * System shortcut for creating or editing widget stacks
 */
class WidgetStackShortcut<T : ActivityContext>(
    target: T,
    itemInfo: ItemInfo,
    originalView: View,
) : SystemShortcut<T>(
    R.drawable.ic_widget,
    if (itemInfo is LauncherAppWidgetInfo && itemInfo.widgetStackId != null) {
        R.string.edit_stack
    } else {
        R.string.create_stack
    },
    target,
    itemInfo,
    originalView,
) {

    override fun onClick(view: View) {
        AbstractFloatingView.closeAllOpenViews(mTarget)

        val launcher = mTarget as? Launcher ?: return
        val widgetInfo = mItemInfo as? LauncherAppWidgetInfo ?: return

        // Show the widget stack dialog using Compose
        showWidgetStackDialog(launcher, widgetInfo)
    }

    companion object {
        /**
         * Factory for creating widget stack shortcuts
         */
        val FACTORY = SystemShortcut.Factory { launcher: Launcher, itemInfo: ItemInfo, originalView: View ->
            // Only show for widgets (both regular widgets and widget stacks)
            if (itemInfo !is LauncherAppWidgetInfo) return@Factory null

            // Show for:
            // 1. Regular widgets (to create a stack)
            // 2. Widget stacks (to edit the stack)
            // NOTE: WidgetStackView itself will also show this shortcut
            WidgetStackShortcut(launcher, itemInfo, originalView)
        }
    }
}
