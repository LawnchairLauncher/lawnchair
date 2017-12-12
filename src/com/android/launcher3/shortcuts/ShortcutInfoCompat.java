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

package com.android.launcher3.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.UserHandle;

/**
 * Wrapper class for {@link android.content.pm.ShortcutInfo}, representing deep shortcuts into apps.
 *
 * Not to be confused with {@link com.android.launcher3.ShortcutInfo}.
 */
@TargetApi(Build.VERSION_CODES.N)
public class ShortcutInfoCompat {
    private static final String INTENT_CATEGORY = "com.android.launcher3.DEEP_SHORTCUT";
    public static final String EXTRA_SHORTCUT_ID = "shortcut_id";

    private ShortcutInfo mShortcutInfo;

    public ShortcutInfoCompat(ShortcutInfo shortcutInfo) {
        mShortcutInfo = shortcutInfo;
    }

    @TargetApi(Build.VERSION_CODES.N)
    public Intent makeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(INTENT_CATEGORY)
                .setComponent(getActivity())
                .setPackage(getPackage())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(EXTRA_SHORTCUT_ID, getId());
    }

    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    public String getPackage() {
        return mShortcutInfo.getPackage();
    }

    public String getId() {
        return mShortcutInfo.getId();
    }

    public CharSequence getShortLabel() {
        return mShortcutInfo.getShortLabel();
    }

    public CharSequence getLongLabel() {
        return mShortcutInfo.getLongLabel();
    }

    public long getLastChangedTimestamp() {
        return mShortcutInfo.getLastChangedTimestamp();
    }

    public ComponentName getActivity() {
        return mShortcutInfo.getActivity();
    }

    public UserHandle getUserHandle() {
        return mShortcutInfo.getUserHandle();
    }

    public boolean hasKeyFieldsOnly() {
        return mShortcutInfo.hasKeyFieldsOnly();
    }

    public boolean isPinned() {
        return mShortcutInfo.isPinned();
    }

    public boolean isDeclaredInManifest() {
        return mShortcutInfo.isDeclaredInManifest();
    }

    public boolean isEnabled() {
        return mShortcutInfo.isEnabled();
    }

    public boolean isDynamic() {
        return mShortcutInfo.isDynamic();
    }

    public int getRank() {
        return mShortcutInfo.getRank();
    }

    public CharSequence getDisabledMessage() {
        return mShortcutInfo.getDisabledMessage();
    }

    @Override
    public String toString() {
        return mShortcutInfo.toString();
    }
}
