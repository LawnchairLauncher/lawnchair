package com.android.launcher3.util.rule;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.os.FileUtils;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.UiDevice;

import com.android.app.viewcapture.ViewCapture;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    private static boolean sSavedBugreport = false;
    final private UiDevice mDevice;
    private final LauncherInstrumentation mLauncher;
    @NonNull
    private final ViewCapture mViewCapture;

    public FailureWatcher(UiDevice device, LauncherInstrumentation launcher,
            @NonNull ViewCapture viewCapture) {
        mDevice = device;
        mLauncher = launcher;
        mViewCapture = viewCapture;
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
                    FailureWatcher.super.apply(base, description).evaluate();
                } finally {
                    // Detect touch events coming from physical screen.
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
        onError(mLauncher, description, e, mViewCapture);
    }

    static File diagFile(Description description, String prefix, String ext) {
        return new File(getInstrumentation().getTargetContext().getFilesDir(),
                prefix + "-" + description.getTestClass().getSimpleName() + "."
                        + description.getMethodName() + "." + ext);
    }

    public static void onError(LauncherInstrumentation launcher, Description description,
            Throwable e) {
        onError(launcher, description, e, null);
    }

    private static void onError(LauncherInstrumentation launcher, Description description,
            Throwable e, @Nullable ViewCapture viewCapture) {

        final File sceenshot = diagFile(description, "TestScreenshot", "png");
        final File hierarchy = diagFile(description, "Hierarchy", "zip");

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

            if (viewCapture != null) {
                out.putNextEntry(new ZipEntry("FS/data/misc/wmtrace/failed_test.vc"));
                viewCapture.dumpTo(out, ApplicationProvider.getApplicationContext());
                out.closeEntry();
            }
        } catch (Exception ignored) {
        }

        Log.e(TAG, "Failed test " + description.getMethodName()
                + ",\nscreenshot will be saved to " + sceenshot
                + ",\nUI dump at: " + hierarchy
                + " (use go/web-hv to open the dump file)", e);
        final UiDevice device = launcher.getDevice();
        device.takeScreenshot(sceenshot);

        // Dump accessibility hierarchy
        try {
            device.dumpWindowHierarchy(diagFile(description, "AccessibilityHierarchy", "uix"));
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save accessibility hierarchy", ex);
        }

        dumpCommand("logcat -d -s TestRunner", diagFile(description, "FilteredLogcat", "txt"));

        // Dump bugreport
        if (!sSavedBugreport) {
            dumpCommand("bugreportz -s", diagFile(description, "Bugreport", "zip"));
            // Not saving bugreport for each failure for time and space economy.
            sSavedBugreport = true;
        }
    }

    private static void dumpStringCommand(String cmd, OutputStream out) throws IOException {
        out.write(("\n\n" + cmd + "\n").getBytes());
        dumpCommand(cmd, out);
    }

    private static void dumpCommand(String cmd, File out) {
        try (BufferedOutputStream buffered = new BufferedOutputStream(
                new FileOutputStream(out))) {
            dumpCommand(cmd, buffered);
        } catch (IOException ex) {
        }
    }

    private static void dumpCommand(String cmd, OutputStream out) throws IOException {
        try (AutoCloseInputStream in = new AutoCloseInputStream(getInstrumentation()
                .getUiAutomation().executeShellCommand(cmd))) {
            FileUtils.copy(in, out);
        }
    }
}
