/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.api

/**
 * Singleton which contains the global state of the compat ui system.
 */
class CompatUIState {

    private val components = mutableMapOf<String, CompatUIComponent>()

    val sharedState = CompatUISharedState()

    val componentStates = mutableMapOf<String, CompatUIComponentState>()

    /**
     * @return The CompatUIComponent for the given componentId if it exists.
     */
    fun getUIComponent(componentId: String): CompatUIComponent? =
        components[componentId]

    /**
     * Registers a component for a given componentId along with its optional state.
     * <p/>
     * @param componentId       The identifier for the component to register.
     * @param comp              The {@link CompatUIComponent} instance to register.
     * @param componentState    The optional state specific of the component. Not all components
     *                          have a specific state so it can be null.
     */
    fun registerUIComponent(
        componentId: String,
        comp: CompatUIComponent,
        componentState: CompatUIComponentState?
    ) {
        components[componentId] = comp
        componentState?.let {
            componentStates[componentId] = componentState
        }
    }

    /**
     * Unregister a component for a given componentId.
     * <p/>
     * @param componentId       The identifier for the component to register.
     */
    fun unregisterUIComponent(componentId: String) {
        components.remove(componentId)
        componentStates.remove(componentId)
    }

    /**
     * Get access to the specific {@link CompatUIComponentState} for a {@link CompatUIComponent}
     * with a given identifier.
     * <p/>
     * @param componentId  The identifier of the {@link CompatUIComponent}.
     * @return The optional state for the component of the provided id.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CompatUIComponentState> stateForComponent(componentId: String) =
        componentStates[componentId] as? T
}
