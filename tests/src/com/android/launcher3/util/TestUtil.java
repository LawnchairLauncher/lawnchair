/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.util;

import static android.util.Base64.NO_PADDING;
import static android.util.Base64.NO_WRAP;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import static com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_KEY;
import static com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_LABEL;
import static com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_TAG;

import android.app.blob.BlobHandle;
import android.app.blob.BlobStoreManager;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Base64;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.FeatureFlags.BooleanFlag;
import com.android.launcher3.config.FeatureFlags.IntFlag;

import org.junit.Assert;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class TestUtil {
    public static final String DUMMY_PACKAGE = "com.example.android.aardwolf";
    public static final int DEFAULT_USER_ID = 0;

    public static void installDummyApp() throws IOException {
        installDummyAppForUser(DEFAULT_USER_ID);
    }

    public static void installDummyAppForUser(int userId) throws IOException {
        // Copy apk from resources to a local file and install from there.
        final Resources resources = getContext().getResources();
        final InputStream in = resources.openRawResource(
                resources.getIdentifier("aardwolf_dummy_app",
                        "raw", getContext().getPackageName()));
        final String apkFilename = getInstrumentation().getTargetContext().
                getFilesDir().getPath() + "/dummy_app.apk";

        try (PackageInstallCheck pic = new PackageInstallCheck()) {
            final FileOutputStream out = new FileOutputStream(apkFilename);
            byte[] buff = new byte[1024];
            int read;

            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            in.close();
            out.close();

            final String result = UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("pm install --user " + userId + " " + apkFilename);
            Assert.assertTrue(
                    "Failed to install wellbeing test apk; make sure the device is rooted",
                    "Success".equals(result.replaceAll("\\s+", "")));
            pic.mAddWait.await();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * Utility class to override a boolean flag during test. Note that the returned SafeCloseable
     * must be closed to restore the original state
     */
    public static SafeCloseable overrideFlag(BooleanFlag flag, boolean value) {
        Predicate<BooleanFlag> originalProxy = FeatureFlags.sBooleanReader;
        Predicate<BooleanFlag> testProxy = f -> f == flag ? value : originalProxy.test(f);
        FeatureFlags.sBooleanReader = testProxy;
        return () -> {
            if (FeatureFlags.sBooleanReader == testProxy) {
                FeatureFlags.sBooleanReader = originalProxy;
            }
        };
    }

    /**
     * Utility class to override a int flag during test. Note that the returned SafeCloseable
     * must be closed to restore the original state
     */
    public static SafeCloseable overrideFlag(IntFlag flag, int value) {
        ToIntFunction<IntFlag> originalProxy = FeatureFlags.sIntReader;
        ToIntFunction<IntFlag> testProxy = f -> f == flag ? value : originalProxy.applyAsInt(f);
        FeatureFlags.sIntReader = testProxy;
        return () -> {
            if (FeatureFlags.sIntReader == testProxy) {
                FeatureFlags.sIntReader = originalProxy;
            }
        };
    }

    public static void uninstallDummyApp() throws IOException {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                "pm uninstall " + DUMMY_PACKAGE);
    }

    /**
     * Sets the default layout for Launcher and returns an object which can be used to clear
     * the data
     */
    public static AutoCloseable setLauncherDefaultLayout(
            Context context, LauncherLayoutBuilder layoutBuilder) throws Exception {
        byte[] data = layoutBuilder.build().getBytes();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);

        BlobHandle handle = BlobHandle.createWithSha256(
                digest, LAYOUT_DIGEST_LABEL, 0, LAYOUT_DIGEST_TAG);
        BlobStoreManager blobManager = context.getSystemService(BlobStoreManager.class);
        final long sessionId = blobManager.createSession(handle);
        CountDownLatch wait = new CountDownLatch(1);
        try (BlobStoreManager.Session session = blobManager.openSession(sessionId)) {
            try (OutputStream out = new AutoCloseOutputStream(session.openWrite(0, -1))) {
                out.write(data);
            }
            session.allowPublicAccess();
            session.commit(AsyncTask.THREAD_POOL_EXECUTOR, i -> wait.countDown());
        }

        String key = Base64.encodeToString(digest, NO_WRAP | NO_PADDING);
        Settings.Secure.putString(context.getContentResolver(), LAYOUT_DIGEST_KEY, key);
        wait.await();
        return () ->
            Settings.Secure.putString(context.getContentResolver(), LAYOUT_DIGEST_KEY, null);
    }

    private static class PackageInstallCheck extends LauncherApps.Callback
            implements AutoCloseable {

        final CountDownLatch mAddWait = new CountDownLatch(1);
        final LauncherApps mLauncherApps;

        PackageInstallCheck() {
            mLauncherApps = getTargetContext().getSystemService(LauncherApps.class);
            mLauncherApps.registerCallback(this, new Handler(Looper.getMainLooper()));
        }

        private void verifyPackage(String packageName) {
            if (DUMMY_PACKAGE.equals(packageName)) {
                mAddWait.countDown();
            }
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            verifyPackage(packageName);
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            verifyPackage(packageName);
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) { }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            for (String packageName : packageNames) {
                verifyPackage(packageName);
            }
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) { }

        @Override
        public void close() {
            mLauncherApps.unregisterCallback(this);
        }
    }
}
