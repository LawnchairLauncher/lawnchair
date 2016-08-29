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

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.shortcuts.ShortcutKey;

import java.util.HashSet;

/**
 * A utility class to check for {@link ItemInfo}
 */
public abstract class ItemInfoMatcher {

    public abstract boolean matches(ItemInfo info, ComponentName cn);

    public static ItemInfoMatcher ofComponents(
            final HashSet<ComponentName> components, final UserHandleCompat user) {
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return components.contains(cn) && info.user.equals(user);
            }
        };
    }

    public static ItemInfoMatcher ofPackages(
            final HashSet<String> packageNames, final UserHandleCompat user) {
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
                        keys.contains(ShortcutKey.fromShortcutInfo((ShortcutInfo) info));
            }
        };
    }
}
