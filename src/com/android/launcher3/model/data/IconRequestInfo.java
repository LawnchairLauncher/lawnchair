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
package com.android.launcher3.model.data;

import static android.graphics.BitmapFactory.decodeByteArray;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.cache.CacheLookupFlag;

/**
 * Class representing one request for an icon to be queried in a sql database.
 *
 * @param <T> ItemInfoWithIcon subclass whose title and icon can be loaded and filled by an sql
 *           query.
 */
public class IconRequestInfo<T extends ItemInfoWithIcon> {

    private static final String TAG = "IconRequestInfo";

    @NonNull public final T itemInfo;
    @Nullable public final LauncherActivityInfo launcherActivityInfo;
    @Nullable public final byte[] iconBlob;
    public final boolean isBlobFullBleed;
    public final CacheLookupFlag lookupFlag;

    public IconRequestInfo(
            @NonNull T itemInfo,
            @Nullable LauncherActivityInfo launcherActivityInfo,
            CacheLookupFlag lookupFlag) {
        this(
                itemInfo,
                launcherActivityInfo,
                /* iconBlob= */ null,
                /* isBlobFullBleed= */ false,
                lookupFlag);
    }

    public IconRequestInfo(
            @NonNull T itemInfo,
            @Nullable LauncherActivityInfo launcherActivityInfo,
            @Nullable byte[] iconBlob,
            boolean isBlobFullBleed,
            CacheLookupFlag lookupFlag) {
        this.itemInfo = itemInfo;
        this.launcherActivityInfo = launcherActivityInfo;
        this.iconBlob = iconBlob;
        this.isBlobFullBleed = isBlobFullBleed;
        this.lookupFlag = lookupFlag;
    }

    /**
     * Loads this request's item info's title and icon from given iconBlob from Launcher.db.
     * Generally used for restoring Promise Icons and pre-archived icons from backup.
     * This method should only be used on {@link IconRequestInfo} for {@link WorkspaceItemInfo}
     *  or {@link AppInfo}.
     */
    public boolean loadIconFromDbBlob(Context context) {
        if (!(itemInfo instanceof WorkspaceItemInfo) && !(itemInfo instanceof AppInfo)) {
            throw new IllegalStateException(
                    "loadIconFromDb should only be used for either WorkspaceItemInfo or AppInfo: "
                            + itemInfo);
        }

        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            BitmapInfo bitmap = parseIconBlob(li);
            ItemInfoWithIcon info = itemInfo;
            if (bitmap == null) {
                Log.d(TAG, "loadIconFromDb: icon blob null, returning. Component="
                        + info.getTargetComponent());
                return false;
            }
            info.bitmap = bitmap;
            return true;
        }
    }

    /** Tries to parse the icon from blob, or null on failure */
    @Nullable
    public BitmapInfo parseIconBlob(BaseIconFactory iconFactory) {
        try {
            return iconBlob == null ? null
                    : iconFactory.createIconBitmap(
                            decodeByteArray(iconBlob, 0, iconBlob.length),
                            /* isFullBleed **/ isBlobFullBleed
                    );
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode byte array for info " + itemInfo, e);
            return null;
        }
    }
}
