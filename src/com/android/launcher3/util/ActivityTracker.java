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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;

import java.lang.ref.WeakReference;

/**
 * Helper class to statically track activity creation
 */
public final class ActivityTracker<T extends BaseActivity> implements Runnable {

    private WeakReference<T> mCurrentActivity = new WeakReference<>(null);
    private WeakReference<SchedulerCallback<T>> mPendingCallback = new WeakReference<>(null);

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

    public void schedule(SchedulerCallback<? extends T> callback) {
        synchronized (this) {
            mPendingCallback = new WeakReference<>((SchedulerCallback<T>) callback);
        }
        MAIN_EXECUTOR.execute(this);
    }

    @Override
    public void run() {
        T activity = mCurrentActivity.get();
        if (activity != null) {
            initIfPending(activity, activity.isStarted());
        }
    }

    public boolean initIfPending(T activity, boolean alreadyOnHome) {
        SchedulerCallback<T> pendingCallback = mPendingCallback.get();
        if (pendingCallback != null) {
            if (!pendingCallback.init(activity, alreadyOnHome)) {
                clearReference(pendingCallback);
            }
            return true;
        }
        return false;
    }

    public boolean clearReference(SchedulerCallback<? extends T> handler) {
        synchronized (this) {
            if (mPendingCallback.get() == handler) {
                mPendingCallback.clear();
                return true;
            }
            return false;
        }
    }

    public boolean hasPending() {
        return mPendingCallback.get() != null;
    }

    public boolean handleCreate(T activity) {
        mCurrentActivity = new WeakReference<>(activity);
        return handleIntent(activity, activity.getIntent(), false, false);
    }

    public boolean handleNewIntent(T activity, Intent intent) {
        return handleIntent(activity, intent, activity.isStarted(), true);
    }

    private boolean handleIntent(
            T activity, Intent intent, boolean alreadyOnHome, boolean explicitIntent) {
        boolean result = false;
        if (intent != null && intent.getExtras() != null) {
            IBinder stateBinder = intent.getExtras().getBinder(EXTRA_SCHEDULER_CALLBACK);
            if (stateBinder instanceof ObjectWrapper) {
                SchedulerCallback<T> handler =
                        ((ObjectWrapper<SchedulerCallback>) stateBinder).get();
                if (!handler.init(activity, alreadyOnHome)) {
                    intent.getExtras().remove(EXTRA_SCHEDULER_CALLBACK);
                }
                result = true;
            }
        }
        if (!result && !explicitIntent) {
            result = initIfPending(activity, alreadyOnHome);
        }
        return result;
    }

    public interface SchedulerCallback<T extends BaseActivity> {

        boolean init(T activity, boolean alreadyOnHome);

        default Intent addToIntent(Intent intent) {
            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_SCHEDULER_CALLBACK, ObjectWrapper.wrap(this));
            intent.putExtras(extras);
            return intent;
        }
    }
}
