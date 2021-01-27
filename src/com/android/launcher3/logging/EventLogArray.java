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
package com.android.launcher3.logging;


import android.util.Log;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * A utility class to record and log events. Events are stored in a fixed size array and old logs
 * are purged as new events come.
 */
public class EventLogArray {

    private static final int TYPE_ONE_OFF = 0;
    private static final int TYPE_FLOAT = 1;
    private static final int TYPE_INTEGER = 2;
    private static final int TYPE_BOOL_TRUE = 3;
    private static final int TYPE_BOOL_FALSE = 4;

    private final String name;
    private final EventEntry[] logs;
    private int nextIndex;
    private int mLogId;

    public EventLogArray(String name, int size) {
        this.name = name;
        logs = new EventEntry[size];
        nextIndex = 0;
    }

    public void addLog(String event) {
        addLog(TYPE_ONE_OFF, event, 0);
    }

    public void addLog(String event, int extras) {
        addLog(TYPE_INTEGER, event, extras);
    }

    public void addLog(String event, boolean extras) {
        addLog(extras ? TYPE_BOOL_TRUE : TYPE_BOOL_FALSE, event, 0);
    }

    private void addLog(int type, String event, float extras) {
        // Merge the logs if its a duplicate
        int last = (nextIndex + logs.length - 1) % logs.length;
        int secondLast = (nextIndex + logs.length - 2) % logs.length;
        if (isEntrySame(logs[last], type, event) && isEntrySame(logs[secondLast], type, event)) {
            logs[last].update(type, event, extras, mLogId);
            logs[secondLast].duplicateCount++;
            return;
        }

        if (logs[nextIndex] == null) {
            logs[nextIndex] = new EventEntry();
        }
        logs[nextIndex].update(type, event, extras, mLogId);
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

    private boolean isEntrySame(EventEntry entry, int type, String event) {
        return entry != null && entry.type == type && entry.event.equals(event);
    }

    /** A single event entry. */
    private static class EventEntry {

        private int type;
        private String event;
        private float extras;
        private long time;
        private int duplicateCount;
        private int traceId;

        public void update(int type, String event, float extras, int traceId) {
            this.type = type;
            this.event = event;
            this.extras = extras;
            this.traceId = traceId;
            time = System.currentTimeMillis();
            duplicateCount = 0;
        }
    }
}
