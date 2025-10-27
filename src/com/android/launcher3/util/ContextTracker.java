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

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class to statically track activity creation
 * @param <CONTEXT> The context type to track
 */
public abstract class ContextTracker<CONTEXT extends ActivityContext> {

    private static final String TAG = "ContextTracker";

    private WeakReference<CONTEXT> mCurrentContext = new WeakReference<>(null);
    private final CopyOnWriteArrayList<SchedulerCallback<CONTEXT>> mCallbacks =
            new CopyOnWriteArrayList<>();

    @Nullable
    public <R extends CONTEXT> R getCreatedContext() {
        return (R) mCurrentContext.get();
    }

    public void onContextDestroyed(CONTEXT context) {
        if (mCurrentContext.get() == context) {
            mCurrentContext.clear();
        }
    }

    public abstract boolean isHomeStarted(CONTEXT context);

    /**
     * Call {@link SchedulerCallback#init(ActivityContext, boolean)} when the
     * context is ready. If the context is already created, this is called immediately.
     *
     * The tracker maintains a strong ref to the callback, so it is up to the caller to return
     * {@code false} in the callback OR to unregister the callback explicitly.
     *
     * @param callback The callback to call init() on when the context is ready.
     */
    public void registerCallback(SchedulerCallback<CONTEXT> callback, String reasonString) {
        Log.d(TAG, "Registering callback: " + callback + ", reason=" + reasonString);
        CONTEXT context = mCurrentContext.get();
        mCallbacks.add(callback);
        if (context != null) {
            if (!callback.init(context, isHomeStarted(context))) {
                unregisterCallback(callback, "ContextTracker.registerCallback: Intent handled");
            }
        }
    }

    /**
     * Unregisters a registered callback.
     */
    public void unregisterCallback(SchedulerCallback<CONTEXT> callback, String reasonString) {
        Log.d(TAG, "Unregistering callback: " + callback + ", reason=" + reasonString);
        mCallbacks.remove(callback);
    }

    public boolean handleCreate(CONTEXT context) {
        mCurrentContext = new WeakReference<>(context);
        return handleCreate(context, isHomeStarted(context));
    }

    public boolean handleNewIntent(CONTEXT context) {
        return handleCreate(context, isHomeStarted(context));
    }

    private boolean handleCreate(CONTEXT context, boolean isHomeStarted) {
        boolean handled = false;
        if (!mCallbacks.isEmpty()) {
            Log.d(TAG, "handleIntent: mCallbacks=" + mCallbacks);
        }
        for (SchedulerCallback<CONTEXT> cb : mCallbacks) {
            if (!cb.init(context, isHomeStarted)) {
                // Callback doesn't want any more updates
                unregisterCallback(cb, "ContextTracker.handleIntent: Intent handled");
            }
            handled = true;
        }
        return handled;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "ContextTracker:");
        writer.println(prefix + "\tmCurrentContext=" + mCurrentContext.get());
        writer.println(prefix + "\tmCallbacks=" + mCallbacks);
    }

    public interface SchedulerCallback<T extends ActivityContext> {

        /**
         * Called when the context is ready.
         * @param isHomeStarted Whether the home activity is already started.
         * @return Whether to continue receiving callbacks (i.e. if the context is recreated).
         */
        boolean init(T context, boolean isHomeStarted);
    }

    public static final class ActivityTracker<T extends BaseActivity> extends ContextTracker<T> {

        @Override
        public boolean isHomeStarted(T context) {
            return context.isStarted();
        }
    }
}
