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

package ch.deletescape.lawnchair.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import android.os.UserHandle;
import ch.deletescape.lawnchair.compat.UserManagerCompat;

/**
 * Wrapper class for {@link android.content.pm.ShortcutInfo}, representing deep shortcuts into apps.
 * <p>
 * Not to be confused with {@link ch.deletescape.lawnchair.ShortcutInfo}.
 */
@TargetApi(Build.VERSION_CODES.N)
public class ShortcutInfoCompat {
    private static final String INTENT_CATEGORY = "ch.deletescape.lawnchair.DEEP_SHORTCUT";
    public static final String EXTRA_SHORTCUT_ID = "shortcut_id";
    private String packageName;
    private String id;
    private CharSequence shortLabel;
    private CharSequence longLabel;
    private ComponentName activity;
    private UserHandle userHandle;
    private int rank;
    private boolean enabled;
    private CharSequence disabledMessage;
    private Drawable icon;

    private ShortcutInfo mShortcutInfo;

    public ShortcutInfoCompat(ShortcutInfo shortcutInfo) {
        mShortcutInfo = shortcutInfo;
    }

    public ShortcutInfoCompat(String packageName, String id, CharSequence shortLabel, CharSequence longLabel,
                              ComponentName activity, UserHandle userHandle, int rank, boolean enabled, CharSequence disabledMessage, Drawable icon){
        this.packageName = packageName;
        this.id = id;
        this.shortLabel = shortLabel;
        this.longLabel = longLabel;
        this.activity = activity;
        this.userHandle = userHandle;
        this.rank = rank;
        this.enabled = enabled;
        this.disabledMessage = disabledMessage;
        this.icon = icon;
    }

    @TargetApi(Build.VERSION_CODES.N)
    public Intent makeIntent(Context context) {
        long serialNumber = UserManagerCompat.getInstance(context)
                .getSerialNumberForUser(getUserHandle());
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(INTENT_CATEGORY)
                .setComponent(getActivity())
                .setPackage(getPackage())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(ItemInfo.EXTRA_PROFILE, serialNumber)
                .putExtra(EXTRA_SHORTCUT_ID, getId());
    }

    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    public String getPackage() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getPackage();
        } else {
            return packageName;
        }
    }

    public String getId() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getId();
        } else {
            return id;
        }
    }

    public CharSequence getShortLabel() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getShortLabel();
        } else {
            return shortLabel;
        }
    }

    public CharSequence getLongLabel() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getLongLabel();
        } else {
            return longLabel;
        }
    }

    public long getLastChangedTimestamp() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getLastChangedTimestamp();
        } else {
            return 0;
        }
    }

    public ComponentName getActivity() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getActivity();
        } else {
            return activity;
        }
    }

    public UserHandle getUserHandle() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getUserHandle();
        } else {
            return userHandle;
        }
    }

    public boolean hasKeyFieldsOnly() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.hasKeyFieldsOnly();
        } else {
            return false;
        }
    }

    public boolean isPinned() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.isPinned();
        } else {
            return false;
        }
    }

    public boolean isDeclaredInManifest() {
        if(Utilities.isNycOrAbove()) {
            return mShortcutInfo.isDeclaredInManifest();
        } else {
            return true;
        }
    }

    public boolean isEnabled() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.isEnabled();
        } else {
            return enabled;
        }
    }

    public boolean isDynamic() {
        if(Utilities.isNycOrAbove()) {
            return mShortcutInfo.isDynamic();
        } else {
            return false;
        }
    }

    public int getRank() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getRank();
        } else {
            return rank;
        }
    }

    public CharSequence getDisabledMessage() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.getDisabledMessage();
        } else {
            return disabledMessage;
        }
    }

    public Drawable getIcon(){
        return icon;
    }

    @Override
    public String toString() {
        if(Utilities.isNycOrAbove()){
            return mShortcutInfo.toString();
        } else {
            return super.toString();
        }
    }

    public LauncherActivityInfoCompat getActivityInfo(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(getActivity());
        return LauncherActivityInfoCompat.create(context, getUserHandle(), intent);
    }
}
