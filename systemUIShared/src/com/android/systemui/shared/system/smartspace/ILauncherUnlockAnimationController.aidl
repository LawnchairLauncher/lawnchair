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

import android.graphics.Rect;
import com.android.systemui.shared.system.smartspace.SmartspaceState;

// Methods for System UI to interface with Launcher to perform the unlock animation.
interface ILauncherUnlockAnimationController {
    // Prepares Launcher for the unlock animation by setting scale/alpha/etc. to their starting
    // values.
    void prepareForUnlock(boolean animateSmartspace, in Rect lockscreenSmartspaceBounds,
        int selectedPage);

    // Set the unlock percentage. This is used when System UI is controlling each frame of the
    // unlock animation, such as during a swipe to unlock touch gesture. Will not apply this change
    // if the unlock amount is animating unless forceIfAnimating is true.
    oneway void setUnlockAmount(float amount, boolean forceIfAnimating);

    // Play a full unlock animation from 0f to 1f. This is used when System UI is unlocking from a
    // single action, such as biometric auth, and doesn't need to control individual frames.
    oneway void playUnlockAnimation(boolean unlocked, long duration, long startDelay);

    // Set the selected page on Launcher's smartspace.
    oneway void setSmartspaceSelectedPage(int selectedPage);

    // Set the visibility of Launcher's smartspace.
    void setSmartspaceVisibility(int visibility);

    // Tell SystemUI the smartspace's current state. Launcher code should call this whenever the
    // smartspace state may have changed.
    oneway void dispatchSmartspaceStateToSysui();
}