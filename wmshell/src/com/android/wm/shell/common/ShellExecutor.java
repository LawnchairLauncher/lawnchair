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

import java.lang.reflect.Array;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Super basic Executor interface that adds support for delayed execution and removing callbacks.
 * Intended to wrap Handler while better-supporting testing.
 */
public interface ShellExecutor extends Executor {

    /**
     * Executes the given runnable. If the caller is running on the same looper as this executor,
     * the runnable must be executed immediately.
     */
    @Override
    void execute(Runnable runnable);

    /**
     * Executes the given runnable in a blocking call. If the caller is running on the same looper
     * as this executor, the runnable must be executed immediately.
     *
     * @throws InterruptedException if runnable does not return in the time specified by
     *                              {@param waitTimeout}
     */
    default void executeBlocking(Runnable runnable, int waitTimeout, TimeUnit waitTimeUnit)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        execute(() -> {
            runnable.run();
            latch.countDown();
        });
        latch.await(waitTimeout, waitTimeUnit);
    }

    /**
     * Convenience method to execute the blocking call with a default timeout.
     *
     * @throws InterruptedException if runnable does not return in the time specified by
     *                              {@param waitTimeout}
     */
    default void executeBlocking(Runnable runnable) throws InterruptedException {
        executeBlocking(runnable, 2, TimeUnit.SECONDS);
    }

    /**
     * Convenience method to execute the blocking call with a default timeout and returns a value.
     * Waits indefinitely for a typed result from a call.
     */
    default <T> T executeBlockingForResult(Supplier<T> runnable, Class clazz) {
        final T[] result = (T[]) Array.newInstance(clazz, 1);
        final CountDownLatch latch = new CountDownLatch(1);
        execute(() -> {
            result[0] = runnable.get();
            latch.countDown();
        });
        try {
            latch.await();
            return result[0];
        } catch (InterruptedException e) {
            return null;
        }
    }


    /**
     * See {@link android.os.Handler#postDelayed(Runnable, long)}.
     */
    void executeDelayed(Runnable runnable, long delayMillis);

    /**
     * See {@link android.os.Handler#removeCallbacks}.
     */
    void removeCallbacks(Runnable runnable);

    /**
     * See {@link android.os.Handler#hasCallbacks(Runnable)}.
     */
    boolean hasCallback(Runnable runnable);
}
