/**
 * Copyright (c) 2018 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

/** An entry that represents a single activity that can be launched. */
public  class AppEntry {

    private String mLabel;
    private Drawable mIcon;
    private Intent mLaunchIntent;

    AppEntry(ResolveInfo info, PackageManager packageManager) {
        mLabel = info.loadLabel(packageManager).toString();
        mIcon = info.loadIcon(packageManager);
        mLaunchIntent = new Intent();
        mLaunchIntent.setComponent(new ComponentName(info.activityInfo.packageName,
                info.activityInfo.name));
    }

    String getLabel() {
        return mLabel;
    }

    Drawable getIcon() {
        return mIcon;
    }

    Intent getLaunchIntent() { return mLaunchIntent; }

    ComponentName getComponentName() {
        return mLaunchIntent.getComponent();
    }

    @Override
    public String toString() {
        return mLabel;
    }
}
