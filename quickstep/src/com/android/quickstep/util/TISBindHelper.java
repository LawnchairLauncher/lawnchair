/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.TouchInteractionService.TISBinder;

import java.util.function.Consumer;

/**
 * Utility class to simplify binding to {@link TouchInteractionService}
 */
public class TISBindHelper implements ServiceConnection {

    private static final String TAG = "TISBindHelper";

    private static final long BACKOFF_MILLIS = 1000;

    // Max backoff caps at 5 mins
    private static final long MAX_BACKOFF_MILLIS = 10 * 60 * 1000;

    private final Handler mHandler = new Handler();
    private final Runnable mConnectionRunnable = this::internalBindToTIS;
    private final Context mContext;
    private final Consumer<TISBinder> mConnectionCallback;

    private short mConnectionAttempts;
    private boolean mTisServiceBound;

    public TISBindHelper(Context context, Consumer<TISBinder> connectionCallback) {
        mContext = context;
        mConnectionCallback = connectionCallback;
        internalBindToTIS();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        if (!(iBinder instanceof TISBinder)) {
            // Seems like there can be a race condition when user unlocks, which kills the TIS
            // process and re-starts it. I guess in the meantime service can be connected to
            // a killed TIS? Either way, unbind and try to re-connect in that case.
            internalUnbindToTIS();
            mHandler.postDelayed(mConnectionRunnable, BACKOFF_MILLIS);
            return;
        }

        Log.d(TAG, "TIS service connected");
        mConnectionCallback.accept((TISBinder) iBinder);
        resetServiceBindRetryState();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.w(TAG, "TIS binding died");
        internalBindToTIS();
    }


    /**
     * Binds to {@link TouchInteractionService}. If the binding fails, attempts to retry via
     * {@link #mConnectionRunnable}. Unbind via {@link #internalUnbindToTIS()}
     */
    private void internalBindToTIS() {
        mTisServiceBound = mContext.bindService(new Intent(mContext, TouchInteractionService.class),
                this, 0);
        if (mTisServiceBound) {
            resetServiceBindRetryState();
            return;
        }

        Log.w(TAG, "Retrying TIS Binder connection attempt: " + mConnectionAttempts);
        final long timeoutMs = (long) Math.min(
                Math.scalb(BACKOFF_MILLIS, mConnectionAttempts), MAX_BACKOFF_MILLIS);
        mHandler.postDelayed(mConnectionRunnable, timeoutMs);
        mConnectionAttempts++;
    }

    /** See {@link #internalBindToTIS()} */
    private void internalUnbindToTIS() {
        if (mTisServiceBound) {
            mContext.unbindService(this);
            mTisServiceBound = false;
        }
    }

    private void resetServiceBindRetryState() {
        if (mHandler.hasCallbacks(mConnectionRunnable)) {
            mHandler.removeCallbacks(mConnectionRunnable);
        }
        mConnectionAttempts = 0;
    }

    /**
     * Called when the activity is destroyed to clear the binding
     */
    public void onDestroy() {
        internalUnbindToTIS();
        resetServiceBindRetryState();
    }
}
