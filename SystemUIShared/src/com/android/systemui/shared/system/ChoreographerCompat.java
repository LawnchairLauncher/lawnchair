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
package com.android.systemui.shared.system;

import static android.view.Choreographer.CALLBACK_INPUT;

import android.view.Choreographer;

/**
 * Wraps the internal choreographer.
 */
public class ChoreographerCompat {

    /**
     * Posts an input callback to the choreographer.
     */
    public static void postInputFrame(Choreographer choreographer, Runnable runnable) {
        choreographer.postCallback(CALLBACK_INPUT, runnable, null);
    }

    public static Choreographer getSfInstance() {
        return Choreographer.getSfInstance();
    }
}
