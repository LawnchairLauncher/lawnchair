/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.quickstep.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A log to keep track of the active gesture.
 */
public class ActiveGestureLog {

    private static final int MAX_GESTURES_TRACKED = 15;

    public static final ActiveGestureLog INSTANCE = new ActiveGestureLog();

    private boolean mIsFullyGesturalNavMode;

    /**
     * NOTE: This value should be kept same as
     * ActivityTaskManagerService#INTENT_EXTRA_LOG_TRACE_ID in platform
     */
    public static final String INTENT_EXTRA_LOG_TRACE_ID = "INTENT_EXTRA_LOG_TRACE_ID";

    private final EventLog[] logs;
    private int nextIndex;
    private int mCurrentLogId = 100;

    private ActiveGestureLog() {
        this.logs = new EventLog[MAX_GESTURES_TRACKED];
        this.nextIndex = 0;
    }

    /**
     * Track the given event for error detection.
     *
     * @param gestureEvent GestureEvent representing an event during the current gesture's
     *                   execution.
     */
    public void trackEvent(@Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        addLog(CompoundString.NO_OP, gestureEvent);
    }

    /**
     * Adds a log to be printed at log-dump-time.
     */
    public void addLog(@NonNull String event) {
        addLog(event, null);
    }

    /**
     * Adds a log to be printed at log-dump-time and track the associated event for error detection.
     *
     * @param gestureEvent GestureEvent representing the event being logged.
     */
    public void addLog(
            @NonNull String event, @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        addLog(new CompoundString(event), gestureEvent);
    }

    public void addLog(@NonNull CompoundString compoundString) {
        addLog(compoundString, null);
    }

    public void addLog(
            @NonNull CompoundString compoundString,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        EventLog lastEventLog = logs[(nextIndex + logs.length - 1) % logs.length];
        if (lastEventLog == null || mCurrentLogId != lastEventLog.logId) {
            EventLog eventLog = new EventLog(mCurrentLogId, mIsFullyGesturalNavMode);
            EventEntry eventEntry = new EventEntry();

            eventEntry.update(compoundString, gestureEvent);
            eventLog.eventEntries.add(eventEntry);
            logs[nextIndex] = eventLog;
            nextIndex = (nextIndex + 1) % logs.length;
            return;
        }

        // Update the last EventLog
        List<EventEntry> lastEventEntries = lastEventLog.eventEntries;
        EventEntry lastEntry = !lastEventEntries.isEmpty()
                ? lastEventEntries.get(lastEventEntries.size() - 1) : null;

        // Update the last EventEntry if it's a duplicate
        if (isEntrySame(lastEntry, compoundString, gestureEvent)) {
            lastEntry.duplicateCount++;
            return;
        }
        EventEntry eventEntry = new EventEntry();

        eventEntry.update(compoundString, gestureEvent);
        lastEventEntries.add(eventEntry);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "ActiveGestureErrorDetector:");
        for (int i = 0; i < logs.length; i++) {
            EventLog eventLog = logs[(nextIndex + i) % logs.length];
            if (eventLog == null) {
                continue;
            }
            ActiveGestureErrorDetector.analyseAndDump(prefix + '\t', writer, eventLog);
        }

