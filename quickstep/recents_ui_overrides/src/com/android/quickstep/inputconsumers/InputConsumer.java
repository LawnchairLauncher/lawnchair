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
 * limitations under the License.
 */
package com.android.quickstep.inputconsumers;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

@TargetApi(Build.VERSION_CODES.O)
public interface InputConsumer {

    int TYPE_NO_OP = 1 << 0;
    int TYPE_OVERVIEW = 1 << 1;
    int TYPE_OTHER_ACTIVITY = 1 << 2;
    int TYPE_ASSISTANT = 1 << 3;
    int TYPE_DEVICE_LOCKED = 1 << 4;
    int TYPE_ACCESSIBILITY = 1 << 5;
    int TYPE_SCREEN_PINNED = 1 << 6;
    int TYPE_OVERVIEW_WITHOUT_FOCUS = 1 << 7;
    int TYPE_RESET_GESTURE = 1 << 8;

    String[] NAMES = new String[] {
           "TYPE_NO_OP",                    // 0
            "TYPE_OVERVIEW",                // 1
            "TYPE_OTHER_ACTIVITY",          // 2
            "TYPE_ASSISTANT",               // 3
            "TYPE_DEVICE_LOCKED",           // 4
            "TYPE_ACCESSIBILITY",           // 5
            "TYPE_SCREEN_PINNED",           // 6
            "TYPE_OVERVIEW_WITHOUT_FOCUS",  // 7
            "TYPE_RESET_GESTURE",           // 8
    };

    InputConsumer NO_OP = () -> TYPE_NO_OP;

    int getType();

    default boolean useSharedSwipeState() {
        return false;
    }

    /**
     * Returns true if the user has crossed the threshold for it to be an explicit action.
     */
    default boolean allowInterceptByParent() {
        return true;
    }

    /**
     * Called by the event queue when the consumer is about to be switched to a new consumer.
     */
    default void onConsumerAboutToBeSwitched() { }

    default void onMotionEvent(MotionEvent ev) { }

    default void onKeyEvent(KeyEvent ev) { }

    default void onInputEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onMotionEvent((MotionEvent) ev);
        } else {
            onKeyEvent((KeyEvent) ev);
        }
    }

    default String getName() {
        String name = "";
        for (int i = 0; i < NAMES.length; i++) {
            if ((getType() & (1 << i)) != 0) {
                if (name.length() > 0) {
                    name += ":";
                }
                name += NAMES[i];
            }
        }
        return name;
    }
}
