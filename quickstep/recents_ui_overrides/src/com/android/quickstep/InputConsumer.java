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
package com.android.quickstep;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

@TargetApi(Build.VERSION_CODES.O)
public interface InputConsumer {
    InputConsumer NO_OP = new InputConsumer() { };

    default boolean isActive() {
        return false;
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
}
