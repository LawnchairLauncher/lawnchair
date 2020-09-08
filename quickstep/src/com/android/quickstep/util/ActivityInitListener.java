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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.ActivityTracker.SchedulerCallback;

import java.util.function.BiPredicate;

public class ActivityInitListener<T extends BaseActivity> implements SchedulerCallback<T> {

    private BiPredicate<T, Boolean> mOnInitListener;
    private final ActivityTracker<T> mActivityTracker;

    private boolean mIsRegistered = false;

    /**
     * @param onInitListener a callback made when the activity is initialized. The callback should
     *                       return true to continue receiving callbacks (ie. for if the activity is
     *                       recreated).
     */
    public ActivityInitListener(BiPredicate<T, Boolean> onInitListener,
            ActivityTracker<T> tracker) {
        mOnInitListener = onInitListener;
        mActivityTracker = tracker;
    }

    @Override
    public final boolean init(T activity, boolean alreadyOnHome) {
        if (!mIsRegistered) {
            return false;
        }
        return handleInit(activity, alreadyOnHome);
    }

    protected boolean handleInit(T activity, boolean alreadyOnHome) {
        return mOnInitListener.test(activity, alreadyOnHome);
    }

    /**
     * Registers the activity-created listener. If the activity is already created, then the
     * callback provided in the constructor will be called synchronously.
     * @param intent The intent that will be used to initialize the activity, if the activity
     *               doesn't already exist. We add the callback as an extra on this intent.
     */
    public void register(Intent intent) {
        mIsRegistered = true;
        mActivityTracker.runCallbackWhenActivityExists(this, intent);
    }

    /**
     * After calling this, we won't {@link #init} even when the activity is ready.
     */
    public void unregister() {
        mIsRegistered = false;
        mOnInitListener = null;
    }

    /**
     * Starts the given intent with the provided animation. Unlike {@link #register(Intent)}, this
     * method will not call {@link #init} if the activity already exists, it will only call it when
     * we get handleIntent() for the provided intent that we're starting.
     */
    public void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
            Context context, Handler handler, long duration) {
        mIsRegistered = true;

        Bundle options = animProvider.toActivityOptions(handler, duration, context).toBundle();
        context.startActivity(addToIntent(new Intent(intent)), options);
    }
}
