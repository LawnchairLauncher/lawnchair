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

package com.android.launcher3.hybridhotseat;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MainThreadInitializedObject;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Helper class to allow hot seat file logging
 */
public class HotseatFileLog {

    public static final int LOG_DAYS = 10;
    private static final String FILE_NAME_PREFIX = "hotseat-log-";
    private static final DateFormat DATE_FORMAT =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    public static final MainThreadInitializedObject<HotseatFileLog> INSTANCE =
            new MainThreadInitializedObject<>(HotseatFileLog::new);


    private final Handler mHandler = new Handler(
            Executors.createAndStartNewLooper("hotseat-logger"));
    private final File mLogsDir;
    private PrintWriter mCurrentWriter;
    private String mFileName;

    private HotseatFileLog(Context context) {
        mLogsDir = context.getFilesDir();
    }

    /**
     * Prints log values to disk
     */
    public void log(String tag, String msg) {
        String out = String.format("%s %s %s", DATE_FORMAT.format(new Date()), tag, msg);

        mHandler.post(() -> {
            synchronized (this) {
                PrintWriter writer = getWriter();
                if (writer != null) {
                    writer.println(out);
                }
            }
        });
    }

    private PrintWriter getWriter() {
        Calendar cal = Calendar.getInstance();
        String fName = FILE_NAME_PREFIX + (cal.get(Calendar.DAY_OF_YEAR) % 10);
        if (fName.equals(mFileName)) return mCurrentWriter;

        boolean append = false;
        File logFile = new File(mLogsDir, fName);
        if (logFile.exists()) {
            Calendar modifiedTime = Calendar.getInstance();
            modifiedTime.setTimeInMillis(logFile.lastModified());

            // If the file was modified more that 36 hours ago, purge the file.
            // We use instead of 24 to account for day-365 followed by day-1
            modifiedTime.add(Calendar.HOUR, 36);
            append = cal.before(modifiedTime);
        }


        if (mCurrentWriter != null) {
            mCurrentWriter.close();
        }
        try {
            mCurrentWriter = new PrintWriter(new FileWriter(logFile, append));
            mFileName = fName;
        } catch (Exception ex) {
            Log.e("HotseatLogs", "Error writing logs to file", ex);
            closeWriter();
        }
        return mCurrentWriter;
    }


    private synchronized void closeWriter() {
        mFileName = null;
        if (mCurrentWriter != null) {
            mCurrentWriter.close();
        }
        mCurrentWriter = null;
    }


    /**
     * Returns a list of all log files
     */
    public synchronized File[] getLogFiles() {
        File[] files = new File[LOG_DAYS + FileLog.LOG_DAYS];
        //include file log files here
        System.arraycopy(FileLog.getLogFiles(), 0, files, 0, FileLog.LOG_DAYS);

        closeWriter();
        for (int i = 0; i < LOG_DAYS; i++) {
            files[FileLog.LOG_DAYS + i] = new File(mLogsDir, FILE_NAME_PREFIX + i);
        }
        return files;
    }
}
