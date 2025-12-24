package com.android.launcher3.logging

/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.android.launcher3.util.IOUtils
import com.android.launcher3.util.LooperExecutor.Companion.createAndStartNewLooper
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.PrintWriter
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wrapper around [Log] to allow writing to a file. This class can safely be called from main
 * thread.
 *
 * Note: This should only be used for logging errors which have a persistent effect on user's data,
 * but whose effect may not be visible immediately.
 */
object FileLog {
    const val ENABLED: Boolean = true
    private const val FILE_NAME_PREFIX = "log-"
    private const val MAX_LOG_FILE_SIZE = (8 shl 20 /** 8 mb **/ ).toLong()
    public const val LOG_DAYS: Int = 4

    private val DATE_FORMAT: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private var sHandler: Handler? = null
    private var sLogsDirectory: File? = null

    @JvmStatic
    fun setDir(logsDir: File) {
        if (ENABLED) {
            synchronized(DATE_FORMAT) {
                // If the target directory changes, stop any active thread.
                if (sHandler != null && logsDir != sLogsDirectory) {
                    (sHandler!!.looper.thread as HandlerThread).quit()
                    sHandler = null
                }
            }
        }
        sLogsDirectory = logsDir
    }

    @JvmStatic
    fun d(tag: String?, msg: String?, e: Exception?) {
        Log.d(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun d(tag: String?, msg: String) {
        Log.d(tag, msg)
        print(tag, msg)
    }

    @JvmStatic
    fun i(tag: String?, msg: String?, e: Exception?) {
        Log.i(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun i(tag: String?, msg: String) {
        Log.i(tag, msg)
        print(tag, msg)
    }

    @JvmStatic
    fun w(tag: String?, msg: String?, e: Exception?) {
        Log.w(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun w(tag: String?, msg: String) {
        Log.w(tag, msg)
        print(tag, msg)
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, e: Exception?) {
        Log.e(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun e(tag: String?, msg: String) {
        Log.e(tag, msg)
        print(tag, msg)
    }

    @JvmOverloads
    @JvmStatic
    fun print(tag: String?, msg: String?, e: Exception? = null) {
        if (!ENABLED) {
            return
        }
        var out: String? = String.format("%s %s %s", DATE_FORMAT.format(Date()), tag, msg)
        if (e != null) {
            out += "\n" + Log.getStackTraceString(e)
        }
        Message.obtain(handler, LogWriterCallback.MSG_WRITE, out).sendToTarget()
    }

    @get:VisibleForTesting
    val handler: Handler
        get() {
            synchronized(DATE_FORMAT) {
                var handler = sHandler
                if (handler == null) {
                    handler = Handler(createAndStartNewLooper("file-logger"), LogWriterCallback())
                    sHandler = handler
                }
                return handler
            }
        }

    /**
     * Blocks until all the pending logs are written to the disk
     *
     * @param out if not null, all the persisted logs are copied to the writer.
     */
    @Throws(InterruptedException::class)
    @JvmStatic
    fun flushAll(out: PrintWriter?): Boolean {
        if (!ENABLED) {
            return false
        }
        val latch = CountDownLatch(1)
        Message.obtain(
                handler,
                LogWriterCallback.MSG_FLUSH,
                Pair.create<PrintWriter?, CountDownLatch?>(out, latch),
            )
            .sendToTarget()

        latch.await(2, TimeUnit.SECONDS)
        return latch.count == 0L
    }

    private fun dumpFile(out: PrintWriter, fileName: String) {
        val logFile = File(sLogsDirectory, fileName)
        if (logFile.exists()) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(FileReader(logFile))
                out.println()
                out.println("--- logfile: $fileName ---")
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    out.println(line)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                IOUtils.closeSilently(reader)
            }
        }
    }

    /**
     * Writes logs to the file. Log files are named log-0 for even days of the year and log-1 for
     * odd days of the year. Logs older than 36 hours are purged.
     */
    private class LogWriterCallback : Handler.Callback {
        private var mCurrentFileName: String? = null
        private var mCurrentWriter: PrintWriter? = null

        fun closeWriter() {
            IOUtils.closeSilently(mCurrentWriter)
            mCurrentWriter = null
        }

        override fun handleMessage(msg: Message): Boolean {
            if (sLogsDirectory == null || !ENABLED) {
                return true
            }
            when (msg.what) {
                MSG_WRITE -> {
                    val cal = Calendar.getInstance()
                    // suffix with number up to LOG_DAYS based on the day of the year.
                    val fileName = FILE_NAME_PREFIX + (cal.get(Calendar.DAY_OF_YEAR) % LOG_DAYS)

                    if (fileName != mCurrentFileName) {
                        closeWriter()
                    }

                    try {
                        if (mCurrentWriter == null) {
                            mCurrentFileName = fileName

                            var append = false
                            val logFile = File(sLogsDirectory, fileName)
                            if (logFile.exists()) {
                                val modifiedTime = Calendar.getInstance()
                                modifiedTime.timeInMillis = logFile.lastModified()

                                // If the file was modified more that 36 hours ago, purge the file.
                                // We use instead of 24 to account for day-365 followed by day-1
                                modifiedTime.add(Calendar.HOUR, 36)
                                append =
                                    cal.before(modifiedTime) && logFile.length() < MAX_LOG_FILE_SIZE
                            }
                            mCurrentWriter = PrintWriter(FileWriter(logFile, append))
                        }

                        mCurrentWriter!!.println(msg.obj as String?)
                        mCurrentWriter!!.flush()

                        // Auto close file stream after some time.
                        sHandler!!.removeMessages(MSG_CLOSE)
                        sHandler!!.sendEmptyMessageDelayed(MSG_CLOSE, CLOSE_DELAY)
                    } catch (e: Exception) {
                        Log.e("FileLog", "Error writing logs to file", e)
                        // Close stream, will try reopening during next log
                        closeWriter()
                    }
                    return true
                }

                MSG_CLOSE -> {
                    closeWriter()
                    return true
                }

                MSG_FLUSH -> {
                    closeWriter()
                    (msg.obj as? Pair<PrintWriter?, CountDownLatch?>)?.let { pair ->
                        pair.first?.let {
                            var i = 0
                            while (i < LOG_DAYS) {
                                dumpFile(it, FILE_NAME_PREFIX + i)
                                i++
                            }
                        }
                        pair.second?.countDown()
                    }
                    return true
                }
            }
            return true
        }

        companion object {
            private const val CLOSE_DELAY: Long = 5000 // 5 seconds
            const val MSG_WRITE = 1
            const val MSG_CLOSE = 2
            const val MSG_FLUSH = 3
        }
    }
}
