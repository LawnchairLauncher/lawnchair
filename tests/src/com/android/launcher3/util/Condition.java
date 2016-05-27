package com.android.launcher3.util;

import android.support.test.uiautomator.UiObject2;

import com.android.launcher3.MainThreadExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Condition {

    public abstract boolean isTrue() throws Throwable;

    /**
     * Converts the condition to be run on UI thread.
     */
    public static Condition runOnUiThread(final Condition condition) {
        final MainThreadExecutor executor = new MainThreadExecutor();
        return new Condition() {
            @Override
            public boolean isTrue() throws Throwable {
                final AtomicBoolean value = new AtomicBoolean(false);
                final Throwable[] exceptions = new Throwable[1];
                final CountDownLatch latch = new CountDownLatch(1);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            value.set(condition.isTrue());
                        } catch (Throwable e) {
                            exceptions[0] = e;
                        }

                    }
                });
                latch.await(1, TimeUnit.SECONDS);
                if (exceptions[0] != null) {
                    throw exceptions[0];
                }
                return value.get();
            }
        };
    }

    public static Condition minChildCount(final UiObject2 obj, final int childCount) {
        return new Condition() {
            @Override
            public boolean isTrue() {
                return obj.getChildCount() >= childCount;
            }
        };
    }
}
