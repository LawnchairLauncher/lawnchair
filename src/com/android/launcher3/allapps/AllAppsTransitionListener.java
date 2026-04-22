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
package com.android.launcher3.allapps;

/**
 * An interface for listening to all-apps open-close transition
 */
public interface AllAppsTransitionListener {
    /**
     * Called when the transition starts
     * @param toAllApps {@code true} if this transition is supposed to end in the AppApps UI
     *
     * @see ActivityAllAppsContainerView
     */
    void onAllAppsTransitionStart(boolean toAllApps);

    /**
     * Called when the transition ends
     * @param toAllApps {@code true} if the final state is all-apps
     *
     * @see ActivityAllAppsContainerView
     */
    void onAllAppsTransitionEnd(boolean toAllApps);
}
