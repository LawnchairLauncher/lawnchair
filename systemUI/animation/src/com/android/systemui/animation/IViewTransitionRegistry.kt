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

package com.android.systemui.animation

import android.view.View

/** Represents a Registry for holding a transitioning view mapped to a token */
interface IViewTransitionRegistry {

    /**
     * Registers the transitioning [view] mapped to returned token
     *
     * @param view The view undergoing transition
     * @return token mapped to the transitioning view
     */
    fun register(view: View): ViewTransitionToken

    /**
     * Unregisters the transitioned view from its corresponding [token]
     *
     * @param token The token corresponding to the transitioning view
     */
    fun unregister(token: ViewTransitionToken)

    /**
     * Extracts a transitioning view from registry using its corresponding [token]
     *
     * @param token The token corresponding to the transitioning view
     */
    fun getView(token: ViewTransitionToken): View?

    /**
     * Return token mapped to the [view], if it is present in the registry
     *
     * @param view the transitioning view whose token we are requesting
     * @return token associated with the [view] if present, else null
     */
    fun getViewToken(view: View): ViewTransitionToken?

    /** Event call to run on registry update (on both [register] and [unregister]) */
    fun onRegistryUpdate()
}
