/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.dagger

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.android.systemui.dagger.qualifiers.Background
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Dagger Module for per-display thread handling. */
// TODO(b/407594919) - Adapt this to use new concurrency module.
@Module
object LauncherConcurrencyModule {
    // Slow BG executor can potentially affect UI if UI is waiting for an updated state from this
    // thread
    private const val BG_SLOW_DISPATCH_THRESHOLD = 1000L
    private const val BG_SLOW_DELIVERY_THRESHOLD = 1000L

    /** Background Looper */
    @Provides
    @LauncherAppSingleton
    @Background
    fun provideBgLooper(): Looper {
        val thread = HandlerThread("LauncherBg", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        thread
            .getLooper()
            .setSlowLogThresholdMs(BG_SLOW_DISPATCH_THRESHOLD, BG_SLOW_DELIVERY_THRESHOLD)
        return thread.getLooper()
    }

    /**
     * Background Handler.
     *
     * Prefer the Background Executor when possible.
     */
    @Provides
    @LauncherAppSingleton
    @Background
    fun provideBgHandler(@Background bgLooper: Looper): Handler = Handler(bgLooper)

    /** CoroutineDispatcher provider. */
    @Provides fun provideCoroutineDispatcherProvider(): DispatcherProvider = ProductionDispatchers

    /** Background CoroutineScope provider. */
    @Provides
    @LauncherAppSingleton
    @Background
    fun provideBgCoroutineScope(dispatcherProvider: DispatcherProvider) =
        CoroutineScope(
            SupervisorJob() + dispatcherProvider.ioBackground + CoroutineName("LauncherBg")
        )
}
