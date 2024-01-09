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

package com.android.quickstep;

import android.content.Context;

import com.android.launcher3.R;

/**
 * Control and get information about the gesture nav bar at the bottom of the screen, which has
 * historically been drawn by SysUI, but is also emulated by the stashed Taskbar on large screens.
 */
public interface NavHandle {

    /**
     * Animate the nav bar being long-pressed.
     *
     * @param isTouchDown {@code true} if the button is starting to be pressed ({@code false} if
     *                                released or canceled)
     * @param shrink {@code true} if the handle should shrink, {@code false} if it should grow
     * @param durationMs how long the animation should take (for the {@code isTouchDown} case, this
     *                   should be the same as the amount of time to trigger a long-press)
     */
    void animateNavBarLongPress(boolean isTouchDown, boolean shrink, long durationMs);

    /** @return {@code true} if this nav handle is actually the stashed taskbar */
    default boolean isNavHandleStashedTaskbar() {
        return false;
    }

    /** @return {@code true} if this nav handle can currently accept long presses */
    default boolean canNavHandleBeLongPressed() {
        return true;
    }

    /** @return the width of this nav handle, in pixels */
    default int getNavHandleWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.navigation_home_handle_width);
    }
}
