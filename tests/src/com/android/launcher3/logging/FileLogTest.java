package com.android.launcher3.logging;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link FileLog}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FileLogTest {

    private File mTempDir;

    @Before
    public void setUp() throws Exception {
        int count = 0;
        do {
            mTempDir = new File(InstrumentationRegistry.getTargetContext().getCacheDir(),
                    "log-test-" + (count++));
        } while(!mTempDir.mkdir());

        FileLog.setDir(mTempDir);
    }

    @After
    public void tearDown() throws Exception {
        // Clear existing logs
        new File(mTempDir, "log-0").delete();
        new File(mTempDir, "log-1").delete();
        mTempDir.delete();
    }

    @Test
    public void testPrintLog() throws Exception {
        if (!FileLog.ENABLED) {
            return;
        }
        FileLog.print("Testing", "hoolalala");
        StringWriter writer = new StringWriter();
        FileLog.flushAll(new PrintWriter(writer));
        assertTrue(writer.toString().contains("hoolalala"));

        FileLog.print("Testing", "abracadabra", new Exception("cat! cat!"));
        writer = new StringWriter();
        FileLog.flushAll(new PrintWriter(writer));
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
        FileLog.flushAll(new PrintWriter(writer));
        assertTrue(writer.toString().contains("hoolalala"));

        Calendar threeDaysAgo = Calendar.getInstance();
        threeDaysAgo.add(Calendar.HOUR, -72);
        new File(mTempDir, "log-0").setLastModified(threeDaysAgo.getTimeInMillis());
        new File(mTempDir, "log-1").setLastModified(threeDaysAgo.getTimeInMillis());

        FileLog.print("Testing", "abracadabra", new Exception("cat! cat!"));
        writer = new StringWriter();
        FileLog.flushAll(new PrintWriter(writer));
        assertTrue(writer.toString().contains("abracadabra"));
        // Exception is also printed
        assertTrue(writer.toString().contains("cat! cat!"));

        // Old logs have been truncated
        assertFalse(writer.toString().contains("hoolalala"));
    }
}
