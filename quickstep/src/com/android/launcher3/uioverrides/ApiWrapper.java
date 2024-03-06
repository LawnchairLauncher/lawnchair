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
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.ColorDrawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.window.RemoteTransition;

import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.UserIconInfo;
import com.android.quickstep.util.FadeOutRemoteTransition;

import java.util.List;
import java.util.Map;

/**
 * A wrapper for the hidden API calls
 */
public class ApiWrapper {

    public static final boolean TASKBAR_DRAWN_IN_PROCESS = true;

    public static Person[] getPersons(ShortcutInfo si) {
        Person[] persons = si.getPersons();
        return persons == null ? Utilities.EMPTY_PERSON_ARRAY : persons;
    }

    public static Map<String, LauncherActivityInfo> getActivityOverrides(Context context) {
        return context.getSystemService(LauncherApps.class).getActivityOverrides();
    }

    /**
     * Creates an ActivityOptions to play fade-out animation on closing targets
     */
    public static ActivityOptions createFadeOutAnimOptions(Context context) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setRemoteTransition(new RemoteTransition(new FadeOutRemoteTransition()));
        return options;
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
                if (android.os.Flags.allowPrivateProfile() && Flags.enablePrivateSpace()) {
                    LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                    LauncherUserInfo launcherUserInfo = launcherApps.getLauncherUserInfo(user);
                    // UserTypes not supported in Launcher are deemed to be the current
                    // Foreground User.
                    int userType = switch (launcherUserInfo.getUserType()) {
                        case UserManager.USER_TYPE_PROFILE_MANAGED -> UserIconInfo.TYPE_WORK;
                        case UserManager.USER_TYPE_PROFILE_CLONE -> UserIconInfo.TYPE_CLONED;
                        case UserManager.USER_TYPE_PROFILE_PRIVATE -> UserIconInfo.TYPE_PRIVATE;
                        default -> UserIconInfo.TYPE_MAIN;
                    };
                    long serial = launcherUserInfo.getUserSerialNumber();
                    users.put(user, new UserIconInfo(user, userType, serial));
                } else {
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
