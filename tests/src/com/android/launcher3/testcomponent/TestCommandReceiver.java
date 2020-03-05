/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.testcomponent;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.test.InstrumentationRegistry;

import com.android.launcher3.tapl.TestHelpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Content provider to receive commands from tests
 */
public class TestCommandReceiver extends ContentProvider {

    public static final String ENABLE_TEST_LAUNCHER = "enable-test-launcher";
    public static final String DISABLE_TEST_LAUNCHER = "disable-test-launcher";
    public static final String KILL_PROCESS = "kill-process";
    public static final String GET_SYSTEM_HEALTH_MESSAGE = "get-system-health-message";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("unimplemented mock method");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("unimplemented mock method");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("unimplemented mock method");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException("unimplemented mock method");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("unimplemented mock method");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        switch (method) {
            case ENABLE_TEST_LAUNCHER: {
                getContext().getPackageManager().setComponentEnabledSetting(
                        new ComponentName(getContext(), TestLauncherActivity.class),
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
                return null;
            }
            case DISABLE_TEST_LAUNCHER: {
                getContext().getPackageManager().setComponentEnabledSetting(
                        new ComponentName(getContext(), TestLauncherActivity.class),
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
                return null;
            }
            case KILL_PROCESS: {
                ((ActivityManager) getContext().getSystemService(Activity.ACTIVITY_SERVICE)).
                        killBackgroundProcesses(arg);
                return null;
            }

            case GET_SYSTEM_HEALTH_MESSAGE: {
                final Bundle response = new Bundle();
                response.putString("result",
                        TestHelpers.getSystemHealthMessage(getContext(), Long.parseLong(arg)));
                return response;
            }
        }
        return super.call(method, arg, extras);
    }

    public static Bundle callCommand(String command) {
        return callCommand(command, null);
    }

    public static Bundle callCommand(String command, String arg) {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Uri uri = Uri.parse("content://" + inst.getContext().getPackageName() + ".commands");
        return inst.getTargetContext().getContentResolver().call(uri, command, arg, null);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = Base64.encodeToString(uri.getPath().getBytes(),
                Base64.NO_CLOSE | Base64.NO_PADDING | Base64.NO_WRAP);
        File file = new File(getContext().getCacheDir(), path);
        if (!file.exists()) {
            // Create an empty file so that we can pass its descriptor
            try {
                file.createNewFile();
            } catch (IOException e) {
            }
        }

        return ParcelFileDescriptor.open(file, MODE_READ_WRITE);
    }
}
