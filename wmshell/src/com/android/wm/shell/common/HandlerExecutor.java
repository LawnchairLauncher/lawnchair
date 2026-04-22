/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.common;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.os.Process.setThreadPriority;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import java.util.function.BiConsumer;

/** Executor implementation which is backed by a Handler. */
public class HandlerExecutor implements ShellExecutor {
    @NonNull
    private final Handler mHandler;
    // See android.os.Process#THREAD_PRIORITY_*
    private final int mDefaultThreadPriority;
    private final int mBoostedThreadPriority;
    // Number of current requests to boost thread priority
    private int mBoostCount;
    private final Object mBoostLock = new Object();
    // Default function for setting thread priority (tid, priority)
    private BiConsumer<Integer, Integer> mSetThreadPriorityFn =
            HandlerExecutor::setThreadPriorityInternal;

    public HandlerExecutor(@NonNull Handler handler) {
        this(handler, THREAD_PRIORITY_DEFAULT, THREAD_PRIORITY_DEFAULT);
    }

    /**
     * Used only if this executor can be boosted, if so, it can be boosted to the given
     * {@param boostPriority}.
     */
    public HandlerExecutor(@NonNull Handler handler, int defaultThreadPriority,
            int boostedThreadPriority) {
        mHandler = handler;
        mDefaultThreadPriority = defaultThreadPriority;
        mBoostedThreadPriority = boostedThreadPriority;
    }

    @VisibleForTesting
    void replaceSetThreadPriorityFn(BiConsumer<Integer, Integer> setThreadPriorityFn) {
        mSetThreadPriorityFn = setThreadPriorityFn;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        if (mHandler.getLooper().isCurrentThread()) {
            command.run();
            return;
        }
        if (!mHandler.post(command)) {
            throw new RuntimeException(mHandler + " is probably exiting");
        }
    }

    @Override
    public void executeDelayed(@NonNull Runnable r, long delayMillis) {
        if (!mHandler.postDelayed(r, delayMillis)) {
            throw new RuntimeException(mHandler + " is probably exiting");
        }
    }

    @Override
    public void removeCallbacks(@NonNull Runnable r) {
        mHandler.removeCallbacks(r);
    }

    @Override
    public boolean hasCallback(Runnable r) {
        return mHandler.hasCallbacks(r);
    }

    @Override
    public void setBoost() {
        synchronized (mBoostLock) {
            if (mDefaultThreadPriority == mBoostedThreadPriority) {
                // Nothing to boost
                return;
            }
            if (mBoostCount == 0) {
                mSetThreadPriorityFn.accept(
                        ((HandlerThread) mHandler.getLooper().getThread()).getThreadId(),
                        mBoostedThreadPriority);
            }
            mBoostCount++;
        }
    }

    @Override
    public void resetBoost() {
        synchronized (mBoostLock) {
            mBoostCount--;
            if (mBoostCount == 0) {
                mSetThreadPriorityFn.accept(
                        ((HandlerThread) mHandler.getLooper().getThread()).getThreadId(),
                        mDefaultThreadPriority);
            }
        }
    }

    @Override
    public boolean isBoosted() {
        synchronized (mBoostLock) {
            return mBoostCount > 0;
        }
    }

    @Override
    @NonNull
    public Looper getLooper() {
        return mHandler.getLooper();
    }

    @Override
    public void assertCurrentThread() {
        if (!mHandler.getLooper().isCurrentThread()) {
            throw new IllegalStateException("must be called on " + mHandler);
        }
    }

    private static void setThreadPriorityInternal(Integer tid, Integer priority) {
        setThreadPriority(tid, priority);
    }
}
