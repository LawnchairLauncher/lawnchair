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

package com.android.launcher3.util;

import android.content.ComponentName;
import android.os.UserHandle;
import android.util.SparseLongArray;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.shortcuts.ShortcutKey;

import java.util.HashSet;

/**
 * A utility class to check for {@link ItemInfo}
 */
public abstract class ItemInfoMatcher {

    public abstract boolean matches(ItemInfo info, ComponentName cn);

    /**
     * Filters {@param infos} to those satisfying the {@link #matches(ItemInfo, ComponentName)}.
     */
    public final HashSet<ItemInfo> filterItemInfos(Iterable<ItemInfo> infos) {
        HashSet<ItemInfo> filtered = new HashSet<>();
        for (ItemInfo i : infos) {
            if (i instanceof ShortcutInfo) {
                ShortcutInfo info = (ShortcutInfo) i;
                ComponentName cn = info.getTargetComponent();
                if (cn != null && matches(info, cn)) {
                    filtered.add(info);
                }
            } else if (i instanceof FolderInfo) {
                FolderInfo info = (FolderInfo) i;
                for (ShortcutInfo s : info.contents) {
                    ComponentName cn = s.getTargetComponent();
                    if (cn != null && matches(s, cn)) {
                        filtered.add(s);
                    }
                }
            } else if (i instanceof LauncherAppWidgetInfo) {
                LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) i;
                ComponentName cn = info.providerName;
                if (cn != null && matches(info, cn)) {
                    filtered.add(info);
                }
            }
        }
        return filtered;
    }

    /**
     * Returns a new matcher with returns true if either this or {@param matcher} returns true.
     */
    public ItemInfoMatcher or(final ItemInfoMatcher matcher) {
       final ItemInfoMatcher that = this;
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return that.matches(info, cn) || matcher.matches(info, cn);
            }
        };
    }

    /**
     * Returns a new matcher with returns true if both this and {@param matcher} returns true.
     */
    public ItemInfoMatcher and(final ItemInfoMatcher matcher) {
        final ItemInfoMatcher that = this;
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return that.matches(info, cn) && matcher.matches(info, cn);
            }
        };
    }

    public static ItemInfoMatcher ofUser(final UserHandle user) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return info.user.equals(user);
            }
        };
    }

    public static ItemInfoMatcher ofComponents(
            final HashSet<ComponentName> components, final UserHandle user) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return components.contains(cn) && info.user.equals(user);
            }
        };
    }

    public static ItemInfoMatcher ofPackages(
            final HashSet<String> packageNames, final UserHandle user) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return packageNames.contains(cn.getPackageName()) && info.user.equals(user);
            }
        };
    }

    public static ItemInfoMatcher ofShortcutKeys(final HashSet<ShortcutKey> keys) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                        keys.contains(ShortcutKey.fromItemInfo(info));
            }
        };
    }

    public static ItemInfoMatcher ofItemIds(
            final LongArrayMap<Boolean> ids, final Boolean matchDefault) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return ids.get(info.id, matchDefault);
            }
        };
    }
}
