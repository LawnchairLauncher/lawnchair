package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.os.FileUtils;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    final private UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;

    public FailureWatcher(UiDevice device, LauncherInstrumentation launcher) {
        mDevice = device;
        mLauncher = launcher;
    }

    @Override
    protected void succeeded(Description description) {
        super.succeeded(description);
        AbstractLauncherUiTest.checkDetectedLeaks(mLauncher);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    if (mLauncher.hadNontestEvents()) {
                        throw new AssertionError(
                                "Launcher received events not sent by the test. This may mean "
                                        + "that the touch screen of the lab device has sent false"
                                        + " events. See the logcat for TaplEvents tag and look "
                                        + "for events with deviceId != -1");
                    }
                }
            }
        };
    }

    @Override
    protected void failed(Throwable e, Description description) {
        onError(mDevice, description, e);
    }

    public static void onError(UiDevice device, Description description, Throwable e) {
        if (device == null) return;
        final File parentFile = getInstrumentation().getTargetContext().getFilesDir();
        final File sceenshot = new File(parentFile,
                "TestScreenshot-" + description.getMethodName() + ".png");
        final File hierarchy = new File(parentFile,
                "Hierarchy-" + description.getMethodName() + ".zip");

        // Dump window hierarchy
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(hierarchy))) {
            out.putNextEntry(new ZipEntry("bugreport.txt"));
            dumpStringCommand("dumpsys window windows", out);
            dumpStringCommand("dumpsys package", out);
            dumpStringCommand("dumpsys activity service TouchInteractionService", out);
            out.closeEntry();

            out.putNextEntry(new ZipEntry("visible_windows.zip"));
            dumpCommand("cmd window dump-visible-window-views", out);
            out.closeEntry();
        } catch (IOException ex) {
        }

        Log.e(TAG, "Failed test " + description.getMethodName()
                + ",\nscreenshot will be saved to " + sceenshot
                + ",\nUI dump at: " + hierarchy
                + " (use go/web-hv to open the dump file)", e);
        device.takeScreenshot(sceenshot);

        // Dump accessibility hierarchy
        final File accessibilityHierarchyFile = new File(parentFile,
                "AccessibilityHierarchy-" + description.getMethodName() + ".uix");
        try {
            device.dumpWindowHierarchy(accessibilityHierarchyFile);
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save accessibility hierarchy", ex);
        }
    }

    private static void dumpStringCommand(String cmd, OutputStream out) throws IOException {
        out.write(("\n\n" + cmd + "\n").getBytes());
        dumpCommand(cmd, out);
    }

    private static void dumpCommand(String cmd, OutputStream out) throws IOException {
        try (AutoCloseInputStream in = new AutoCloseInputStream(getInstrumentation()
                .getUiAutomation().executeShellCommand(cmd))) {
            FileUtils.copy(in, out);
        }
    }
}
