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

import static com.android.launcher3.LauncherSettings.Favorites.BACKUP_TABLE_NAME;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.LauncherRoboTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;

/**
 * Tests to verify backup and restore flow.
 */
@RunWith(LauncherRoboTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class BackupRestoreTest {

    private LauncherModelHelper mModelHelper;
    private SQLiteDatabase mDb;

    @Before
    public void setUp() {
        mModelHelper = new LauncherModelHelper();
        RestoreDbTask.setPending(RuntimeEnvironment.application, true);
        mDb = mModelHelper.provider.getDb();
    }

    @Test
    public void testOnCreateDbIfNotExists_CreatesBackup() {
        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));
    }
}
