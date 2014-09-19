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

import com.android.launcher3.Utilities;

import java.util.HashSet;

public abstract class PackageInstallerCompat {

    public static final int STATUS_INSTALLED = 0;
    public static final int STATUS_INSTALLING = 1;
    public static final int STATUS_FAILED = 2;

    private static final Object sInstanceLock = new Object();
    private static PackageInstallerCompat sInstance;

    public static PackageInstallerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.isLmpOrAbove()) {
                    sInstance = new PackageInstallerCompatVL(context);
                } else {
                    sInstance = new PackageInstallerCompatV16(context) { };
                }
            }
            return sInstance;
        }
    }

    public abstract HashSet<String> updateAndGetActiveSessionCache();

    public abstract void onPause();

    public abstract void onResume();

    public abstract void onFinishBind();

    public abstract void onStop();

    public abstract void recordPackageUpdate(String packageName, int state, int progress);

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
