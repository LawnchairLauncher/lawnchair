/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.InstantAppResolver;

import java.util.HashSet;
import java.util.List;

/**
 * Handles changes due to a sessions updates for a currently installing app.
 */
public class PackageInstallStateChangedTask extends BaseModelUpdateTask {

    @NonNull
    private final PackageInstallInfo mInstallInfo;

    public PackageInstallStateChangedTask(@NonNull final PackageInstallInfo installInfo) {
        mInstallInfo = installInfo;
    }

    @Override
    public void execute(@NonNull final LauncherAppState app, @NonNull final BgDataModel dataModel,
            @NonNull final AllAppsList apps) {
        if (mInstallInfo.state == PackageInstallInfo.STATUS_INSTALLED) {
            try {
                // For instant apps we do not get package-add. Use setting events to update
                // any pinned icons.
                ApplicationInfo ai = app.getContext()
                        .getPackageManager().getApplicationInfo(mInstallInfo.packageName, 0);
                if (InstantAppResolver.newInstance(app.getContext()).isInstantApp(ai)) {
                    app.getModel().newModelCallbacks()
                            .onPackageAdded(ai.packageName, mInstallInfo.user);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
            // Ignore install success events as they are handled by Package add events.
            return;
        }

        synchronized (apps) {
            List<AppInfo> updatedAppInfos = apps.updatePromiseInstallInfo(mInstallInfo);
            if (!updatedAppInfos.isEmpty()) {
                for (AppInfo appInfo : updatedAppInfos) {
                    scheduleCallbackTask(c -> c.bindIncrementalDownloadProgressUpdated(appInfo));
                }
            }
            bindApplicationsIfNeeded();
        }

        synchronized (dataModel) {
            final HashSet<ItemInfo> updates = new HashSet<>();
            dataModel.forAllWorkspaceItemInfos(mInstallInfo.user, si -> {
                if (si.hasPromiseIconUi()
                        && mInstallInfo.packageName.equals(si.getTargetPackage())) {
                    si.setProgressLevel(mInstallInfo);
                    updates.add(si);
                }
            });

            for (LauncherAppWidgetInfo widget : dataModel.appWidgets) {
                if (widget.providerName.getPackageName().equals(mInstallInfo.packageName)) {
                    widget.installProgress = mInstallInfo.progress;
                    updates.add(widget);
                }
            }

            if (!updates.isEmpty()) {
                scheduleCallbackTask(callbacks -> callbacks.bindRestoreItemsChange(updates));
            }
        }
    }
}
