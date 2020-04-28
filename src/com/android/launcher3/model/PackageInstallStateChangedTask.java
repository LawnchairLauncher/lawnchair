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

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.util.InstantAppResolver;

import java.util.HashSet;

/**
 * Handles changes due to a sessions updates for a currently installing app.
 */
public class PackageInstallStateChangedTask extends BaseModelUpdateTask {

    private final PackageInstallInfo mInstallInfo;

    public PackageInstallStateChangedTask(PackageInstallInfo installInfo) {
        mInstallInfo = installInfo;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
        if (mInstallInfo.state == PackageInstallerCompat.STATUS_INSTALLED) {
            try {
                // For instant apps we do not get package-add. Use setting events to update
                // any pinned icons.
                ApplicationInfo ai = app.getContext()
                        .getPackageManager().getApplicationInfo(mInstallInfo.packageName, 0);
                if (InstantAppResolver.newInstance(app.getContext()).isInstantApp(ai)) {
                    app.getModel().onPackageAdded(ai.packageName, mInstallInfo.user);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
            // Ignore install success events as they are handled by Package add events.
            return;
        }

        synchronized (apps) {
            PromiseAppInfo updated = apps.updatePromiseInstallInfo(mInstallInfo);
            if (updated != null) {
                scheduleCallbackTask(c -> c.bindPromiseAppProgressUpdated(updated));
            }
            bindApplicationsIfNeeded();
        }

        synchronized (dataModel) {
            final HashSet<ItemInfo> updates = new HashSet<>();
            for (ItemInfo info : dataModel.itemsIdMap) {
                if (info instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo si = (WorkspaceItemInfo) info;
                    ComponentName cn = si.getTargetComponent();
                    if (si.hasPromiseIconUi() && (cn != null)
                            && mInstallInfo.packageName.equals(cn.getPackageName())) {
                        si.setInstallProgress(mInstallInfo.progress);
                        if (mInstallInfo.state == PackageInstallerCompat.STATUS_FAILED) {
                            // Mark this info as broken.
                            si.status &= ~WorkspaceItemInfo.FLAG_INSTALL_SESSION_ACTIVE;
                        }
                        updates.add(si);
                    }
                }
            }

            for (LauncherAppWidgetInfo widget : dataModel.appWidgets) {
                if (widget.providerName.getPackageName().equals(mInstallInfo.packageName)) {
                    widget.installProgress = mInstallInfo.progress;
                    updates.add(widget);
                }
            }

            if (!updates.isEmpty()) {
                scheduleCallbackTask(new CallbackTask() {
                    @Override
                    public void execute(Callbacks callbacks) {
                        callbacks.bindRestoreItemsChange(updates);
                    }
                });
            }
        }
    }
}
