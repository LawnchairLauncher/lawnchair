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

import static com.android.launcher3.LauncherSettings.Favorites.BACKUP_TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.addTableToDb;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;
import static com.android.launcher3.util.LauncherModelHelper.APP_ICON;
import static com.android.launcher3.util.LauncherModelHelper.NO__ICON;
import static com.android.launcher3.util.LauncherModelHelper.SHORTCUT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.ReflectionHelpers.setField;

import android.app.backup.BackupManager;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.shadows.LShadowBackupManager;
import com.android.launcher3.shadows.LShadowUserManager;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;

/**
 * Tests to verify backup and restore flow.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class BackupRestoreTest {

    private static final long MY_OLD_PROFILE_ID = 1;
    private static final long MY_PROFILE_ID = 0;
    private static final long OLD_WORK_PROFILE_ID = 11;
    private static final int WORK_PROFILE_ID = 10;

    private static final int SYSTEM_USER = 0;
    private static final int FLAG_SYSTEM = 0x00000800;
    private static final int FLAG_PROFILE = 0x00001000;

    private LShadowUserManager mUserManager;
    private BackupManager mBackupManager;
    private LauncherModelHelper mModelHelper;
    private SQLiteDatabase mDb;
    private InvariantDeviceProfile mIdp;
    private UserHandle mMainProfileUser;
    private UserHandle mWorkProfileUser;

    @Before
    public void setUp() {
        setupUserManager();
        setupBackupManager();
        mModelHelper = new LauncherModelHelper();
        RestoreDbTask.setPending(RuntimeEnvironment.application, true);
        mDb = mModelHelper.provider.getDb();
        mIdp = InvariantDeviceProfile.INSTANCE.get(RuntimeEnvironment.application);
    }

    private void setupUserManager() {
        final UserManager userManager = RuntimeEnvironment.application.getSystemService(
                UserManager.class);
        mUserManager = Shadow.extract(userManager);
        // sign in to primary user
        mMainProfileUser = mUserManager.addUser(SYSTEM_USER, "me", FLAG_SYSTEM);
        // sign in to work profile
        mWorkProfileUser = mUserManager.addUser(WORK_PROFILE_ID, "work", FLAG_PROFILE);
    }

    private void setupBackupManager() {
        mBackupManager = new BackupManager(RuntimeEnvironment.application);
        final LShadowBackupManager bm = Shadow.extract(mBackupManager);
        bm.addProfile(MY_OLD_PROFILE_ID, mMainProfileUser);
        bm.addProfile(OLD_WORK_PROFILE_ID, mWorkProfileUser);
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
            }}, 1, MY_OLD_PROFILE_ID);
        // setup grid for work profile on second screen
        mModelHelper.createGrid(new int[][][]{{
                { NO__ICON, APP_ICON, SHORTCUT, SHORTCUT},
                { SHORTCUT, SHORTCUT, NO__ICON, NO__ICON},
                { NO__ICON, NO__ICON, SHORTCUT, SHORTCUT},
                { APP_ICON, SHORTCUT, SHORTCUT, NO__ICON},
            }}, 2, OLD_WORK_PROFILE_ID);
        // simulates the creation of backup upon restore
        new GridBackupTable(RuntimeEnvironment.application, mDb, mIdp.numHotseatIcons,
                mIdp.numColumns, mIdp.numRows).doBackup(
                        MY_OLD_PROFILE_ID, GridBackupTable.OPTION_REQUIRES_SANITIZATION);
        // reset favorites table
        createTableUsingOldProfileId();
    }

    private void verifyTableIsEmpty(String tableName) {
        assertEquals(0, getCount(mDb, "SELECT * FROM " + tableName));
    }

    private void verifyTableIsFilled(String tableName, boolean sanitized) {
        assertEquals(sanitized ? 12 : 13, getCount(mDb,
                "SELECT * FROM " + tableName + " WHERE profileId = "
                        + (sanitized ? MY_PROFILE_ID : MY_OLD_PROFILE_ID)));
        assertEquals(10, getCount(mDb, "SELECT * FROM " + tableName + " WHERE profileId = "
                + (sanitized ? WORK_PROFILE_ID : OLD_WORK_PROFILE_ID)));
    }

    private void createTableUsingOldProfileId() {
        // simulates the creation of favorites table on old device
        dropTable(mDb, TABLE_NAME);
        addTableToDb(mDb, MY_OLD_PROFILE_ID, false);
    }

    private void createRestoreSession() throws Exception {
        final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        final PackageInstaller installer = RuntimeEnvironment.application.getPackageManager()
                .getPackageInstaller();
        final int sessionId = installer.createSession(params);
        final PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);
        setField(info, "installReason", INSTALL_REASON_DEVICE_RESTORE);
        // TODO: (b/148410677) we should verify the following call instead
        //  InstallSessionHelper.INSTANCE.get(getContext()).restoreDbIfApplicable(info);
        RestoreDbTask.restoreIfPossible(RuntimeEnvironment.application,
                mModelHelper.provider.getHelper(), mBackupManager);
    }

    private static int getCount(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.getCount();
        }
    }
}
