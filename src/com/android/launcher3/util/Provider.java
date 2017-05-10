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

package com.android.launcher3.util;

/**
 * Utility class to allow lazy initialization of objects.
 */
public abstract class Provider<T> {

    /**
     * Initializes and returns the object. This may contain expensive operations not suitable
     * to UI thread.
     */
    public abstract T get();

    public static <T> Provider<T> of (final T value) {
        return new Provider<T>() {
            @Override
            public T get() {
                return value;
            }
        };
    }
}
