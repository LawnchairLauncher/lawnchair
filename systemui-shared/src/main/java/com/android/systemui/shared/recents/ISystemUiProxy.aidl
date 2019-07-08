/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.shared.recents;

import android.graphics.Rect;
import com.android.systemui.shared.system.GraphicBufferCompat;

/**
 * Temporary callbacks into SystemUI.
 */
interface ISystemUiProxy {

    /**
     * Proxies SurfaceControl.screenshotToBuffer().
     */
    GraphicBufferCompat screenshot(in Rect sourceCrop, int width, int height, int minLayer,
            int maxLayer, boolean useIdentityTransform, int rotation) = 0;

    /**
     * Begins screen pinning on the provided {@param taskId}.
     */
    void startScreenPinning(int taskId) = 1;

    /**
     * Enables/disables launcher/overview interaction features {@link InteractionType}.
     */
    void setInteractionState(int flags) = 4;

    /**
     * Notifies SystemUI that split screen has been invoked.
     */
    void onSplitScreenInvoked() = 5;

    /**
     * Notifies SystemUI that Overview is shown.
     */
    void onOverviewShown(boolean fromHome) = 6;

    /**
     * Get the secondary split screen app's rectangle when not minimized.
     */
    Rect getNonMinimizedSplitScreenSecondaryBounds() = 7;

    /**
     * Control the {@param alpha} of the back button in the navigation bar and {@param animate} if
     * needed from current value
     */
    void setBackButtonAlpha(float alpha, boolean animate) = 8;
}
