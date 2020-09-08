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

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;

import java.lang.ref.WeakReference;

/**
 * Helper class to statically track activity creation
 * @param <T> The activity type to track
 */
public final class ActivityTracker<T extends BaseActivity> {

    private WeakReference<T> mCurrentActivity = new WeakReference<>(null);

    private static final String EXTRA_SCHEDULER_CALLBACK = "launcher.scheduler_callback";

    @Nullable
    public <R extends T> R getCreatedActivity() {
        return (R) mCurrentActivity.get();
    }

    public void onActivityDestroyed(T activity) {
        if (mCurrentActivity.get() == activity) {
            mCurrentActivity.clear();
        }
    }

    /**
     * Call {@link SchedulerCallback#init(BaseActivity, boolean)} when the activity is ready.
     * If the activity is already created, this is called immediately, otherwise we add the
     * callback as an extra on the intent, and will call init() when we get handleIntent().
     * @param callback The callback to call init() on when the activity is ready.
     * @param intent The intent that will be used to initialize the activity, if the activity
     *               doesn't already exist. We add the callback as an extra on this intent.
     */
    public void runCallbackWhenActivityExists(SchedulerCallback<T> callback, Intent intent) {
        T activity = mCurrentActivity.get();
        if (activity != null) {
            callback.init(activity, activity.isStarted());
        } else {
            callback.addToIntent(intent);
        }
    }

    public boolean handleCreate(T activity) {
        mCurrentActivity = new WeakReference<>(activity);
        return handleIntent(activity, activity.getIntent(), false);
    }

    public boolean handleNewIntent(T activity, Intent intent) {
        return handleIntent(activity, intent, activity.isStarted());
    }

    private boolean handleIntent(T activity, Intent intent, boolean alreadyOnHome) {
        if (intent != null && intent.getExtras() != null) {
            IBinder stateBinder = intent.getExtras().getBinder(EXTRA_SCHEDULER_CALLBACK);
            if (stateBinder instanceof ObjectWrapper) {
                SchedulerCallback<T> handler =
                        ((ObjectWrapper<SchedulerCallback>) stateBinder).get();
                if (!handler.init(activity, alreadyOnHome)) {
                    intent.getExtras().remove(EXTRA_SCHEDULER_CALLBACK);
                }
                return true;
            }
        }
        return false;
    }

    public interface SchedulerCallback<T extends BaseActivity> {

        /**
         * Called when the activity is ready.
         * @param alreadyOnHome Whether the activity is already started.
         * @return Whether to continue receiving callbacks (i.e. if the activity is recreated).
         */
        boolean init(T activity, boolean alreadyOnHome);

        /**
         * Adds this callback as an extra on the intent, so we can retrieve it in handleIntent() and
         * call {@link #init}. The intent should be used to start the activity after calling this
         * method in order for us to get handleIntent().
         */
        default Intent addToIntent(Intent intent) {
            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_SCHEDULER_CALLBACK, ObjectWrapper.wrap(this));
            intent.putExtras(extras);
            return intent;
        }
    }
}
