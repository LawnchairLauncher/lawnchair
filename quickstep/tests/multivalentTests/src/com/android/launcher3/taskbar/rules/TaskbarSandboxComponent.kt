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

package com.android.launcher3.taskbar.rules

import android.content.Context
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.compose.core.widgetpicker.NoOpWidgetPickerModule
import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.BasePerDisplayModule
import com.android.launcher3.dagger.DisplayContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherConcurrencyModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.dagger.WindowContext
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.fallback.window.RecentsWindowManager
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy

@LauncherAppSingleton
@Component(modules = [AllTaskbarSandboxModules::class])
interface TaskbarSandboxComponent : LauncherAppComponent {

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindSystemUiProxy(proxy: SystemUiProxy): Builder

        @BindsInstance fun bindSettingsCache(settingsCache: SettingsCache): Builder

        override fun build(): TaskbarSandboxComponent
    }
}

@Module(
    includes =
        [
            ApiWrapperModule::class,
            StaticObjectModule::class,
            WidgetModule::class,
            AppModule::class,
            BasePerDisplayModule::class,
            LauncherConcurrencyModule::class,
            ExecutorsModule::class,
            LauncherExecutorsModule::class,
            FakePrefsModule::class,
            DisplayControllerModule::class,
            TaskbarSandboxWmProxyModule::class,
            TaskbarPerDisplayReposModule::class,
            DesktopVisibilityControllerModule::class,
            NoOpWidgetPickerModule::class,
        ]
)
interface AllTaskbarSandboxModules

@Module
abstract class DisplayControllerModule {
    @Binds abstract fun bindDisplayController(controller: DisplayControllerSpy): DisplayController
}

/** A wrapper over display controller which allows modifying the underlying info */
@LauncherAppSingleton
class DisplayControllerSpy
@Inject
constructor(
    @ApplicationContext context: Context,
    wmProxy: WindowManagerProxy,
    private val prefs: LauncherPrefs,
    lifecycle: DaggerSingletonTracker,
) : DisplayController(context, wmProxy, prefs, lifecycle) {

    var infoModifier: ((Info) -> Info)? = null

    // When overview on CD is enabled, DisplayController queries getInfoForDisplay instead of
    // getInfo for the primary (virtual) display used in tests. So, override it to get info from the
    // default display.
    private val defaultInfoModifierForDisplay: ((Info?) -> Info?)? =
        if (Flags.enableOverviewOnConnectedDisplays()) {
            { _ -> info }
        } else {
            null
        }

    var infoModifierForDisplay: ((Info?) -> Info?)? = defaultInfoModifierForDisplay

    private var prefListener: LauncherPrefChangeListener? = null

    init {
        // When overview on CD is disabled, DisplayController only adds the info associated with
        // the DEFAULT_DISPLAY. So, instead of changing the production code of DisplayController to
        // use display from context we manually add the info associated with the virtual display.
        if (!Flags.enableOverviewOnConnectedDisplays()) {
            getOrCreatePerDisplayInfo(context.display)
            lifecycle.addCloseable { removePerDisplayInfo(context.displayId) }
        }
    }

    override fun getInfo(): Info = infoModifier?.invoke(super.getInfo()) ?: super.getInfo()

    override fun getInfoForDisplay(displayId: Int): Info? =
        infoModifierForDisplay?.invoke(super.getInfoForDisplay(displayId))
            ?: super.getInfoForDisplay(displayId)

    /**
     * Sets up [TASKBAR_PINNING] pref listener for the given display.
     *
     * <p>DisplayController sets up LauncherPrefChangeListener only for the DEFAULT_DISPLAY, this is
     * correct but tests rely on treating the created virtual display as default. So, instead of
     * changing the production code of DisplayController to be more testable, we add a custom
     * listener for our virtual display.
     */
    fun setupTaskbarPinningPrefListener(displayId: Int) {
        prefListener =
            LauncherPrefChangeListener { notifyConfigChangeForDisplay(displayId) }
                .also { prefs.addListener(it, TASKBAR_PINNING) }
    }

    fun cleanup() {
        prefListener?.let { prefs.removeListener(it, TASKBAR_PINNING) }
        infoModifier = null
        infoModifierForDisplay = defaultInfoModifierForDisplay
    }
}

/** Convenient extension to access [DisplayControllerSpy] from [TaskbarWindowSandboxContext]. */
val TaskbarWindowSandboxContext.displayControllerSpy: DisplayControllerSpy?
    get() = DisplayController.INSTANCE[this] as? DisplayControllerSpy

@Module
object DesktopVisibilityControllerModule {
    @JvmStatic
    @Provides
    @LauncherAppSingleton
    fun provideDesktopVisibilityController(
        @ApplicationContext context: Context,
        systemUiProxy: SystemUiProxy,
        lifecycleTracker: DaggerSingletonTracker,
    ): DesktopVisibilityController {
        return spy(DesktopVisibilityController(context, systemUiProxy, lifecycleTracker))
    }
}

@Module
object TaskbarPerDisplayReposModule {
    @Provides
    @LauncherAppSingleton
    fun provideRecentsAnimationDeviceStateRepo():
        PerDisplayRepository<RecentsAnimationDeviceState> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideTaskAnimationManagerRepo(): PerDisplayRepository<TaskAnimationManager> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideRotationTouchHandlerRepo(): PerDisplayRepository<RotationTouchHelper> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideFallbackWindowInterfaceRepo(): PerDisplayRepository<FallbackWindowInterface> = mock()

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowManagerRepo(): PerDisplayRepository<RecentsWindowManager> = mock()

    @Provides
    @LauncherAppSingleton
    @DisplayContext
    fun provideDisplayContext(): PerDisplayRepository<Context> = mock()

    @Provides
    @LauncherAppSingleton
    @WindowContext
    fun provideWindowContext(): PerDisplayRepository<Context> = mock()
}
