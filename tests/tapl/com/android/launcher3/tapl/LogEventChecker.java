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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.util.Log;

import com.android.launcher3.testing.TestProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to read log on a background thread.
 */
public class LogEventChecker {

    private static final Pattern EVENT_LOG_ENTRY = Pattern.compile(
            ".*" + TestProtocol.TAPL_EVENTS_TAG + ": (?<sequence>[a-zA-Z]+) / (?<event>.*)");

    private static final String START_PREFIX = "START_READER ";
    private static final String FINISH_PREFIX = "FINISH_READER ";
    private static final String SKIP_EVENTS_TAG = "b/153670015";

    private volatile CountDownLatch mFinished;

    // Map from an event sequence name to an ordered list of expected events in that sequence.
    private final ListMap<Pattern> mExpectedEvents = new ListMap<>();

    private final ListMap<String> mEvents = new ListMap<>();
    private final Semaphore mEventsCounter = new Semaphore(0);

    private volatile String mStartCommand;
    private volatile String mFinishCommand;

    LogEventChecker() {
        final Thread thread = new Thread(this::onRun, "log-reader-thread");
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();
    }

    void start() {
        if (mFinished != null) {
            try {
                mFinished.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                mFinished = null;
            }
        }
        mEvents.clear();
        Log.d(SKIP_EVENTS_TAG, "Cleared events");
        mExpectedEvents.clear();
        mEventsCounter.drainPermits();
        final String id = UUID.randomUUID().toString();
        mStartCommand = START_PREFIX + id;
        mFinishCommand = FINISH_PREFIX + id;
        Log.d(SKIP_EVENTS_TAG, "Expected finish command: " + mFinishCommand);
        Log.d(TestProtocol.TAPL_EVENTS_TAG, mStartCommand);
    }

    private void onRun() {
        while (true) readEvents();
    }

    private void readEvents() {
        try {
            // Note that we use Runtime.exec to start the log reading process instead of running
            // it via UIAutomation, so that we can directly access the "Process" object and
            // ensure that the instrumentation is not stuck forever.
            final String cmd = "logcat -s " + TestProtocol.TAPL_EVENTS_TAG;

            final Process logcatProcess = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    logcatProcess.getInputStream()))) {
                while (true) {
                    // Skip everything before the next start command.
                    for (; ; ) {
                        final String event = reader.readLine();
                        if (event == null) {
                            Log.d(SKIP_EVENTS_TAG, "Read a null line while waiting for start");
                            return;
                        }
                        if (event.contains(mStartCommand)) {
                            Log.d(SKIP_EVENTS_TAG, "Read start: " + event);
                            break;
                        }
                    }

                    // Store all actual events until the finish command.
                    for (; ; ) {
                        final String event = reader.readLine();
                        if (event == null) {
                            Log.d(SKIP_EVENTS_TAG, "Read a null line after waiting for start");
                            mEventsCounter.drainPermits();
                            mEvents.clear();
                            return;
                        }
                        if (event.contains(mFinishCommand)) {
                            mFinished.countDown();
                            Log.d(SKIP_EVENTS_TAG, "Read finish: " + event);
                            break;
                        } else {
                            final Matcher matcher = EVENT_LOG_ENTRY.matcher(event);
                            if (matcher.find()) {
                                mEvents.add(matcher.group("sequence"), matcher.group("event"));
                                Log.d(SKIP_EVENTS_TAG, "Read event: " + event);
                                mEventsCounter.release();
                            } else {
                                Log.d(SKIP_EVENTS_TAG, "Read something unexpected: " + event);
                            }
                        }
                    }
                }
            } finally {
                logcatProcess.destroyForcibly();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void expectPattern(String sequence, Pattern pattern) {
        mExpectedEvents.add(sequence, pattern);
    }

    private void finishSync(long waitForExpectedCountMs) {
        try {
            // Wait until Launcher generates the expected number of events.
            int expectedCount = mExpectedEvents.entrySet()
                    .stream().mapToInt(e -> e.getValue().size()).sum();
            mEventsCounter.tryAcquire(expectedCount, waitForExpectedCountMs, MILLISECONDS);
            finishNoWait();
            mFinished.await();
            mFinished = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void finishNoWait() {
        mFinished = new CountDownLatch(1);
        Log.d(TestProtocol.TAPL_EVENTS_TAG, mFinishCommand);
    }

    String verify(long waitForExpectedCountMs, boolean successfulGesture) {
        finishSync(waitForExpectedCountMs);

        final StringBuilder sb = new StringBuilder();
        boolean hasMismatches = false;
        for (Map.Entry<String, List<Pattern>> expectedEvents : mExpectedEvents.entrySet()) {
            String sequence = expectedEvents.getKey();

            List<String> actual = new ArrayList<>(mEvents.getNonNull(sequence));
            Log.d(SKIP_EVENTS_TAG, "Verifying events");
            final int mismatchPosition = getMismatchPosition(expectedEvents.getValue(), actual);
            hasMismatches = hasMismatches
                    || mismatchPosition != -1 && !ignoreMistatch(successfulGesture, sequence);
            formatSequenceWithMismatch(
                    sb,
                    sequence,
                    expectedEvents.getValue(),
                    actual,
                    mismatchPosition);
        }
        // Check for unexpected event sequences in the actual data.
        for (String actualNamedSequence : mEvents.keySet()) {
            if (!mExpectedEvents.containsKey(actualNamedSequence)) {
                hasMismatches = hasMismatches
                        || !ignoreMistatch(successfulGesture, actualNamedSequence);
                formatSequenceWithMismatch(
                        sb,
                        actualNamedSequence,
                        new ArrayList<>(),
                        mEvents.get(actualNamedSequence),
                        0);
            }
        }

        return hasMismatches ? "mismatching events: " + sb.toString() : null;
    }

    // Workaround for b/154157191
    private static boolean ignoreMistatch(boolean successfulGesture, String sequence) {
        // b/156287114
        return false;
//        return TestProtocol.SEQUENCE_TIS.equals(sequence) && successfulGesture;
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
