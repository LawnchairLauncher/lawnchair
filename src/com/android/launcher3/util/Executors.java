/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.THREAD_PRIORITY_FOREGROUND;

import android.os.Looper;
import android.os.Process;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Various different executors used in Launcher
 *
 * @deprecated To help promote adoption of dagger and other dependency injection frameworks,
 * this class is deprecated. Please use dagger to inject executors instead via the
 * {@link com.android.launcher3.concurrent} package. See go/launcher-executors-module for more
 * details.
 */
@Deprecated
public class Executors {

    private static final int POOL_SIZE =
            Math.max(Runtime.getRuntime().availableProcessors(), 2);
    private static final int KEEP_ALIVE = 1;

    /** Dedicated executor instances for work depending on other packages. */
    private static final Map<String, LooperExecutor> PACKAGE_EXECUTORS = new ConcurrentHashMap<>();

    /**
     * An {@link ThreadPoolExecutor} to be used with async task with no limit on the queue size.
     *
     * @deprecated Use {@link com.android.launcher3.concurrent.ExecutorsModule} to inject an
     * executor annotated with
     * {@link com.android.launcher3.concurrent.annotations.ThreadPool} instead.
     */
    public static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    /**
     * An {@link LooperExecutor} to be used with async task where order is important.
     *
     * @deprecated Use {@link com.android.launcher3.concurrent.ExecutorsModule} to inject an
     * executor annotated with {@link com.android.launcher3.concurrent.annotations.Background}
     * instead.
     */
    public static final LooperExecutor ORDERED_BG_EXECUTOR =
            new LooperExecutor("BackgroundExecutor", THREAD_PRIORITY_BACKGROUND);

    /**
     * Returns the executor for running tasks on the main thread.
     *
     * @deprecated Use {@link com.android.launcher3.concurrent.ExecutorsModule} to inject an
     * executor annotated with {@link com.android.launcher3.concurrent.annotations.Ui} instead.
     */
    public static final LooperExecutor MAIN_EXECUTOR =
            new LooperExecutor(Looper.getMainLooper(), THREAD_PRIORITY_FOREGROUND);

    /**
     * A background executor for using time sensitive actions where user is waiting for response.
     *
     * @deprecated Use {@link com.android.launcher3.concurrent.ExecutorsModule} to inject an
     * executor annotated with {@link
     * com.android.launcher3.concurrent.annotations.LightweightBackground} instead.
     */
    public static final LooperExecutor UI_HELPER_EXECUTOR =
            new LooperExecutor("UiThreadHelper", Process.THREAD_PRIORITY_FOREGROUND);

    /**
     * A background executor for running tasks that are not time sensitive, typically for data
     * transformations.
     *
     * @deprecated Use {@link com.android.launcher3.concurrent.ExecutorsModule} to inject an
     * executor annotated with {@link
     * com.android.launcher3.concurrent.annotations.LightweightBackground} instead.
     */
    public static final LooperExecutor DATA_HELPER_EXECUTOR =
            new LooperExecutor("DataThreadHelper", Process.THREAD_PRIORITY_DEFAULT);

    /**
     * Executor used for running Launcher model related tasks (eg loading icons or updated db)
     */
    public static final LooperExecutor MODEL_EXECUTOR = new LooperExecutor("launcher-loader");

    /**
     * Returns and caches a single thread executor for a given package.
     *
     * @deprecated Prefer using an executor annotated from the {@link
     * com.android.launcher3.concurrent} package.
     * @param packageName Package associated with the executor.
     */
    public static LooperExecutor getPackageExecutor(String packageName) {
        return PACKAGE_EXECUTORS.computeIfAbsent(packageName, LooperExecutor::new);
    }

    /**
     * A simple ThreadFactory to set the thread name and priority when used with executors.
     *
     * @deprecated Prefer using an executor annotated from the
     * {@link com.android.launcher3.concurrent} package.
     */
    public static class SimpleThreadFactory implements ThreadFactory {

        private final int mPriority;
        private final String mNamePrefix;

        private final AtomicInteger mCount = new AtomicInteger(0);

        public SimpleThreadFactory(String namePrefix, int priority) {
            mNamePrefix = namePrefix;
            mPriority = priority;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(mPriority);
                runnable.run();
            }, mNamePrefix + mCount.incrementAndGet());
            return t;
        }
    }
}
