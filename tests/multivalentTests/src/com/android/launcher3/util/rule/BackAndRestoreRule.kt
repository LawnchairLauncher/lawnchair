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

package com.android.launcher3.util.rule

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Removes all launcher's DBs from the device and copies the dbs in
 * assets/databases/BackupAndRestore to the device. It also set's the needed LauncherPrefs variables
 * needed to kickstart a backup and restore.
 */
class BackAndRestoreRule : TestRule {

    private val phoneContext = getInstrumentation().targetContext

    private fun dbBackUp() = File(phoneContext.dataDir.path, "/databasesBackUp")

    private fun dbDirectory() = File(phoneContext.dataDir.path, "/databases")

    private fun isWorkspaceDatabase(rawFileName: String): Boolean {
        val fileName = Paths.get(rawFileName).fileName.pathString
        return fileName.startsWith("launcher") && fileName.endsWith(".db")
    }

    fun getDatabaseFiles() = dbDirectory().listFiles().filter { isWorkspaceDatabase(it.name) }

    /**
     * Setting RESTORE_DEVICE would trigger a restore next time the Launcher starts, and we remove
     * the widgets and apps ids to prevent issues when loading the database.
     */
    private fun setRestoreConstants() {
        LauncherPrefs.get(phoneContext)
            .put(LauncherPrefs.RESTORE_DEVICE.to(InvariantDeviceProfile.TYPE_MULTI_DISPLAY))
        LauncherPrefs.get(phoneContext)
            .remove(LauncherPrefs.OLD_APP_WIDGET_IDS, LauncherPrefs.APP_WIDGET_IDS)
    }

    private fun uploadDatabase(dbName: String) {
        val file = File(File(getInstrumentation().targetContext.dataDir, "/databases"), dbName)
        file.writeBytes(
            getInstrumentation()
                .context
                .assets
                .open("databases/BackupAndRestore/$dbName")
                .readBytes()
        )
        file.setWritable(true, false)
    }

    private fun uploadDbs() {
        uploadDatabase("launcher.db")
        uploadDatabase("launcher_4_by_4.db")
        uploadDatabase("launcher_4_by_5.db")
        uploadDatabase("launcher_3_by_3.db")
    }

    private fun savePreviousState() {
        dbBackUp().deleteRecursively()
        if (!dbDirectory().renameTo(dbBackUp())) {
            throw Exception("Unable to move databases to backup directory")
        }
        dbDirectory().mkdir()
        if (!dbDirectory().exists()) {
            throw Exception("Databases directory doesn't exists")
        }
    }

    private fun restorePreviousState() {
        dbDirectory().deleteRecursively()
        if (!dbBackUp().renameTo(dbDirectory())) {
            throw Exception("Unable to restore backup directory to databases directory")
        }
        dbBackUp().delete()
    }

    fun before() {
        savePreviousState()
        setRestoreConstants()
        uploadDbs()
    }

    fun after() {
        restorePreviousState()
    }

    override fun apply(base: Statement?, description: Description?): Statement =
        object : Statement() {
            override fun evaluate() {
                before()
                try {
                    base?.evaluate()
                } finally {
                    after()
                }
            }
        }
}
