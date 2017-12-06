/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ModelWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Handles addition of app shortcuts for managed profiles.
 * Methods of class should only be called on {@link LauncherModel#sWorkerThread}.
 */
public class ManagedProfileHeuristic {

    private static final String USER_FOLDER_ID_PREFIX = "user_folder_";

    /**
     * Duration (in milliseconds) for which app shortcuts will be added to work folder.
     */
    private static final long AUTO_ADD_TO_FOLDER_DURATION = 8 * 60 * 60 * 1000;

    public static void onAllAppsLoaded(final Context context,
            List<LauncherActivityInfo> apps, UserHandle user) {
        if (Process.myUserHandle().equals(user)) {
            return;
        }

        UserFolderInfo ufi = new UserFolderInfo(context, user, null);
        // We only handle folder creation once. Later icon additions are handled using package
        // or session events.
        if (ufi.folderAlreadyCreated) {
            return;
        }

        if (Utilities.ATLEAST_OREO && !SessionCommitReceiver.isEnabled(context)) {
            // Just mark the folder id preference to avoid new folder creation later.
            ufi.prefs.edit().putLong(ufi.folderIdKey, ItemInfo.NO_ID).apply();
            return;
        }

        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_BULK_ADD);
        for (LauncherActivityInfo app : apps) {
            // Queue all items which should go in the work folder.
            if (app.getFirstInstallTime() < ufi.addIconToFolderTime) {
                InstallShortcutReceiver.queueActivityInfo(app, context);
            }
        }
        // Post the queue update on next frame, so that the loader gets finished.
        new Handler(LauncherModel.getWorkerLooper()).post(new Runnable() {
            @Override
            public void run() {
                InstallShortcutReceiver.disableAndFlushInstallQueue(
                        InstallShortcutReceiver.FLAG_BULK_ADD, context);
            }
        });
    }


    /**
     * Utility class to help workspace icon addition.
     */
    public static class UserFolderInfo {

        final ArrayList<ShortcutInfo> pendingShortcuts = new ArrayList<>();

        final UserHandle user;

        final long userSerial;
        // Time until which icons will be added to folder instead.
        final long addIconToFolderTime;

        final String folderIdKey;
        final SharedPreferences prefs;

        final boolean folderAlreadyCreated;
        final FolderInfo folderInfo;

        boolean folderPendingAddition;

        public UserFolderInfo(Context context, UserHandle user, BgDataModel dataModel) {
            this.user = user;

            UserManagerCompat um = UserManagerCompat.getInstance(context);
            userSerial = um.getSerialNumberForUser(user);
            addIconToFolderTime = um.getUserCreationTime(user) + AUTO_ADD_TO_FOLDER_DURATION;

            folderIdKey = USER_FOLDER_ID_PREFIX + userSerial;
            prefs = prefs(context);

            folderAlreadyCreated = prefs.contains(folderIdKey);
            if (dataModel != null) {
                if (folderAlreadyCreated) {
                    long folderId = prefs.getLong(folderIdKey, ItemInfo.NO_ID);
                    folderInfo = dataModel.folders.get(folderId);
                } else {
                    folderInfo = new FolderInfo();
                    folderInfo.title = context.getText(R.string.work_folder_name);
                    folderInfo.setOption(FolderInfo.FLAG_WORK_FOLDER, true, null);
                    folderPendingAddition = true;
                }
            } else {
                folderInfo = null;
            }
        }

        /**
         * Returns the ItemInfo which should be added to the workspace. In case the the provided
         * {@link ShortcutInfo} or a wrapped {@link FolderInfo} or null.
         */
        public ItemInfo convertToWorkspaceItem(
                ShortcutInfo shortcut, LauncherActivityInfo activityInfo) {
            if (activityInfo.getFirstInstallTime() >= addIconToFolderTime) {
                return shortcut;
            }

            if (folderAlreadyCreated) {
                if (folderInfo == null) {
                    // Work folder was deleted by user, add icon to home screen.
                    return shortcut;
                } else {
                    // Add item to work folder instead. Nothing needs to be added
                    // on the homescreen.
                    pendingShortcuts.add(shortcut);
                    return null;
                }
            }

            pendingShortcuts.add(shortcut);
            folderInfo.add(shortcut, false);
            if (folderPendingAddition) {
                folderPendingAddition = false;
                return folderInfo;
            } else {
                // WorkFolder already requested to be added. Nothing new needs to be added.
                return null;
            }
        }

        public void applyPendingState(ModelWriter writer) {
            if (folderInfo == null) {
                return;
            }

            int startingRank = 0;
            if (folderAlreadyCreated) {
                startingRank = folderInfo.contents.size();
            }

            for (ShortcutInfo info : pendingShortcuts) {
                info.rank = startingRank++;
                writer.addItemToDatabase(info, folderInfo.id, 0, 0, 0);
            }

            if (folderAlreadyCreated) {
                // FolderInfo could already be bound. We need to add shortcuts on the UI thread.
                new MainThreadExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        folderInfo.prepareAutoUpdate();
                        for (ShortcutInfo info : pendingShortcuts) {
                            folderInfo.add(info, false);
                        }
                    }
                });
            } else {
                prefs.edit().putLong(folderIdKey, folderInfo.id).apply();
            }
        }
    }

    /**
     * Verifies that entries corresponding to {@param users} exist and removes all invalid entries.
     */
    public static void processAllUsers(List<UserHandle> users, Context context) {
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        HashSet<String> validKeys = new HashSet<>();
        for (UserHandle user : users) {
            validKeys.add(USER_FOLDER_ID_PREFIX + userManager.getSerialNumberForUser(user));
        }

        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!validKeys.contains(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /**
     * For each user, if a work folder has not been created, mark it such that the folder will
     * never get created.
     */
    public static void markExistingUsersForNoFolderCreation(Context context) {
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        UserHandle myUser = Process.myUserHandle();

        SharedPreferences prefs = null;
        for (UserHandle user : userManager.getUserProfiles()) {
            if (myUser.equals(user)) {
                continue;
            }
            if (prefs == null) {
                prefs = prefs(context);
            }
            String folderIdKey = USER_FOLDER_ID_PREFIX + userManager.getSerialNumberForUser(user);
            if (!prefs.contains(folderIdKey)) {
                prefs.edit().putLong(folderIdKey, ItemInfo.NO_ID).apply();
            }
        }
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.MANAGED_USER_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }
}
