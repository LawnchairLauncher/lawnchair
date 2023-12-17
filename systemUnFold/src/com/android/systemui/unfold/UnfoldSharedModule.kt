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

import com.android.systemui.unfold.config.UnfoldTransitionConfig
import com.android.systemui.unfold.progress.FixedTimingTransitionProgressProvider
import com.android.systemui.unfold.progress.PhysicsBasedUnfoldTransitionProgressProvider
import com.android.systemui.unfold.progress.UnfoldTransitionProgressForwarder
import com.android.systemui.unfold.updates.DeviceFoldStateProvider
import com.android.systemui.unfold.updates.FoldStateProvider
import com.android.systemui.unfold.updates.hinge.EmptyHingeAngleProvider
import com.android.systemui.unfold.updates.hinge.HingeAngleProvider
import com.android.systemui.unfold.updates.hinge.HingeSensorAngleProvider
import com.android.systemui.unfold.util.ATraceLoggerTransitionProgressListener
import com.android.systemui.unfold.util.ScaleAwareTransitionProgressProvider
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityManager
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityManagerImpl
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityProvider
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider
import javax.inject.Singleton

@Module(includes = [UnfoldSharedInternalModule::class])
class UnfoldSharedModule {
    @Provides
    @Singleton
    fun provideFoldStateProvider(
        deviceFoldStateProvider: DeviceFoldStateProvider
    ): FoldStateProvider = deviceFoldStateProvider

    @Provides
    @Singleton
    fun unfoldKeyguardVisibilityProvider(
        impl: UnfoldKeyguardVisibilityManagerImpl
    ): UnfoldKeyguardVisibilityProvider = impl

    @Provides
    @Singleton
    fun unfoldKeyguardVisibilityManager(
        impl: UnfoldKeyguardVisibilityManagerImpl
    ): UnfoldKeyguardVisibilityManager = impl
}

/**
 * Needed as methods inside must be public, but their parameters can be internal (and, a public
 * method can't have internal parameters). Making the module internal and included in a public one
 * fixes the issue.
 */
@Module
internal class UnfoldSharedInternalModule {
    @Provides
    @Singleton
    fun unfoldTransitionProgressProvider(
        config: UnfoldTransitionConfig,
        scaleAwareProviderFactory: ScaleAwareTransitionProgressProvider.Factory,
        tracingListener: ATraceLoggerTransitionProgressListener,
        physicsBasedUnfoldTransitionProgressProvider:
            Provider<PhysicsBasedUnfoldTransitionProgressProvider>,
        fixedTimingTransitionProgressProvider: Provider<FixedTimingTransitionProgressProvider>,
    ): Optional<UnfoldTransitionProgressProvider> {
        if (!config.isEnabled) {
            return Optional.empty()
        }
        val baseProgressProvider =
            if (config.isHingeAngleEnabled) {
                physicsBasedUnfoldTransitionProgressProvider.get()
            } else {
                fixedTimingTransitionProgressProvider.get()
            }

        return Optional.of(
            scaleAwareProviderFactory.wrap(baseProgressProvider).apply {
                // Always present callback that logs animation beginning and end.
                addCallback(tracingListener)
            }
        )
    }

    @Provides
    fun hingeAngleProvider(
        config: UnfoldTransitionConfig,
        hingeAngleSensorProvider: Provider<HingeSensorAngleProvider>
    ): HingeAngleProvider {
        return if (config.isHingeAngleEnabled) {
            hingeAngleSensorProvider.get()
        } else {
            EmptyHingeAngleProvider
        }
    }

    @Provides
    @Singleton
    fun provideProgressForwarder(
            config: UnfoldTransitionConfig,
            progressForwarder: Provider<UnfoldTransitionProgressForwarder>
    ): Optional<UnfoldTransitionProgressForwarder> {
        if (!config.isEnabled) {
            return Optional.empty()
        }
        return Optional.of(progressForwarder.get())
    }
}
