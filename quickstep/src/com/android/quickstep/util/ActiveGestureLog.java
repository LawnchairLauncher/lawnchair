/*
 * Copyright (C) 2019 The Android Open Source Project
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A log to keep track of the active gesture.
 */
public class ActiveGestureLog {

    private static final int MAX_GESTURES_TRACKED = 10;

    public static final ActiveGestureLog INSTANCE = new ActiveGestureLog();

    /**
     * NOTE: This value should be kept same as
     * ActivityTaskManagerService#INTENT_EXTRA_LOG_TRACE_ID in platform
     */
    public static final String INTENT_EXTRA_LOG_TRACE_ID = "INTENT_EXTRA_LOG_TRACE_ID";

    private static final int TYPE_ONE_OFF = 0;
    private static final int TYPE_FLOAT = 1;
    private static final int TYPE_INTEGER = 2;
    private static final int TYPE_BOOL_TRUE = 3;
    private static final int TYPE_BOOL_FALSE = 4;
    private static final int TYPE_INPUT_CONSUMER = 5;
    private static final int TYPE_GESTURE_EVENT = 6;

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
        addLog(TYPE_GESTURE_EVENT, "", 0, CompoundString.NO_OP, gestureEvent);
    }

    public void addLog(String event) {
        addLog(event, null);
    }

    public void addLog(String event, int extras) {
        addLog(event, extras, null);
    }

    public void addLog(String event, boolean extras) {
        addLog(event, extras, null);
    }

    public void addLog(CompoundString compoundString) {
        addLog(TYPE_INPUT_CONSUMER, "", 0, compoundString, null);
    }

    /**
     * Adds a log and track the associated event for error detection.
     *
     * @param gestureEvent GestureEvent representing the event being logged.
     */
    public void addLog(
            String event, @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        addLog(TYPE_ONE_OFF, event, 0, CompoundString.NO_OP, gestureEvent);
    }

    public void addLog(
            String event,
            int extras,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        addLog(TYPE_INTEGER, event, extras, CompoundString.NO_OP, gestureEvent);
    }

    public void addLog(
            String event,
            boolean extras,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        addLog(
                extras ? TYPE_BOOL_TRUE : TYPE_BOOL_FALSE,
                event,
                0,
                CompoundString.NO_OP,
                gestureEvent);
    }

    private void addLog(
            int type,
            String event,
            float extras,
            CompoundString compoundString,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        EventLog lastEventLog = logs[(nextIndex + logs.length - 1) % logs.length];
        if (lastEventLog == null || mCurrentLogId != lastEventLog.logId) {
            EventLog eventLog = new EventLog(mCurrentLogId);
            EventEntry eventEntry = new EventEntry();

            eventEntry.update(type, event, extras, compoundString, gestureEvent);
            eventLog.eventEntries.add(eventEntry);
            logs[nextIndex] = eventLog;
            nextIndex = (nextIndex + 1) % logs.length;
            return;
        }

        // Update the last EventLog
        List<EventEntry> lastEventEntries = lastEventLog.eventEntries;
        EventEntry lastEntry = lastEventEntries.size() > 0
                ? lastEventEntries.get(lastEventEntries.size() - 1) : null;

        // Update the last EventEntry if it's a duplicate
        if (isEntrySame(lastEntry, type, event, extras, compoundString, gestureEvent)) {
            lastEntry.duplicateCount++;
            return;
        }
        EventEntry eventEntry = new EventEntry();

        eventEntry.update(type, event, extras, compoundString, gestureEvent);
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
                date.setTime(eventEntry.time);

                StringBuilder msg = new StringBuilder(prefix + "\t\t").append(sdf.format(date))
                        .append(eventEntry.event);
                switch (eventEntry.type) {
                    case TYPE_BOOL_FALSE:
                        msg.append(": false");
                        break;
                    case TYPE_BOOL_TRUE:
                        msg.append(": true");
                        break;
                    case TYPE_FLOAT:
                        msg.append(": ").append(eventEntry.extras);
                        break;
                    case TYPE_INTEGER:
                        msg.append(": ").append((int) eventEntry.extras);
                        break;
                    case TYPE_INPUT_CONSUMER:
                        msg.append(eventEntry.mCompoundString);
                        break;
                    case TYPE_GESTURE_EVENT:
                        continue;
                    default: // fall out
                }
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

    /** Returns the current log ID. This should be used when a log trace is being reused. */
    public int getLogId() {
        return mCurrentLogId;
    }

    private boolean isEntrySame(
            EventEntry entry,
            int type,
            String event,
            float extras,
            CompoundString compoundString,
            ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        return entry != null
                && entry.type == type
                && entry.event.equals(event)
                && Float.compare(entry.extras, extras) == 0
                && entry.mCompoundString.equals(compoundString)
                && entry.gestureEvent == gestureEvent;
    }

    /** A single event entry. */
    protected static class EventEntry {

        private int type;
        private String event;
        private float extras;
        @NonNull private CompoundString mCompoundString;
        private ActiveGestureErrorDetector.GestureEvent gestureEvent;
        private long time;
        private int duplicateCount;

        private EventEntry() {}

        @Nullable
        protected ActiveGestureErrorDetector.GestureEvent getGestureEvent() {
            return gestureEvent;
        }

        private void update(
                int type,
                String event,
                float extras,
                @NonNull CompoundString compoundString,
                ActiveGestureErrorDetector.GestureEvent gestureEvent) {
            this.type = type;
            this.event = event;
            this.extras = extras;
            this.mCompoundString = compoundString;
            this.gestureEvent = gestureEvent;
            time = System.currentTimeMillis();
            duplicateCount = 0;
        }
    }

    /** An entire log of entries associated with a single log ID */
    protected static class EventLog {

        protected final List<EventEntry> eventEntries = new ArrayList<>();
        protected final int logId;

        private EventLog(int logId) {
            this.logId = logId;
        }
    }

    /** A buildable string stored as an array for memory efficiency. */
    public static class CompoundString {

        public static final CompoundString NO_OP = new CompoundString();

        private final List<String> mSubstrings;

        private final boolean mIsNoOp;

        private CompoundString() {
            this(null);
        }

        public CompoundString(String substring) {
            mIsNoOp = substring == null;
            if (mIsNoOp) {
                mSubstrings = null;
                return;
            }
            mSubstrings = new ArrayList<>();
            mSubstrings.add(substring);
        }

        public CompoundString append(CompoundString substring) {
            if (mIsNoOp) {
                return this;
            }
            mSubstrings.addAll(substring.mSubstrings);

            return this;
        }

        public CompoundString append(String substring) {
            if (mIsNoOp) {
                return this;
            }
            mSubstrings.add(substring);

            return this;
        }

        @Override
        public String toString() {
            if (mIsNoOp) {
                return "ERROR: cannot use No-Op compound string";
            }
            StringBuilder sb = new StringBuilder();
            for (String substring : mSubstrings) {
                sb.append(substring);
            }

            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIsNoOp, mSubstrings);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CompoundString)) {
                return false;
            }
            CompoundString other = (CompoundString) obj;
            return (mIsNoOp == other.mIsNoOp) && Objects.equals(mSubstrings, other.mSubstrings);
        }
    }
}
