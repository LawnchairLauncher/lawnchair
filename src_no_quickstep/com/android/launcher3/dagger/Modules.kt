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

import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.RefreshRateTracker.RefreshRateTrackerImpl
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactoryImpl
import dagger.Binds
import dagger.Module

private object Modules {}

@Module abstract class WindowManagerProxyModule {}

@Module abstract class ApiWrapperModule {}

@Module
abstract class WidgetModule {
    @Binds
    abstract fun bindWidgetHolderFactory(factor: WidgetHolderFactoryImpl): WidgetHolderFactory
}

@Module abstract class PluginManagerWrapperModule {}

@Module
abstract class StaticObjectModule {
    @Binds abstract fun bindRefreshRateTracker(tracker: RefreshRateTrackerImpl): RefreshRateTracker
}

// Module containing bindings for the final derivative app
@Module abstract class AppModule {}

@Module abstract class PerDisplayModule {}

@Module abstract class LauncherConcurrencyModule {}
