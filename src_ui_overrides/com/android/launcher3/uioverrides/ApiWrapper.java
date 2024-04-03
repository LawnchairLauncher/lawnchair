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

package com.android.launcher3.uioverrides;

import android.app.ActivityOptions;
import android.app.Person;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.ColorDrawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.UserIconInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A wrapper for the hidden API calls
 */
public class ApiWrapper {

    public static final boolean TASKBAR_DRAWN_IN_PROCESS = false;

    public static Person[] getPersons(ShortcutInfo si) {
        return Utilities.EMPTY_PERSON_ARRAY;
    }

    public static Map<String, LauncherActivityInfo> getActivityOverrides(Context context) {
        return Collections.emptyMap();
    }

    /**
     * Creates an ActivityOptions to play fade-out animation on closing targets
     */
    public static ActivityOptions createFadeOutAnimOptions(Context context) {
        return ActivityOptions.makeCustomAnimation(context, 0, android.R.anim.fade_out);
    }

    /**
     * Returns a map of all users on the device to their corresponding UI properties
     */
    public static Map<UserHandle, UserIconInfo> queryAllUsers(Context context) {
        UserManager um = context.getSystemService(UserManager.class);
        Map<UserHandle, UserIconInfo> users = new ArrayMap<>();
        List<UserHandle> usersActual = um.getUserProfiles();
        if (usersActual != null) {
            for (UserHandle user : usersActual) {
                long serial = um.getSerialNumberForUser(user);

                // Simple check to check if the provided user is work profile
                // TODO: Migrate to a better platform API
                NoopDrawable d = new NoopDrawable();
                boolean isWork = (d != context.getPackageManager().getUserBadgedIcon(d, user));
                UserIconInfo info = new UserIconInfo(
                        user,
                        isWork ? UserIconInfo.TYPE_WORK : UserIconInfo.TYPE_MAIN,
                        serial);
                users.put(user, info);
            }
        }
        return users;
    }

    private static class NoopDrawable extends ColorDrawable {
        @Override
        public int getIntrinsicHeight() {
            return 1;
        }

        @Override
        public int getIntrinsicWidth() {
            return 1;
        }
    }
}
