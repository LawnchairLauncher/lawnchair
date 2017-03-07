/*
 * Copyright (C) 2014 The Android Open Source Project
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

package ch.deletescape.lawnchair.compat;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.UserHandle;

public class UserHandleCompat {
    private UserHandle mUser;

    private UserHandleCompat(UserHandle user) {
        mUser = user;
    }

    private UserHandleCompat() {
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static UserHandleCompat myUserHandle() {
        return new UserHandleCompat(android.os.Process.myUserHandle());
    }

    public static UserHandleCompat fromUser(UserHandle user) {
        if (user == null) {
            return null;
        } else {
            return new UserHandleCompat(user);
        }
    }

    public UserHandle getUser() {
        return mUser;
    }

    @Override
    public String toString() {
        return mUser.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UserHandleCompat)) {
            return false;
        }
        return mUser.equals(((UserHandleCompat) other).mUser);
    }

    @Override
    public int hashCode() {
        return mUser.hashCode();
    }

    /**
     * Adds {@link UserHandle} to the intent in for L or above.
     * Pre-L the launcher doesn't support showing apps for multiple
     * profiles so this is a no-op.
     */
    public void addToIntent(Intent intent, String name) {
        if (mUser != null) {
            intent.putExtra(name, mUser);
        }
    }

    public static UserHandleCompat fromIntent(Intent intent) {
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (user != null) {
            return UserHandleCompat.fromUser(user);
        }
        return null;
    }
}
