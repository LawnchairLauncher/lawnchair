package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.util.Log;

import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    private static int sScreenshotCount = 0;
    private AbstractLauncherUiTest mAbstractLauncherUiTest;

    public FailureWatcher(AbstractLauncherUiTest abstractLauncherUiTest) {
        mAbstractLauncherUiTest = abstractLauncherUiTest;
    }

    private void dumpViewHierarchy() {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            mAbstractLauncherUiTest.getDevice().dumpWindowHierarchy(stream);
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
        if (mAbstractLauncherUiTest.getDevice() == null) return;
        final String pathname = getInstrumentation().getTargetContext().
                getFilesDir().getPath() + "/TaplTestScreenshot" + sScreenshotCount++ + ".png";
        Log.e(TAG, "Failed test " + description.getMethodName() +
                ", screenshot will be saved to " + pathname +
                ", track trace is below, UI object dump is further below:\n" +
                Log.getStackTraceString(e));
        dumpViewHierarchy();

        try {
            final String dumpsysResult = mAbstractLauncherUiTest.getDevice().executeShellCommand(
                    "dumpsys activity service TouchInteractionService");
            Log.d(TAG, "TouchInteractionService: " + dumpsysResult);
        } catch (IOException ex) {
        }

        mAbstractLauncherUiTest.getDevice().takeScreenshot(new File(pathname));
    }
}
