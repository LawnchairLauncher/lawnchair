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

package com.android.launcher3.popup

import android.content.Context
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.ActivityContext
import java.util.stream.Collectors

/**
 * Controller for app icons. It handles actions for the popups such as showing and dismissing
 * popups. This is used for icons and shortcuts in the workspace, hotseat, and all apps.
 */
class PopupControllerForAppIcon<T> : PopupController<T> where T : Context, T : ActivityContext {
    override fun show(view: View): Popup? {
        val icon = view as BubbleTextView
        val launcher = Launcher.getLauncher(icon.context)
        if (PopupContainer.getOpen(launcher) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus()
            return null
        }
        val item = icon.tag as ItemInfo
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return null
        }
        val popupDataProvider = launcher.activityComponent.popupDataProvider
        val deepShortcutCount = popupDataProvider.getShortcutCountForItem(item)
        val systemShortcuts =
            launcher
                .getSupportedShortcuts(item)
                .map<SystemShortcut<Launcher>> { s ->
                    s.getShortcut(launcher, item, icon) as SystemShortcut<Launcher>?
                }
                .filter { it != null }
                .collect(Collectors.toList())

        val container =
            PopupContainerWithArrow.create<Launcher>(
                context = launcher,
                originalView = icon,
                itemInfo = item,
            )
        container.configureForLauncher(launcher, item)
        container.populateAndShowRows(deepShortcutCount,
            if (view.showingMinimalPopup) emptyList() else systemShortcuts)
        launcher.refreshAndBindWidgetsForPackageUser(PackageUserKey.fromItemInfo(item))
        container.requestFocus()
        return container
    }

    override fun dismiss() {
        TODO("Not yet implemented")
    }
}
