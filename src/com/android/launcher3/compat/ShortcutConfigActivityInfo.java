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

package com.android.launcher3.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;

import java.lang.reflect.Method;

/**
 * Wrapper class for representing a shortcut configure activity.
 */
public abstract class ShortcutConfigActivityInfo {

    private static final String TAG = "SCActivityInfo";

    private final ComponentName mCn;
    private final UserHandle mUser;

    protected ShortcutConfigActivityInfo(ComponentName cn, UserHandle user) {
        mCn = cn;
        mUser = user;
    }

    public ComponentName getComponent() {
        return mCn;
    }

    public UserHandle getUser() {
        return mUser;
    }

    public int getItemType() {
        return LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }

    public abstract CharSequence getLabel();

    public abstract Drawable getFullResIcon(IconCache cache);

    /**
     * Return a shortcut info, if it can be created directly on drop, without requiring any
     * {@link #startConfigActivity(Activity, int)}.
     */
    public ShortcutInfo createShortcutInfo() {
        return null;
    }

    public boolean startConfigActivity(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT)
                .setComponent(getComponent());
        try {
            activity.startActivityForResult(intent, requestCode);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
        return false;
    }

    /**
     * Returns true if various properties ({@link #getLabel()}, {@link #getFullResIcon}) can
     * be safely persisted.
     */
    public boolean isPersistable() {
        return true;
    }

    static class ShortcutConfigActivityInfoVL extends ShortcutConfigActivityInfo {

        private final ActivityInfo mInfo;
        private final PackageManager mPm;


        public ShortcutConfigActivityInfoVL(ActivityInfo info, PackageManager pm) {
            super(new ComponentName(info.packageName, info.name), Process.myUserHandle());
            mInfo = info;
            mPm = pm;
        }

        @Override
        public CharSequence getLabel() {
            return mInfo.loadLabel(mPm);
        }

        @Override
        public Drawable getFullResIcon(IconCache cache) {
            return cache.getFullResIcon(mInfo);
        }
    }

    @TargetApi(26)
    static class ShortcutConfigActivityInfoVO extends ShortcutConfigActivityInfo {

        private final LauncherActivityInfo mInfo;

        public ShortcutConfigActivityInfoVO(LauncherActivityInfo info) {
            super(info.getComponentName(), info.getUser());
            mInfo = info;
        }

        @Override
        public CharSequence getLabel() {
            return mInfo.getLabel();
        }

        @Override
        public Drawable getFullResIcon(IconCache cache) {
            return cache.getFullResIcon(mInfo);
        }

        @Override
        public boolean startConfigActivity(Activity activity, int requestCode) {
            if (getUser().equals(Process.myUserHandle())) {
                return super.startConfigActivity(activity, requestCode);
            }
            try {
                Method m = LauncherApps.class.getDeclaredMethod(
                        "getShortcutConfigActivityIntent", LauncherActivityInfo.class);
                IntentSender is = (IntentSender) m.invoke(
                        activity.getSystemService(LauncherApps.class), mInfo);
                activity.startIntentSenderForResult(is, requestCode, null, 0, 0, 0);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error calling new API", e);
                return false;
            }
        }
    }
}
