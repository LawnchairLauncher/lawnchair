/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.notification.NotificationMainView;
import com.android.launcher3.shortcuts.DeepShortcutView;

import java.util.ArrayList;

/**
 * Extension of {@link LauncherAccessibilityDelegate} with actions specific to shortcuts in
 * deep shortcuts menu.
 */
public class ShortcutMenuAccessibilityDelegate extends LauncherAccessibilityDelegate {

    private static final int DISMISS_NOTIFICATION = R.id.action_dismiss_notification;

    public ShortcutMenuAccessibilityDelegate(Launcher launcher) {
        super(launcher);
        mActions.put(DISMISS_NOTIFICATION, new AccessibilityAction(DISMISS_NOTIFICATION,
                launcher.getText(R.string.action_dismiss_notification)));
    }

    @Override
    public void addSupportedActions(View host, AccessibilityNodeInfo info, boolean fromKeyboard) {
        if ((host.getParent() instanceof DeepShortcutView)) {
            info.addAction(mActions.get(ADD_TO_WORKSPACE));
        } else if (host instanceof NotificationMainView) {
            NotificationMainView notificationView = (NotificationMainView) host;
            if (notificationView.canChildBeDismissed(notificationView)) {
                info.addAction(mActions.get(DISMISS_NOTIFICATION));
            }
        }
    }

    @Override
    public boolean performAction(View host, ItemInfo item, int action) {
        if (action == ADD_TO_WORKSPACE) {
            if (!(host.getParent() instanceof DeepShortcutView)) {
                return false;
            }
            final ShortcutInfo info = ((DeepShortcutView) host.getParent()).getFinalInfo();
            final int[] coordinates = new int[2];
            final long screenId = findSpaceOnWorkspace(item, coordinates);
            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    mLauncher.getModelWriter().addItemToDatabase(info,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            screenId, coordinates[0], coordinates[1]);
                    ArrayList<ItemInfo> itemList = new ArrayList<>();
                    itemList.add(info);
                    mLauncher.bindItems(itemList, 0, itemList.size(), true);
                    AbstractFloatingView.closeAllOpenViews(mLauncher);
                    announceConfirmation(R.string.item_added_to_workspace);
                }
            };

            if (!mLauncher.showWorkspace(true, onComplete)) {
                onComplete.run();
            }
            return true;
        } else if (action == DISMISS_NOTIFICATION) {
            if (!(host instanceof NotificationMainView)) {
                return false;
            }
            NotificationMainView notificationView = (NotificationMainView) host;
            notificationView.onChildDismissed(notificationView);
            announceConfirmation(R.string.notification_dismissed);
            return true;
        }
        return false;
    }
}
