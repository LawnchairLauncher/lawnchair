/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.testing;

import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.launcher3.Utilities;
import com.android.launcher3.testing.shared.TestProtocol;

import java.util.function.BiConsumer;

public final class TestLogging {
    private static final String TAPL_EVENTS_TAG = "TaplEvents";
    private static final String LAUNCHER_EVENTS_TAG = "LauncherEvents";
    private static BiConsumer<String, String> sEventConsumer;
    public static boolean sHadEventsNotFromTest;

    private static void recordEventSlow(String sequence, String event, boolean reportToTapl) {
        Log.d(reportToTapl ? TAPL_EVENTS_TAG : LAUNCHER_EVENTS_TAG,
                sequence + " / " + event);
        final BiConsumer<String, String> eventConsumer = sEventConsumer;
        if (reportToTapl && eventConsumer != null) {
            eventConsumer.accept(sequence, event);
        }
    }

    public static void recordEvent(String sequence, String event) {
        if (Utilities.isRunningInTestHarness()) {
            recordEventSlow(sequence, event, true);
        }
    }

    public static void recordEvent(String sequence, String message, Object parameter) {
        if (Utilities.isRunningInTestHarness()) {
            recordEventSlow(sequence, message + ": " + parameter, true);
        }
    }

    private static void registerEventNotFromTest(InputEvent event) {
        if (!sHadEventsNotFromTest && event.getDeviceId() != -1) {
            sHadEventsNotFromTest = true;
            Log.d(TestProtocol.PERMANENT_DIAG_TAG, "First event not from test: " + event);
        }
    }

    public static void recordKeyEvent(String sequence, String message, KeyEvent event) {
        if (Utilities.isRunningInTestHarness()) {
            recordEventSlow(sequence, message + ": " + event, true);
            registerEventNotFromTest(event);
        }
    }

    public static void recordMotionEvent(String sequence, String message, MotionEvent event) {
        final int action = event.getAction();
        if (Utilities.isRunningInTestHarness() && action != MotionEvent.ACTION_MOVE) {
            // "Expecting" in TAPL motion events was thought to be producing considerable noise in
            // tests due to failed checks for expected events. So we are not sending them to TAPL.
            // Other events, such as EVENT_PILFER_POINTERS produce less noise and are thought to
            // be more useful.
            // That's why we pass false as the value for the 'reportToTapl' parameter.
            recordEventSlow(sequence, message + ": " + event, false);
            registerEventNotFromTest(event);
        }
    }

    static void setEventConsumer(BiConsumer<String, String> consumer) {
        sEventConsumer = consumer;
    }
}
