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

import com.android.launcher3.IconProvider;
import com.android.launcher3.R;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Caching logic for LauncherActivityInfo.
 */
public class LauncherActivityCachingLogic
        implements CachingLogic<LauncherActivityInfo>, ResourceBasedOverride {

    /**
     * Creates and returns a new instance
     */
    public static LauncherActivityCachingLogic newInstance(Context context) {
        return Overrides.getObject(LauncherActivityCachingLogic.class, context,
                R.string.launcher_activity_logic_class);
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
    public BitmapInfo loadIcon(Context context, LauncherActivityInfo object) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            return li.createBadgedIconBitmap(
                    IconProvider.INSTANCE.get(context)
                            .getIcon(object, li.mFillResIconDpi, true /* flattenDrawable */),
                    object.getUser(), object.getApplicationInfo().targetSdkVersion);
        }
    }
}
