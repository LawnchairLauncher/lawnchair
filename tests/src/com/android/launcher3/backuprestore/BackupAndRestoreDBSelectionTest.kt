/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.backuprestore

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.BackAndRestoreRule
import com.android.launcher3.util.rule.setFlags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Makes sure to test {@code RestoreDbTask#removeOldDBs}, we need to remove all the dbs that are not
 * the last one used when we restore the device.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class BackupAndRestoreDBSelectionTest {

    @JvmField @Rule var backAndRestoreRule = BackAndRestoreRule()

    @JvmField
    @Rule
    val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    @Before
    fun setUp() {
        setFlagsRule.setFlags(true, Flags.FLAG_ENABLE_NARROW_GRID_RESTORE)
    }

    @Test
    fun oldDatabasesNotPresentAfterRestore() {
        val dbController = ModelDbController(getInstrumentation().targetContext)
        dbController.tryMigrateDB(null)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            assert(backAndRestoreRule.getDatabaseFiles().size == 1) {
                "There should only be one database after restoring, the last one used. Actual databases ${backAndRestoreRule.getDatabaseFiles()}"
            }
            assert(
                !LauncherPrefs.get(getInstrumentation().targetContext)
                    .has(LauncherPrefs.RESTORE_DEVICE)
            ) {
                "RESTORE_DEVICE shouldn't be present after a backup and restore."
            }
        }
    }
}
