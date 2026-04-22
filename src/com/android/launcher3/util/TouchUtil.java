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
package com.android.launcher3.util;

import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

/** Util class for touch event. */
public final class TouchUtil {

    private TouchUtil() {}

    /**
     * Detect ACTION_DOWN or ACTION_MOVE from mouse right button. Note that we cannot detect
     * ACTION_UP from mouse's right button because, in that case,
     * {@link MotionEvent#getButtonState()} returns 0 for any mouse button (right, middle, right).
     */
    public static boolean isMouseRightClickDownOrMove(@NonNull MotionEvent event) {
        return event.isFromSource(InputDevice.SOURCE_MOUSE)
                && ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0);
    }
}
