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

package com.android.launcher3.util

import android.view.View
import androidx.annotation.AnyThread
import com.android.launcher3.views.FloatingIconViewCompanion
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/** Wraps a [CompletableFuture] to find a view and post in target executor */
class AsyncView(private val executor: ExecutorService, findMatchingViewCallable: () -> View?) {
    private val matchingViewFuture: CompletableFuture<View?> =
        CompletableFuture.supplyAsync({ findMatchingViewCallable() }, executor)

    @AnyThread
    fun postAlpha(alpha: Float) {
        matchingViewFuture.thenApplyAsync({ view -> view?.alpha = alpha }, executor)
    }

    @AnyThread
    fun postVisibilityAsFloatingIconViewCompanion(isVisible: Boolean) {
        matchingViewFuture.thenApplyAsync(
            { view ->
                if (view is FloatingIconViewCompanion) {
                    view.setIconVisible(isVisible)
                    view.setForceHideDot(!isVisible)
                    view.setForceHideRing(!isVisible)
                } else {
                    view?.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
                }
            },
            executor,
        )
    }

    @AnyThread
    fun postForceHideDotRingAsFloatingIconViewCompanion(hide: Boolean) {
        matchingViewFuture.thenApplyAsync(
            { view ->
                (view as? FloatingIconViewCompanion)?.let { floatingIconView ->
                    floatingIconView.setForceHideDot(hide)
                    floatingIconView.setForceHideRing(hide)
                }
            },
            executor,
        )
    }
}
