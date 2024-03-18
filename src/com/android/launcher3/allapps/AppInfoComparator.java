/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.LabelComparator;

import java.util.Comparator;

/**
 * A comparator to arrange items based on user profiles.
 */
public class AppInfoComparator implements Comparator<AppInfo> {

    private final UserCache mUserManager;
    private final UserHandle mMyUser;
    private final LabelComparator mLabelComparator;

    public AppInfoComparator(Context context) {
        mUserManager = UserCache.INSTANCE.get(context);
        mMyUser = Process.myUserHandle();
        mLabelComparator = new LabelComparator();
    }

    @Override
    public int compare(AppInfo a, AppInfo b) {
        // Order by the title in the current locale
        int result = mLabelComparator.compare(getSortingTitle(a), getSortingTitle(b));
        if (result != 0) {
            return result;
        }

        // If labels are same, compare component names
        result = a.componentName.compareTo(b.componentName);
        if (result != 0) {
            return result;
        }

        if (mMyUser.equals(a.user)) {
            return -1;
        } else {
            Long aUserSerial = mUserManager.getSerialNumberForUser(a.user);
            Long bUserSerial = mUserManager.getSerialNumberForUser(b.user);
            return aUserSerial.compareTo(bUserSerial);
        }
    }

    private String getSortingTitle(AppInfo info) {
        if (!TextUtils.isEmpty(info.appTitle)) {
            return info.appTitle.toString();
        }
        if (info.title != null) {
            return info.title.toString();
        }
        return "";
    }
}
