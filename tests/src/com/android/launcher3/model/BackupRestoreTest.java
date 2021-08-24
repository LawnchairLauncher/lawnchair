/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.content.pm.PackageManager.INSTALL_REASON_DEVICE_RESTORE;
import static android.os.Process.myUserHandle;

import static com.android.launcher3.LauncherSettings.Favorites.BACKUP_TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.addTableToDb;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;
import static com.android.launcher3.util.LauncherModelHelper.APP_ICON;
import static com.android.launcher3.util.LauncherModelHelper.NO__ICON;
import static com.android.launcher3.util.LauncherModelHelper.SHORTCUT;
import static com.android.launcher3.util.ReflectionHelpers.getField;
import static com.android.launcher3.util.ReflectionHelpers.setField;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.app.backup.BackupManager;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.LongSparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.SafeCloseable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to verify backup and restore flow.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BackupRestoreTest {

    private static final int PER_USER_RANGE = 200000;


    private long mCurrentMyProfileId;
    private long mOldMyProfileId;

    private long mCurrentWorkProfileId;
    private long mOldWorkProfileId;

    private BackupManager mBackupManager;
    private LauncherModelHelper mModelHelper;
    private SQLiteDatabase mDb;
    private InvariantDeviceProfile mIdp;

    private UserHandle mWorkUserHandle;

    private SafeCloseable mUserChangeListener;

    @Before
    public void setUp() {
        mModelHelper = new LauncherModelHelper();

        mCurrentMyProfileId = mModelHelper.defaultProfileId;
        mOldMyProfileId = mCurrentMyProfileId + 1;
        mCurrentWorkProfileId = mOldMyProfileId + 1;
        mOldWorkProfileId = mCurrentWorkProfileId + 1;

        mWorkUserHandle = UserHandle.getUserHandleForUid(PER_USER_RANGE);
        mUserChangeListener = UserCache.INSTANCE.get(mModelHelper.sandboxContext)
                .addUserChangeListener(() -> { });

        setupUserManager();
        setupBackupManager();
        RestoreDbTask.setPending(mModelHelper.sandboxContext);
        mDb = mModelHelper.provider.getDb();
        mIdp = InvariantDeviceProfile.INSTANCE.get(mModelHelper.sandboxContext);

    }

    @After
    public void tearDown() {
        mUserChangeListener.close();
        mModelHelper.destroy();
    }

    private void setupUserManager() {
        UserCache cache = UserCache.INSTANCE.get(mModelHelper.sandboxContext);
        synchronized (cache) {
            LongSparseArray<UserHandle> users = getField(cache, "mUsers");
            users.clear();
            users.put(mCurrentMyProfileId, myUserHandle());
            users.put(mCurrentWorkProfileId, mWorkUserHandle);

            ArrayMap<UserHandle, Long> userMap = getField(cache, "mUserToSerialMap");
            userMap.clear();
            userMap.put(myUserHandle(), mCurrentMyProfileId);
            userMap.put(mWorkUserHandle, mCurrentWorkProfileId);
        }
    }

    private void setupBackupManager() {
        mBackupManager = spy(new BackupManager(mModelHelper.sandboxContext));
        doReturn(myUserHandle()).when(mBackupManager)
                .getUserForAncestralSerialNumber(eq(mOldMyProfileId));
        doReturn(mWorkUserHandle).when(mBackupManager)
                .getUserForAncestralSerialNumber(eq(mOldWorkProfileId));
    }

    @Test
    public void testOnCreateDbIfNotExists_CreatesBackup() {
        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));
    }

    @Test
    public void testOnRestoreSessionWithValidCondition_PerformsRestore() throws Exception {
        setupBackup();
        verifyTableIsFilled(BACKUP_TABLE_NAME, false);
        verifyTableIsEmpty(TABLE_NAME);
        createRestoreSession();
        verifyTableIsFilled(TABLE_NAME, true);
    }

    private void setupBackup() {
        createTableUsingOldProfileId();
        // setup grid for main user on first screen
        mModelHelper.createGrid(new int[][][]{{
                { APP_ICON, APP_ICON, SHORTCUT, SHORTCUT},
                { SHORTCUT, SHORTCUT, NO__ICON, NO__ICON},
                { NO__ICON, NO__ICON, SHORTCUT, SHORTCUT},
                { APP_ICON, SHORTCUT, SHORTCUT, APP_ICON},
            }}, 1, mOldMyProfileId);
        // setup grid for work profile on second screen
        mModelHelper.createGrid(new int[][][]{{
                { NO__ICON, APP_ICON, SHORTCUT, SHORTCUT},
                { SHORTCUT, SHORTCUT, NO__ICON, NO__ICON},
                { NO__ICON, NO__ICON, SHORTCUT, SHORTCUT},
                { APP_ICON, SHORTCUT, SHORTCUT, NO__ICON},
            }}, 2, mOldWorkProfileId);
        // simulates the creation of backup upon restore
        new GridBackupTable(mModelHelper.sandboxContext, mDb, mIdp.numDatabaseHotseatIcons,
                mIdp.numColumns, mIdp.numRows).doBackup(
                mOldMyProfileId, GridBackupTable.OPTION_REQUIRES_SANITIZATION);
        // reset favorites table
        createTableUsingOldProfileId();
    }

    private void verifyTableIsEmpty(String tableName) {
        assertEquals(0, getCount(mDb, "SELECT * FROM " + tableName));
    }

    private void verifyTableIsFilled(String tableName, boolean sanitized) {
        assertEquals(sanitized ? 12 : 13, getCount(mDb,
                "SELECT * FROM " + tableName + " WHERE profileId = "
                        + (sanitized ? mCurrentMyProfileId : mOldMyProfileId)));
        assertEquals(10, getCount(mDb, "SELECT * FROM " + tableName + " WHERE profileId = "
                + (sanitized ? mCurrentWorkProfileId : mOldWorkProfileId)));
    }

    private void createTableUsingOldProfileId() {
        // simulates the creation of favorites table on old device
        dropTable(mDb, TABLE_NAME);
        addTableToDb(mDb, mOldMyProfileId, false);
    }

    private void createRestoreSession() throws Exception {
        final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        final PackageInstaller installer = mModelHelper.sandboxContext.getPackageManager()
                .getPackageInstaller();
        final int sessionId = installer.createSession(params);
        final PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);
        setField(info, "installReason", INSTALL_REASON_DEVICE_RESTORE);
        // TODO: (b/148410677) we should verify the following call instead
        //  InstallSessionHelper.INSTANCE.get(getContext()).restoreDbIfApplicable(info);
        RestoreDbTask.restoreIfPossible(mModelHelper.sandboxContext,
                mModelHelper.provider.getHelper(), mBackupManager);
    }

    private static int getCount(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.getCount();
        }
    }
}
