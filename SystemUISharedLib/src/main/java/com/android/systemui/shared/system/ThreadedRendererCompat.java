/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.view.ThreadedRenderer;

/**
 * @see ThreadedRenderer
 */
public class ThreadedRendererCompat {

    public static int EGL_CONTEXT_PRIORITY_HIGH_IMG = 0x3101;
    public static int EGL_CONTEXT_PRIORITY_MEDIUM_IMG = 0x3102;
    public static int EGL_CONTEXT_PRIORITY_LOW_IMG = 0x3103;

    public static void setContextPriority(int priority) {
        ThreadedRenderer.setContextPriority(priority);
    }
}
