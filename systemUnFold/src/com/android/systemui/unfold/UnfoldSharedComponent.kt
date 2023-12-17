/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.unfold

import android.content.ContentResolver
import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import com.android.systemui.unfold.config.UnfoldTransitionConfig
import com.android.systemui.unfold.dagger.UnfoldMain
import com.android.systemui.unfold.dagger.UnfoldSingleThreadBg
import com.android.systemui.unfold.progress.RemoteUnfoldTransitionReceiver
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.unfold.updates.RotationChangeProvider
import com.android.systemui.unfold.updates.hinge.HingeAngleProvider
import com.android.systemui.unfold.updates.screen.ScreenStatusProvider
import com.android.systemui.unfold.util.CurrentActivityTypeProvider
import com.android.systemui.unfold.util.UnfoldTransitionATracePrefix
import dagger.BindsInstance
import dagger.Component
import java.util.Optional
import java.util.concurrent.Executor
import javax.inject.Singleton

/**
 * Provides [UnfoldTransitionProgressProvider]. The [Optional] is empty when the transition
 * animation is disabled.
 *
 * This component is meant to be used for places that don't use dagger. By providing those
 * parameters to the factory, all dagger objects are correctly instantiated. See
 * [createUnfoldSharedComponent] for an example.
 */
@Singleton
@Component(modules = [UnfoldSharedModule::class])
interface UnfoldSharedComponent {

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance context: Context,
            @BindsInstance config: UnfoldTransitionConfig,
            @BindsInstance screenStatusProvider: ScreenStatusProvider,
            @BindsInstance foldProvider: FoldProvider,
            @BindsInstance activityTypeProvider: CurrentActivityTypeProvider,
            @BindsInstance sensorManager: SensorManager,
            @BindsInstance @UnfoldMain handler: Handler,
            @BindsInstance @UnfoldMain executor: Executor,
            @BindsInstance @UnfoldSingleThreadBg singleThreadBgExecutor: Executor,
            @BindsInstance @UnfoldTransitionATracePrefix tracingTagPrefix: String,
            @BindsInstance displayManager: DisplayManager,
            @BindsInstance contentResolver: ContentResolver = context.contentResolver
        ): UnfoldSharedComponent
    }

    val unfoldTransitionProvider: Optional<UnfoldTransitionProgressProvider>
    val hingeAngleProvider: HingeAngleProvider
    val rotationChangeProvider: RotationChangeProvider
}

/**
 * Generates a [RemoteTransitionProgress] usable to receive unfold transition progress from another
 * process.
 */
@Singleton
@Component(modules = [UnfoldRemoteModule::class])
interface RemoteUnfoldSharedComponent {

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance context: Context,
            @BindsInstance config: UnfoldTransitionConfig,
            @BindsInstance @UnfoldMain executor: Executor,
            @BindsInstance @UnfoldMain handler: Handler,
            @BindsInstance @UnfoldSingleThreadBg singleThreadBgExecutor: Executor,
            @BindsInstance displayManager: DisplayManager,
            @BindsInstance @UnfoldTransitionATracePrefix tracingTagPrefix: String,
        ): RemoteUnfoldSharedComponent
    }

    val remoteTransitionProgress: Optional<RemoteUnfoldTransitionReceiver>
    val rotationChangeProvider: RotationChangeProvider
}

/**
 * Usable to receive and propagate unfold transition progresses
 *
 * All unfold events received by [remoteReceiver] will be propagated to [localProvider].
 * [remoteReceiver] is meant to receive events from a remote process (E.g. from a binder service).
 */
data class RemoteTransitionProgress(
    val localProvider: UnfoldTransitionProgressProvider,
    val remoteReceiver: UnfoldTransitionProgressProvider.TransitionProgressListener
)
