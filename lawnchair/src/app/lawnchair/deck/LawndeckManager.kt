package app.lawnchair.deck

import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.util.restartLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.provider.RestoreDbTask
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LawndeckManager(private val context: Context) {

    // TODO

    private val launcher = context.launcherNullable ?: LawnchairLauncher.instance?.launcher

    suspend fun enableLawndeck() = withContext(Dispatchers.IO) {
        if (!backupExists("bk")) createBackup("bk")
        if (backupExists("lawndeck")) {
            restoreBackup("lawndeck")
        } else {
            addAllAppsToWorkspace()
        }
    }

    suspend fun disableLawndeck() = withContext(Dispatchers.IO) {
        if (backupExists("bk")) {
            createBackup("lawndeck")
            restoreBackup("bk")
        }
    }

    private fun createBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            db.copyTo(backupDb, overwrite = true)
            if (journal.exists()) journal.copyTo(backupJournal, overwrite = true)
        }
    }.onFailure { Log.e("LawndeckManager", "Failed to create backup: $suffix", it) }

    private fun restoreBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            backupDb.copyTo(db, overwrite = true)
            if (backupJournal.exists()) backupJournal.copyTo(journal, overwrite = true)
        }
        postRestoreActions()
    }.onFailure { Log.e("LawndeckManager", "Failed to restore backup: $suffix", it) }

    private fun getDatabaseFiles(suffix: String): DatabaseFiles {
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val dbFile = context.getDatabasePath(idp.dbFile)
        return DatabaseFiles(
            db = dbFile,
            backupDb = File(dbFile.parent, "${suffix}_${idp.dbFile}"),
            journal = File(dbFile.parent, "${idp.dbFile}-journal"),
            backupJournal = File(dbFile.parent, "${suffix}_${idp.dbFile}-journal"),
        )
    }

    private fun backupExists(suffix: String): Boolean = getDatabaseFiles(suffix).backupDb.exists()

    private fun postRestoreActions() {
        ModelDbController(context).let { RestoreDbTask.performRestore(context, it) }
        restartLauncher(context)
    }

    private fun addAllAppsToWorkspace() {
        launcher?.mAppsView?.appsStore?.apps
            ?.sortedBy { it.title?.toString()?.lowercase(Locale.getDefault()) }
            ?.forEach { app ->
                ItemInstallQueue.INSTANCE.get(context).queueItem(app.targetPackage, app.user)
            }
    }

    private data class DatabaseFiles(
        val db: File,
        val backupDb: File,
        val journal: File,
        val backupJournal: File,
    )
}
