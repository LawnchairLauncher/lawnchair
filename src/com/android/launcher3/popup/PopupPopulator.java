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
import android.support.annotation.VisibleForTesting;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationItemView;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Contains logic relevant to populating a {@link PopupContainerWithArrow}. In particular,
 * this class determines which items appear in the container, and in what order.
 */
public class PopupPopulator {

    public static final int MAX_ITEMS = 4;
    @VisibleForTesting static final int NUM_DYNAMIC = 2;

    public enum Item {
        SHORTCUT(R.layout.deep_shortcut),
        NOTIFICATION(R.layout.notification);

        public final int layoutId;

        Item(int layoutId) {
            this.layoutId = layoutId;
        }
    }

    public static Item[] getItemsToPopulate(List<String> shortcutIds, String[] notificationKeys) {
        boolean hasNotifications = notificationKeys.length > 0;
        int numNotificationItems = hasNotifications ? 1 : 0;
        int numItems = Math.min(MAX_ITEMS, shortcutIds.size() + numNotificationItems);
        Item[] items = new Item[numItems];
        for (int i = 0; i < numItems; i++) {
            items[i] = Item.SHORTCUT;
        }
        if (hasNotifications) {
            // The notification layout is always first.
            items[0] = Item.NOTIFICATION;
        }
        return items;
    }

    public static Item[] reverseItems(Item[] items) {
        if (items == null) return null;
        int numItems = items.length;
        Item[] reversedArray = new Item[numItems];
        for (int i = 0; i < numItems; i++) {
            reversedArray[i] = items[numItems - i - 1];
        }
        return reversedArray;
    }

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
     * Filters the shortcuts so that only MAX_ITEMS or fewer shortcuts are retained.
     * We want the filter to include both static and dynamic shortcuts, so we always
     * include NUM_DYNAMIC dynamic shortcuts, if at least that many are present.
     *
     * @return a subset of shortcuts, in sorted order, with size <= MAX_ITEMS.
     */
    public static List<ShortcutInfoCompat> sortAndFilterShortcuts(
            List<ShortcutInfoCompat> shortcuts) {
        Collections.sort(shortcuts, SHORTCUT_RANK_COMPARATOR);
        if (shortcuts.size() <= MAX_ITEMS) {
            return shortcuts;
        }

        // The list of shortcuts is now sorted with static shortcuts followed by dynamic
        // shortcuts. We want to preserve this order, but only keep MAX_ITEMS.
        List<ShortcutInfoCompat> filteredShortcuts = new ArrayList<>(MAX_ITEMS);
        int numDynamic = 0;
        int size = shortcuts.size();
        for (int i = 0; i < size; i++) {
            ShortcutInfoCompat shortcut = shortcuts.get(i);
            int filteredSize = filteredShortcuts.size();
            if (filteredSize < MAX_ITEMS) {
                // Always add the first MAX_ITEMS to the filtered list.
                filteredShortcuts.add(shortcut);
                if (shortcut.isDynamic()) {
                    numDynamic++;
                }
                continue;
            }
            // At this point, we have MAX_ITEMS already, but they may all be static.
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

    public static Runnable createUpdateRunnable(final Launcher launcher, ItemInfo originalInfo,
            final Handler uiHandler, final PopupContainerWithArrow container,
            final List<String> shortcutIds, final List<DeepShortcutView> shortcutViews,
            final String[] notificationKeys, final NotificationItemView notificationView) {
        final ComponentName activity = originalInfo.getTargetComponent();
        final UserHandle user = originalInfo.user;
        return new Runnable() {
            @Override
            public void run() {
                if (notificationView != null) {
                    List<StatusBarNotification> notifications = launcher.getPopupDataProvider()
                            .getStatusBarNotificationsForKeys(notificationKeys);
                    List<NotificationInfo> infos = new ArrayList<>(notifications.size());
                    for (int i = 0; i < notifications.size(); i++) {
                        StatusBarNotification notification = notifications.get(i);
                        infos.add(new NotificationInfo(launcher, notification));
                    }
                    uiHandler.post(new UpdateNotificationChild(notificationView, infos));
                }

                final List<ShortcutInfoCompat> shortcuts = PopupPopulator.sortAndFilterShortcuts(
                        DeepShortcutManager.getInstance(launcher).queryForShortcutsContainer(
                                activity, shortcutIds, user));
                for (int i = 0; i < shortcuts.size() && i < shortcutViews.size(); i++) {
                    final ShortcutInfoCompat shortcut = shortcuts.get(i);
                    ShortcutInfo si = new ShortcutInfo(shortcut, launcher);
                    // Use unbadged icon for the menu.
                    si.iconBitmap = LauncherIcons.createShortcutIcon(
                            shortcut, launcher, false /* badged */);
                    si.rank = i;
                    uiHandler.post(new UpdateShortcutChild(container, shortcutViews.get(i),
                            si, shortcut));
                }
            }
        };
    }

    /** Updates the child of this container at the given index based on the given shortcut info. */
    private static class UpdateShortcutChild implements Runnable {
        private final PopupContainerWithArrow mContainer;
        private final DeepShortcutView mShortcutChild;
        private final ShortcutInfo mShortcutChildInfo;
        private final ShortcutInfoCompat mDetail;

        public UpdateShortcutChild(PopupContainerWithArrow container, DeepShortcutView shortcutChild,
                ShortcutInfo shortcutChildInfo, ShortcutInfoCompat detail) {
            mContainer = container;
            mShortcutChild = shortcutChild;
            mShortcutChildInfo = shortcutChildInfo;
            mDetail = detail;
        }

        @Override
        public void run() {
            mShortcutChild.applyShortcutInfo(mShortcutChildInfo, mDetail,
                    mContainer.mShortcutsItemView);
        }
    }

    /** Updates the child of this container at the given index based on the given shortcut info. */
    private static class UpdateNotificationChild implements Runnable {
        private NotificationItemView mNotificationView;
        private List<NotificationInfo> mNotificationInfos;

        public UpdateNotificationChild(NotificationItemView notificationView,
                List<NotificationInfo> notificationInfos) {
            mNotificationView = notificationView;
            mNotificationInfos = notificationInfos;
        }

        @Override
        public void run() {
            mNotificationView.applyNotificationInfos(mNotificationInfos);
        }
    }
}
