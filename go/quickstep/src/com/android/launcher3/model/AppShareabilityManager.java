/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.room.Room;

import com.android.internal.annotations.VisibleForTesting;
import com.android.launcher3.model.AppShareabilityDatabase.ShareabilityDao;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class maintains the shareability status of installed apps.
 * Each app's status is retrieved from the Play Store's API. Statuses are cached in order
 * to limit extraneous calls to that API (which can be time-consuming).
 */
public class AppShareabilityManager implements SafeCloseable {
    @Retention(SOURCE)
    @IntDef({
        ShareabilityStatus.UNKNOWN,
        ShareabilityStatus.NOT_SHAREABLE,
        ShareabilityStatus.SHAREABLE
    })
    public @interface ShareabilityStatus {
        int UNKNOWN = 0;
        int NOT_SHAREABLE = 1;
        int SHAREABLE = 2;
    }

    private static final String TAG = "AppShareabilityManager";
    private static final String DB_NAME = "shareabilityDatabase";
    public static MainThreadInitializedObject<AppShareabilityManager> INSTANCE =
            new MainThreadInitializedObject<>(AppShareabilityManager::new);

    private final Context mContext;
    // Local map to store the data in memory for quick access
    private final Map<String, Integer> mDataMap;
    // Database to persist the data across reboots
    private AppShareabilityDatabase mDatabase;
    // Data Access Object for the database
    private ShareabilityDao mDao;
    // Class to perform shareability checks
    private AppShareabilityChecker mShareChecker;

    private AppShareabilityManager(Context context) {
        mContext = context;
        mDataMap = new ArrayMap<>();
        mDatabase = Room.databaseBuilder(mContext, AppShareabilityDatabase.class, DB_NAME).build();
        mDao = mDatabase.shareabilityDao();
        MODEL_EXECUTOR.post(this::readFromDB);
    }

    /**
     * Set the shareability checker. The checker determines whether given apps are shareable.
     * This must be set before the manager can update its data.
     * @param checker Implementation of AppShareabilityChecker to perform the checks
     */
    public void setShareabilityChecker(AppShareabilityChecker checker) {
        mShareChecker = checker;
    }

    /**
     * Retrieve the ShareabilityStatus of an app from the local map
     * This does not interact with the saved database
     * @param packageName The app's package name
     * @return The status as a ShareabilityStatus integer
     */
    public synchronized @ShareabilityStatus int getStatus(String packageName) {
        @ShareabilityStatus int status = ShareabilityStatus.UNKNOWN;
        if (mDataMap.containsKey(packageName)) {
            status = mDataMap.get(packageName);
        }
        return status;
    }

    /**
     * Set the status of a given app. This updates the local map as well as the saved database.
     */
    public synchronized void setStatus(String packageName, @ShareabilityStatus int status) {
        mDataMap.put(packageName, status);

        // Write to the database on a separate thread
        MODEL_EXECUTOR.post(() ->
                mDao.insertAppStatus(new AppShareabilityStatus(packageName, status)));
    }

    /**
     * Set the statuses of given apps. This updates the local map as well as the saved database.
     */
    public synchronized void setStatuses(List<AppShareabilityStatus> statuses) {
        for (int i = 0, size = statuses.size(); i < size; i++) {
            AppShareabilityStatus entry = statuses.get(i);
            mDataMap.put(entry.packageName, entry.status);
        }

        // Write to the database on a separate thread
        MODEL_EXECUTOR.post(() ->
                mDao.insertAppStatuses(statuses.toArray(new AppShareabilityStatus[0])));
    }

    /**
     * Request a status update for a specific app
     * @param packageName The app's package name
     * @param callback Optional callback to be called when the update is complete. The received
     *                 Boolean denotes whether the update was successful.
     */
    public void requestAppStatusUpdate(String packageName, @Nullable Consumer<Boolean> callback) {
        MODEL_EXECUTOR.post(() -> updateCache(packageName, callback));
    }

    /**
     * Request a status update for all apps
     */
    public void requestFullUpdate() {
        MODEL_EXECUTOR.post(this::updateCache);
    }

    /**
     * Update the cached shareability data for all installed apps
     */
    @WorkerThread
    private void updateCache() {
        updateCache(/* packageName */ null, /* callback */ null);
    }

    /**
     * Update the cached shareability data
     * @param packageName A specific package to update. If null, all installed apps will be updated.
     * @param callback Optional callback to be called when the update is complete. The received
     *                 Boolean denotes whether the update was successful.
     */
    @WorkerThread
    private void updateCache(@Nullable String packageName, @Nullable Consumer<Boolean> callback) {
        if (mShareChecker == null) {
            Log.e(TAG, "AppShareabilityChecker not set");
            return;
        }

        List<String> packageNames = new ArrayList<>();
        if (packageName != null) {
            packageNames.add(packageName);
        } else {
            LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
            List<LauncherActivityInfo> installedApps =
                    launcherApps.getActivityList(/* packageName */ null, Process.myUserHandle());
            for (int i = 0, size = installedApps.size(); i < size; i++) {
                packageNames.add(installedApps.get(i).getApplicationInfo().packageName);
            }
        }

        mShareChecker.checkApps(packageNames, this, callback);
    }

    @WorkerThread
    private synchronized void readFromDB() {
        mDataMap.clear();
        List<AppShareabilityStatus> entries = mDao.getAllEntries();
        for (int i = 0, size = entries.size(); i < size; i++) {
            AppShareabilityStatus entry = entries.get(i);
            mDataMap.put(entry.packageName, entry.status);
        }
    }

    @Override
    public void close() {
        mDatabase.close();
    }

    /**
     * Provides a testable instance of this class
     * This instance allows database queries on the main thread
     * @hide */
    @VisibleForTesting
    public static AppShareabilityManager getTestInstance(Context context) {
        AppShareabilityManager manager = new AppShareabilityManager(context);
        manager.mDatabase.close();
        manager.mDatabase = Room.inMemoryDatabaseBuilder(context, AppShareabilityDatabase.class)
                .allowMainThreadQueries()
                .build();
        manager.mDao = manager.mDatabase.shareabilityDao();
        return manager;
    }
}
