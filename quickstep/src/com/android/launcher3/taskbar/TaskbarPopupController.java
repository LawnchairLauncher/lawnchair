/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import androidx.annotation.NonNull;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.views.ActivityContext;

import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements interfaces required to show and allow interacting with a PopupContainerWithArrow.
 */
public class TaskbarPopupController {

    private static final SystemShortcut.Factory<TaskbarActivityContext>
            APP_INFO = SystemShortcut.AppInfo::new;

    private final PopupDataProvider mPopupDataProvider;

    public TaskbarPopupController() {
        // TODO (b/198438631): add notifications dots change listener
        mPopupDataProvider = new PopupDataProvider(packageUserKey -> {});
    }

    @NonNull
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    public void setDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    /**
     * Shows the notifications and deep shortcuts associated with a Taskbar {@param icon}.
     * @return the container if shown or null.
     */
    public PopupContainerWithArrow<TaskbarActivityContext> showForIcon(BubbleTextView icon) {
        TaskbarActivityContext context = ActivityContext.lookupContext(icon.getContext());
        if (PopupContainerWithArrow.getOpen(context) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        ItemInfo item = (ItemInfo) icon.getTag();
        if (!PopupContainerWithArrow.canShow(icon, item)) {
            return null;
        }

        final PopupContainerWithArrow<TaskbarActivityContext> container =
                (PopupContainerWithArrow) context.getLayoutInflater().inflate(
                        R.layout.popup_container, context.getDragLayer(), false);
        // TODO (b/198438631): configure for taskbar/context

        container.populateAndShow(icon,
                mPopupDataProvider.getShortcutCountForItem(item),
                mPopupDataProvider.getNotificationKeysForItem(item),
                // TODO (b/198438631): add support for INSTALL shortcut factory
                Stream.of(APP_INFO)
                        .map(s -> s.getShortcut(context, item))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        container.requestFocus();
        return container;
    }
}
