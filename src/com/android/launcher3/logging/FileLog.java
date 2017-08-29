package com.android.launcher3.logging;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper around {@link Log} to allow writing to a file.
 * This class can safely be called from main thread.
 *
 * Note: This should only be used for logging errors which have a persistent effect on user's data,
 * but whose effect may not be visible immediately.
 */
public final class FileLog {

    protected static final boolean ENABLED =
            FeatureFlags.IS_DOGFOOD_BUILD || Utilities.IS_DEBUG_DEVICE;
    private static final String FILE_NAME_PREFIX = "log-";
    private static final DateFormat DATE_FORMAT =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private static final long MAX_LOG_FILE_SIZE = 4 << 20;  // 4 mb

    private static Handler sHandler = null;
    private static File sLogsDirectory = null;

    public static void setDir(File logsDir) {
        if (ENABLED) {
            synchronized (DATE_FORMAT) {
                // If the target directory changes, stop any active thread.
                if (sHandler != null && !logsDir.equals(sLogsDirectory)) {
                    ((HandlerThread) sHandler.getLooper().getThread()).quit();
                    sHandler = null;
                }
            }
        }
        sLogsDirectory = logsDir;
    }

    public static void d(String tag, String msg, Exception e) {
        Log.d(tag, msg, e);
        print(tag, msg, e);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        print(tag, msg);
    }

    public static void e(String tag, String msg, Exception e) {
        Log.e(tag, msg, e);
        print(tag, msg, e);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        print(tag, msg);
    }

    public static void print(String tag, String msg) {
        print(tag, msg, null);
    }

    public static void print(String tag, String msg, Exception e) {
        if (!ENABLED) {
            return;
        }
        String out = String.format("%s %s %s", DATE_FORMAT.format(new Date()), tag, msg);
        if (e != null) {
            out += "\n" + Log.getStackTraceString(e);
        }
        Message.obtain(getHandler(), LogWriterCallback.MSG_WRITE, out).sendToTarget();
    }

    private static Handler getHandler() {
        synchronized (DATE_FORMAT) {
            if (sHandler == null) {
                HandlerThread thread = new HandlerThread("file-logger");
                thread.start();
                sHandler = new Handler(thread.getLooper(), new LogWriterCallback());
            }
        }
        return sHandler;
    }

    /**
     * Blocks until all the pending logs are written to the disk
     * @param out if not null, all the persisted logs are copied to the writer.
     */
    public static void flushAll(PrintWriter out) throws InterruptedException {
        if (!ENABLED) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Message.obtain(getHandler(), LogWriterCallback.MSG_FLUSH,
                Pair.create(out, latch)).sendToTarget();

        latch.await(2, TimeUnit.SECONDS);
    }

    /**
     * Writes logs to the file.
     * Log files are named log-0 for even days of the year and log-1 for odd days of the year.
     * Logs older than 36 hours are purged.
     */
    private static class LogWriterCallback implements Handler.Callback {

        private static final long CLOSE_DELAY = 5000;  // 5 seconds

        private static final int MSG_WRITE = 1;
        private static final int MSG_CLOSE = 2;
        private static final int MSG_FLUSH = 3;

        private String mCurrentFileName = null;
        private PrintWriter mCurrentWriter = null;

        private void closeWriter() {
            Utilities.closeSilently(mCurrentWriter);
            mCurrentWriter = null;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (sLogsDirectory == null || !ENABLED) {
                return true;
            }
            switch (msg.what) {
                case MSG_WRITE: {
                    Calendar cal = Calendar.getInstance();
                    // suffix with 0 or 1 based on the day of the year.
                    String fileName = FILE_NAME_PREFIX + (cal.get(Calendar.DAY_OF_YEAR) & 1);

                    if (!fileName.equals(mCurrentFileName)) {
                        closeWriter();
                    }

                    try {
                        if (mCurrentWriter == null) {
                            mCurrentFileName = fileName;

                            boolean append = false;
                            File logFile = new File(sLogsDirectory, fileName);
                            if (logFile.exists()) {
                                Calendar modifiedTime = Calendar.getInstance();
                                modifiedTime.setTimeInMillis(logFile.lastModified());

                                // If the file was modified more that 36 hours ago, purge the file.
                                // We use instead of 24 to account for day-365 followed by day-1
                                modifiedTime.add(Calendar.HOUR, 36);
                                append = cal.before(modifiedTime)
                                        && logFile.length() < MAX_LOG_FILE_SIZE;
                            }
                            mCurrentWriter = new PrintWriter(new FileWriter(logFile, append));
                        }

                        mCurrentWriter.println((String) msg.obj);
                        mCurrentWriter.flush();

                        // Auto close file stream after some time.
                        sHandler.removeMessages(MSG_CLOSE);
                        sHandler.sendEmptyMessageDelayed(MSG_CLOSE, CLOSE_DELAY);
                    } catch (Exception e) {
                        Log.e("FileLog", "Error writing logs to file", e);
                        // Close stream, will try reopening during next log
                        closeWriter();
                    }
                    return true;
                }
                case MSG_CLOSE: {
                    closeWriter();
                    return true;
                }
                case MSG_FLUSH: {
                    closeWriter();
                    Pair<PrintWriter, CountDownLatch> p =
                            (Pair<PrintWriter, CountDownLatch>) msg.obj;

                    if (p.first != null) {
                        dumpFile(p.first, FILE_NAME_PREFIX + 0);
                        dumpFile(p.first, FILE_NAME_PREFIX + 1);
                    }
                    p.second.countDown();
                    return true;
                }
            }
            return true;
        }
    }

    private static void dumpFile(PrintWriter out, String fileName) {
        File logFile = new File(sLogsDirectory, fileName);
        if (logFile.exists()) {

            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(logFile));
                out.println();
                out.println("--- logfile: " + fileName + " ---");
                String line;
                while ((line = in.readLine()) != null) {
                    out.println(line);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                Utilities.closeSilently(in);
            }
        }
    }
}
