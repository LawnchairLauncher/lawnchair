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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;

@TargetApi(Build.VERSION_CODES.M)
public class UserManagerCompatVM extends UserManagerCompatVL {

    UserManagerCompatVM(Context context) {
        super(context);
    }

    @Override
    public long getUserCreationTime(UserHandle user) {
        return mUserManager.getUserCreationTime(user);
    }
}
