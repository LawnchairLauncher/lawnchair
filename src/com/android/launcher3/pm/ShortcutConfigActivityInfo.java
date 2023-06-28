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

package com.android.launcher3.pm;

import static com.android.launcher3.Utilities.allowBGLaunch;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper class for representing a shortcut configure activity.
 */
public abstract class ShortcutConfigActivityInfo implements ComponentWithLabelAndIcon {

    private static final String TAG = "SCActivityInfo";

    private final ComponentName mCn;
    private final UserHandle mUser;

    protected ShortcutConfigActivityInfo(ComponentName cn, UserHandle user) {
        mCn = cn;
        mUser = user;
    }

    @Override
    public ComponentName getComponent() {
        return mCn;
    }

    @Override
    public UserHandle getUser() {
        return mUser;
    }

    public int getItemType() {
        return LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
    }

    @Override
    public abstract Drawable getFullResIcon(IconCache cache);

    /**
     * Return a WorkspaceItemInfo, if it can be created directly on drop, without requiring any
     * {@link #startConfigActivity(Activity, int)}.
     */
    public WorkspaceItemInfo createWorkspaceItemInfo() {
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
            Log.e(TAG, "Launcher does not have the permission to launch " + intent
                    + ". Make sure to create a MAIN intent-filter for the corresponding activity "
                    + "or use the exported attribute for this activity.", e);
        }
        return false;
    }

    /**
     * Returns true if various properties ({@link #getLabel(PackageManager)},
     * {@link #getFullResIcon}) can be safely persisted.
     */
    public boolean isPersistable() {
        return true;
    }

    @TargetApi(26)
    public static class ShortcutConfigActivityInfoVO extends ShortcutConfigActivityInfo {

        private final LauncherActivityInfo mInfo;

        public ShortcutConfigActivityInfoVO(LauncherActivityInfo info) {
            super(info.getComponentName(), info.getUser());
            mInfo = info;
        }

        @Override
        public CharSequence getLabel(PackageManager pm) {
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
            IntentSender is = activity.getSystemService(LauncherApps.class)
                    .getShortcutConfigActivityIntent(mInfo);
            ActivityOptions options = allowBGLaunch(ActivityOptions.makeBasic());
            try {
                activity.startIntentSenderForResult(is, requestCode, null, 0, 0, 0,
                        options.toBundle());
                return true;
            } catch (IntentSender.SendIntentException e) {
                Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    public static List<ShortcutConfigActivityInfo> queryList(
            Context context, @Nullable PackageUserKey packageUser) {
        List<ShortcutConfigActivityInfo> result = new ArrayList<>();
        final List<UserHandle> users;
        final String packageName;
        if (packageUser == null) {
            users = UserCache.INSTANCE.get(context).getUserProfiles();
            packageName = null;
        } else {
            users = Collections.singletonList(packageUser.mUser);
            packageName = packageUser.mPackageName;
        }
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        for (UserHandle user : users) {
            for (LauncherActivityInfo activityInfo :
                    launcherApps.getShortcutConfigActivityList(packageName, user)) {
                if (activityInfo.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.O) {
                    result.add(new ShortcutConfigActivityInfoVO(activityInfo));
                }
            }
        }
        return result;
    }
}
