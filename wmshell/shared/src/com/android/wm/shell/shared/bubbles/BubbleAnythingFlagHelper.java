/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.shared.bubbles;

import com.android.wm.shell.Flags;

/**
 * Bubble anything has some dependent flags, this class simplifies the checks.
 * (TODO: b/389737359 - remove this when the feature is launched).
 */
public class BubbleAnythingFlagHelper {

    private BubbleAnythingFlagHelper() {}

    /** Whether creating any bubble or the overall bubble anything feature is enabled. */
    public static boolean enableCreateAnyBubble() {
        return enableBubbleAnything() || Flags.enableCreateAnyBubble();
    }

    /**
     * Whether creating any bubble and transforming to fullscreen, or the overall bubble anything
     * feature is enabled.
     */
    public static boolean enableBubbleToFullscreen() {
        return enableBubbleAnything()
                || (Flags.enableBubbleToFullscreen()
                && Flags.enableCreateAnyBubble());
    }

    /** Whether the overall bubble anything feature is enabled. */
    public static boolean enableBubbleAnything() {
        return Flags.enableBubbleAnything();
    }
}
