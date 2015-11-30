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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles addition of app shortcuts for managed profiles.
 * Methods of class should only be called on {@link LauncherModel#sWorkerThread}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ManagedProfileHeuristic {

    private static final String TAG = "ManagedProfileHeuristic";

    /**
     * Maintain a set of packages installed per user.
     */
    private static final String INSTALLED_PACKAGES_PREFIX = "installed_packages_for_user_";

    private static final String USER_FOLDER_ID_PREFIX = "user_folder_";

    /**
     * Duration (in milliseconds) for which app shortcuts will be added to work folder.
     */
    private static final long AUTO_ADD_TO_FOLDER_DURATION = 8 * 60 * 60 * 1000;

    public static ManagedProfileHeuristic get(Context context, UserHandleCompat user) {
        if (Utilities.ATLEAST_LOLLIPOP && !UserHandleCompat.myUserHandle().equals(user)) {
            return new ManagedProfileHeuristic(context, user);
        }
        return null;
    }

    private final Context mContext;
    private final UserHandleCompat mUser;
    private final LauncherModel mModel;

    private final SharedPreferences mPrefs;
    private final long mUserSerial;
    private final long mUserCreationTime;
    private final String mPackageSetKey;

    private ArrayList<ShortcutInfo> mHomescreenApps;
    private ArrayList<ShortcutInfo> mWorkFolderApps;
    private HashMap<ShortcutInfo, Long> mShortcutToInstallTimeMap;

    private ManagedProfileHeuristic(Context context, UserHandleCompat user) {
        mContext = context;
        mUser = user;
        mModel = LauncherAppState.getInstance().getModel();

        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        mUserSerial = userManager.getSerialNumberForUser(user);
        mUserCreationTime = userManager.getUserCreationTime(user);
        mPackageSetKey = INSTALLED_PACKAGES_PREFIX + mUserSerial;

        mPrefs = mContext.getSharedPreferences(LauncherFiles.MANAGED_USER_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
    }

    private void initVars() {
        mHomescreenApps = new ArrayList<>();
        mWorkFolderApps = new ArrayList<>();
        mShortcutToInstallTimeMap = new HashMap<>();
    }

    /**
     * Checks the list of user apps and adds icons for newly installed apps on the homescreen or
     * workfolder.
     */
    public void processUserApps(List<LauncherActivityInfoCompat> apps) {
        initVars();

        HashSet<String> packageSet = new HashSet<>();
        final boolean userAppsExisted = getUserApps(packageSet);

        boolean newPackageAdded = false;
        for (LauncherActivityInfoCompat info : apps) {
            String packageName = info.getComponentName().getPackageName();
            if (!packageSet.contains(packageName)) {
                packageSet.add(packageName);
                newPackageAdded = true;
                markForAddition(info, info.getFirstInstallTime());
            }
        }

        if (newPackageAdded) {
            mPrefs.edit().putStringSet(mPackageSetKey, packageSet).apply();
            // Do not add shortcuts on the homescreen for the first time. This prevents the launcher
            // getting filled with the managed user apps, when it start with a fresh DB (or after
            // a very long time).
            finalizeAdditions(userAppsExisted);
        }
    }

    private void markForAddition(LauncherActivityInfoCompat info, long installTime) {
        ArrayList<ShortcutInfo> targetList =
                (installTime <= mUserCreationTime + AUTO_ADD_TO_FOLDER_DURATION) ?
                        mWorkFolderApps : mHomescreenApps;
        ShortcutInfo si = ShortcutInfo.fromActivityInfo(info, mContext);
        mShortcutToInstallTimeMap.put(si, installTime);
        targetList.add(si);
    }

    private void sortList(ArrayList<ShortcutInfo> infos) {
        Collections.sort(infos, new Comparator<ShortcutInfo>() {

            @Override
            public int compare(ShortcutInfo lhs, ShortcutInfo rhs) {
                Long lhsTime = mShortcutToInstallTimeMap.get(lhs);
                Long rhsTime = mShortcutToInstallTimeMap.get(rhs);
                return Utilities.longCompare(lhsTime == null ? 0 : lhsTime,
                        rhsTime == null ? 0 : rhsTime);
            }
        });
    }

    /**
     * Adds and binds shortcuts marked to be added to the work folder.
     */
    private void finalizeWorkFolder() {
        if (mWorkFolderApps.isEmpty()) {
            return;
        }
        sortList(mWorkFolderApps);

        // Try to get a work folder.
        String folderIdKey = USER_FOLDER_ID_PREFIX + mUserSerial;
        if (mPrefs.contains(folderIdKey)) {
            long folderId = mPrefs.getLong(folderIdKey, 0);
            final FolderInfo workFolder = mModel.findFolderById(folderId);

            if (workFolder == null || !workFolder.hasOption(FolderInfo.FLAG_WORK_FOLDER)) {
                // Could not get a work folder. Add all the icons to homescreen.
                mHomescreenApps.addAll(mWorkFolderApps);
                return;
            }
            saveWorkFolderShortcuts(folderId, workFolder.contents.size());

            final ArrayList<ShortcutInfo> shortcuts = mWorkFolderApps;
            // FolderInfo could already be bound. We need to add shortcuts on the UI thread.
            new MainThreadExecutor().execute(new Runnable() {

                @Override
                public void run() {
                    for (ShortcutInfo info : shortcuts) {
                        workFolder.add(info);
                    }
                }
            });
        } else {
            // Create a new folder.
            final FolderInfo workFolder = new FolderInfo();
            workFolder.title = mContext.getText(R.string.work_folder_name);
            workFolder.setOption(FolderInfo.FLAG_WORK_FOLDER, true, null);

            // Add all shortcuts before adding it to the UI, as an empty folder might get deleted.
            for (ShortcutInfo info : mWorkFolderApps) {
                workFolder.add(info);
            }

            // Add the item to home screen and DB. This also generates an item id synchronously.
            ArrayList<ItemInfo> itemList = new ArrayList<ItemInfo>(1);
            itemList.add(workFolder);
            mModel.addAndBindAddedWorkspaceItems(mContext, itemList);
            mPrefs.edit().putLong(USER_FOLDER_ID_PREFIX + mUserSerial, workFolder.id).apply();

            saveWorkFolderShortcuts(workFolder.id, 0);
        }
    }

    /**
     * Add work folder shortcuts to the DB.
     */
    private void saveWorkFolderShortcuts(long workFolderId, int startingRank) {
        for (ItemInfo info : mWorkFolderApps) {
            info.rank = startingRank++;
            LauncherModel.addItemToDatabase(mContext, info, workFolderId, 0, 0, 0);
        }
    }

    /**
     * Adds and binds all shortcuts marked for addition.
     */
    private void finalizeAdditions(boolean addHomeScreenShortcuts) {
        finalizeWorkFolder();

        if (addHomeScreenShortcuts && !mHomescreenApps.isEmpty()) {
            sortList(mHomescreenApps);
            mModel.addAndBindAddedWorkspaceItems(mContext, mHomescreenApps);
        }
    }

    /**
     * Updates the list of installed apps and adds any new icons on homescreen or work folder.
     */
    public void processPackageAdd(String[] packages) {
        initVars();
        HashSet<String> packageSet = new HashSet<>();
        final boolean userAppsExisted = getUserApps(packageSet);

        boolean newPackageAdded = false;
        long installTime = System.currentTimeMillis();
        LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mContext);

        for (String packageName : packages) {
            if (!packageSet.contains(packageName)) {
                packageSet.add(packageName);
                newPackageAdded = true;

                List<LauncherActivityInfoCompat> activities =
                        launcherApps.getActivityList(packageName, mUser);
                if (!activities.isEmpty()) {
                    markForAddition(activities.get(0), installTime);
                }
            }
        }

        if (newPackageAdded) {
            mPrefs.edit().putStringSet(mPackageSetKey, packageSet).apply();
            finalizeAdditions(userAppsExisted);
        }
    }

    /**
     * Updates the list of installed packages for the user.
     */
    public void processPackageRemoved(String[] packages) {
        HashSet<String> packageSet = new HashSet<String>();
        getUserApps(packageSet);
        boolean packageRemoved = false;

        for (String packageName : packages) {
            if (packageSet.remove(packageName)) {
                packageRemoved = true;
            }
        }

        if (packageRemoved) {
            mPrefs.edit().putStringSet(mPackageSetKey, packageSet).apply();
        }
    }

    /**
     * Reads the list of user apps which have already been processed.
     * @return false if the list didn't exist, true otherwise
     */
    private boolean getUserApps(HashSet<String> outExistingApps) {
        Set<String> userApps = mPrefs.getStringSet(mPackageSetKey, null);
        if (userApps == null) {
            return false;
        } else {
            outExistingApps.addAll(userApps);
            return true;
        }
    }

    /**
     * Verifies that entries corresponding to {@param users} exist and removes all invalid entries.
     */
    public static void processAllUsers(List<UserHandleCompat> users, Context context) {
        if (!Utilities.ATLEAST_LOLLIPOP) {
            return;
        }
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        HashSet<String> validKeys = new HashSet<String>();
        for (UserHandleCompat user : users) {
            addAllUserKeys(userManager.getSerialNumberForUser(user), validKeys);
        }

        SharedPreferences prefs = context.getSharedPreferences(
                LauncherFiles.MANAGED_USER_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!validKeys.contains(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private static void addAllUserKeys(long userSerial, HashSet<String> keysOut) {
        keysOut.add(INSTALLED_PACKAGES_PREFIX + userSerial);
        keysOut.add(USER_FOLDER_ID_PREFIX + userSerial);
    }

    /**
     * For each user, if a work folder has not been created, mark it such that the folder will
     * never get created.
     */
    public static void markExistingUsersForNoFolderCreation(Context context) {
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        UserHandleCompat myUser = UserHandleCompat.myUserHandle();

        SharedPreferences prefs = null;
        for (UserHandleCompat user : userManager.getUserProfiles()) {
            if (myUser.equals(user)) {
                continue;
            }

            if (prefs == null) {
                prefs = context.getSharedPreferences(
                        LauncherFiles.MANAGED_USER_PREFERENCES_KEY,
                        Context.MODE_PRIVATE);
            }
            String folderIdKey = USER_FOLDER_ID_PREFIX + userManager.getSerialNumberForUser(user);
            if (!prefs.contains(folderIdKey)) {
                prefs.edit().putLong(folderIdKey, ItemInfo.NO_ID).apply();
            }
        }
    }
}
