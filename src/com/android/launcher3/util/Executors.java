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

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Various different executors used in Launcher
 */
public class Executors {

    // These values are same as that in {@link AsyncTask}.
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;

    /**
     * An {@link Executor} to be used with async task with no limit on the queue size.
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    /**
     * Returns the executor for running tasks on the main thread.
     */
    public static final LooperExecutor MAIN_EXECUTOR =
            new LooperExecutor(Looper.getMainLooper());

    /**
     * A background executor for using time sensitive actions where user is waiting for response.
     */
    public static final LooperExecutor UI_HELPER_EXECUTOR =
            new LooperExecutor(createAndStartNewForegroundLooper("UiThreadHelper"));

    /**
     * Utility method to get a started handler thread statically
     */
    public static Looper createAndStartNewLooper(String name) {
        return createAndStartNewLooper(name, Process.THREAD_PRIORITY_DEFAULT);
    }

    /**
     * Utility method to get a started handler thread statically with the provided priority
     */
    public static Looper createAndStartNewLooper(String name, int priority) {
        HandlerThread thread = new HandlerThread(name, priority);
        thread.start();
        return thread.getLooper();
    }

    /**
     * Similar to {@link #createAndStartNewLooper(String)}, but starts the thread with
     * foreground priority.
     * Think before using
     */
    public static Looper createAndStartNewForegroundLooper(String name) {
        return createAndStartNewLooper(name, Process.THREAD_PRIORITY_FOREGROUND);
    }

    /**
     * Executor used for running Launcher model related tasks (eg loading icons or updated db)
     */
    public static final LooperExecutor MODEL_EXECUTOR =
            new LooperExecutor(createAndStartNewLooper("launcher-loader"));
}
