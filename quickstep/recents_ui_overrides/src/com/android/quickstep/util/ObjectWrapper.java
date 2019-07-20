/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import android.os.Binder;
import android.os.IBinder;

/**
 * Utility class to pass non-parcealable objects within same process using parcealable payload.
 *
 * It wraps the object in a binder as binders are singleton within a process
 */
public class ObjectWrapper<T> extends Binder {

    private final T mObject;

    public ObjectWrapper(T object) {
        mObject = object;
    }

    public T get() {
        return mObject;
    }

    public static IBinder wrap(Object obj) {
        return new ObjectWrapper<>(obj);
    }
}
