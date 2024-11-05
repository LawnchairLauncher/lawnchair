/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.shared.system.smartspace;

import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.SmartspaceState;

// System UI unlock controller. Launcher will provide a LauncherUnlockAnimationController to this
// controller, which System UI will use to control the unlock animation within the Launcher window.
interface ISysuiUnlockAnimationController {
    // Provides an implementation of the LauncherUnlockAnimationController to System UI, so that
    // SysUI can use it to control the unlock animation in the launcher window.
    oneway void setLauncherUnlockController(
        String activityClass, ILauncherUnlockAnimationController callback);

    // Called by Launcher whenever anything happens to change the state of its smartspace. System UI
    // proactively saves this and uses it to perform the unlock animation without needing to make a
    // blocking query to Launcher asking about the smartspace state.
    oneway void onLauncherSmartspaceStateUpdated(in SmartspaceState state);
}