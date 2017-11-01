package com.android.launcher3.util;

import android.os.SystemClock;

/**
 * A utility class for waiting for a condition to be true.
 */
public class Wait {

    private static final long DEFAULT_SLEEP_MS = 200;

    public static boolean atMost(Condition condition, long timeout) {
        return atMost(condition, timeout, DEFAULT_SLEEP_MS);
    }

    public static boolean atMost(Condition condition, long timeout, long sleepMillis) {
        long endTime = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < endTime) {
            try {
                if (condition.isTrue()) {
                    return true;
                }
            } catch (Throwable t) {
                // Ignore
            }
            SystemClock.sleep(sleepMillis);
        }

        // Check once more before returning false.
        try {
            if (condition.isTrue()) {
                return true;
            }
        } catch (Throwable t) {
            // Ignore
        }
        return false;
    }
}
