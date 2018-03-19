/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.quickstep.ActivityControlHelper.ActivityInitListener;

import java.lang.ref.WeakReference;
import java.util.function.BiPredicate;

/**
 * Utility class to track create/destroy for RecentsActivity
 */
@TargetApi(Build.VERSION_CODES.P)
public class RecentsActivityTracker implements ActivityInitListener {

    private static final Object LOCK = new Object();
    private static WeakReference<RecentsActivityTracker> sTracker = new WeakReference<>(null);

    private final BiPredicate<RecentsActivity, Boolean> mOnInitListener;

    public RecentsActivityTracker(BiPredicate<RecentsActivity, Boolean> onInitListener) {
        mOnInitListener = onInitListener;
    }

    @Override
    public void register() {
        synchronized (LOCK) {
            sTracker = new WeakReference<>(this);
        }
    }

    @Override
    public void unregister() {
        synchronized (LOCK) {
            if (sTracker.get() == this) {
                sTracker.clear();
            }
        }
    }

    public static void onRecentsActivityCreate(RecentsActivity activity) {
        synchronized (LOCK) {
            RecentsActivityTracker tracker = sTracker.get();
            if (tracker != null && tracker.mOnInitListener.test(activity, false)) {
                sTracker.clear();
            }
        }
    }
}
