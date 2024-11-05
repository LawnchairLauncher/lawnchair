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

package com.android.wm.shell.bubbles;

import android.os.Bundle;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;
/**
 * Listener interface that Launcher attaches to SystemUI to get bubbles callbacks.
 */
oneway interface IBubblesListener {

    /**
     * Called when the bubbles state changes.
     */
    void onBubbleStateChange(in Bundle update);

    /**
     * Called when bubble bar should temporarily be animated to a new location.
     * Does not result in a state change.
     */
    void animateBubbleBarLocation(in BubbleBarLocation location);
}