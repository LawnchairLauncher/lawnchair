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

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowManagerGlobal
import com.android.app.displaylib.DefaultDisplayOnlyInstanceRepositoryImpl
import com.android.app.displaylib.DisplayLibBackground
import com.android.app.displaylib.DisplayLibComponent
import com.android.app.displaylib.DisplayRepository
import com.android.app.displaylib.DisplaysWithDecorationsRepository
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.displaylib.SingleInstanceRepositoryImpl
import com.android.app.displaylib.createDisplayLibComponent
import com.android.launcher3.Utilities
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.fallback.window.RecentsWindowFlags.enableOverviewOnConnectedDisplays
import com.android.quickstep.fallback.window.RecentsWindowManager
import com.android.quickstep.fallback.window.RecentsWindowManagerInstanceProvider
import com.android.systemui.dagger.qualifiers.Background
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

@Module(includes = [BasePerDisplayModule::class, PerDisplayRepositoriesModule::class])
interface PerDisplayModule

@Module(includes = [DisplayLibModule::class])
interface BasePerDisplayModule {
    @Binds
    @DisplayLibBackground
    abstract fun bindDisplayLibBackground(@Background bgScope: CoroutineScope): CoroutineScope
}

@Module
object PerDisplayRepositoriesModule {
    @Provides
    @LauncherAppSingleton
    fun provideRecentsAnimationDeviceStateRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<RecentsAnimationDeviceState>,
        instanceFactory: RecentsAnimationDeviceState.Factory,
        rotationTouchHelperRepository: PerDisplayRepository<RotationTouchHelper>,
    ): PerDisplayRepository<RecentsAnimationDeviceState> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "RecentsAnimationDeviceStateRepo",
                { displayId ->
                    rotationTouchHelperRepository[displayId]?.let {
                        instanceFactory.create(displayId, it)
                    }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "RecentsAnimationDeviceStateRepo",
                rotationTouchHelperRepository[DEFAULT_DISPLAY]?.let {
                    instanceFactory.create(DEFAULT_DISPLAY, it)
                }!!, // Assert the default display is always available.
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideTaskAnimationManagerRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<TaskAnimationManager>,
        instanceFactory: TaskAnimationManager.Factory,
    ): PerDisplayRepository<TaskAnimationManager> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create("TaskAnimationManagerRepo", instanceFactory::create)
        } else {
            SingleInstanceRepositoryImpl(
                "TaskAnimationManager",
                instanceFactory.create(DEFAULT_DISPLAY),
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideRotationTouchHandlerRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<RotationTouchHelper>,
        instanceFactory: RotationTouchHelper.Factory,
        @WindowContext windowContextRepository: PerDisplayRepository<Context>,
    ): PerDisplayRepository<RotationTouchHelper> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "RotationTouchHelperRepo",
                { displayId ->
                    windowContextRepository[displayId]?.let { instanceFactory.create(it) }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "RotationTouchHelperRepo",
                instanceFactory.create(windowContextRepository[DEFAULT_DISPLAY]),
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideFallbackWindowInterfaceRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<FallbackWindowInterface>
    ): PerDisplayRepository<FallbackWindowInterface> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "FallbackWindowInterfaceRepo",
                { _ -> FallbackWindowInterface() },
            )
        } else {
            SingleInstanceRepositoryImpl("FallbackWindowInterfaceRepo", FallbackWindowInterface())
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowManagerRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<RecentsWindowManager>,
        instanceProvider: RecentsWindowManagerInstanceProvider,
    ): PerDisplayRepository<RecentsWindowManager> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create("RecentsWindowManagerRepo", instanceProvider)
        } else {
            DefaultDisplayOnlyInstanceRepositoryImpl("RecentsWindowManagerRepo", instanceProvider)
        }
    }

    @Provides
    @LauncherAppSingleton
    @DisplayContext
    fun provideDisplayContext(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<Context>,
        displayRepository: DisplayRepository,
        @ApplicationContext context: Context,
    ): PerDisplayRepository<Context> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "DisplayContextRepo",
                { displayId ->
                    displayRepository.getDisplay(displayId)?.let {
                        context.createDisplayContext(it)
                    }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "DisplayContextRepo",
                context.createDisplayContext(displayRepository.getDisplay(DEFAULT_DISPLAY)!!),
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    @WindowContext
    fun provideWindowContext(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<Context>,
        displayRepository: DisplayRepository,
        @ApplicationContext context: Context,
    ): PerDisplayRepository<Context> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "DisplayContextRepo",
                { displayId ->
                    displayRepository.getDisplay(displayId)?.let {
                        if (Utilities.ATLEAST_S) {
                            context.createWindowContext(
                                it,
                                TYPE_APPLICATION_OVERLAY,
                                /* options=*/ null,
                            )
                        } else {
                            context.createDisplayContext(it)
                        }
                    }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "DisplayContextRepo",
                if (Utilities.ATLEAST_S) {
                    context.createWindowContext(
                        displayRepository.getDisplay(DEFAULT_DISPLAY)!!,
                        TYPE_APPLICATION_OVERLAY,
                        /* options=*/ null,
                    )
                } else {
                    context.createDisplayContext(displayRepository.getDisplay(DEFAULT_DISPLAY)!!)
                }
            )
        }
    }
}

/**
 * Module to bind the DisplayRepository from displaylib to the LauncherAppSingleton dagger graph.
 */
@Module
object DisplayLibModule {
    @Provides
    @LauncherAppSingleton
    fun displayLibComponent(
        @ApplicationContext context: Context,
        @Background bgHandler: Handler,
        @Background bgApplicationScope: CoroutineScope,
        coroutineDispatcherProvider: DispatcherProvider,
    ): DisplayLibComponent {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val windowManager = checkNotNull(WindowManagerGlobal.getWindowManagerService())
        return createDisplayLibComponent(
            displayManager,
            windowManager,
            bgHandler,
            bgApplicationScope,
            coroutineDispatcherProvider.ioBackground,
        )
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplayRepositoryFromLib(
        displayLibComponent: DisplayLibComponent
    ): DisplayRepository {
        return displayLibComponent.displayRepository
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplaysWithDecorationsRepository(
        displayLibComponent: DisplayLibComponent
    ): DisplaysWithDecorationsRepository {
        return displayLibComponent.displaysWithDecorationsRepository
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplaysWithDecorationsRepositoryCompat(
        displayLibComponent: DisplayLibComponent
    ): DisplaysWithDecorationsRepositoryCompat {
        return displayLibComponent.displaysWithDecorationsRepositoryCompat
    }

    @Provides
    fun dumpRegistrationLambda(): PerDisplayRepository.InitCallback =
        PerDisplayRepository.InitCallback { debugName, _ ->
            Log.d("PerDisplayInitCallback", debugName)
        }
}
