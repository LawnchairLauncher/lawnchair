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

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.test.uiautomator.UiDevice;

import org.junit.Assert;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public class TestUtil {
    public static final String DUMMY_PACKAGE = "com.example.android.aardwolf";

    public static void installDummyApp() throws IOException {
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
                    .executeShellCommand("pm install " + apkFilename);
            Assert.assertTrue(
                    "Failed to install wellbeing test apk; make sure the device is rooted",
                    "Success".equals(result.replaceAll("\\s+", "")));
            pic.mAddWait.await();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static void uninstallDummyApp() throws IOException {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                "pm uninstall " + DUMMY_PACKAGE);
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
