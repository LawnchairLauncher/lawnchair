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
package com.android.launcher3.model;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.os.Process.myUserHandle;

import static com.android.launcher3.pm.InstallSessionHelper.getUserHandle;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class to send broadcasts to package installers that have:
 * - Items on the first screen
 * - Items with an active install session
 *
 * The packages are broken down by: folder items, workspace items, hotseat items, and widgets.
 *
 * Package installers only receive data for items that they are installing.
 */
public class FirstScreenBroadcast {

    private static final String TAG = "FirstScreenBroadcast";
    private static final boolean DEBUG = false;

    private static final String ACTION_FIRST_SCREEN_ACTIVE_INSTALLS
            = "com.android.launcher3.action.FIRST_SCREEN_ACTIVE_INSTALLS";

    private static final String FOLDER_ITEM_EXTRA = "folderItem";
    private static final String WORKSPACE_ITEM_EXTRA = "workspaceItem";
    private static final String HOTSEAT_ITEM_EXTRA = "hotseatItem";
    private static final String WIDGET_ITEM_EXTRA = "widgetItem";

    private static final String VERIFICATION_TOKEN_EXTRA = "verificationToken";

    private final HashMap<PackageUserKey, SessionInfo> mSessionInfoForPackage;

    public FirstScreenBroadcast(HashMap<PackageUserKey, SessionInfo> sessionInfoForPackage) {
        mSessionInfoForPackage = sessionInfoForPackage;
    }

    /**
     * Sends a broadcast to all package installers that have items with active sessions on the users
     * first screen.
     */
    public void sendBroadcasts(Context context, List<ItemInfo> firstScreenItems) {
        UserHandle myUser = myUserHandle();
        mSessionInfoForPackage
                .values()
                .stream()
                .filter(info -> myUser.equals(getUserHandle(info)))
                .collect(groupingBy(SessionInfo::getInstallerPackageName,
                        mapping(SessionInfo::getAppPackageName, Collectors.toSet())))
                .forEach((installer, packages) ->
                    sendBroadcastToInstaller(context, installer, packages, firstScreenItems));
    }

    /**
     * @param installerPackageName Package name of the package installer.
     * @param packages List of packages with active sessions for this package installer.
     * @param firstScreenItems List of items on the first screen.
     */
    private void sendBroadcastToInstaller(Context context, String installerPackageName,
            Set<String> packages, List<ItemInfo> firstScreenItems) {
        Set<String> folderItems = new HashSet<>();
        Set<String> workspaceItems = new HashSet<>();
        Set<String> hotseatItems = new HashSet<>();
        Set<String> widgetItems = new HashSet<>();

        for (ItemInfo info : firstScreenItems) {
            if (info instanceof FolderInfo) {
                FolderInfo folderInfo = (FolderInfo) info;
                String folderItemInfoPackage;
                for (ItemInfo folderItemInfo : folderInfo.contents) {
                    folderItemInfoPackage = getPackageName(folderItemInfo);
                    if (folderItemInfoPackage != null
                            && packages.contains(folderItemInfoPackage)) {
                        folderItems.add(folderItemInfoPackage);
                    }
                }
            }

            String packageName = getPackageName(info);
            if (packageName == null || !packages.contains(packageName)) {
                continue;
            }
            if (info instanceof LauncherAppWidgetInfo) {
                widgetItems.add(packageName);
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                hotseatItems.add(packageName);
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                workspaceItems.add(packageName);
            }
        }

        if (DEBUG) {
            printList(installerPackageName, "Folder item", folderItems);
            printList(installerPackageName, "Workspace item", workspaceItems);
            printList(installerPackageName, "Hotseat item", hotseatItems);
            printList(installerPackageName, "Widget item", widgetItems);
        }

        context.sendBroadcast(new Intent(ACTION_FIRST_SCREEN_ACTIVE_INSTALLS)
                .setPackage(installerPackageName)
                .putStringArrayListExtra(FOLDER_ITEM_EXTRA, new ArrayList<>(folderItems))
                .putStringArrayListExtra(WORKSPACE_ITEM_EXTRA, new ArrayList<>(workspaceItems))
                .putStringArrayListExtra(HOTSEAT_ITEM_EXTRA, new ArrayList<>(hotseatItems))
                .putStringArrayListExtra(WIDGET_ITEM_EXTRA, new ArrayList<>(widgetItems))
                .putExtra(VERIFICATION_TOKEN_EXTRA, PendingIntent.getActivity(context, 0,
                        new Intent(), FLAG_ONE_SHOT | FLAG_IMMUTABLE)));
    }

    private static String getPackageName(ItemInfo info) {
        String packageName = null;
        if (info instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) info;
            if (widgetInfo.providerName != null) {
                packageName = widgetInfo.providerName.getPackageName();
            }
        } else if (info.getTargetComponent() != null){
            packageName = info.getTargetComponent().getPackageName();
        }
        return packageName;
    }

    private static void printList(String packageInstaller, String label, Set<String> packages) {
        for (String pkg : packages) {
            Log.d(TAG, packageInstaller + ":" + label + ":" + pkg);
        }
    }
}
