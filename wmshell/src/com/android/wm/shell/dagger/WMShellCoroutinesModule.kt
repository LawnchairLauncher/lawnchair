/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.dagger

import android.os.Handler
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellDesktopThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import dagger.Module
import dagger.Provides
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Providers for various WmShell-specific coroutines-related constructs.
 *
 * Providers of [MainCoroutineDispatcher] intentionally creates the dispatcher with a [Handler]
 * backing it instead of a [ShellExecutor] because [ShellExecutor.asCoroutineDispatcher] will
 * create a [CoroutineDispatcher] whose [CoroutineDispatcher.isDispatchNeeded] is effectively never
 * dispatching. This is because even if dispatched, the backing [ShellExecutor.execute] always runs
 * the [Runnable] immediately if called from the same thread, whereas
 * [Handler.asCoroutineDispatcher] will create a [MainCoroutineDispatcher] that correctly
 * dispatches (queues) when [CoroutineDispatcher.isDispatchNeeded] is true using [Handler.post].
 * For callers that do need a non-dispatching version, [MainCoroutineDispatcher.immediate] is
 * available.
 */
@Module
class WMShellCoroutinesModule {
  @Provides
  @ShellMainThread
  fun provideMainDispatcher(
    @ShellMainThread mainHandler: Handler
  ): MainCoroutineDispatcher = mainHandler.asCoroutineDispatcher()

  @Provides
  @ShellBackgroundThread
  fun provideBackgroundDispatcher(
      @ShellBackgroundThread backgroundHandler: Handler
  ): MainCoroutineDispatcher = backgroundHandler.asCoroutineDispatcher()

  @Provides
  @ShellDesktopThread
  fun provideDesktopDispatcher(
    @ShellDesktopThread desktopHandler: Handler
  ): MainCoroutineDispatcher = desktopHandler.asCoroutineDispatcher()

  @Provides
  @WMSingleton
  @ShellMainThread
  fun provideApplicationScope(
      @ShellMainThread applicationDispatcher: MainCoroutineDispatcher,
  ): CoroutineScope = CoroutineScope(applicationDispatcher)

  @Provides
  @WMSingleton
  @ShellBackgroundThread
  fun provideBackgroundCoroutineScope(
      @ShellBackgroundThread backgroundDispatcher: MainCoroutineDispatcher,
  ): CoroutineScope = CoroutineScope(backgroundDispatcher)

  @Provides
  @WMSingleton
  @ShellBackgroundThread
  fun provideBackgroundCoroutineContext(
      @ShellBackgroundThread backgroundDispatcher: MainCoroutineDispatcher
  ): CoroutineContext = backgroundDispatcher + SupervisorJob()
}
