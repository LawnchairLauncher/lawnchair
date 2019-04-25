/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.view.MotionEvent;

import java.io.PrintWriter;

public interface TouchController {

    /**
     * Called when the draglayer receives touch event.
     */
    boolean onControllerTouchEvent(MotionEvent ev);

    /**
     * Called when the draglayer receives a intercept touch event.
     */
    boolean onControllerInterceptTouchEvent(MotionEvent ev);

    default void dump(String prefix, PrintWriter writer) { }
}
