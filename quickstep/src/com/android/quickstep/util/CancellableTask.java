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
package com.android.quickstep.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

/**
 * Utility class to executore a task on background and post the result on UI thread
 */
public abstract class CancellableTask<T> implements Runnable {

    private boolean mCancelled = false;

    @Override
    public final void run() {
        if (mCancelled) {
            return;
        }
        T result = getResultOnBg();
        if (mCancelled) {
            return;
        }
        MAIN_EXECUTOR.execute(() -> {
            if (mCancelled) {
                return;
            }
            handleResult(result);
        });
    }

    /**
     * Called on the worker thread to process the request. The return object is passed to
     * {@link #handleResult(Object)}
     */
    @WorkerThread
    public abstract T getResultOnBg();

    /**
     * Called on the UI thread to handle the final result.
     * @param result
     */
    @UiThread
    public abstract void handleResult(T result);

    /**
     * Cancels the request. If it is called before {@link #handleResult(Object)}, that method
     * will not be called
     */
    public void cancel() {
        mCancelled = true;
    }
}
