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
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.picker.WidgetRecommendationCategory;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
public class PopupDataProvider implements NotificationListener.NotificationsChangedListener {

    private static final boolean LOGD = false;
    private static final String TAG = "PopupDataProvider";

    private final Consumer<Predicate<PackageUserKey>> mNotificationDotsChangeListener;

    /** Maps launcher activity components to a count of how many shortcuts they have. */
    private HashMap<ComponentKey, Integer> mDeepShortcutMap = new HashMap<>();
    /** Maps packages to their DotInfo's . */
    private Map<PackageUserKey, DotInfo> mPackageUserToDotInfos = new HashMap<>();

    /** All installed widgets. */
    private List<WidgetsListBaseEntry> mAllWidgets = List.of();
    /** Widgets that can be recommended to the users. */
    private List<ItemInfo> mRecommendedWidgets = List.of();

    private PopupDataChangeListener mChangeListener = PopupDataChangeListener.INSTANCE;

    public PopupDataProvider(Consumer<Predicate<PackageUserKey>> notificationDotsChangeListener) {
        mNotificationDotsChangeListener = notificationDotsChangeListener;
    }

    private void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        mNotificationDotsChangeListener.accept(updatedDots);
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey,
            NotificationKeyData notificationKey) {
        DotInfo dotInfo = mPackageUserToDotInfos.get(postedPackageUserKey);
        if (dotInfo == null) {
            dotInfo = new DotInfo();
            mPackageUserToDotInfos.put(postedPackageUserKey, dotInfo);
        }
        if (dotInfo.addOrUpdateNotificationKey(notificationKey)) {
            updateNotificationDots(postedPackageUserKey::equals);
        }
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
            NotificationKeyData notificationKey) {
        DotInfo oldDotInfo = mPackageUserToDotInfos.get(removedPackageUserKey);
        if (oldDotInfo != null && oldDotInfo.removeNotificationKey(notificationKey)) {
            if (oldDotInfo.getNotificationKeys().size() == 0) {
                mPackageUserToDotInfos.remove(removedPackageUserKey);
            }
            updateNotificationDots(removedPackageUserKey::equals);
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        if (activeNotifications == null) return;
        // This will contain the PackageUserKeys which have updated dots.
        HashMap<PackageUserKey, DotInfo> updatedDots = new HashMap<>(mPackageUserToDotInfos);
        mPackageUserToDotInfos.clear();
        for (StatusBarNotification notification : activeNotifications) {
            PackageUserKey packageUserKey = PackageUserKey.fromNotification(notification);
            DotInfo dotInfo = mPackageUserToDotInfos.get(packageUserKey);
            if (dotInfo == null) {
                dotInfo = new DotInfo();
                mPackageUserToDotInfos.put(packageUserKey, dotInfo);
            }
            dotInfo.addOrUpdateNotificationKey(NotificationKeyData.fromNotification(notification));
        }

        // Add and remove from updatedDots so it contains the PackageUserKeys of updated dots.
        for (PackageUserKey packageUserKey : mPackageUserToDotInfos.keySet()) {
            DotInfo prevDot = updatedDots.get(packageUserKey);
            DotInfo newDot = mPackageUserToDotInfos.get(packageUserKey);
            if (prevDot == null
                    || prevDot.getNotificationCount() != newDot.getNotificationCount()) {
                updatedDots.put(packageUserKey, newDot);
            } else {
                // No need to update the dot if it already existed (no visual change).
                // Note that if the dot was removed entirely, we wouldn't reach this point because
                // this loop only includes active notifications added above.
                updatedDots.remove(packageUserKey);
            }
        }

        if (!updatedDots.isEmpty()) {
            updateNotificationDots(updatedDots::containsKey);
        }
    }

    public void setDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mDeepShortcutMap = deepShortcutMapCopy;
        if (LOGD) Log.d(TAG, "bindDeepShortcutMap: " + mDeepShortcutMap);
    }

    public int getShortcutCountForItem(ItemInfo info) {
        if (!ShortcutUtil.supportsDeepShortcuts(info)) {
            return 0;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return 0;
        }

        Integer count = mDeepShortcutMap.get(new ComponentKey(component, info.user));
        return count == null ? 0 : count;
    }

    public @Nullable DotInfo getDotInfoForItem(@NonNull ItemInfo info) {
        if (!ShortcutUtil.supportsShortcuts(info)) {
            return null;
        }
        DotInfo dotInfo = mPackageUserToDotInfos.get(PackageUserKey.fromItemInfo(info));
        if (dotInfo == null) {
            return null;
        }

        // If the item represents a pinned shortcut, ensure that there is a notification
        // for this shortcut
        String shortcutId = ShortcutUtil.getShortcutIdIfPinnedShortcut(info);
        if (shortcutId == null) {
            return dotInfo;
        }
        String[] personKeys = ShortcutUtil.getPersonKeysIfPinnedShortcut(info);
        return (dotInfo.getNotificationKeys().stream().anyMatch(notification -> {
            if (notification.shortcutId != null) {
                return notification.shortcutId.equals(shortcutId);
            }
            if (notification.personKeysFromNotification.length != 0) {
                return Arrays.equals(notification.personKeysFromNotification, personKeys);
            }
            return false;
        })) ? dotInfo : null;
    }

    /**
     * Sets a list of recommended widgets ordered by their order of appearance in the widgets
     * recommendation UI.
     */
    public void setRecommendedWidgets(List<ItemInfo> recommendedWidgets) {
        mRecommendedWidgets = recommendedWidgets;
        mChangeListener.onRecommendedWidgetsBound();
    }

    public void setAllWidgets(List<WidgetsListBaseEntry> allWidgets) {
        mAllWidgets = allWidgets;
        mChangeListener.onWidgetsBound();
    }

    public void setChangeListener(PopupDataChangeListener listener) {
        mChangeListener = listener == null ? PopupDataChangeListener.INSTANCE : listener;
    }

    public List<WidgetsListBaseEntry> getAllWidgets() {
        return mAllWidgets;
    }

    /** Returns a list of recommended widgets. */
    public List<WidgetItem> getRecommendedWidgets() {
        HashMap<ComponentKey, WidgetItem> allWidgetItems = new HashMap<>();
        mAllWidgets.stream()
                .filter(entry -> entry instanceof WidgetsListContentEntry)
                .forEach(entry -> ((WidgetsListContentEntry) entry).mWidgets
                        .forEach(widget -> allWidgetItems.put(
                                new ComponentKey(widget.componentName, widget.user), widget)));
        return mRecommendedWidgets.stream()
                .map(recommendedWidget -> allWidgetItems.get(
                        new ComponentKey(recommendedWidget.getTargetComponent(),
                                recommendedWidget.user)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Returns the recommended widgets mapped by their category. */
    @NonNull
    public Map<WidgetRecommendationCategory, List<WidgetItem>> getCategorizedRecommendedWidgets() {
        Map<ComponentKey, WidgetItem> allWidgetItems = mAllWidgets.stream()
                .filter(entry -> entry instanceof WidgetsListContentEntry)
                .flatMap(entry -> entry.mWidgets.stream())
                .distinct()
                .collect(Collectors.toMap(
                        widget -> new ComponentKey(widget.componentName, widget.user),
                        Function.identity()
                ));
        return mRecommendedWidgets.stream()
                .filter(itemInfo -> itemInfo instanceof PendingAddWidgetInfo
                        && ((PendingAddWidgetInfo) itemInfo).recommendationCategory != null)
                .collect(Collectors.groupingBy(
                        it -> ((PendingAddWidgetInfo) it).recommendationCategory,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .map(it -> allWidgetItems.get(
                                                new ComponentKey(it.getTargetComponent(),
                                                        it.user)))
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList())
                        )
                ));
    }

    public List<WidgetItem> getWidgetsForPackageUser(PackageUserKey packageUserKey) {
        return mAllWidgets.stream()
                .filter(row -> row instanceof WidgetsListContentEntry
                        && row.mPkgItem.packageName.equals(packageUserKey.mPackageName))
                .flatMap(row -> ((WidgetsListContentEntry) row).mWidgets.stream())
                .filter(widget -> packageUserKey.mUser.equals(widget.user))
                .collect(Collectors.toList());
    }

    /** Gets the WidgetsListContentEntry for the currently selected header. */
    public WidgetsListContentEntry getSelectedAppWidgets(PackageUserKey packageUserKey) {
        return (WidgetsListContentEntry) mAllWidgets.stream()
                .filter(row -> row instanceof WidgetsListContentEntry
                        && PackageUserKey.fromPackageItemInfo(row.mPkgItem).equals(packageUserKey))
                .findAny()
                .orElse(null);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "PopupDataProvider:");
        writer.println(prefix + "\tmPackageUserToDotInfos:" + mPackageUserToDotInfos);
    }

    public interface PopupDataChangeListener {

        PopupDataChangeListener INSTANCE = new PopupDataChangeListener() { };

        default void onWidgetsBound() { }

        /** A callback to get notified when recommended widgets are bound. */
        default void onRecommendedWidgetsBound() { }
    }
}
