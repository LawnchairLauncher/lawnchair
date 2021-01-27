package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;

import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.Assert;

import java.util.function.Supplier;

/**
 * A utility class for waiting for a condition to be true.
 */
public class Wait {

    private static final long DEFAULT_SLEEP_MS = 200;

    public static void atMost(String message, Condition condition, long timeout,
            LauncherInstrumentation launcher) {
        atMost(() -> message, condition, timeout, DEFAULT_SLEEP_MS, launcher);
    }

    public static void atMost(Supplier<String> message, Condition condition, long timeout,
            LauncherInstrumentation launcher) {
        atMost(message, condition, timeout, DEFAULT_SLEEP_MS, launcher);
    }

    public static void atMost(Supplier<String> message, Condition condition, long timeout,
            long sleepMillis,
            LauncherInstrumentation launcher) {
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
        launcher.checkForAnomaly();
        Assert.fail(message.get());
    }

    /**
     * Interface representing a generic condition
     */
    public interface Condition {

        boolean isTrue() throws Throwable;
    }
}
