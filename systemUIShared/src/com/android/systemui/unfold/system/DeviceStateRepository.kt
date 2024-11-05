/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.system

import com.android.systemui.unfold.dagger.UnfoldMain
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.unfold.updates.FoldProvider.FoldCallback
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Provides whether the device is folded. */
interface DeviceStateRepository {
    val isFolded: Flow<Boolean>
}

@Singleton
class DeviceStateRepositoryImpl
@Inject
constructor(
    private val foldProvider: FoldProvider,
    @UnfoldMain private val executor: Executor,
) : DeviceStateRepository {

    override val isFolded: Flow<Boolean>
        get() =
            callbackFlow {
                    val callback = FoldCallback { isFolded -> trySend(isFolded) }
                    foldProvider.registerCallback(callback, executor)
                    awaitClose { foldProvider.unregisterCallback(callback) }
                }
                .buffer(capacity = Channel.CONFLATED)
}
