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

package com.android.launcher3.compat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * {@link LauncherActivityInfoCompat} which loads its data only when needed.
 */
public class DeferredLauncherActivityInfo extends LauncherActivityInfoCompat {

    private final ComponentName mComponent;
    private final UserHandleCompat mUser;
    private final Context mContext;

    private LauncherActivityInfoCompat mActualInfo;

    public DeferredLauncherActivityInfo(
            ComponentName component, UserHandleCompat user, Context context) {
        mComponent = component;
        mUser = user;
        mContext = context;
    }

    @Override
    public ComponentName getComponentName() {
        return mComponent;
    }

    @Override
    public UserHandleCompat getUser() {
        return mUser;
    }

    private synchronized LauncherActivityInfoCompat getActualInfo() {
        if (mActualInfo == null) {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(mComponent);
            mActualInfo = LauncherAppsCompat.getInstance(mContext).resolveActivity(intent, mUser);
        }
        return mActualInfo;
    }

    @Override
    public CharSequence getLabel() {
        return getActualInfo().getLabel();
    }

    @Override
    public Drawable getIcon(int density) {
        return getActualInfo().getIcon(density);
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return getActualInfo().getApplicationInfo();
    }

    @Override
    public long getFirstInstallTime() {
        return getActualInfo().getFirstInstallTime();
    }
}
