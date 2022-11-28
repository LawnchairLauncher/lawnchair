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
package com.android.launcher3.util;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class to statically track activity creation
 * @param <T> The activity type to track
 */
public final class ActivityTracker<T extends BaseActivity> {

    public static final int TYPE_INIT = 0;
    public static final int TYPE_DESTROY = 1;
    public static final int TYPE_BOTH = 2;

    private WeakReference<T> mCurrentActivity = new WeakReference<>(null);
    private CopyOnWriteArrayList<SchedulerCallback<T>> mActivityReadyCallbacks =
            new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<SchedulerCallback<T>> mActivityDestroyedCallbacks =
            new CopyOnWriteArrayList<>();

    @Nullable
    public <R extends T> R getCreatedActivity() {
        return (R) mCurrentActivity.get();
    }

    public void onActivityDestroyed(T activity) {
        if (mCurrentActivity.get() == activity) {
            mCurrentActivity.clear();
        }
        for (SchedulerCallback<T> cb : mActivityDestroyedCallbacks) {
            cb.onActivityDestroyed();
            unregisterCallback(cb, TYPE_DESTROY);
        }
    }

    /** Registers an activity create callback. */
    public void registerCallback(SchedulerCallback<T> callback) {
        registerCallback(callback, TYPE_INIT);
    }

    /**
     * Call {@link SchedulerCallback#onActivityReady(BaseActivity, boolean)} when the
     * activity is ready and/or {@link SchedulerCallback#onActivityDestroyed()} when the activity
     * is destroyed.
     *
     * If type is {@link ActivityTracker#TYPE_INIT} TYPE_INIT or
     * {@link ActivityTracker#TYPE_BOTH} and the activity is already created, this
     * {@link SchedulerCallback#onActivityReady(BaseActivity, boolean)} is called immediately.
     *
     * If type is {@link ActivityTracker#TYPE_DESTROY} or
     * {@link ActivityTracker#TYPE_BOTH} and the activity is already destroyed,
     * {@link SchedulerCallback#onActivityDestroyed()} is called immediately.
     *
     * The tracker maintains a strong ref to the callbacks, so it is up to the caller to return
     * {@code false} in {@link SchedulerCallback#onActivityReady(BaseActivity, boolean)} OR to
     * unregister the callback explicitly.
     *
     * @param callback The callback to call init() or cleanUp() on when the activity is ready or
     *                 destroyed.
     * @param type whether to use this callback on activity create, destroy or both.
     */
    public void registerCallback(SchedulerCallback<T> callback, int type) {
        T activity = mCurrentActivity.get();
        if (type == TYPE_INIT || type == TYPE_BOTH) {
            mActivityReadyCallbacks.add(callback);
            if (activity != null) {
                if (!callback.onActivityReady(activity, activity.isStarted())) {
                    unregisterCallback(callback, TYPE_INIT);
                }
            }
        }
        if (type == TYPE_DESTROY || type == TYPE_BOTH) {
            mActivityDestroyedCallbacks.add(callback);
            if (activity == null) {
                callback.onActivityDestroyed();
                unregisterCallback(callback, TYPE_DESTROY);
            }
        }
    }

    /**
     * Unregisters a registered activity create callback.
     */
    public void unregisterCallback(SchedulerCallback<T> callback) {
        unregisterCallback(callback, TYPE_INIT);
    }

    /**
     * Unregisters a registered callback.
     */
    public void unregisterCallback(SchedulerCallback<T> callback, int type) {
        if (type == TYPE_INIT || type == TYPE_BOTH) {
            mActivityReadyCallbacks.remove(callback);
        }
        if (type == TYPE_DESTROY || type == TYPE_BOTH) {
            mActivityDestroyedCallbacks.remove(callback);
        }
    }

    public boolean handleCreate(T activity) {
        mCurrentActivity = new WeakReference<>(activity);
        return handleIntent(activity, false /* alreadyOnHome */);
    }

    public boolean handleNewIntent(T activity) {
        return handleIntent(activity, activity.isStarted());
    }

    private boolean handleIntent(T activity, boolean alreadyOnHome) {
        boolean handled = false;
        for (SchedulerCallback<T> cb : mActivityReadyCallbacks) {
            if (!cb.onActivityReady(activity, alreadyOnHome)) {
                // Callback doesn't want any more updates
                unregisterCallback(cb);
            }
            handled = true;
        }
        return handled;
    }

    public interface SchedulerCallback<T extends BaseActivity> {

        /**
         * Called when the activity is ready.
         * @param alreadyOnHome Whether the activity is already started.
         * @return Whether to continue receiving callbacks (i.e. if the activity is recreated).
         */
        boolean onActivityReady(T activity, boolean alreadyOnHome);

        /**
         * Called then the activity gets destroyed.
         */
        default void onActivityDestroyed() { }
    }
}
