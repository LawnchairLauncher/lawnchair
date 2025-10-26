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

import app.lawnchair.util.LawnchairWindowManagerProxy
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepWidgetHolderFactory
import com.android.launcher3.uioverrides.SystemApiWrapper
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.quickstep.util.GestureExclusionManager
import dagger.Binds
import dagger.Module
import dagger.Provides

private object Modules {}

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: LawnchairWindowManagerProxy): WindowManagerProxy
}

@Module
abstract class ApiWrapperModule {
    @Binds abstract fun bindApiWrapper(systemApiWrapper: SystemApiWrapper): ApiWrapper
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: QuickstepWidgetHolderFactory): WidgetHolderFactory
}

@Module
abstract class PluginManagerWrapperModule {
    @Binds
    abstract fun bindPluginManagerWrapper(impl: PluginManagerWrapperImpl): PluginManagerWrapper
}

@Module
object StaticObjectModule {

    @Provides
    @JvmStatic
    fun provideGestureExclusionManager(): GestureExclusionManager = GestureExclusionManager.INSTANCE

    @Provides
    @JvmStatic
    fun provideRefreshRateTracker(): RefreshRateTracker = ChoreographerFrameRateTracker
}
