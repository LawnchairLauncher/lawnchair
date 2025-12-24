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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.logging.FileLog.flushAll
import com.android.launcher3.logging.FileLog.print
import com.android.launcher3.logging.FileLog.setDir
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [FileLog] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FileLogTest {
    private var mTempDir: File? = null

    @Before
    fun setUp() {
        var count = 0
        do {
            mTempDir =
                File(
                    ApplicationProvider.getApplicationContext<Context>().cacheDir,
                    "log-test-" + (count++),
                )
        } while (!mTempDir!!.mkdir())

        setDir(mTempDir!!)
    }

    @After
    fun tearDown() {
        // Clear existing logs
        for (i in 0..<FileLog.LOG_DAYS) {
            File(mTempDir, "log-$i").delete()
        }
        mTempDir!!.delete()
    }

    @Test
    @Throws(Exception::class)
    fun testPrintLog() {
        if (!FileLog.ENABLED) {
            return
        }
        print("Testing", "hoolalala")
        var writer = StringWriter()
        Assert.assertTrue(flushAll(PrintWriter(writer)))
        Assert.assertTrue(writer.toString().contains("hoolalala"))

        print("Testing", "abracadabra", Exception("cat! cat!"))
        writer = StringWriter()
        Assert.assertTrue(flushAll(PrintWriter(writer)))
        Assert.assertTrue(writer.toString().contains("abracadabra"))
        // Exception is also printed
        Assert.assertTrue(writer.toString().contains("cat! cat!"))

        // Old logs still present after flush
        Assert.assertTrue(writer.toString().contains("hoolalala"))
    }

    @Test
    @Throws(Exception::class)
    fun testOldFileTruncated() {
        if (!FileLog.ENABLED) {
            return
        }
        print("Testing", "hoolalala")
        var writer = StringWriter()
        Assert.assertTrue(flushAll(PrintWriter(writer)))
        Assert.assertTrue(writer.toString().contains("hoolalala"))

        val threeDaysAgo = Calendar.getInstance()
        threeDaysAgo.add(Calendar.HOUR, -72)
        for (i in 0..<FileLog.LOG_DAYS) {
            File(mTempDir, "log-$i").setLastModified(threeDaysAgo.timeInMillis)
        }

        print("Testing", "abracadabra", Exception("cat! cat!"))
        writer = StringWriter()
        Assert.assertTrue(flushAll(PrintWriter(writer)))
        Assert.assertTrue(writer.toString().contains("abracadabra"))
        // Exception is also printed
        Assert.assertTrue(writer.toString().contains("cat! cat!"))

        // Old logs have been truncated
        Assert.assertFalse(writer.toString().contains("hoolalala"))
    }
}
