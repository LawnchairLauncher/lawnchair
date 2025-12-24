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

package com.android.launcher3.taskbar

import android.content.Context
import android.content.ContextWrapper
import com.android.launcher3.InMemoryLauncherPrefs
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.compose.widgetpicker.LauncherWidgetPickerModule
import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.HomeScreenFilesModule
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherConcurrencyModule
import com.android.launcher3.dagger.LauncherModelModule
import com.android.launcher3.dagger.PerDisplayModule
import com.android.launcher3.dagger.SettingsModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.SystemDragModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.dagger.WindowManagerProxyModule
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import dagger.BindsInstance
import dagger.Component

/**
 * Sandbox for enabling Taskbar in direct boot mode.
 *
 * Swaps out dependencies that depend on encrypted storage for alternatives that still allow for
 * system navigation (e.g. back button) to show up on keyguard before the first unlock.
 */
class TaskbarBootAppContext(base: Context) : SandboxContext(base) {

    init {
        initDaggerComponent(
            DaggerTaskbarBootComponent.builder()
                .bindPrefs(InMemoryLauncherPrefs(this))
                .bindPluginManagerWrapper(PluginManagerWrapper())
        )
    }

    /**
     * Wrap a Taskbar window context for sandboxing it under this sandbox.
     *
     * This context should be a middle layer between [TaskbarActivityContext] and its window
     * context. In other words, [TaskbarActivityContext] should wrap the returned context, which
     * wraps [base].
     */
    fun wrapWindowContext(base: Context): Context = TaskbarBootContextWrapper(base)

    private inner class TaskbarBootContextWrapper(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext() = this@TaskbarBootAppContext
    }
}

@LauncherAppSingleton
@Component(
    modules =
        [
            WindowManagerProxyModule::class,
            ApiWrapperModule::class,
            StaticObjectModule::class,
            WidgetModule::class,
            AppModule::class,
            PerDisplayModule::class,
            LauncherConcurrencyModule::class,
            ExecutorsModule::class,
            LauncherExecutorsModule::class,
            LauncherWidgetPickerModule::class,
            LauncherModelModule::class,
            HomeScreenFilesModule::class,
            SettingsModule::class,
            SystemDragModule::class,
        ]
)
interface TaskbarBootComponent : LauncherAppComponent {
    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindPrefs(prefs: LauncherPrefs): Builder

        @BindsInstance fun bindPluginManagerWrapper(wrapper: PluginManagerWrapper): Builder

        override fun build(): TaskbarBootComponent
    }
}