        writer.println(prefix + "ActiveGestureLog history:");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSSZ  ", Locale.US);
        Date date = new Date();
        for (int i = 0; i < logs.length; i++) {
            EventLog eventLog = logs[(nextIndex + i) % logs.length];
            if (eventLog == null) {
                continue;
            }

            writer.println(prefix + "\tLogs for logId: " + eventLog.logId);
            for (EventEntry eventEntry : eventLog.eventEntries) {
                if (eventEntry.mCompoundString.mIsNoOp) {
                    continue;
                }
                date.setTime(eventEntry.time);

                StringBuilder msg = new StringBuilder(prefix + "\t\t")
                        .append(sdf.format(date))
                        .append(eventEntry.mCompoundString);
                if (eventEntry.duplicateCount > 0) {
                    msg.append(" & ").append(eventEntry.duplicateCount).append(" similar events");
                }
                writer.println(msg);
            }
        }
    }

    /**
     * Increments and returns the current log ID. This should be used every time a new log trace
     * is started.
     */
    public int incrementLogId() {
        return mCurrentLogId++;
    }

    public void setIsFullyGesturalNavMode(boolean isFullyGesturalNavMode) {
        mIsFullyGesturalNavMode = isFullyGesturalNavMode;
    }

    /** Returns the current log ID. This should be used when a log trace is being reused. */
    public int getLogId() {
        return mCurrentLogId;
    }

    private boolean isEntrySame(
            EventEntry entry,
            CompoundString compoundString,
            ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        return entry != null
                && entry.mCompoundString.equals(compoundString)
                && entry.gestureEvent == gestureEvent;
    }

    /** A single event entry. */
    protected static class EventEntry {

        @NonNull private CompoundString mCompoundString;
        private ActiveGestureErrorDetector.GestureEvent gestureEvent;
        private long time;
        private int duplicateCount;

        private EventEntry() {}

        @Nullable
        protected ActiveGestureErrorDetector.GestureEvent getGestureEvent() {
            return gestureEvent;
        }

        public int getDuplicateCount() {
            return duplicateCount;
        }

        private void update(
                @NonNull CompoundString compoundString,
                ActiveGestureErrorDetector.GestureEvent gestureEvent) {
            this.mCompoundString = compoundString;
            this.gestureEvent = gestureEvent;
            time = System.currentTimeMillis();
            duplicateCount = 0;
        }

        public long getTime() {
            return time;
        }
    }

    /** An entire log of entries associated with a single log ID */
    protected static class EventLog {

        protected final List<EventEntry> eventEntries =
                Collections.synchronizedList(new ArrayList<>());
        protected final int logId;
        protected final boolean mIsFullyGesturalNavMode;

        private EventLog(int logId, boolean isFullyGesturalNavMode) {
            this.logId = logId;
            mIsFullyGesturalNavMode = isFullyGesturalNavMode;
        }
    }

    /** A buildable string stored as an array for memory efficiency. */
    public static class CompoundString {

        public static final CompoundString NO_OP = new CompoundString(true);

        private final List<String> mSubstrings;
        private final List<Object> mArgs;

        private final boolean mIsNoOp;

        public static CompoundString newEmptyString() {
            return new CompoundString(false);
        }

        private CompoundString(boolean isNoOp) {
            mIsNoOp = isNoOp;
            mSubstrings = mIsNoOp ? null : new ArrayList<>();
            mArgs = mIsNoOp ? null : new ArrayList<>();
        }

        public CompoundString(String substring, Object... args) {
            this(substring == null);

            append(substring, args);
        }

        public CompoundString append(CompoundString substring) {
            if (mIsNoOp || substring.mIsNoOp) {
                return this;
            }
            mSubstrings.addAll(substring.mSubstrings);
            mArgs.addAll(substring.mArgs);

            return this;
        }

        public CompoundString append(String substring, Object... args) {
            if (mIsNoOp) {
                return this;
            }
            mSubstrings.add(substring);
            mArgs.addAll(Arrays.stream(args).toList());

            return this;
        }

        @Override
        public String toString() {
            if (mIsNoOp) return null;
            StringBuilder sb = new StringBuilder();
            for (String substring : mSubstrings) {
                sb.append(substring);
            }
            return String.format(sb.toString(), mArgs.toArray());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIsNoOp, mSubstrings, mArgs);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CompoundString other)) {
                return false;
            }
            return (mIsNoOp == other.mIsNoOp)
                    && Objects.equals(mSubstrings, other.mSubstrings)
                    && Objects.equals(mArgs, other.mArgs);
        }
    }
}
