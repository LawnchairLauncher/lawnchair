/**
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

package com.android.launcher3.uioverrides;

import static android.os.IBinder.FLAG_ONEWAY;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.MainThread;

import java.util.HashSet;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A binder proxy transaction listener for tracking non-whitelisted binder calls.
 */
public class DejankBinderTracker implements Binder.ProxyTransactListener {
    private static final String TAG = "DejankBinderTracker";

    private static final Object sLock = new Object();
    private static final HashSet<String> sWhitelistedFrameworkClasses = new HashSet<>();
    static {
        // Common IPCs that are ok to block the main thread.
        sWhitelistedFrameworkClasses.add("android.view.IWindowSession");
        sWhitelistedFrameworkClasses.add("android.os.IPowerManager");
    }
    private static boolean sTemporarilyIgnoreTracking = false;

    // Used by the client to limit binder tracking to specific regions
    private static boolean sTrackingAllowed = false;

    private BiConsumer<String, Integer> mUnexpectedTransactionCallback;
    private boolean mIsTracking = false;

    /**
     * Temporarily ignore blocking binder calls for the duration of this {@link Runnable}.
     */
    @MainThread
    public static void whitelistIpcs(Runnable runnable) {
        sTemporarilyIgnoreTracking = true;
        runnable.run();
        sTemporarilyIgnoreTracking = false;
    }

    /**
     * Temporarily ignore blocking binder calls for the duration of this {@link Supplier}.
     */
    @MainThread
    public static <T> T whitelistIpcs(Supplier<T> supplier) {
        sTemporarilyIgnoreTracking = true;
        T value = supplier.get();
        sTemporarilyIgnoreTracking = false;
        return value;
    }

    /**
     * Enables binder tracking during a test.
     */
    @MainThread
    public static void allowBinderTrackingInTests() {
        sTrackingAllowed = true;
    }

    /**
     * Disables binder tracking during a test.
     */
    @MainThread
    public static void disallowBinderTrackingInTests() {
        sTrackingAllowed = false;
    }

    public DejankBinderTracker(BiConsumer<String, Integer> unexpectedTransactionCallback) {
        mUnexpectedTransactionCallback = unexpectedTransactionCallback;
    }

    @MainThread
    public void startTracking() {
        if (!Build.TYPE.toLowerCase(Locale.ROOT).contains("debug")
                && !Build.TYPE.toLowerCase(Locale.ROOT).equals("eng")) {
            Log.wtf(TAG, "Unexpected use of binder tracker in non-debug build", new Exception());
            return;
        }
        if (mIsTracking) {
            return;
        }
        mIsTracking = true;
        Binder.setProxyTransactListener(this);
    }

    @MainThread
    public void stopTracking() {
        if (!mIsTracking) {
            return;
        }
        mIsTracking = false;
        Binder.setProxyTransactListener(null);
    }

    // Override the hidden Binder#onTransactStarted method
    public synchronized Object onTransactStarted(IBinder binder, int transactionCode, int flags) {
        if (!mIsTracking
                || !sTrackingAllowed
                || sTemporarilyIgnoreTracking
                || (flags & FLAG_ONEWAY) == FLAG_ONEWAY
                || !isMainThread()) {
            return null;
        }

        String descriptor;
        try {
            descriptor = binder.getInterfaceDescriptor();
            if (sWhitelistedFrameworkClasses.contains(descriptor)) {
                return null;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            descriptor = binder.getClass().getSimpleName();
        }

        mUnexpectedTransactionCallback.accept(descriptor, transactionCode);
        return null;
    }

    @Override
    public Object onTransactStarted(IBinder binder, int transactionCode) {
        // Do nothing
        return null;
    }

    @Override
    public void onTransactEnded(Object session) {
        // Do nothing
    }

    public static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }
}
