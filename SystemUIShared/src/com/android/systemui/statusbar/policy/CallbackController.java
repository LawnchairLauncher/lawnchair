/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

public interface CallbackController<T> {

    /** Add a callback */
    void addCallback(@NonNull T listener);

    /** Remove a callback */
    void removeCallback(@NonNull T listener);

    /**
     * Wrapper to {@link #addCallback(Object)} when a lifecycle is in the resumed state
     * and {@link #removeCallback(Object)} when not resumed automatically.
     */
    default T observe(LifecycleOwner owner, T listener) {
        return observe(owner.getLifecycle(), listener);
    }

    /**
     * Wrapper to {@link #addCallback(Object)} when a lifecycle is in the resumed state
     * and {@link #removeCallback(Object)} when not resumed automatically.
     */
    default T observe(Lifecycle lifecycle, T listener) {
        lifecycle.addObserver((LifecycleEventObserver) (lifecycleOwner, event) -> {
            if (event == Event.ON_RESUME) {
                addCallback(listener);
            } else if (event == Event.ON_PAUSE) {
                removeCallback(listener);
            }
        });
        return listener;
    }
}
