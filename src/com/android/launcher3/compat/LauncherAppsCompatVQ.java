/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;

import com.android.launcher3.util.LooperExecutor;

import java.util.List;

@TargetApi(29)
public class LauncherAppsCompatVQ extends LauncherAppsCompatVO {

    LauncherAppsCompatVQ(Context context) {
        super(context);
    }

    public List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions() {
        return mLauncherApps.getAllPackageInstallerSessions();
    }

    @Override
    public void registerSessionCallback(LooperExecutor executor, SessionCallback sessionCallback) {
        mLauncherApps.registerPackageInstallerSessionCallback(executor, sessionCallback);
    }

    @Override
    public void unregisterSessionCallback(SessionCallback sessionCallback) {
        mLauncherApps.unregisterPackageInstallerSessionCallback(sessionCallback);
    }
}
