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

package com.android.systemui.unfold.progress

import android.os.Handler
import androidx.annotation.FloatRange
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import com.android.systemui.unfold.dagger.UnfoldMain
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Collections.synchronizedMap

/**
 * [UnfoldTransitionProgressProvider] that forwards all progress to the main thread handler.
 *
 * This is needed when progress are calculated in the background, but some listeners need the
 * callbacks in the main thread.
 *
 * Note that this class assumes that the root provider has thread safe callback registration, as
 * they might be called from any thread.
 */
class MainThreadUnfoldTransitionProgressProvider
@AssistedInject
constructor(
    @UnfoldMain private val mainHandler: Handler,
    @Assisted private val rootProvider: UnfoldTransitionProgressProvider
) : UnfoldTransitionProgressProvider {

    private val listenerMap: MutableMap<TransitionProgressListener, TransitionProgressListener> =
        synchronizedMap(mutableMapOf())

    override fun addCallback(listener: TransitionProgressListener) {
        val proxy = TransitionProgressListerProxy(listener)
        rootProvider.addCallback(proxy)
        listenerMap[listener] = proxy
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        val proxy = listenerMap.remove(listener) ?: return
        rootProvider.removeCallback(proxy)
    }

    override fun destroy() {
        rootProvider.destroy()
    }

    inner class TransitionProgressListerProxy(private val listener: TransitionProgressListener) :
        TransitionProgressListener {
        override fun onTransitionStarted() {
            mainHandler.post { listener.onTransitionStarted() }
        }

        override fun onTransitionProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
            mainHandler.post { listener.onTransitionProgress(progress) }
        }

        override fun onTransitionFinishing() {
            mainHandler.post { listener.onTransitionFinishing() }
        }

        override fun onTransitionFinished() {
            mainHandler.post { listener.onTransitionFinished() }
        }
    }

    @AssistedFactory
    interface Factory {
        /** Creates a [MainThreadUnfoldTransitionProgressProvider] that wraps the [rootProvider]. */
        fun create(
            rootProvider: UnfoldTransitionProgressProvider
        ): MainThreadUnfoldTransitionProgressProvider
    }
}
