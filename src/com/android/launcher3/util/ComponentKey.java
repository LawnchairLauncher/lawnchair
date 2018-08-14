package com.android.launcher3.util;

/**
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

import android.content.ComponentName;
import android.os.UserHandle;

import java.util.Arrays;

public class ComponentKey {

    public final ComponentName componentName;
    public final UserHandle user;

    private final int mHashCode;

    public ComponentKey(ComponentName componentName, UserHandle user) {
        Preconditions.assertNotNull(componentName);
        Preconditions.assertNotNull(user);
        this.componentName = componentName;
        this.user = user;
        mHashCode = Arrays.hashCode(new Object[] {componentName, user});

    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object o) {
        ComponentKey other = (ComponentKey) o;
        return other.componentName.equals(componentName) && other.user.equals(user);
    }

    /**
     * Encodes a component key as a string of the form [flattenedComponentString#userId].
     */
    @Override
    public String toString() {
        return componentName.flattenToString() + "#" + user;
    }
}