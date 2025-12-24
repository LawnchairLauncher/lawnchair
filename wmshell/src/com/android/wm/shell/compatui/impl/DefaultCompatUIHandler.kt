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

package com.android.wm.shell.compatui.impl

import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.api.CompatUIComponentFactory
import com.android.wm.shell.compatui.api.CompatUIComponentIdGenerator
import com.android.wm.shell.compatui.api.CompatUIEvent
import com.android.wm.shell.compatui.api.CompatUIHandler
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUIRepository
import com.android.wm.shell.compatui.api.CompatUIRequest
import com.android.wm.shell.compatui.api.CompatUIState
import java.util.function.Consumer

/** Default implementation of {@link CompatUIHandler} to handle CompatUI components */
class DefaultCompatUIHandler(
    private val compatUIRepository: CompatUIRepository,
    private val compatUIState: CompatUIState,
    private val componentIdGenerator: CompatUIComponentIdGenerator,
    private val componentFactory: CompatUIComponentFactory,
    private val executor: ShellExecutor,
) : CompatUIHandler {

    private var compatUIEventSender: Consumer<CompatUIEvent>? = null

    override fun onCompatInfoChanged(compatUIInfo: CompatUIInfo) {
        compatUIRepository.iterateOn { spec ->
            // We get the identifier for the component depending on the task and spec
            val componentId = componentIdGenerator.generateId(compatUIInfo, spec)
            spec.log("Evaluating component $componentId")
            // We check in the state if the component does not yet exist
            var component = compatUIState.getUIComponent(componentId)
            if (component == null) {
                spec.log("Component $componentId not present")
                // We evaluate the predicate
                if (spec.lifecycle.creationPredicate(compatUIInfo, compatUIState.sharedState)) {
                    spec.log("Component $componentId should be created")
                    // We create the component and store in the
                    // global state
                    component =
                        componentFactory.create(spec, componentId, compatUIState, compatUIInfo)
                    spec.log("Component $componentId created $component")
                    // We initialize the state for the component
                    val compState =
                        spec.lifecycle.stateBuilder(compatUIInfo, compatUIState.sharedState)
                    spec.log("Component $componentId initial state $compState")
                    compatUIState.registerUIComponent(componentId, component, compState)
                    spec.log("Component $componentId registered")
                    // We initialize the layout for the component
                    component.initLayout(compatUIInfo)
                    spec.log("Component $componentId layout created")
                    // Now we can invoke the update passing the shared state and
                    // the state specific to the component
                    executor.execute {
                        component.update(compatUIInfo)
                        spec.log("Component $componentId updated with $compatUIInfo")
                    }
                }
            } else {
                // The component is present. We check if we need to remove it
                if (
                    spec.lifecycle.removalPredicate(
                        compatUIInfo,
                        compatUIState.sharedState,
                        compatUIState.stateForComponent(componentId),
                    )
                ) {
                    spec.log("Component $componentId should be removed")
                    // We clean the component
                    component.release()
                    spec.log("Component $componentId released")
                    compatUIState.unregisterUIComponent(componentId)
                    spec.log("Component $componentId removed from registry")
                } else {
                    executor.execute {
                        // The component exists so we need to invoke the update methods
                        component.update(compatUIInfo)
                        spec.log("Component $componentId updated with $compatUIInfo")
                    }
                }
            }
        }
        // Empty at the moment
    }

    override fun setCallback(compatUIEventSender: Consumer<CompatUIEvent>?) {
        this.compatUIEventSender = compatUIEventSender
    }

    override fun sendCompatUIRequest(compatUIRequest: CompatUIRequest) {}
}
