package app.lawnchair.backup

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.util.Log
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URISyntaxException
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NovaBackupInfo(
    val columns: Int,
    val rows: Int,
    val hotseatCount: Int,
    val appCount: Int,
    val widgetCount: Int,
    val folderCount: Int,
    val shortcutCount: Int,
    val iconPackPackage: String?,
)

class NovaBackupConverter(
    private val context: Context,
    private val uri: Uri,
) {
    suspend fun parseInfo(): NovaBackupInfo = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "nova_${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            extractFromZip(uri, tempDir, setOf(NOVA_XML, NOVA_DB))

            val xmlFile = File(tempDir, NOVA_XML)
            val dbFile = File(tempDir, NOVA_DB)
            require(xmlFile.exists() && dbFile.exists()) { "Missing nova.xml or nova.db" }

            val novaConfig = parseNovaConfig(xmlFile)
            val (apps, widgets, folders, shortcuts) = countItems(dbFile)

            NovaBackupInfo(
                columns = novaConfig.columns,
                rows = novaConfig.rows,
                hotseatCount = novaConfig.dockCols,
                appCount = apps,
                widgetCount = widgets,
                folderCount = folders,
                shortcutCount = shortcuts,
                iconPackPackage = novaConfig.iconPackPackage,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun convertAndRestore(info: NovaBackupInfo) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "nova_${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            extractFromZip(uri, tempDir, setOf(NOVA_DB))

            val novaDbFile = File(tempDir, NOVA_DB)
            require(novaDbFile.exists()) { "Missing nova.db" }

            val stagedDbFile = File(tempDir, "nova_workspace.db")
            val importedDeepShortcuts = createRestoredDb(novaDbFile, stagedDbFile)

            val gridInfo = DeviceProfileOverrides.DBGridInfo(
                numHotseatColumns = info.hotseatCount,
                numRows = info.rows,
                numColumns = info.columns,
            )
            val gridState = DeviceGridState(
                info.columns,
                info.rows,
                info.hotseatCount,
                InvariantDeviceProfile.TYPE_PHONE,
                gridInfo.dbFile,
            )

            context.getDatabasePath(LawnchairBackup.LAUNCHER_DB_FILE_NAME).parentFile?.deleteRecursively()
            gridState.writeToPrefs(context, true)
            writeGridToLawnchairPrefs(info)

            val restoredDbFile = context.getDatabasePath(LawnchairBackup.RESTORED_DB_FILE_NAME)
            restoredDbFile.parentFile?.mkdirs()
            stagedDbFile.copyTo(restoredDbFile, overwrite = true)

            val dbController = ModelDbController(context)
            RestoreDbTask.performRestore(context, dbController)

            pinImportedDeepShortcuts(importedDeepShortcuts)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeGridToLawnchairPrefs(info: NovaBackupInfo) {
        val prefs = PreferenceManager.getInstance(context)
        prefs.batchEdit {
            prefs.workspaceColumns.set(info.columns)
            prefs.workspaceRows.set(info.rows)
            prefs.hotseatColumns.set(info.hotseatCount)
            prefs.iconPackPackage.set(info.iconPackPackage.orEmpty())
        }
    }

    private data class NovaConfig(val columns: Int, val rows: Int, val dockCols: Int, val iconPackPackage: String?)

    private fun parseNovaConfig(xmlFile: File): NovaConfig {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val root = doc.documentElement
        var columns = 5
        var rows = 5
        var dockCols = 5
        var iconPackPackage: String? = null

        val stringNodes = root.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val text = node.textContent ?: continue
            when (name) {
                "desktop_grid" -> {
                    val match = Regex("(\\d+)x(\\d+)").find(text) ?: continue
                    columns = match.groupValues[1].toInt()
                    rows = match.groupValues[2].toInt()
                }

                "theme_icon_pack" -> {
                    val parts = text.split(":")
                    if (parts.size >= 3) iconPackPackage = parts[2]
                }
            }
        }

        val intNodes = root.getElementsByTagName("int")
        for (i in 0 until intNodes.length) {
            val node = intNodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val value = node.attributes.getNamedItem("value")?.nodeValue?.toIntOrNull() ?: continue
            if (name == "dock_grid_cols") dockCols = value
        }

        return NovaConfig(columns, rows, dockCols, iconPackPackage)
    }

    private data class ItemCounts(val apps: Int, val widgets: Int, val folders: Int, val shortcuts: Int)

    private fun countItems(dbFile: File): ItemCounts {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            it.rawQuery(
                """SELECT itemType, COUNT(*) FROM favorites
                    WHERE itemType != -1
                    AND (container = $NOVA_CONTAINER_DESKTOP
                        OR container = $NOVA_CONTAINER_HOTSEAT
                        OR container >= 0)
                    GROUP BY itemType""",
                null,
            ).use { cursor ->
                var apps = 0
                var widgets = 0
                var folders = 0
                var shortcuts = 0
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(0)
                    val count = cursor.getInt(1)
                    when (type) {
                        Favorites.ITEM_TYPE_APPLICATION -> apps += count
                        Favorites.ITEM_TYPE_APPWIDGET, Favorites.ITEM_TYPE_CUSTOM_APPWIDGET -> widgets += count
                        Favorites.ITEM_TYPE_FOLDER -> folders += count
                        Favorites.ITEM_TYPE_SHORTCUT, Favorites.ITEM_TYPE_DEEP_SHORTCUT -> shortcuts += count
                    }
                }
                ItemCounts(apps, widgets, folders, shortcuts)
            }
        }
    }

    private fun createRestoredDb(
        novaDbFile: File,
        targetDbFile: File,
    ): Map<String, Set<String>> {
        val profileId = UserCache.INSTANCE.get(context)
            .getSerialNumberForUser(Process.myUserHandle())
        val importedDeepShortcuts = linkedMapOf<String, LinkedHashSet<String>>()

        val targetDb = SQLiteDatabase.openOrCreateDatabase(targetDbFile, null)
        targetDb.use { db ->
            db.version = DatabaseHelper.SCHEMA_VERSION
            Favorites.addTableToDb(db, profileId, false)

            val novaDb = SQLiteDatabase.openDatabase(
                novaDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            novaDb.use { src ->
                db.beginTransaction()
                try {
                    insertNovaItems(src, db, profileId, importedDeepShortcuts)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        return importedDeepShortcuts.mapValues { (_, shortcutIds) -> shortcutIds.toSet() }
    }

    private fun insertNovaItems(
        src: SQLiteDatabase,
        db: SQLiteDatabase,
        profileId: Long,
        importedDeepShortcuts: MutableMap<String, LinkedHashSet<String>>,
    ) {
        src.rawQuery(
            "SELECT * FROM favorites WHERE itemType != -1",
            null,
        ).use { cursor ->
            val now = System.currentTimeMillis()

            while (cursor.moveToNext()) {
                val novaId = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val novaContainer = cursor.getInt(cursor.getColumnIndexOrThrow("container"))
                val itemType = cursor.getInt(cursor.getColumnIndexOrThrow("itemType"))

                val isDesktop = novaContainer == NOVA_CONTAINER_DESKTOP
                val isHotseat = novaContainer == NOVA_CONTAINER_HOTSEAT
                val isFolderChild = novaContainer >= 0

                if (!isDesktop && !isHotseat && !isFolderChild) continue

                val container = when {
                    isDesktop -> Favorites.CONTAINER_DESKTOP
                    isHotseat -> Favorites.CONTAINER_HOTSEAT
                    else -> novaContainer
                }

                val title = getStringOrNull(cursor, "title")
                val rawIntent = getStringOrNull(cursor, "intent")
                val importedDeepShortcut = if (itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    parseNovaDeepShortcut(rawIntent)?.also { shortcut ->
                        importedDeepShortcuts.getOrPut(shortcut.packageName) { linkedSetOf() }
                            .add(shortcut.shortcutId)
                    }
                } else {
                    null
                }
                val intent = importedDeepShortcut?.toLauncherIntentUri() ?: rawIntent
                val cellX = cursor.getDouble(cursor.getColumnIndexOrThrow("cellX")).toInt()
                val cellY = cursor.getDouble(cursor.getColumnIndexOrThrow("cellY")).toInt()
                val screen = if (isHotseat) cellX else cursor.getInt(cursor.getColumnIndexOrThrow("screen"))
                val spanX = cursor.getDouble(cursor.getColumnIndexOrThrow("spanX")).toInt().coerceAtLeast(1)
                val spanY = cursor.getDouble(cursor.getColumnIndexOrThrow("spanY")).toInt().coerceAtLeast(1)
                val icon = getBlobOrNull(cursor, "icon")
                val appWidgetProvider = getStringOrNull(cursor, "appWidgetProvider")
                val rank = when {
                    isFolderChild -> calculateFolderRank(screen, cellX, cellY)
                    isHotseat -> screen
                    else -> 0
                }

                db.execSQL(
                    """INSERT INTO favorites (
                        _id, title, intent, container, screen, cellX, cellY,
                        spanX, spanY, itemType, appWidgetId, icon,
                        appWidgetProvider, modified, restored, profileId,
                        rank, options, appWidgetSource
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, -1, ?, ?, ?, 0, ?, ?, 0, -1)""",
                    arrayOf(
                        novaId,
                        title,
                        intent,
                        container,
                        screen,
                        cellX,
                        if (isHotseat) 0 else cellY,
                        spanX,
                        spanY,
                        itemType,
                        icon,
                        appWidgetProvider,
                        now,
                        profileId,
                        rank,
                    ),
                )
            }
        }
    }

    private fun pinImportedDeepShortcuts(importedDeepShortcuts: Map<String, Set<String>>) {
        if (importedDeepShortcuts.isEmpty()) return

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
        val user = Process.myUserHandle()

        importedDeepShortcuts.forEach { (packageName, shortcutIds) ->
            val pinnedShortcutIds = linkedSetOf<String>()
            val currentPinnedShortcuts = ShortcutRequest(context, user)
                .forPackage(packageName)
                .query(ShortcutRequest.PINNED or LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
            if (currentPinnedShortcuts.wasSuccess()) {
                currentPinnedShortcuts.mapTo(pinnedShortcutIds) { it.id }
            }
            pinnedShortcutIds.addAll(shortcutIds)

            try {
                launcherApps.pinShortcuts(packageName, pinnedShortcutIds.toList(), user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to pin imported deep shortcuts for $packageName", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to pin imported deep shortcuts for $packageName", e)
            }
        }
    }

    private fun getStringOrNull(cursor: android.database.Cursor, column: String): String? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
    }

    private fun getBlobOrNull(cursor: android.database.Cursor, column: String): ByteArray? {
        val idx = cursor.getColumnIndex(column)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getBlob(idx) else null
    }

    private fun extractFromZip(uri: Uri, destDir: File, fileNames: Set<String>) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open backup URI")
        val destCanonical = destDir.canonicalPath
        pfd.use {
            FileInputStream(it.fileDescriptor).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name in fileNames) {
                            val outFile = File(destDir, entry.name)
                            require(outFile.canonicalPath.startsWith(destCanonical + File.separator)) {
                                "Zip entry outside target dir: ${entry.name}"
                            }
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }
    }

    private fun calculateFolderRank(screen: Int, cellX: Int, cellY: Int): Int {
        return (screen * FOLDER_PAGE_RANK_OFFSET) + (cellY * FOLDER_ROW_RANK_OFFSET) + cellX
    }

    companion object {
        private const val TAG = "NovaBackupConverter"
        private const val NOVA_XML = "nova.xml"
        private const val NOVA_DB = "nova.db"
        private const val NOVA_CONTAINER_DESKTOP = -100
        private const val NOVA_CONTAINER_HOTSEAT = -101
        private const val FOLDER_PAGE_RANK_OFFSET = 1_000
        private const val FOLDER_ROW_RANK_OFFSET = 100
    }
}

internal data class ImportedDeepShortcut(
    val packageName: String,
    val shortcutId: String,
)

internal fun ImportedDeepShortcut.toLauncherIntentUri(): String {
    return ShortcutKey.makeIntent(shortcutId, packageName).toUri(0)
}

internal fun parseNovaDeepShortcut(intentUri: String?): ImportedDeepShortcut? {
    if (intentUri.isNullOrEmpty()) return null

    val intent = try {
        Intent.parseUri(intentUri, 0)
    } catch (_: URISyntaxException) {
        return null
    }

    val packageName = intent.`package` ?: intent.component?.packageName ?: return null
    val shortcutId = intent.getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) ?: return null
    return ImportedDeepShortcut(packageName, shortcutId)
}
