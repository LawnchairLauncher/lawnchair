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

package com.android.launcher3.statemanager;


import static com.android.launcher3.LauncherState.FLAG_CLOSE_POPUPS;
import static com.android.launcher3.statemanager.BaseState.FLAG_NON_INTERACTIVE;

import androidx.annotation.CallSuper;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * Interface for a container that can be managed by a state manager.
 *
 * @param <STATE_TYPE> The type of state that the container can be in.
 */
public interface StatefulContainer<STATE_TYPE extends BaseState<STATE_TYPE>> extends
        ActivityContext {

    /**
     * Creates a factory for atomic state animations
     */
    default StateManager.AtomicAnimationFactory<STATE_TYPE> createAtomicAnimationFactory() {
        return new StateManager.AtomicAnimationFactory<>(0);
    }

    /**
     * Create handlers to control the property changes for this activity
     */
    void collectStateHandlers(List<StateManager.StateHandler<STATE_TYPE>> out);

    /**
     * Retrieves state manager for given container
     */
    StateManager<STATE_TYPE, ?> getStateManager();

    /**
     * Called when transition to state ends
     * @param state current state of State_Type
     */
    default void onStateSetEnd(STATE_TYPE state) { }

    /**
     * Called when transition to state starts
     * @param state current state of State_Type
     */
    @CallSuper
    default void onStateSetStart(STATE_TYPE state) {
        if (state.hasFlag(FLAG_CLOSE_POPUPS)) {
            AbstractFloatingView.closeAllOpenViews(this, !state.hasFlag(FLAG_NON_INTERACTIVE));
        }
    }

    /**
     * Returns true if the activity is in the provided state
     * @param state current state of State_Type
     */
    default boolean isInState(STATE_TYPE state) {
        return getStateManager().getState() == state;
    }

    /**
     * Returns true if state change should transition with animation
     */
    boolean shouldAnimateStateChange();
}
