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

package com.android.wm.shell.shared;

import android.window.RemoteTransition;
import android.window.TransitionFilter;

/**
 * Listener interface that Launcher attaches to SystemUI to get home activity transition callbacks
 * on the default display.
 */
oneway interface IHomeTransitionListener {

    /**
     * Called when a transition changes the visibility of the home activity on the default display.
     */
    void onHomeVisibilityChanged(in boolean isVisible);
}

