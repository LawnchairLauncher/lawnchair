/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;

import com.android.launcher3.icons.cache.CachingLogic;

public class LauncherActivtiyCachingLogic implements CachingLogic<LauncherActivityInfo> {

    private final IconCache mCache;

    public LauncherActivtiyCachingLogic(IconCache cache) {
        mCache = cache;
    }

    @Override
    public ComponentName getComponent(LauncherActivityInfo object) {
        return object.getComponentName();
    }

    @Override
    public UserHandle getUser(LauncherActivityInfo object) {
        return object.getUser();
    }

    @Override
    public CharSequence getLabel(LauncherActivityInfo object) {
        return object.getLabel();
    }

    @Override
    public void loadIcon(Context context, LauncherActivityInfo object,
            BitmapInfo target) {
        LauncherIcons li = LauncherIcons.obtain(context);
        li.createBadgedIconBitmap(mCache.getFullResIcon(object),
                object.getUser(), object.getApplicationInfo().targetSdkVersion).applyTo(target);
        li.recycle();
    }
}