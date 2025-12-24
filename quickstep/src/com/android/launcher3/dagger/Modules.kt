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

import android.annotation.ElapsedRealtimeLong
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.android.internal.R
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerImpl
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.dragndrop.SystemDragListener
import com.android.launcher3.dragndrop.SystemDragListenerFactory
import com.android.launcher3.homescreenfiles.HomeScreenFilesMediaStoreProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIconProviderImpl
import com.android.launcher3.logging.StatsLogManager.StatsLogManagerFactory
import com.android.launcher3.secondarydisplay.SecondaryDisplayDelegate
import com.android.launcher3.secondarydisplay.SecondaryDisplayQuickstepDelegateImpl
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepWidgetHolderFactory
import com.android.launcher3.uioverrides.SystemApiWrapper
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.quickstep.InstantAppResolverImpl
import com.android.quickstep.LauncherRestoreEventLoggerImpl
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogCompatManagerFactory
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.quickstep.util.ContextualSearchStateManager
import com.android.quickstep.util.GestureExclusionManager
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.wm.shell.shared.desktopmode.DesktopState
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import java.util.Optional
import java.util.concurrent.ExecutorService
import javax.inject.Named

import app.lawnchair.factory.LawnchairWidgetHolder
import app.lawnchair.util.LawnchairWindowManagerProxy

private object Modules {}

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: LawnchairWindowManagerProxy): WindowManagerProxy
}

@Module
abstract class ActivityContextModule {
    @Binds
    abstract fun bindSecondaryDisplayDelegate(
        impl: SecondaryDisplayQuickstepDelegateImpl
    ): SecondaryDisplayDelegate
}

@Module
abstract class ApiWrapperModule {
    @Binds
    abstract fun bindStatsLogManagerFactory(
        impl: StatsLogCompatManagerFactory
    ): StatsLogManagerFactory

    @Binds abstract fun bindApiWrapper(systemApiWrapper: SystemApiWrapper): ApiWrapper

    @Binds
    abstract fun bindIconProvider(iconProviderImpl: LauncherIconProviderImpl): LauncherIconProvider

    @Binds abstract fun bindInstantAppResolver(impl: InstantAppResolverImpl): InstantAppResolver

    @Binds
    abstract fun bindRestoreEventLogger(
        impl: LauncherRestoreEventLoggerImpl
    ): LauncherRestoreEventLogger
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: LawnchairWidgetHolder.Factory): WidgetHolderFactory
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

    @Provides
    @JvmStatic
    fun provideActivityManagerWrapper(): ActivityManagerWrapper =
        ActivityManagerWrapper.getInstance()

    @Provides
    @JvmStatic
    @ElapsedRealtimeLong
    fun provideElapsedRealTime(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @ElementsIntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideSearchEntryPointsDefault(@ApplicationContext ctx: Context): Set<Uri> =
        if (ctx.resources.getBoolean(R.bool.config_searchAllEntrypointsEnabledDefault)) {
            setOf(ContextualSearchStateManager.SEARCH_ALL_ENTRYPOINTS_ENABLED_URI)
        } else emptySet()

    @Provides
    @JvmStatic
    fun provideDesktopState(@ApplicationContext context: Context): DesktopState =
        DesktopState.getInstance(context)
}

@Module
interface SystemDragOptionalDependenciesModule {
    @BindsOptionalOf fun bindSystemDragListenerFactory(): SystemDragListenerFactory
}

@Module(includes = [SystemDragOptionalDependenciesModule::class])
object SystemDragModule {
    @Provides
    @LauncherAppSingleton
    fun provideSystemDragController(
        iconCache: Lazy<IconCache>,
        systemDragListenerFactory: Optional<SystemDragListenerFactory>,
    ): SystemDragController =
        if (enableSystemDrag())
            SystemDragControllerImpl(
                systemDragListenerFactory.orElse { launcher ->
                    SystemDragListener(launcher, iconCache)
                }
            )
        else SystemDragControllerStub()
}

/** A dagger module responsible for managing files on the home screen. */
@Module
object HomeScreenFilesModule {
    @Provides
    @LauncherAppSingleton
    fun provideHomeScreenFilesProvider(
        @ApplicationContext context: Context,
        @ThreadPool executorService: ExecutorService,
        tracker: DaggerSingletonTracker,
    ): HomeScreenFilesProvider {
        return if (HomeScreenFilesUtils.isFeatureEnabled) {
            HomeScreenFilesMediaStoreProvider(context, executorService, tracker)
        } else {
            HomeScreenFilesNoOpProvider()
        }
    }
}
