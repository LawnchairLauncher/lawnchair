/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Offloads work from other threads by running it in a background thread.
 */
public class BackgroundExecutor {

    private static final BackgroundExecutor sInstance = new BackgroundExecutor();

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);

    /**
     * @return the static instance of the background executor.
     */
    public static BackgroundExecutor get() {
        return sInstance;
    }

    /**
     * Runs the given {@param callable} on one of the background executor threads.
     */
    public <T> Future<T> submit(Callable<T> callable) {
        return mExecutorService.submit(callable);
    }

    /**
     * Runs the given {@param runnable} on one of the background executor threads.
     */
    public Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    /**
     * Runs the given {@param runnable} on one of the background executor threads. Return
     * {@param result} when the future is resolved.
     */
    public <T> Future<T> submit(Runnable runnable, T result) {
        return mExecutorService.submit(runnable, result);
    }
}
