/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.systemui.unfold.dagger.UseReceivingFilter
import com.android.systemui.unfold.progress.RemoteUnfoldTransitionReceiver
import com.android.systemui.unfold.util.ATraceLoggerTransitionProgressListener
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider
import javax.inject.Singleton

/** Binds classes needed to provide unfold transition progresses to another process. */
@Module
class UnfoldRemoteModule {
    @Provides
    @Singleton
    fun provideTransitionProvider(
        config: UnfoldTransitionConfig,
        traceListener: ATraceLoggerTransitionProgressListener,
        remoteReceiverProvider: Provider<RemoteUnfoldTransitionReceiver>,
    ): Optional<RemoteUnfoldTransitionReceiver> {
        if (!config.isEnabled) {
            return Optional.empty()
        }
        val remoteReceiver = remoteReceiverProvider.get()
        remoteReceiver.addCallback(traceListener)
        return Optional.of(remoteReceiver)
    }

    @Provides @UseReceivingFilter fun useReceivingFilter(): Boolean = true
}
