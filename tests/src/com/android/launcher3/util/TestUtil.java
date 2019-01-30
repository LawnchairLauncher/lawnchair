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

import android.content.res.Resources;

import androidx.test.uiautomator.UiDevice;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

        final FileOutputStream out = new FileOutputStream(apkFilename);
        byte[] buff = new byte[1024];
        int read;

        while ((read = in.read(buff)) > 0) {
            out.write(buff, 0, read);
        }
        in.close();
        out.close();

        UiDevice.getInstance(getInstrumentation()).executeShellCommand("pm install " + apkFilename);
    }

    public static void uninstallDummyApp() throws IOException {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                "pm uninstall " + DUMMY_PACKAGE);
    }
}
