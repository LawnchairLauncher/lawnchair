package com.android.launcher3.logging;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

/**
 * Tests for {@link FileLog}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FileLogTest {

    private File mTempDir;
    @Before
    public void setUp() {
        int count = 0;
        do {
            mTempDir = new File(getApplicationContext().getCacheDir(),
                    "log-test-" + (count++));
        } while (!mTempDir.mkdir());

        FileLog.setDir(mTempDir);
    }

    @After
    public void tearDown() {
        // Clear existing logs
        for (int i = 0; i < FileLog.LOG_DAYS; i++) {
            new File(mTempDir, "log-" + i).delete();
        }
        mTempDir.delete();
    }

    @Test
    public void testPrintLog() throws Exception {
        if (!FileLog.ENABLED) {
            return;
        }
        FileLog.print("Testing", "hoolalala");
        StringWriter writer = new StringWriter();
        assertTrue(FileLog.flushAll(new PrintWriter(writer)));
        assertTrue(writer.toString().contains("hoolalala"));

        FileLog.print("Testing", "abracadabra", new Exception("cat! cat!"));
        writer = new StringWriter();
        assertTrue(FileLog.flushAll(new PrintWriter(writer)));
        assertTrue(writer.toString().contains("abracadabra"));
        // Exception is also printed
        assertTrue(writer.toString().contains("cat! cat!"));

        // Old logs still present after flush
        assertTrue(writer.toString().contains("hoolalala"));
    }

    @Test
    public void testOldFileTruncated() throws Exception {
        if (!FileLog.ENABLED) {
            return;
        }
        FileLog.print("Testing", "hoolalala");
        StringWriter writer = new StringWriter();
        assertTrue(FileLog.flushAll(new PrintWriter(writer)));
        assertTrue(writer.toString().contains("hoolalala"));

        Calendar threeDaysAgo = Calendar.getInstance();
        threeDaysAgo.add(Calendar.HOUR, -72);
        for (int i = 0; i < FileLog.LOG_DAYS; i++) {
            new File(mTempDir, "log-" + i).setLastModified(threeDaysAgo.getTimeInMillis());
        }

        FileLog.print("Testing", "abracadabra", new Exception("cat! cat!"));
        writer = new StringWriter();
        assertTrue(FileLog.flushAll(new PrintWriter(writer)));
        assertTrue(writer.toString().contains("abracadabra"));
        // Exception is also printed
        assertTrue(writer.toString().contains("cat! cat!"));

        // Old logs have been truncated
        assertFalse(writer.toString().contains("hoolalala"));
    }
}
