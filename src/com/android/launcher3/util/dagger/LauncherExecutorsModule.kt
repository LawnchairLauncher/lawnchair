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
package com.android.launcher3.util.dagger

import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.concurrent.annotations.ThreadPool

import com.android.launcher3.concurrent.ExecutorsModule

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LooperExecutor

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors

import dagger.Module
import dagger.Binds
import dagger.Provides

/**
 * Module that provides the executors for Launcher3 as per the [ExecutorsModule]
 * interface.
 */
@Module
abstract class LauncherExecutorsModule {

    // Include UI LooperExecutor bindings here since they're not provided
    // by the ExecutorsModule interface.
    @Binds
    @LauncherAppSingleton
    @Ui
    abstract fun provideUiExecutor(
        @Ui executor: LooperExecutor
    ): ListeningExecutorService

    companion object {
        @Provides
        @LauncherAppSingleton
        @Ui
        fun provideUiLooperExecutor(): LooperExecutor {
            return Executors.MAIN_EXECUTOR
        }

        @Provides
        @LauncherAppSingleton
        @LightweightBackground(LightweightBackgroundPriority.UI)
        fun provideUiExecutorService(): ListeningExecutorService {
            return Executors.UI_HELPER_EXECUTOR
        }

        @Provides
        @LauncherAppSingleton
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        fun provideDataExecutorService(): ListeningExecutorService {
            return Executors.DATA_HELPER_EXECUTOR
        }

        @Provides
        @LauncherAppSingleton
        @Background
        fun provideBackgroundExecutorService(): ListeningExecutorService {
            return Executors.ORDERED_BG_EXECUTOR
        }

        @Provides
        @LauncherAppSingleton
        @ThreadPool
        fun provideThreadPoolExecutorService(): ListeningExecutorService {
            return MoreExecutors.listeningDecorator(Executors.THREAD_POOL_EXECUTOR)
        }
    }
}
