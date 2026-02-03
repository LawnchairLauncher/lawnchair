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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PatternMatcher;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;
import java.util.function.Consumer;

public class SimpleBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "SimpleBroadcastReceiver";
    // Keeps a strong reference to the context.
    private final Context mContext;

    private final Consumer<Intent> mIntentConsumer;

    // Handler to register/unregister broadcast receiver
    private final Handler mHandler;

    public SimpleBroadcastReceiver(@NonNull Context context, LooperExecutor looperExecutor,
            Consumer<Intent> intentConsumer) {
        this(context, looperExecutor.getHandler(), intentConsumer);
    }

    public SimpleBroadcastReceiver(@NonNull Context context, Handler handler,
            Consumer<Intent> intentConsumer) {
        mContext = context;
        mIntentConsumer = intentConsumer;
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mIntentConsumer.accept(intent);
    }

    /** Calls {@link #register(Runnable, String...)} with null completionCallback. */
    @AnyThread
    public void register(String... actions) {
        register(null, actions);
    }

    /**
     * Calls {@link #register(Runnable, int, String...)} with null completionCallback.
     */
    @AnyThread
    public void register(int flags, String... actions) {
        register(null, flags, actions);
    }

    /**
     * Register broadcast receiver. If this method is called on the same looper with mHandler's
     * looper, then register will be called synchronously. Otherwise asynchronously. This ensures
     * register happens on {@link #mHandler}'s looper.
     *
     * @param completionCallback callback that will be triggered after registration is completed,
     *                           caller usually pass this callback to check if states has changed
     *                           while registerReceiver() is executed on a binder call.
     */
    @AnyThread
    public void register(@Nullable Runnable completionCallback, String... actions) {
        if (Looper.myLooper() == mHandler.getLooper()) {
            registerInternal(mContext, completionCallback, actions);
        } else {
            mHandler.post(() -> registerInternal(mContext, completionCallback, actions));
        }
    }

    /** Register broadcast receiver and run completion callback if passed. */
    @AnyThread
    private void registerInternal(
            @NonNull Context context, @Nullable Runnable completionCallback, String... actions) {
        ContextCompat.registerReceiver(context, this, getFilter(actions),
            ContextCompat.RECEIVER_NOT_EXPORTED);
        if (completionCallback != null) {
            completionCallback.run();
        }
    }

    /**
     * Same as {@link #register(Runnable, String...)} above but with additional flags
     * params utilizine the original {@link Context}.
     */
    @AnyThread
    public void register(@Nullable Runnable completionCallback, int flags, String... actions) {
        if (Looper.myLooper() == mHandler.getLooper()) {
            registerInternal(mContext, completionCallback, flags, actions);
        } else {
            mHandler.post(() -> registerInternal(mContext, completionCallback, flags, actions));
        }
    }

    /** Register broadcast receiver and run completion callback if passed. */
    @AnyThread
    private void registerInternal(
            @NonNull Context context, @Nullable Runnable completionCallback, int flags,
            String... actions) {
        context.registerReceiver(this, getFilter(actions), flags);
        if (completionCallback != null) {
            completionCallback.run();
        }
    }

    /**
     * Same as {@link #register(Runnable, int, String...)} above but with additional permission
     * params utilizine the original {@link Context}.
     */
    @AnyThread
    public void register(@Nullable Runnable completionCallback,
                         String broadcastPermission, int flags, String... actions) {
        if (Looper.myLooper() == mHandler.getLooper()) {
            registerInternal(mContext, completionCallback, broadcastPermission, flags, actions);
        } else {
            mHandler.post(() -> registerInternal(mContext, completionCallback, broadcastPermission,
                    flags, actions));
        }
    }

    /** Register broadcast receiver with permission and run completion callback if passed. */
    @AnyThread
    private void registerInternal(
            @NonNull Context context, @Nullable Runnable completionCallback,
            String broadcastPermission, int flags, String... actions) {
        context.registerReceiver(this, getFilter(actions), broadcastPermission, null, flags);
        if (completionCallback != null) {
            completionCallback.run();
        }
    }

    /** Same as {@link #register(Runnable, String...)} above but with pkg name. */
    @AnyThread
    public void registerPkgActions(@Nullable String pkg, String... actions) {
        if (Looper.myLooper() == mHandler.getLooper()) {
            mContext.registerReceiver(this, getPackageFilter(pkg, actions));
        } else {
            mHandler.post(() -> {
                mContext.registerReceiver(this, getPackageFilter(pkg, actions));
            });
        }
    }

    /**
     * Unregister broadcast receiver. If this method is called on the same looper with mHandler's
     * looper, then unregister will be called synchronously. Otherwise asynchronously. This ensures
     * unregister happens on {@link #mHandler}'s looper.
     */
    @AnyThread
    public void unregisterReceiverSafely() {
        if (Looper.myLooper() == mHandler.getLooper()) {
            unregisterReceiverSafelyInternal(mContext);
        } else {
            mHandler.post(() -> {
                unregisterReceiverSafelyInternal(mContext);
            });
        }
    }

    /** Unregister broadcast receiver ignoring any errors. */
    @AnyThread
    private void unregisterReceiverSafelyInternal(@NonNull Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            // It was probably never registered or already unregistered. Ignore.
        }
    }

    /**
     * Creates an intent filter to listen for actions with a specific package in the data field.
     */
    public static IntentFilter getPackageFilter(String pkg, String... actions) {
        IntentFilter filter = getFilter(actions);
        filter.addDataScheme("package");
        if (!TextUtils.isEmpty(pkg)) {
            filter.addDataSchemeSpecificPart(pkg, PatternMatcher.PATTERN_LITERAL);
        }
        return filter;
    }

    private static IntentFilter getFilter(String... actions) {
        IntentFilter filter = new IntentFilter();
        for (String action : actions) {
            filter.addAction(action);
        }
        return filter;
    }
}
