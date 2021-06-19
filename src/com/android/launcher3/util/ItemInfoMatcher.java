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

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;

import java.util.HashSet;
import java.util.Set;

/**
 * A utility class to check for {@link ItemInfo}
 */
public interface ItemInfoMatcher {

    boolean matches(ItemInfo info, ComponentName cn);

    /**
     * Returns true if the itemInfo matches this check
     */
    default boolean matchesInfo(ItemInfo info) {
        if (info != null) {
            ComponentName cn = info.getTargetComponent();
            return cn != null && matches(info, cn);
        } else {
            return false;
        }
    }

    /**
     * Returns a new matcher with returns true if either this or {@param matcher} returns true.
     */
    default ItemInfoMatcher or(ItemInfoMatcher matcher) {
        return (info, cn) -> matches(info, cn) || matcher.matches(info, cn);
    }

    /**
     * Returns a new matcher with returns true if both this and {@param matcher} returns true.
     */
    default ItemInfoMatcher and(ItemInfoMatcher matcher) {
        return (info, cn) -> matches(info, cn) && matcher.matches(info, cn);
    }

    /**
     * Returns a new matcher with returns the opposite value of this.
     */
    default ItemInfoMatcher negate() {
        return (info, cn) -> !matches(info, cn);
    }

    static ItemInfoMatcher ofUser(UserHandle user) {
        return (info, cn) -> info.user.equals(user);
    }

    static ItemInfoMatcher ofComponents(HashSet<ComponentName> components, UserHandle user) {
        return (info, cn) -> components.contains(cn) && info.user.equals(user);
    }

    static ItemInfoMatcher ofPackages(Set<String> packageNames, UserHandle user) {
        return (info, cn) -> packageNames.contains(cn.getPackageName()) && info.user.equals(user);
    }

    static ItemInfoMatcher ofShortcutKeys(Set<ShortcutKey> keys) {
        return  (info, cn) -> info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                        keys.contains(ShortcutKey.fromItemInfo(info));
    }

    /**
     * Returns a matcher for items with provided ids
     */
    static ItemInfoMatcher ofItemIds(IntSet ids) {
        return (info, cn) -> ids.contains(info.id);
    }
}
