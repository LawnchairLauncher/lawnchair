/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.Context;

import java.util.HashMap;

public abstract class PackageInstallerCompat {

    public static final int STATUS_INSTALLED = 0;
    public static final int STATUS_INSTALLING = 1;
    public static final int STATUS_FAILED = 2;

    private static final Object sInstanceLock = new Object();
    private static PackageInstallerCompat sInstance;

    public static PackageInstallerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new PackageInstallerCompatVL(context);
            }
            return sInstance;
        }
    }

    /**
     * @return a map of active installs to their progress
     */
    public abstract HashMap<String, Integer> updateAndGetActiveSessionCache();

    public abstract void onStop();

    public static final class PackageInstallInfo {
        public final String packageName;

        public int state;
        public int progress;

        public PackageInstallInfo(String packageName) {
            this.packageName = packageName;
        }

        public PackageInstallInfo(String packageName, int state, int progress) {
            this.packageName = packageName;
            this.state = state;
            this.progress = progress;
        }
    }
}
