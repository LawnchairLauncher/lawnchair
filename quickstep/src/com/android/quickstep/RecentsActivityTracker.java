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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.util.RemoteAnimationProvider;

import java.lang.ref.WeakReference;
import java.util.function.BiPredicate;

/**
 * Utility class to track create/destroy for some {@link BaseRecentsActivity}.
 */
@TargetApi(Build.VERSION_CODES.P)
public class RecentsActivityTracker<T extends BaseRecentsActivity> implements ActivityInitListener {

    private static WeakReference<BaseRecentsActivity> sCurrentActivity =
            new WeakReference<>(null);
    private static final Scheduler sScheduler = new Scheduler();

    private final BiPredicate<T, Boolean> mOnInitListener;

    public RecentsActivityTracker(BiPredicate<T, Boolean> onInitListener) {
        mOnInitListener = onInitListener;
    }

    @Override
    public void register() {
        sScheduler.schedule(this);
    }

    @Override
    public void unregister() {
        sScheduler.clearReference(this);
    }

    private boolean init(T activity, boolean visible) {
        return mOnInitListener.test(activity, visible);
    }

    public static <T extends BaseRecentsActivity> T getCurrentActivity() {
        return (T) sCurrentActivity.get();
    }

    @Override
    public void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
            Context context, Handler handler, long duration) {
        register();

        Bundle options = animProvider.toActivityOptions(handler, duration, context).toBundle();
        context.startActivity(intent, options);
    }

    public static void onRecentsActivityCreate(BaseRecentsActivity activity) {
        sCurrentActivity = new WeakReference<>(activity);
        sScheduler.initIfPending(activity, false);
    }


    public static void onRecentsActivityNewIntent(BaseRecentsActivity activity) {
        sScheduler.initIfPending(activity, activity.isStarted());
    }

    public static void onRecentsActivityDestroy(BaseRecentsActivity activity) {
        if (sCurrentActivity.get() == activity) {
            sCurrentActivity.clear();
        }
    }


    private static class Scheduler implements Runnable {

        private WeakReference<RecentsActivityTracker> mPendingTracker = new WeakReference<>(null);

        public synchronized void schedule(RecentsActivityTracker tracker) {
            mPendingTracker = new WeakReference<>(tracker);
            MAIN_EXECUTOR.execute(this);
        }

        @Override
        public void run() {
            BaseRecentsActivity activity = sCurrentActivity.get();
            if (activity != null) {
                initIfPending(activity, activity.isStarted());
            }
        }

        public synchronized boolean initIfPending(BaseRecentsActivity activity,
                boolean alreadyOnHome) {
            RecentsActivityTracker tracker = mPendingTracker.get();
            if (tracker != null) {
                if (!tracker.init(activity, alreadyOnHome)) {
                    mPendingTracker.clear();
                }
                return true;
            }
            return false;
        }

        public synchronized boolean clearReference(RecentsActivityTracker tracker) {
            if (mPendingTracker.get() == tracker) {
                mPendingTracker.clear();
                return true;
            }
            return false;
        }
    }
}
