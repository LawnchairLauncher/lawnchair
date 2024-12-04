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

package com.android.wm.shell.freeform;

import android.window.WindowContainerTransaction;

/**
 * The interface around {@link FreeformTaskTransitionHandler} for task listeners to start freeform
 * task transitions.
 */
public interface FreeformTaskTransitionStarter {

    /**
     * Starts a windowing mode transition.
     *
     * @param targetWindowingMode the target windowing mode
     * @param wct the {@link WindowContainerTransaction} that changes the windowing mode
     *
     */
    void startWindowingModeTransition(int targetWindowingMode, WindowContainerTransaction wct);

    /**
     * Starts window minimization transition
     *
     * @param wct the {@link WindowContainerTransaction} that changes the windowing mode
     *
     */
    void startMinimizedModeTransition(WindowContainerTransaction wct);

    /**
     * Starts close window transition
     *
     * @param wct the {@link WindowContainerTransaction} that closes the task
     *
     */
    void startRemoveTransition(WindowContainerTransaction wct);
}