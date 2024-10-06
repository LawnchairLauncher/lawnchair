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
package com.android.launcher3.tapl;

import android.os.SystemClock;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class to verify expected events.
 */
public class LogEventChecker {

    private final LauncherInstrumentation mLauncher;

    // Map from an event sequence name to an ordered list of expected events in that sequence.
    private final ListMap<Pattern> mExpectedEvents = new ListMap<>();

    LogEventChecker(LauncherInstrumentation launcher) {
        mLauncher = launcher;
    }

    boolean start() {
        mExpectedEvents.clear();
        return mLauncher.getTestInfo(TestProtocol.REQUEST_START_EVENT_LOGGING) != null;
    }

    void expectPattern(String sequence, Pattern pattern) {
        mExpectedEvents.add(sequence, pattern);
    }

    // Waits for the expected number of events and returns them.
    private ListMap<String> finishSync(long waitForExpectedCountMs) {
        final long startTime = SystemClock.uptimeMillis();
        // Event strings with '/' separating the sequence and the event.
        ArrayList<String> rawEvents;

        while (true) {
            rawEvents = mLauncher.getTestInfo(TestProtocol.REQUEST_GET_TEST_EVENTS)
                    .getStringArrayList(TestProtocol.TEST_INFO_RESPONSE_FIELD);
            if (rawEvents == null) return null;

            final int expectedCount = mExpectedEvents.entrySet()
                    .stream().mapToInt(e -> e.getValue().size()).sum();
            if (rawEvents.size() >= expectedCount
                    || SystemClock.uptimeMillis() > startTime + waitForExpectedCountMs) {
                break;
            }
            SystemClock.sleep(100);
        }

        finishNoWait();

        // Parse raw events into a map.
        final ListMap<String> eventSequences = new ListMap<>();
        for (String rawEvent : rawEvents) {
            final String[] split = rawEvent.split("/");
            eventSequences.add(split[0], split[1]);
        }
        return eventSequences;
    }

    void finishNoWait() {
        mLauncher.getTestInfo(TestProtocol.REQUEST_STOP_EVENT_LOGGING);
    }

    String verify(long waitForExpectedCountMs) {
        final ListMap<String> actualEvents = finishSync(waitForExpectedCountMs);
        if (actualEvents == null) return "null event sequences because launcher likely died";

        return lowLevelMismatchDiagnostics(actualEvents);
    }

    private String lowLevelMismatchDiagnostics(ListMap<String> actualEvents) {
        final StringBuilder sb = new StringBuilder();
        boolean hasMismatches = false;
        for (Map.Entry<String, List<Pattern>> expectedEvents : mExpectedEvents.entrySet()) {
            String sequence = expectedEvents.getKey();

            List<String> actual = new ArrayList<>(actualEvents.getNonNull(sequence));
            final int mismatchPosition = getMismatchPosition(expectedEvents.getValue(), actual);
            hasMismatches = hasMismatches || mismatchPosition != -1;
            formatSequenceWithMismatch(
                    sb,
                    sequence,
                    expectedEvents.getValue(),
                    actual,
                    mismatchPosition);
        }
        // Check for unexpected event sequences in the actual data.
        for (String actualNamedSequence : actualEvents.keySet()) {
            if (!mExpectedEvents.containsKey(actualNamedSequence)) {
                hasMismatches = true;
                formatSequenceWithMismatch(
                        sb,
                        actualNamedSequence,
                        new ArrayList<>(),
                        actualEvents.get(actualNamedSequence),
                        0);
            }
        }

        return hasMismatches ? "Mismatching events: " + sb.toString() : null;
    }

    // If the list of actual events matches the list of expected events, returns -1, otherwise
    // the position of the mismatch.
    private static int getMismatchPosition(List<Pattern> expected, List<String> actual) {
        for (int i = 0; i < expected.size(); ++i) {
            if (i >= actual.size()
                    || !expected.get(i).matcher(actual.get(i)).find()) {
                return i;
            }
        }

        if (actual.size() > expected.size()) return expected.size();

        return -1;
    }

    private static void formatSequenceWithMismatch(
            StringBuilder sb,
            String sequenceName,
            List<Pattern> expected,
            List<String> actualEvents,
            int mismatchPosition) {
        sb.append("\n>> SEQUENCE " + sequenceName + " - "
                + (mismatchPosition == -1 ? "MATCH" : "MISMATCH"));
        sb.append("\n  EXPECTED:");
        formatEventListWithMismatch(sb, expected, mismatchPosition);
        sb.append("\n  ACTUAL:");
        formatEventListWithMismatch(sb, actualEvents, mismatchPosition);
    }

    private static void formatEventListWithMismatch(StringBuilder sb, List events, int position) {
        for (int i = 0; i < events.size(); ++i) {
            sb.append("\n  | ");
            sb.append(i == position ? "---> " : "     ");
            sb.append(events.get(i).toString());
        }
        if (position == events.size()) sb.append("\n  | ---> (end)");
    }

    private static class ListMap<T> extends HashMap<String, List<T>> {

        void add(String key, T value) {
            getNonNull(key).add(value);
        }

        List<T> getNonNull(String key) {
            List<T> list = get(key);
            if (list == null) {
                list = new ArrayList<>();
                put(key, list);
            }
            return list;
        }
    }
}
