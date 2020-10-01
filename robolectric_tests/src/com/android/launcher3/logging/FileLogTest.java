package com.android.launcher3.logging;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.util.Scheduler;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

/**
 * Tests for {@link FileLog}
 */
@RunWith(RobolectricTestRunner.class)
public class FileLogTest {

    private File mTempDir;
    private boolean mTestActive;

    @Before
    public void setUp() {
        int count = 0;
        do {
            mTempDir = new File(RuntimeEnvironment.application.getCacheDir(),
                    "log-test-" + (count++));
        } while (!mTempDir.mkdir());

        FileLog.setDir(mTempDir);

        mTestActive = true;
        Scheduler scheduler = Shadows.shadowOf(FileLog.getHandler().getLooper()).getScheduler();
        new Thread(() -> {
            while (mTestActive) {
                scheduler.advanceToLastPostedRunnable();
            }
        }).start();
    }

    @After
    public void tearDown() {
        // Clear existing logs
        for (int i = 0; i < FileLog.LOG_DAYS; i++) {
            new File(mTempDir, "log-" + i).delete();
        }
        mTempDir.delete();

        mTestActive = false;
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
