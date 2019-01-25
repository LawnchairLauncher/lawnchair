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

package com.android.launcher3.util;

/**
 * Event tracker for reliably reproducing race conditions in tests.
 * The app should call onEvent() for events that the test will try to reproduce in all possible
 * orders.
 */
public class RaceConditionTracker {
    public final static boolean ENTER = true;
    public final static boolean EXIT = false;
    static final String ENTER_POSTFIX = "enter";
    static final String EXIT_POSTFIX = "exit";

    public interface EventProcessor {
        void onEvent(String eventName);
    }

    private static EventProcessor sEventProcessor;

    static void setEventProcessor(EventProcessor eventProcessor) {
        sEventProcessor = eventProcessor;
    }

    public static void onEvent(String eventName) {
        if (sEventProcessor != null) sEventProcessor.onEvent(eventName);
    }

    public static void onEvent(String eventName, boolean isEnter) {
        if (sEventProcessor != null) {
            sEventProcessor.onEvent(enterExitEvt(eventName, isEnter));
        }
    }

    public static String enterExitEvt(String eventName, boolean isEnter) {
        return eventName + ":" + (isEnter ? ENTER_POSTFIX : EXIT_POSTFIX);
    }

    public static String enterEvt(String eventName) {
        return enterExitEvt(eventName, ENTER);
    }

    public static String exitEvt(String eventName) {
        return enterExitEvt(eventName, EXIT);
    }
}
