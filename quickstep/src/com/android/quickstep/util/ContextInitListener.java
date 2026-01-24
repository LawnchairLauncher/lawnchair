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

import com.android.launcher3.util.ContextTracker;
import com.android.launcher3.util.ContextTracker.SchedulerCallback;
import com.android.launcher3.views.ActivityContext;

import java.util.function.BiPredicate;

public class ContextInitListener<CONTEXT extends ActivityContext> implements
        SchedulerCallback<CONTEXT> {

    private BiPredicate<CONTEXT, Boolean> mOnInitListener;
    private final ContextTracker<CONTEXT> mContextTracker;

    private boolean mIsRegistered = false;

    /**
     * @param onInitListener a callback made when the activity is initialized. The callback should
     *                       return true to continue receiving callbacks (ie. for if the activity is
     *                       recreated).
     */
    public ContextInitListener(BiPredicate<CONTEXT, Boolean> onInitListener,
            ContextTracker<CONTEXT> tracker) {
        mOnInitListener = onInitListener;
        mContextTracker = tracker;
    }

    @Override
    public final boolean init(CONTEXT activity, boolean isHomeStarted) {
        if (!mIsRegistered) {
            // Don't receive any more updates
            return false;
        }
        return handleInit(activity, isHomeStarted);
    }

    protected boolean handleInit(CONTEXT activity, boolean isHomeStarted) {
        return mOnInitListener.test(activity, isHomeStarted);
    }

    /**
     * Registers the activity-created listener. If the activity is already created, then the
     * callback provided in the constructor will be called synchronously.
     */
    public void register(String reasonString) {
        mIsRegistered = true;
        mContextTracker.registerCallback(this, reasonString);
    }

    /**
     * After calling this, we won't {@link #init} even when the activity is ready.
     */
    public void unregister(String reasonString) {
        mContextTracker.unregisterCallback(this, reasonString);
        mIsRegistered = false;
        mOnInitListener = null;
    }
}
