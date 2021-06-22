package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    final private UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;

    public FailureWatcher(UiDevice device, LauncherInstrumentation launcher) {
        mDevice = device;
        mLauncher = launcher;
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
    protected void succeeded(Description description) {
        super.succeeded(description);
        AbstractLauncherUiTest.checkDetectedLeaks(mLauncher);
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
}
