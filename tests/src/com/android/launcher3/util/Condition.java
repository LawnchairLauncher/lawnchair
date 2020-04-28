package com.android.launcher3.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import androidx.test.uiautomator.UiObject2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public interface Condition {

    boolean isTrue() throws Throwable;

    /**
     * Converts the condition to be run on UI thread.
     */
    static Condition runOnUiThread(final Condition condition) {
        final LooperExecutor executor = MAIN_EXECUTOR;
        return () -> {
            final AtomicBoolean value = new AtomicBoolean(false);
            final Throwable[] exceptions = new Throwable[1];
            final CountDownLatch latch = new CountDownLatch(1);
            executor.execute(() -> {
                try {
                    value.set(condition.isTrue());
                } catch (Throwable e) {
                    exceptions[0] = e;
                }

            });
            latch.await(1, TimeUnit.SECONDS);
            if (exceptions[0] != null) {
                throw exceptions[0];
            }
            return value.get();
        };
    }

    static Condition minChildCount(final UiObject2 obj, final int childCount) {
        return () -> obj.getChildCount() >= childCount;
    }
}
