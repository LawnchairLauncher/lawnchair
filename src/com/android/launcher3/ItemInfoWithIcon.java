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

package com.android.launcher3;

import static com.android.launcher3.icons.BitmapInfo.LOW_RES_ICON;

import android.graphics.Bitmap;

import com.android.launcher3.icons.BitmapInfo;

/**
 * Represents an ItemInfo which also holds an icon.
 */
public abstract class ItemInfoWithIcon extends ItemInfo {

    public static final String TAG = "ItemInfoDebug";

    /**
     * A bitmap version of the application icon.
     */
    public Bitmap iconBitmap;

    /**
     * Dominant color in the {@link #iconBitmap}.
     */
    public int iconColor;

    /**
     * Indicates that the icon is disabled due to safe mode restrictions.
     */
    public static final int FLAG_DISABLED_SAFEMODE = 1 << 0;

    /**
     * Indicates that the icon is disabled as the app is not available.
     */
    public static final int FLAG_DISABLED_NOT_AVAILABLE = 1 << 1;

    /**
     * Indicates that the icon is disabled as the app is suspended
     */
    public static final int FLAG_DISABLED_SUSPENDED = 1 << 2;

    /**
     * Indicates that the icon is disabled as the user is in quiet mode.
     */
    public static final int FLAG_DISABLED_QUIET_USER = 1 << 3;

    /**
     * Indicates that the icon is disabled as the publisher has disabled the actual shortcut.
     */
    public static final int FLAG_DISABLED_BY_PUBLISHER = 1 << 4;

    /**
     * Indicates that the icon is disabled as the user partition is currently locked.
     */
    public static final int FLAG_DISABLED_LOCKED_USER = 1 << 5;

    public static final int FLAG_DISABLED_MASK = FLAG_DISABLED_SAFEMODE |
            FLAG_DISABLED_NOT_AVAILABLE | FLAG_DISABLED_SUSPENDED |
            FLAG_DISABLED_QUIET_USER | FLAG_DISABLED_BY_PUBLISHER | FLAG_DISABLED_LOCKED_USER;

    /**
     * The item points to a system app.
     */
    public static final int FLAG_SYSTEM_YES = 1 << 6;

    /**
     * The item points to a non system app.
     */
    public static final int FLAG_SYSTEM_NO = 1 << 7;

    public static final int FLAG_SYSTEM_MASK = FLAG_SYSTEM_YES | FLAG_SYSTEM_NO;

    /**
     * Flag indicating that the icon is an {@link android.graphics.drawable.AdaptiveIconDrawable}
     * that can be optimized in various way.
     */
    public static final int FLAG_ADAPTIVE_ICON = 1 << 8;

    /**
     * Flag indicating that the icon is badged.
     */
    public static final int FLAG_ICON_BADGED = 1 << 9;

    /**
     * Status associated with the system state of the underlying item. This is calculated every
     * time a new info is created and not persisted on the disk.
     */
    public int runtimeStatusFlags = 0;

    protected ItemInfoWithIcon() { }

    protected ItemInfoWithIcon(ItemInfoWithIcon info) {
        super(info);
        iconBitmap = info.iconBitmap;
        iconColor = info.iconColor;
        runtimeStatusFlags = info.runtimeStatusFlags;
    }

    @Override
    public boolean isDisabled() {
        return (runtimeStatusFlags & FLAG_DISABLED_MASK) != 0;
    }

    /**
     * Indicates whether we're using a low res icon
     */
    public boolean usingLowResIcon() {
        return iconBitmap == LOW_RES_ICON;
    }

    public void applyFrom(BitmapInfo info) {
        iconBitmap = info.icon;
        iconColor = info.color;
    }

    /**
     * @return a copy of this
     */
    public abstract ItemInfoWithIcon clone();
}
