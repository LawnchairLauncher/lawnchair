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

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * A log to keep track of the active gesture.
 */
public class ActiveGestureLog {

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

    private final String name;
    private final EventEntry[] logs;
    private int nextIndex;
    private int mLogId;

    private ActiveGestureLog() {
        this.name = "touch_interaction_log";
        this.logs = new EventEntry[40];
        this.nextIndex = 0;
    }

    public void addLog(String event) {
        addLog(TYPE_ONE_OFF, event, 0, CompoundString.NO_OP);
    }

    public void addLog(String event, int extras) {
        addLog(TYPE_INTEGER, event, extras, CompoundString.NO_OP);
    }

    public void addLog(String event, boolean extras) {
        addLog(extras ? TYPE_BOOL_TRUE : TYPE_BOOL_FALSE, event, 0, CompoundString.NO_OP);
    }

    public void addLog(CompoundString compoundString) {
        addLog(TYPE_INPUT_CONSUMER, "", 0, compoundString);
    }

    private void addLog(
            int type, String event, float extras, @NonNull CompoundString compoundString) {
        // Merge the logs if it's a duplicate
        int last = (nextIndex + logs.length - 1) % logs.length;
        int secondLast = (nextIndex + logs.length - 2) % logs.length;
        if (isEntrySame(logs[last], type, event, compoundString)
                && isEntrySame(logs[secondLast], type, event, compoundString)) {
            logs[last].update(type, event, extras, compoundString, mLogId);
            logs[secondLast].duplicateCount++;
            return;
        }

        if (logs[nextIndex] == null) {
            logs[nextIndex] = new EventEntry();
        }
        logs[nextIndex].update(type, event, extras, compoundString, mLogId);
        nextIndex = (nextIndex + 1) % logs.length;
    }

    public void clear() {
        Arrays.setAll(logs, (i) -> null);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "EventLog (" + name + ") history:");
        SimpleDateFormat sdf = new SimpleDateFormat("  HH:mm:ss.SSSZ  ", Locale.US);
        Date date = new Date();

        for (int i = 0; i < logs.length; i++) {
            EventEntry log = logs[(nextIndex + logs.length - i - 1) % logs.length];
            if (log == null) {
                continue;
            }
            date.setTime(log.time);

            StringBuilder msg = new StringBuilder(prefix).append(sdf.format(date))
                    .append(log.event);
            switch (log.type) {
                case TYPE_BOOL_FALSE:
                    msg.append(": false");
                    break;
                case TYPE_BOOL_TRUE:
                    msg.append(": true");
                    break;
                case TYPE_FLOAT:
                    msg.append(": ").append(log.extras);
                    break;
                case TYPE_INTEGER:
                    msg.append(": ").append((int) log.extras);
                    break;
                case TYPE_INPUT_CONSUMER:
                    msg.append(log.mCompoundString);
                    break;
                default: // fall out
            }
            if (log.duplicateCount > 0) {
                msg.append(" & ").append(log.duplicateCount).append(" similar events");
            }
            msg.append(" traceId: ").append(log.traceId);
            writer.println(msg);
        }
    }

    /** Returns a 3 digit random number between 100-999 */
    public int generateAndSetLogId() {
        Random r = new Random();
        mLogId = r.nextInt(900) + 100;
        return mLogId;
    }

    private boolean isEntrySame(
            EventEntry entry, int type, String event, CompoundString compoundString) {
        return entry != null
                && entry.type == type
                && entry.event.equals(event)
                && entry.mCompoundString.equals(compoundString);
    }

    /** A single event entry. */
    private static class EventEntry {

        private int type;
        private String event;
        private float extras;
        @NonNull private CompoundString mCompoundString;
        private long time;
        private int duplicateCount;
        private int traceId;

        public void update(
                int type,
                String event,
                float extras,
                @NonNull CompoundString compoundString,
                int traceId) {
            this.type = type;
            this.event = event;
            this.extras = extras;
            this.mCompoundString = compoundString;
            this.traceId = traceId;
            time = System.currentTimeMillis();
            duplicateCount = 0;
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
            return mIsNoOp && other.mIsNoOp && Objects.equals(mSubstrings, other.mSubstrings);
        }
    }
}
