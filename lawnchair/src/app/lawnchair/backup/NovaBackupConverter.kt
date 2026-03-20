package app.lawnchair.backup

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
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

class NovaBackupConverter(
    private val context: Context,
    private val uri: Uri,
) {

    companion object {
        private const val TAG = "NovaBackupConverter"
        private const val NOVA_XML = "nova.xml"
        private const val NOVA_DB = "nova.db"
        private const val NOVA_CONTAINER_DESKTOP = -100
        private const val NOVA_CONTAINER_HOTSEAT = -101
        private const val FOLDER_PAGE_RANK_OFFSET = 1_000
        private const val FOLDER_ROW_RANK_OFFSET = 100
        private const val NOVA_TEMP_DIR_PREFIX = "nova_"
        private const val NOVA_WORKSPACE_DB = "nova_workspace.db"
        private const val NOVA_TABLE_FAVORITES = "favorites"
        private const val NOVA_XML_TAG_STRING = "string"
        private const val NOVA_XML_TAG_INT = "int"
        private const val NOVA_XML_ATTR_NAME = "name"
        private const val NOVA_XML_ATTR_VALUE = "value"
        private const val NOVA_XML_KEY_DESKTOP_GRID = "desktop_grid"
        private const val NOVA_XML_KEY_ICON_PACK = "theme_icon_pack"
        private const val NOVA_XML_KEY_DOCK_COLS = "dock_grid_cols"
        private const val NOVA_ICON_PACK_MIN_PARTS = 3
        private const val NOVA_ICON_PACK_PACKAGE_INDEX = 2
        private const val NOVA_COL_ID = "_id"
        private const val NOVA_COL_CONTAINER = "container"
        private const val NOVA_COL_ITEM_TYPE = "itemType"
        private const val NOVA_COL_TITLE = "title"
        private const val NOVA_COL_INTENT = "intent"
        private const val NOVA_COL_CELL_X = "cellX"
        private const val NOVA_COL_CELL_Y = "cellY"
        private const val NOVA_COL_SCREEN = "screen"
        private const val NOVA_COL_SPAN_X = "spanX"
        private const val NOVA_COL_SPAN_Y = "spanY"
        private const val NOVA_COL_ICON = "icon"
        private const val NOVA_COL_APP_WIDGET_PROVIDER = "appWidgetProvider"
    }

    private val novaGridRegex = Regex("(\\d+)x(\\d+)")

    data class NovaBackupInfo(
        val columns: Int?,
        val rows: Int?,
        val hotseatCount: Int?,
        val appCount: Int,
        val widgetCount: Int,
        val folderCount: Int,
        val shortcutCount: Int,
        val iconPackPackage: String?,
        val iconPackLabel: String?,
    )

    private data class NovaConfig(
        val columns: Int?,
        val rows: Int?,
        val dockCols: Int?,
        val iconPackPackage: String?,
    )

    private data class ItemCounts(
        val apps: Int,
        val widgets: Int,
        val folders: Int,
        val shortcuts: Int,
    )

    private data class ImportedDeepShortcut(
        val packageName: String,
        val shortcutId: String,
    ) {
        fun toLauncherIntentUri(): String = ShortcutKey.makeIntent(shortcutId, packageName).toUri(0)
    }

    suspend fun parseInfo(): NovaBackupInfo = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$NOVA_TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            extractFromZip(uri, tempDir, setOf(NOVA_XML, NOVA_DB))

            val xmlFile = File(tempDir, NOVA_XML)
            val dbFile = File(tempDir, NOVA_DB)
            require(xmlFile.exists() && dbFile.exists()) { "Missing $NOVA_XML or $NOVA_DB" }

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
                iconPackLabel = novaConfig.iconPackPackage?.let { packageName ->
                    resolveIconPackLabel(packageName)
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun convertAndRestore(info: NovaBackupInfo) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$NOVA_TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            extractFromZip(uri, tempDir, setOf(NOVA_DB))

            val novaDbFile = File(tempDir, NOVA_DB)
            require(novaDbFile.exists()) { "Missing $NOVA_DB" }

            val stagedDbFile = File(tempDir, NOVA_WORKSPACE_DB)
            val importedDeepShortcuts = createRestoredDb(novaDbFile, stagedDbFile)

            val columns = info.columns
            val rows = info.rows
            val hotseatCount = info.hotseatCount
            if (columns != null && rows != null && hotseatCount != null) {
                val gridInfo = DeviceProfileOverrides.DBGridInfo(
                    numHotseatColumns = hotseatCount,
                    numRows = rows,
                    numColumns = columns,
                )
                val gridState = DeviceGridState(
                    columns,
                    rows,
                    hotseatCount,
                    InvariantDeviceProfile.TYPE_PHONE,
                    gridInfo.dbFile,
                )
                gridState.writeToPrefs(context, true)
                gridState.writeToPrefs(context)
                InvariantDeviceProfile.INSTANCE.get(context).dbFile = gridInfo.dbFile
            }
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

    private fun resolveIconPackLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun writeGridToLawnchairPrefs(info: NovaBackupInfo) {
        val prefs = PreferenceManager.getInstance(context)
        prefs.sp.edit().apply {
            info.columns?.let { putInt(prefs.workspaceColumns.key, it) }
            info.rows?.let { putInt(prefs.workspaceRows.key, it) }
            info.hotseatCount?.let { putInt(prefs.hotseatColumns.key, it) }
            info.iconPackPackage?.let { putString(prefs.iconPackPackage.key, it) }
        }.commit()
    }

    private fun parseNovaConfig(xmlFile: File): NovaConfig {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val root = doc.documentElement
        var columns: Int? = null
        var rows: Int? = null
        var dockCols: Int? = null
        var iconPackPackage: String? = null

        val stringNodes = root.getElementsByTagName(NOVA_XML_TAG_STRING)
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem(NOVA_XML_ATTR_NAME)?.nodeValue ?: continue
            val text = node.textContent ?: continue
            when (name) {
                NOVA_XML_KEY_DESKTOP_GRID -> {
                    val match = novaGridRegex.find(text) ?: continue
                    columns = match.groupValues[1].toIntOrNull() ?: continue
                    rows = match.groupValues[2].toIntOrNull() ?: continue
                }

                NOVA_XML_KEY_ICON_PACK -> {
                    val parts = text.split(":")
                    if (parts.size >= NOVA_ICON_PACK_MIN_PARTS) iconPackPackage = parts[NOVA_ICON_PACK_PACKAGE_INDEX]
                }
            }
        }

        val intNodes = root.getElementsByTagName(NOVA_XML_TAG_INT)
        for (i in 0 until intNodes.length) {
            val node = intNodes.item(i)
            val name = node.attributes.getNamedItem(NOVA_XML_ATTR_NAME)?.nodeValue ?: continue
            val value = node.attributes.getNamedItem(NOVA_XML_ATTR_VALUE)?.nodeValue?.toIntOrNull() ?: continue
            if (name == NOVA_XML_KEY_DOCK_COLS) dockCols = value
        }

        return NovaConfig(columns, rows, dockCols, iconPackPackage)
    }

    private fun countItems(dbFile: File): ItemCounts {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            it.rawQuery(
                """SELECT $NOVA_COL_ITEM_TYPE, COUNT(*) FROM $NOVA_TABLE_FAVORITES
                    WHERE $NOVA_COL_ITEM_TYPE != -1
                    AND ($NOVA_COL_CONTAINER = $NOVA_CONTAINER_DESKTOP
                        OR $NOVA_COL_CONTAINER = $NOVA_CONTAINER_HOTSEAT
                        OR $NOVA_COL_CONTAINER >= 0)
                    GROUP BY $NOVA_COL_ITEM_TYPE""",
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
        val importedDeepShortcuts = mutableMapOf<String, MutableSet<String>>()

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
        importedDeepShortcuts: MutableMap<String, MutableSet<String>>,
    ) {
        src.rawQuery(
            "SELECT * FROM $NOVA_TABLE_FAVORITES WHERE $NOVA_COL_ITEM_TYPE != -1",
            null,
        ).use { cursor ->
            val now = System.currentTimeMillis()

            while (cursor.moveToNext()) {
                val novaId = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_ID))
                val novaContainer = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_CONTAINER))
                val itemType = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_ITEM_TYPE))

                val isDesktop = novaContainer == NOVA_CONTAINER_DESKTOP
                val isHotseat = novaContainer == NOVA_CONTAINER_HOTSEAT
                val isFolderChild = novaContainer >= 0

                if (!isDesktop && !isHotseat && !isFolderChild) continue

                val container = when {
                    isDesktop -> Favorites.CONTAINER_DESKTOP
                    isHotseat -> Favorites.CONTAINER_HOTSEAT
                    else -> novaContainer
                }

                val title = getStringOrNull(cursor, NOVA_COL_TITLE)
                val rawIntent = getStringOrNull(cursor, NOVA_COL_INTENT)
                val importedDeepShortcut = if (itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    parseNovaDeepShortcut(rawIntent)?.also { shortcut ->
                        importedDeepShortcuts.getOrPut(shortcut.packageName) { mutableSetOf() }
                            .add(shortcut.shortcutId)
                    }
                } else {
                    null
                }
                val intent = importedDeepShortcut?.toLauncherIntentUri() ?: rawIntent
                val cellX = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_CELL_X)).toInt()
                val cellY = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_CELL_Y)).toInt()
                val screen = if (isHotseat) cellX else cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_SCREEN))
                val spanX = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_SPAN_X)).toInt().coerceAtLeast(1)
                val spanY = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_SPAN_Y)).toInt().coerceAtLeast(1)
                val icon = getBlobOrNull(cursor, NOVA_COL_ICON)
                val appWidgetProvider = getStringOrNull(cursor, NOVA_COL_APP_WIDGET_PROVIDER)
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

    private fun parseNovaDeepShortcut(intentUri: String?): ImportedDeepShortcut? {
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
}
