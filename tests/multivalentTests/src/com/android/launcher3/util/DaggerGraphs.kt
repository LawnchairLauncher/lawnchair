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

package com.android.launcher3.util

import com.android.launcher3.FakeLauncherPrefs
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.dagger.WindowManagerProxyModule
import dagger.Binds
import dagger.Module

private class DaggerGraphs {}

@Module
abstract class FakePrefsModule {
    @Binds abstract fun bindLauncherPrefs(prefs: FakeLauncherPrefs): LauncherPrefs
}

/** All modules. We also exclude the plugin module from tests */
@Module(
    includes =
        [
            ApiWrapperModule::class,
            WindowManagerProxyModule::class,
            StaticObjectModule::class,
            WidgetModule::class,
            AppModule::class,
        ]
)
class AllModulesForTest

/** All modules except the WMProxy */
@Module(
    includes =
        [ApiWrapperModule::class, StaticObjectModule::class, AppModule::class, WidgetModule::class]
)
class AllModulesMinusWMProxy

/** All modules except the ApiWrapper */
@Module(
    includes =
        [
            WindowManagerProxyModule::class,
            StaticObjectModule::class,
            AppModule::class,
            WidgetModule::class,
        ]
)
class AllModulesMinusApiWrapper
