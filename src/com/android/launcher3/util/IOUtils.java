/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Supports various IO utility functions
 */
public class IOUtils {

    private static final int BUF_SIZE = 0x1000; // 4K
    private static final String TAG = "IOUtils";

    public static byte[] toByteArray(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return toByteArray(in);
        }
    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    public static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        int r;
        while ((r = from.read(buf)) != -1) {
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    /**
     * Utility method to debug binary data
     */
    public static String createTempFile(Context context, byte[] data) {
        if (!FeatureFlags.IS_DOGFOOD_BUILD) {
            throw new IllegalStateException("Method only allowed in development mode");
        }

        String name = UUID.randomUUID().toString();
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream fo = new FileOutputStream(file)) {
            fo.write(data);
            fo.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return file.getAbsolutePath();
    }

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                if (FeatureFlags.IS_DOGFOOD_BUILD) {
                    Log.d(TAG, "Error closing", e);
                }
            }
        }
    }
}
