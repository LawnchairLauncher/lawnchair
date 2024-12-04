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

package com.android.wm.shell.dagger;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.THREAD_PRIORITY_DISPLAY;
import static android.os.Process.THREAD_PRIORITY_TOP_APP_BOOST;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Trace;
import android.view.Choreographer;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalMainThread;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.annotations.ShellSplashscreenThread;

import dagger.Module;
import dagger.Provides;

/**
 * Provides basic concurrency-related dependencies from {@link com.android.wm.shell}, these
 * dependencies are only accessible from components within the WM subcomponent.
 */
@Module
public abstract class WMShellConcurrencyModule {

    private static final int MSGQ_SLOW_DELIVERY_THRESHOLD_MS = 30;
    private static final int MSGQ_SLOW_DISPATCH_THRESHOLD_MS = 30;

    /**
     * Returns whether to enable a separate shell thread for the shell features.
     */
    public static boolean enableShellMainThread(Context context) {
        return context.getResources().getBoolean(R.bool.config_enableShellMainThread);
    }

    //
    // Shell Concurrency - Components used for managing threading in the Shell and SysUI
    //


    /**
     * Provide a SysUI main-thread Handler.
     *
     * Prefer the Main Executor when possible.
     */
    @Provides
    @ExternalMainThread
    public static Handler provideMainHandler() {
        return new Handler(Looper.getMainLooper());
    }

    /**
     * Provide a SysUI main-thread Executor.
     */
    @WMSingleton
    @Provides
    @ExternalMainThread
    public static ShellExecutor provideSysUIMainExecutor(
            @ExternalMainThread Handler sysuiMainHandler) {
        return new HandlerExecutor(sysuiMainHandler);
    }

    /**
     * Creates a shell main thread to be injected into the shell components.  This does not provide
     * the {@param HandleThread}, but is used to create the thread prior to initializing the
     * WM component, and is explicitly bound.
     *
     * See {@link com.android.systemui.SystemUIFactory#init(Context, boolean)}.
     */
    public static HandlerThread createShellMainThread() {
        HandlerThread mainThread = new HandlerThread("wmshell.main", THREAD_PRIORITY_DISPLAY);
        return mainThread;
    }

    /**
     * Shell main-thread Handler, don't use this unless really necessary (ie. need to dedupe
     * multiple types of messages, etc.)
     *
     * @param mainThread If non-null, this thread is expected to be started already
     */
    @WMSingleton
    @Provides
    @ShellMainThread
    public static Handler provideShellMainHandler(Context context,
            @Nullable @ShellMainThread HandlerThread mainThread,
            @ExternalMainThread Handler sysuiMainHandler) {
        if (enableShellMainThread(context)) {
            if (mainThread == null) {
                // If this thread wasn't pre-emptively started, then create and start it
                mainThread = createShellMainThread();
                mainThread.start();
            }
            if (Build.IS_DEBUGGABLE) {
                mainThread.getLooper().setTraceTag(Trace.TRACE_TAG_WINDOW_MANAGER);
                mainThread.getLooper().setSlowLogThresholdMs(MSGQ_SLOW_DISPATCH_THRESHOLD_MS,
                        MSGQ_SLOW_DELIVERY_THRESHOLD_MS);
            }
            return Handler.createAsync(mainThread.getLooper());
        }
        return sysuiMainHandler;
    }

    /**
     * Provide a Shell main-thread Executor.
     */
    @WMSingleton
    @Provides
    @ShellMainThread
    public static ShellExecutor provideShellMainExecutor(Context context,
            @ShellMainThread Handler mainHandler,
            @ExternalMainThread ShellExecutor sysuiMainExecutor) {
        if (enableShellMainThread(context)) {
            return new HandlerExecutor(mainHandler);
        }
        return sysuiMainExecutor;
    }

    /**
     * Provide a Shell main-thread {@link Choreographer} with the app vsync.
     *
     * @param executor the executor of the shell main thread
     */
    @WMSingleton
    @Provides
    @ShellMainThread
    public static Choreographer provideShellMainChoreographer(
            @ShellMainThread ShellExecutor executor) {
        try {
            final Choreographer[] choreographer = new Choreographer[1];
            executor.executeBlocking(() -> choreographer[0] = Choreographer.getInstance());
            return choreographer[0];
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to obtain main Choreographer.", e);
        }
    }

    /**
     * Provide a Shell animation-thread Executor.
     */
    @WMSingleton
    @Provides
    @ShellAnimationThread
    public static ShellExecutor provideShellAnimationExecutor() {
         HandlerThread shellAnimationThread = new HandlerThread("wmshell.anim",
                 THREAD_PRIORITY_DISPLAY);
         shellAnimationThread.start();
        if (Build.IS_DEBUGGABLE) {
            shellAnimationThread.getLooper().setTraceTag(Trace.TRACE_TAG_WINDOW_MANAGER);
            shellAnimationThread.getLooper().setSlowLogThresholdMs(MSGQ_SLOW_DISPATCH_THRESHOLD_MS,
                    MSGQ_SLOW_DELIVERY_THRESHOLD_MS);
        }
         return new HandlerExecutor(Handler.createAsync(shellAnimationThread.getLooper()));
    }

    /**
     * Provides a Shell splashscreen-thread Executor
     */
    @WMSingleton
    @Provides
    @ShellSplashscreenThread
    public static ShellExecutor provideSplashScreenExecutor() {
        HandlerThread shellSplashscreenThread = new HandlerThread("wmshell.splashscreen",
                THREAD_PRIORITY_TOP_APP_BOOST);
        shellSplashscreenThread.start();
        return new HandlerExecutor(shellSplashscreenThread.getThreadHandler());
    }

    /**
     * Provides a Shell background thread Handler for low priority background tasks.
     */
    @WMSingleton
    @Provides
    @ShellBackgroundThread
    public static Handler provideSharedBackgroundHandler() {
        HandlerThread shellBackgroundThread = new HandlerThread("wmshell.background",
                THREAD_PRIORITY_BACKGROUND);
        shellBackgroundThread.start();
        return shellBackgroundThread.getThreadHandler();
    }

    /**
     * Provides a Shell background thread Executor for low priority background tasks.
     */
    @WMSingleton
    @Provides
    @ShellBackgroundThread
    public static ShellExecutor provideSharedBackgroundExecutor(
            @ShellBackgroundThread Handler handler) {
        return new HandlerExecutor(handler);
    }
}
