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

package com.android.launcher3.dragndrop;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.android.launcher3.IconCache;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PinItemRequestCompat;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;

/**
 * Extension of ShortcutConfigActivityInfo to be used in the confirmation prompt for pin item
 * request.
 */
@TargetApi(Build.VERSION_CODES.N_MR1)
class PinShortcutRequestActivityInfo extends ShortcutConfigActivityInfo {

    // Class name used in the target component, such that it will never represent an
    // actual existing class.
    private static final String DUMMY_COMPONENT_CLASS = "pinned-shortcut";

    private final PinItemRequestCompat mRequest;
    private final ShortcutInfo mInfo;
    private final Context mContext;

    public PinShortcutRequestActivityInfo(PinItemRequestCompat request, Context context) {
        super(new ComponentName(request.getShortcutInfo().getPackage(), DUMMY_COMPONENT_CLASS),
                request.getShortcutInfo().getUserHandle());
        mRequest = request;
        mInfo = request.getShortcutInfo();
        mContext = context;
    }

    @Override
    public int getItemType() {
        return LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
    }

    @Override
    public CharSequence getLabel() {
        return mInfo.getShortLabel();
    }

    @Override
    public Drawable getFullResIcon(IconCache cache) {
        return mContext.getSystemService(LauncherApps.class)
                .getShortcutIconDrawable(mInfo, LauncherAppState.getIDP(mContext).fillResIconDpi);
    }

    @Override
    public com.android.launcher3.ShortcutInfo createShortcutInfo() {
        // Total duration for the drop animation to complete.
        long duration = mContext.getResources().getInteger(R.integer.config_dropAnimMaxDuration) +
                Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT +
                mContext.getResources().getInteger(R.integer.config_overlayTransitionTime) / 2;
        // Delay the actual accept() call until the drop animation is complete.
        return LauncherAppsCompat.createShortcutInfoFromPinItemRequest(
                mContext, mRequest, duration);
    }

    @Override
    public boolean startConfigActivity(Activity activity, int requestCode) {
        return false;
    }

    @Override
    public boolean isPersistable() {
        return false;
    }
}
