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

package com.android.launcher3.compat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.android.launcher3.Utilities;

import java.util.List;

public abstract class UserManagerCompat {
    protected UserManagerCompat() {
    }

    public static UserManagerCompat getInstance(Context context) {
        if (Utilities.isLmpOrAbove()) {
            return new UserManagerCompatVL(context);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return new UserManagerCompatV17(context);
        } else {
            return new UserManagerCompatV16();
        }
    }

    public abstract List<UserHandleCompat> getUserProfiles();
    public abstract long getSerialNumberForUser(UserHandleCompat user);
    public abstract UserHandleCompat getUserForSerialNumber(long serialNumber);
    public abstract Drawable getBadgedDrawableForUser(Drawable unbadged, UserHandleCompat user);
    public abstract CharSequence getBadgedLabelForUser(CharSequence label, UserHandleCompat user);
}
