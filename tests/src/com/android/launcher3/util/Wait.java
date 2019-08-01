package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;

import org.junit.Assert;

/**
 * A utility class for waiting for a condition to be true.
 */
public class Wait {

    private static final long DEFAULT_SLEEP_MS = 200;

    public static void atMost(String message, Condition condition, long timeout) {
        atMost(message, condition, timeout, DEFAULT_SLEEP_MS);
    }

    public static void atMost(String message, Condition condition, long timeout, long sleepMillis) {
        final long startTime = SystemClock.uptimeMillis();
        long endTime = startTime + timeout;
        Log.d("Wait", "atMost: " + startTime + " - " + endTime);
        while (SystemClock.uptimeMillis() < endTime) {
            try {
                if (condition.isTrue()) {
                    return;
                }
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            SystemClock.sleep(sleepMillis);
        }

        // Check once more before returning false.
        try {
            if (condition.isTrue()) {
                return;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        Log.d("Wait", "atMost: timed out: " + SystemClock.uptimeMillis());
        Assert.fail(message);
    }
}
