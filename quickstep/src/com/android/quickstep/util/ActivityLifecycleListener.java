/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.ActivityTracker.SchedulerCallback;

import java.util.function.BiPredicate;

/**
 * Listener for activity initialized and/or destroyed.
 */
public class ActivityLifecycleListener<T extends BaseActivity> implements
        SchedulerCallback<T> {

    private static final String TAG = "ActivityLifecycleListener";

    @Nullable private final BiPredicate<T, Boolean> mOnInitListener;
    @Nullable private final Runnable mOnDestroyListener;
    private final ActivityTracker<T> mActivityTracker;

    private boolean mIsRegistered = false;

    /**
     * One or both of {@code onInitListener} and {@code onInitListener} must be provided, otherwise
     * the created instance will effectively be a no-op.
     *
     * @param onInitListener a callback made when the activity is initialized. The callback should
     *                       return true to continue receiving callbacks (ie. for if the activity is
     *                       recreated).
     * @param onDestroyListener a callback made when the activity is destroyed.
     */
    public ActivityLifecycleListener(
            @Nullable BiPredicate<T, Boolean> onInitListener,
            @Nullable Runnable onDestroyListener,
            ActivityTracker<T> tracker) {
        if (onInitListener == null && onDestroyListener == null) {
            throw new IllegalArgumentException("Both listeners cannot be null");
        }
        mOnInitListener = onInitListener;
        mOnDestroyListener = onDestroyListener;
        mActivityTracker = tracker;
    }

    @Override
    public final boolean onActivityReady(T activity, boolean alreadyOnHome) {
        if (!mIsRegistered) {
            // Don't receive any more updates
            return false;
        }
        return handleActivityReady(activity, alreadyOnHome);
    }

    protected boolean handleActivityReady(T activity, boolean alreadyOnHome) {
        if (mOnInitListener == null) {
            Log.e(TAG, "Cannot handle init: init listener is null", new Exception());
            return false;
        }
        return mOnInitListener.test(activity, alreadyOnHome);
    }

    @Override
    public void onActivityDestroyed() {
        if (mOnDestroyListener == null) {
            Log.e(TAG, "Cannot clean up: destroy listener is null", new Exception());
            return;
        }
        mOnDestroyListener.run();
    }

    /**
     * Registers the activity-created listener. If the activity is already created, then the
     * callback provided in the constructor will be called synchronously.
     */
    public void register() {
        mIsRegistered = true;
        mActivityTracker.registerCallback(this, getType());
    }

    /**
     * After calling this, we won't call {@link #onActivityReady} even when the activity is ready.
     */
    public void unregister() {
        mActivityTracker.unregisterCallback(this, getType());
        mIsRegistered = false;
    }

    private int getType() {
        return mOnInitListener != null && mOnDestroyListener != null
                ? ActivityTracker.TYPE_BOTH
                : (mOnInitListener != null
                        ? ActivityTracker.TYPE_INIT
                        : ActivityTracker.TYPE_DESTROY);
    }
}
