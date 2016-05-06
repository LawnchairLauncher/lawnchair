package com.android.launcher3.logging;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

/**
 * Tests for {@link FileLog}
 */
@SmallTest
public class FileLogTest extends AndroidTestCase {

    private File mTempDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        int count = 0;
        do {
            mTempDir = new File(getContext().getCacheDir(), "log-test-" + (count++));
        } while(!mTempDir.mkdir());

        FileLog.setDir(mTempDir);
    }

    @Override
    protected void tearDown() throws Exception {
        // Clear existing logs
        new File(mTempDir, "log-0").delete();
        new File(mTempDir, "log-1").delete();
        mTempDir.delete();
        super.tearDown();
    }

    public void testPrintLog() throws Exception {
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

    public void testOldFileTruncated() throws Exception {
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
