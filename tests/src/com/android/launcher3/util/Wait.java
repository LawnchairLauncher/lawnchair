package com.android.launcher3.util;

import android.os.SystemClock;

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
        long endTime = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < endTime) {
            try {
                if (condition.isTrue()) {
                    return;
                }
            } catch (Throwable t) {
                // Ignore
            }
            SystemClock.sleep(sleepMillis);
        }

        // Check once more before returning false.
        try {
            if (condition.isTrue()) {
                return;
            }
        } catch (Throwable t) {
            // Ignore
        }
        Assert.fail(message);
    }
}
