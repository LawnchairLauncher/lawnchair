package com.android.launcher3.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import java.util.HashSet;
import java.util.List;

/**
 * Handles addition of app shortcuts for managed profiles.
 * Methods of class should only be called on {@link LauncherModel#sWorkerThread}.
 */
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
        if (Utilities.isLmpOrAbove() && !UserHandleCompat.myUserHandle().equals(user)) {
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

    private ArrayList<ItemInfo> mHomescreenApps;
    private ArrayList<ItemInfo> mWorkFolderApps;

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

    /**
     * Checks the list of user apps and adds icons for newly installed apps on the homescreen or
     * workfolder.
     */
    public void processUserApps(List<LauncherActivityInfoCompat> apps) {
        mHomescreenApps = new ArrayList<ItemInfo>();
        mWorkFolderApps = new ArrayList<ItemInfo>();
        HashSet<String> packageSet = getPackageSet();
        boolean newPackageAdded = false;

        for (LauncherActivityInfoCompat info : apps) {
            String packageName = info.getComponentName().getPackageName();
            if (!packageSet.contains(packageName)) {
                packageSet.add(packageName);
                newPackageAdded = true;

                try {
                    PackageInfo pkgInfo = mContext.getPackageManager()
                            .getPackageInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
                    markForAddition(info, pkgInfo.firstInstallTime);
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Unknown package " + packageName, e);
                }
            }
        }

        if (newPackageAdded) {
            mPrefs.edit().putStringSet(mPackageSetKey, packageSet).apply();
            finalizeAdditions();
        }
    }

    private void markForAddition(LauncherActivityInfoCompat info, long installTime) {
        ArrayList<ItemInfo> targetList =
                (installTime <= mUserCreationTime + AUTO_ADD_TO_FOLDER_DURATION) ?
                        mWorkFolderApps : mHomescreenApps;
        targetList.add(ShortcutInfo.fromActivityInfo(info, mContext));
    }

    /**
     * Adds and binds shortcuts marked to be added to the work folder.
     */
    private void finalizeWorkFolder() {
        if (mWorkFolderApps.isEmpty()) {
            return;
        }

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

            final ArrayList<ItemInfo> shortcuts = mWorkFolderApps;
            // FolderInfo could already be bound. We need to add shortcuts on the UI thread.
            new MainThreadExecutor().execute(new Runnable() {

                @Override
                public void run() {
                    for (ItemInfo info : shortcuts) {
                        workFolder.add((ShortcutInfo) info);
                    }
                }
            });
        } else {
            // Create a new folder.
            final FolderInfo workFolder = new FolderInfo();
            workFolder.title = mContext.getText(R.string.work_folder_name);
            workFolder.setOption(FolderInfo.FLAG_WORK_FOLDER, true, null);

            // Add all shortcuts before adding it to the UI, as an empty folder might get deleted.
            for (ItemInfo info : mWorkFolderApps) {
                workFolder.add((ShortcutInfo) info);
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
    private void finalizeAdditions() {
        finalizeWorkFolder();

        if (!mHomescreenApps.isEmpty()) {
            mModel.addAndBindAddedWorkspaceItems(mContext, mHomescreenApps);
        }
    }

    /**
     * Updates the list of installed apps and adds any new icons on homescreen or work folder.
     */
    public void processPackageAdd(String[] packages) {
        mHomescreenApps = new ArrayList<ItemInfo>();
        mWorkFolderApps = new ArrayList<ItemInfo>();
        HashSet<String> packageSet = getPackageSet();
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
            finalizeAdditions();
        }
    }

    /**
     * Updates the list of installed packages for the user.
     */
    public void processPackageRemoved(String[] packages) {
        HashSet<String> packageSet = getPackageSet();
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

    @SuppressWarnings("unchecked")
    private HashSet<String> getPackageSet() {
        return new HashSet<String>(mPrefs.getStringSet(mPackageSetKey, Collections.EMPTY_SET));
    }

    /**
     * Verifies that entries corresponding to {@param users} exist and removes all invalid entries.
     */
    public static void processAllUsers(List<UserHandleCompat> users, Context context) {
        if (!Utilities.isLmpOrAbove()) {
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
}
