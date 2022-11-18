/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.model;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Utils class for {@link com.android.launcher3.LauncherModel}.
 */
public class ModelUtils {

    private static final String TAG = "ModelUtils";

    /**
     * Filters the set of items who are directly or indirectly (via another container) on the
     * specified screen.
     */
    public static <T extends ItemInfo> void filterCurrentWorkspaceItems(
            final IntSet currentScreenIds,
            ArrayList<T> allWorkspaceItems,
            ArrayList<T> currentScreenItems,
            ArrayList<T> otherScreenItems) {
        // Purge any null ItemInfos
        allWorkspaceItems.removeIf(Objects::isNull);
        // Order the set of items by their containers first, this allows use to walk through the
        // list sequentially, build up a list of containers that are in the specified screen,
        // as well as all items in those containers.
        IntSet itemsOnScreen = new IntSet();
        Collections.sort(allWorkspaceItems,
                (lhs, rhs) -> Integer.compare(lhs.container, rhs.container));
        for (T info : allWorkspaceItems) {
            if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (TestProtocol.sDebugTracing) {
                    Log.d(TestProtocol.NULL_INT_SET, "filterCurrentWorkspaceItems: "
                            + currentScreenIds);
                }
                if (currentScreenIds.contains(info.screenId)) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                currentScreenItems.add(info);
                itemsOnScreen.add(info.id);
            } else {
                if (itemsOnScreen.contains(info.container)) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            }
        }
    }

    /**
     * Iterates though current workspace items and returns available hotseat ranks for prediction.
     */
    public static IntArray getMissingHotseatRanks(List<ItemInfo> items, int len) {
        IntSet seen = new IntSet();
        items.stream().filter(
                info -> info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)
                .forEach(i -> seen.add(i.screenId));
        IntArray result = new IntArray(len);
        IntStream.range(0, len).filter(i -> !seen.contains(i)).forEach(result::add);
        return result;
    }


    /**
     * Creates a workspace item info for the legacy shortcut intent
     */
    @SuppressWarnings("deprecation")
    public static WorkspaceItemInfo fromLegacyShortcutIntent(Context context, Intent data) {
        if (!isValidExtraType(data, Intent.EXTRA_SHORTCUT_INTENT, Intent.class)
                || !(isValidExtraType(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.class))
                || !(isValidExtraType(data, Intent.EXTRA_SHORTCUT_ICON, Bitmap.class))) {

            Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        Intent launchIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (launchIntent == null || label == null) {
            Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        final WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.user = Process.myUserHandle();

        BitmapInfo iconInfo = null;
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            Bitmap bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
            if (bitmap != null) {
                iconInfo = li.createIconBitmap(bitmap);
            } else {
                info.iconResource = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
                if (info.iconResource != null) {
                    iconInfo = li.createIconBitmap(info.iconResource);
                }
            }
        }

        if (iconInfo == null) {
            Log.e(TAG, "Invalid icon by the app");
            return null;
        }
        info.bitmap = iconInfo;
        info.contentDescription = info.title = Utilities.trim(label);
        info.intent = launchIntent;
        return info;
    }

    /**
     * @return true if the extra is either null or is of type {@param type}
     */
    private static boolean isValidExtraType(Intent intent, String key, Class type) {
        Object extra = intent.getParcelableExtra(key);
        return extra == null || type.isInstance(extra);
    }
}
