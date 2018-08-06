/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.popup;

import android.content.ComponentName;
import android.os.Handler;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Contains logic relevant to populating a {@link PopupContainerWithArrow}. In particular,
 * this class determines which items appear in the container, and in what order.
 */
public class PopupPopulator {

    public static final int MAX_SHORTCUTS = 4;
    @VisibleForTesting static final int NUM_DYNAMIC = 2;
    public static final int MAX_SHORTCUTS_IF_NOTIFICATIONS = 2;

    /**
     * Sorts shortcuts in rank order, with manifest shortcuts coming before dynamic shortcuts.
     */
    private static final Comparator<ShortcutInfoCompat> SHORTCUT_RANK_COMPARATOR
            = new Comparator<ShortcutInfoCompat>() {
        @Override
        public int compare(ShortcutInfoCompat a, ShortcutInfoCompat b) {
            if (a.isDeclaredInManifest() && !b.isDeclaredInManifest()) {
                return -1;
            }
            if (!a.isDeclaredInManifest() && b.isDeclaredInManifest()) {
                return 1;
            }
            return Integer.compare(a.getRank(), b.getRank());
        }
    };

    /**
     * Filters the shortcuts so that only MAX_SHORTCUTS or fewer shortcuts are retained.
     * We want the filter to include both static and dynamic shortcuts, so we always
     * include NUM_DYNAMIC dynamic shortcuts, if at least that many are present.
     *
     * @param shortcutIdToRemoveFirst An id that should be filtered out first, if any.
     * @return a subset of shortcuts, in sorted order, with size <= MAX_SHORTCUTS.
     */
    public static List<ShortcutInfoCompat> sortAndFilterShortcuts(
            List<ShortcutInfoCompat> shortcuts, @Nullable String shortcutIdToRemoveFirst) {
        // Remove up to one specific shortcut before sorting and doing somewhat fancy filtering.
        if (shortcutIdToRemoveFirst != null) {
            Iterator<ShortcutInfoCompat> shortcutIterator = shortcuts.iterator();
            while (shortcutIterator.hasNext()) {
                if (shortcutIterator.next().getId().equals(shortcutIdToRemoveFirst)) {
                    shortcutIterator.remove();
                    break;
                }
            }
        }

        Collections.sort(shortcuts, SHORTCUT_RANK_COMPARATOR);
        if (shortcuts.size() <= MAX_SHORTCUTS) {
            return shortcuts;
        }

        // The list of shortcuts is now sorted with static shortcuts followed by dynamic
        // shortcuts. We want to preserve this order, but only keep MAX_SHORTCUTS.
        List<ShortcutInfoCompat> filteredShortcuts = new ArrayList<>(MAX_SHORTCUTS);
        int numDynamic = 0;
        int size = shortcuts.size();
        for (int i = 0; i < size; i++) {
            ShortcutInfoCompat shortcut = shortcuts.get(i);
            int filteredSize = filteredShortcuts.size();
            if (filteredSize < MAX_SHORTCUTS) {
                // Always add the first MAX_SHORTCUTS to the filtered list.
                filteredShortcuts.add(shortcut);
                if (shortcut.isDynamic()) {
                    numDynamic++;
                }
                continue;
            }
            // At this point, we have MAX_SHORTCUTS already, but they may all be static.
            // If there are dynamic shortcuts, remove static shortcuts to add them.
            if (shortcut.isDynamic() && numDynamic < NUM_DYNAMIC) {
                numDynamic++;
                int lastStaticIndex = filteredSize - numDynamic;
                filteredShortcuts.remove(lastStaticIndex);
                filteredShortcuts.add(shortcut);
            }
        }
        return filteredShortcuts;
    }

    public static Runnable createUpdateRunnable(final Launcher launcher, final ItemInfo originalInfo,
            final Handler uiHandler, final PopupContainerWithArrow container,
            final List<String> shortcutIds, final List<DeepShortcutView> shortcutViews,
            final List<NotificationKeyData> notificationKeys) {
        final ComponentName activity = originalInfo.getTargetComponent();
        final UserHandle user = originalInfo.user;
        return () -> {
            if (!notificationKeys.isEmpty()) {
                List<StatusBarNotification> notifications = launcher.getPopupDataProvider()
                        .getStatusBarNotificationsForKeys(notificationKeys);
                List<NotificationInfo> infos = new ArrayList<>(notifications.size());
                for (int i = 0; i < notifications.size(); i++) {
                    StatusBarNotification notification = notifications.get(i);
                    infos.add(new NotificationInfo(launcher, notification));
                }
                uiHandler.post(() -> container.applyNotificationInfos(infos));
            }

            List<ShortcutInfoCompat> shortcuts = DeepShortcutManager.getInstance(launcher)
                    .queryForShortcutsContainer(activity, shortcutIds, user);
            String shortcutIdToDeDupe = notificationKeys.isEmpty() ? null
                    : notificationKeys.get(0).shortcutId;
            shortcuts = PopupPopulator.sortAndFilterShortcuts(shortcuts, shortcutIdToDeDupe);
            for (int i = 0; i < shortcuts.size() && i < shortcutViews.size(); i++) {
                final ShortcutInfoCompat shortcut = shortcuts.get(i);
                final ShortcutInfo si = new ShortcutInfo(shortcut, launcher);
                // Use unbadged icon for the menu.
                LauncherIcons li = LauncherIcons.obtain(launcher);
                li.createShortcutIcon(shortcut, false /* badged */).applyTo(si);
                li.recycle();
                si.rank = i;

                final DeepShortcutView view = shortcutViews.get(i);
                uiHandler.post(() -> view.applyShortcutInfo(si, shortcut, container));
            }

            // This ensures that mLauncher.getWidgetsForPackageUser()
            // doesn't return null (it puts all the widgets in memory).
            uiHandler.post(() -> launcher.refreshAndBindWidgetsForPackageUser(
                    PackageUserKey.fromItemInfo(originalInfo)));
        };
    }
}
