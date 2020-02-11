package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    private static boolean sHadFailedTestDeinitialization;
    final private UiDevice mDevice;

    public FailureWatcher(UiDevice device) {
        mDevice = device;
    }

    private static void dumpViewHierarchy(UiDevice device) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            device.dumpWindowHierarchy(stream);
            stream.flush();
            stream.close();
            for (String line : stream.toString().split("\\r?\\n")) {
                Log.e(TAG, line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "error dumping XML to logcat", e);
        }
    }

    @Override
    protected void failed(Throwable e, Description description) {
        onError(mDevice, description, e);
    }

    public static void onError(UiDevice device, Description description, Throwable e) {
        if (device == null) return;
        final String pathname = getInstrumentation().getTargetContext().
                getFilesDir().getPath() + "/TestScreenshot-" + description.getMethodName()
                + ".png";
        Log.e(TAG, "Failed test " + description.getMethodName() +
                ", screenshot will be saved to " + pathname +
                ", track trace is below, UI object dump is further below:\n" +
                Log.getStackTraceString(e));
        dumpViewHierarchy(device);

        try {
            final String dumpsysResult = device.executeShellCommand(
                    "dumpsys activity service TouchInteractionService");
            Log.d(TAG, "TouchInteractionService: " + dumpsysResult);
        } catch (IOException ex) {
        }

        device.takeScreenshot(new File(pathname));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                if (sHadFailedTestDeinitialization) {
                    Log.d(TAG, "Skipping due to a recent test deinitialization failure: " +
                            description.getDisplayName());
                    return;
                }

                try {
                    FailureWatcher.super.apply(base, description).evaluate();
                } catch (Throwable e) {
                    final String stackTrace = Log.getStackTraceString(e);
                    if (!stackTrace.contains(
                            "androidx.test.internal.runner.junit4.statement.RunBefores.evaluate")) {
                        // Test failed to deinitialize. Since the global state is probably
                        // corrupted, won't execute other tests.
                        Log.d(TAG,
                                "Detected an exception from test finalizer, will skip further "
                                        + "tests: " + stackTrace);
                        sHadFailedTestDeinitialization = true;
                    }
                    throw e;
                }
            }
        };
    }
}
