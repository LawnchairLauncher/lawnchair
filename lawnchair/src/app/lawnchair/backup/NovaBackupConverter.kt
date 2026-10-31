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
import app.lawnchair.dock.DrawerButton
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.GridType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import java.io.File
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
        private const val NOVA_XML_KEY_DOCK_PAGES = "dock_pages_count"
        private const val NOVA_SHORTCUT_HANDLER = "com.teslacoilsw.launcher/.NovaShortcutHandler"
        private const val NOVA_ACTION_APP_DRAWER = "LAUNCHER_ACTION=APP_DRAWER"
        private const val MAX_DOCK_PAGES = 5
        private const val SQL_IN_CHUNK = 500
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
        private const val NOVA_SMART_FOLDER_MARKER = "FOLDER%3A-"
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
        val dockPages: Int = 1,
    )

    private data class NovaConfig(
        val columns: Int?,
        val rows: Int?,
        val dockCols: Int?,
        val iconPackPackage: String?,
        val dockPages: Int = 1,
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

    /** Reads grid, dock and item counts from the backup for the confirmation screen. */
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
                dockPages = novaConfig.dockPages,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Replaces the launcher layout with the Nova backup and applies its grid and dock settings. */
    suspend fun convertAndRestore(info: NovaBackupInfo) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "$NOVA_TEMP_DIR_PREFIX${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            extractFromZip(uri, tempDir, setOf(NOVA_DB))

            val novaDbFile = File(tempDir, NOVA_DB)
            require(novaDbFile.exists()) { "Missing $NOVA_DB" }

            val stagedDbFile = File(tempDir, NOVA_WORKSPACE_DB)
            val staged = createRestoredDb(novaDbFile, stagedDbFile, info)

            val columns = info.columns
            val rows = info.rows
            val hotseatCount = info.hotseatCount
            if (columns != null && rows != null && hotseatCount != null) {
                val gridInfo = DeviceProfileOverrides.DBGridInfo(
                    numHotseatColumns = hotseatCount,
                    numRows = rows,
                    numColumns = columns,
                )
                // The database must hold every dock page, as DeviceProfileOverrides sizes it
                val gridState = DeviceGridState(
                    columns,
                    rows,
                    hotseatCount * info.dockPages,
                    InvariantDeviceProfile.TYPE_PHONE,
                    gridInfo.dbFile,
                    GridType.GRID_TYPE_ANY,
                )
                gridState.writeToPrefs(context, true)
                gridState.writeToPrefs(context)
                InvariantDeviceProfile.INSTANCE.get(context).dbFile = gridInfo.dbFile
            }
            writeGridToLawnchairPrefs(info)

            val restoredDbFile = context.getDatabasePath(LawnchairBackup.RESTORED_DB_FILE_NAME)
            restoredDbFile.parentFile?.mkdirs()
            deleteLauncherDatabases(restoredDbFile.parentFile)
            stagedDbFile.copyTo(restoredDbFile, overwrite = true)

            val dbController = ModelDbController(context)
            RestoreDbTask.performRestore(context, dbController)
            keepPlaceholders(dbController, staged.placeholderIds)

            pinImportedDeepShortcuts(staged.importedDeepShortcuts)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Removes the launcher databases with their -wal, -shm and -journal files, as the Lawnchair
     * backup restore does. The restored database takes the name of the one in use when the grid
     * is unchanged, and a stale write-ahead log left next to it would bring the old layout back.
     */
    private fun deleteLauncherDatabases(dir: File?) {
        dir?.listFiles()
            ?.filter { it.name.startsWith("launcher") || it.name.startsWith(LawnchairBackup.RESTORED_DB_FILE_NAME) }
            ?.forEach { it.delete() }
    }

    private fun resolveIconPackLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /** Writes Nova's grid, static paged dock and icon pack into Lawnchair's preferences. */
    private fun writeGridToLawnchairPrefs(info: NovaBackupInfo) {
        val prefs = PreferenceManager.getInstance(context)
        prefs.sp.edit().apply {
            info.columns?.let { putInt(prefs.workspaceColumns.key, it) }
            info.rows?.let { putInt(prefs.workspaceRows.key, it) }
            info.hotseatCount?.let { putInt(prefs.hotseatColumns.key, it) }
            putInt(prefs.hotseatRows.key, 1)
            putInt(prefs.dockPages.key, info.dockPages)
            // Nova opens the dock on its center page, with the other pages to the left and right
            putInt(prefs.dockDefaultPage.key, info.dockPages / 2)
            // Nova's dock is static; suggestions would fill and rearrange the restored slots
            putBoolean(prefs.dockSuggestions.key, false)
            info.iconPackPackage?.let { putString(prefs.iconPackPackage.key, it) }
        }.commit()
    }

    /** Parses grid size, dock columns, dock pages and icon pack from nova.xml. */
    private fun parseNovaConfig(xmlFile: File): NovaConfig {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val root = doc.documentElement
        var columns: Int? = null
        var rows: Int? = null
        var dockCols: Int? = null
        var dockPages = 1
        var iconPackPackage: String? = null

        val stringNodes = root.getElementsByTagName(NOVA_XML_TAG_STRING)
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem(NOVA_XML_ATTR_NAME)?.nodeValue ?: continue
            val text = node.textContent ?: continue
            when (name) {
                NOVA_XML_KEY_DESKTOP_GRID -> {
                    val match = novaGridRegex.find(text) ?: continue
                    rows = match.groupValues[1].toIntOrNull() ?: continue
                    columns = match.groupValues[2].toIntOrNull() ?: continue
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
            if (name == NOVA_XML_KEY_DOCK_PAGES) dockPages = value.coerceIn(1, MAX_DOCK_PAGES)
        }

        return NovaConfig(columns, rows, dockCols, iconPackPackage, dockPages)
    }

    /** Counts apps, widgets, folders and shortcuts that the restore would bring over. */
    private fun countItems(dbFile: File): ItemCounts {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        return db.use {
            it.rawQuery(
                """SELECT $NOVA_COL_ITEM_TYPE, COUNT(*) FROM $NOVA_TABLE_FAVORITES
                    WHERE $NOVA_COL_ITEM_TYPE != -1
                    AND ($NOVA_COL_CONTAINER = $NOVA_CONTAINER_DESKTOP
                        OR $NOVA_COL_CONTAINER = $NOVA_CONTAINER_HOTSEAT
                        OR $NOVA_COL_CONTAINER >= 0
                        OR $NOVA_COL_CONTAINER <= -200)
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

    /** Result of staging: deep shortcuts to pin, and items kept as placeholders for missing apps. */
    private data class StagedDb(
        val importedDeepShortcuts: Map<String, Set<String>>,
        val placeholderIds: List<Int>,
    )

    /**
     * performRestore resets every item to FLAG_RESTORED_ICON, which the loader deletes for apps
     * that are neither installed nor installing. Placeholders need FLAG_RESTORE_STARTED back.
     */
    private fun keepPlaceholders(dbController: ModelDbController, ids: List<Int>) {
        if (ids.isEmpty()) return
        val flags = WorkspaceItemInfo.FLAG_RESTORED_ICON or WorkspaceItemInfo.FLAG_RESTORE_STARTED
        ids.chunked(SQL_IN_CHUNK).forEach { chunk ->
            dbController.db.execSQL(
                "UPDATE ${Favorites.TABLE_NAME} SET ${Favorites.RESTORED} = ${Favorites.RESTORED} | $flags " +
                    "WHERE ${Favorites._ID} IN (${chunk.joinToString(",")})",
            )
        }
    }

    /** Converts nova.db into a Lawnchair favorites database at [targetDbFile]. */
    private fun createRestoredDb(
        novaDbFile: File,
        targetDbFile: File,
        info: NovaBackupInfo,
    ): StagedDb {
        val profileId = UserCache.INSTANCE.get(context)
            .getSerialNumberForUser(Process.myUserHandle())
        val importedDeepShortcuts = mutableMapOf<String, MutableSet<String>>()
        val placeholderIds = mutableListOf<Int>()

        val targetDb = SQLiteDatabase.openOrCreateDatabase(targetDbFile, null)
        targetDb.use { db ->
            db.version = DatabaseHelper.SCHEMA_VERSION
            Favorites.addTableToDb(db, profileId, false)

            val novaDb = SQLiteDatabase.openDatabase(
                novaDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            novaDb.use { src ->
                db.beginTransaction()
                try {
                    insertNovaItems(src, db, profileId, importedDeepShortcuts, placeholderIds, info)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        return StagedDb(
            importedDeepShortcuts.mapValues { (_, shortcutIds) -> shortcutIds.toSet() },
            placeholderIds,
        )
    }

    /**
     * Copies Nova's favorites into [db]. Dock items are re-ranked per page, Nova's drawer button
     * becomes Lawnchair's, Nova-only actions are skipped, and ids of placeholders for apps that
     * are not installed are collected in [placeholderIds].
     */
    private fun insertNovaItems(
        src: SQLiteDatabase,
        db: SQLiteDatabase,
        profileId: Long,
        importedDeepShortcuts: MutableMap<String, MutableSet<String>>,
        placeholderIds: MutableList<Int>,
        info: NovaBackupInfo,
    ) {
        val smartFolderMap = buildSmartFolderMap(src)
        // Backups without dock_grid_cols keep the current dock width; a missing value must not
        // turn into a huge multiplier for the flat dock rank
        val dockCols = info.hotseatCount?.takeIf { it > 0 }
            ?: PreferenceManager.getInstance(context).hotseatColumns.get().coerceAtLeast(1)
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val drawerIcon by lazy { GraphicsUtils.flattenBitmap(DrawerButton.icon(context).icon) }

        src.rawQuery(
            "SELECT * FROM $NOVA_TABLE_FAVORITES WHERE $NOVA_COL_ITEM_TYPE != -1",
            null,
        ).use { cursor ->
            val now = System.currentTimeMillis()

            while (cursor.moveToNext()) {
                val novaId = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_ID))
                val novaContainer = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_CONTAINER))
                var itemType = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_ITEM_TYPE))

                val isDesktop = novaContainer == NOVA_CONTAINER_DESKTOP
                val isHotseat = novaContainer == NOVA_CONTAINER_HOTSEAT
                val isFolderChild = novaContainer >= 0
                val isSmartFolderChild = novaContainer in smartFolderMap

                if (!isDesktop && !isHotseat && !isFolderChild && !isSmartFolderChild) continue

                val container = when {
                    isDesktop -> Favorites.CONTAINER_DESKTOP
                    isHotseat -> Favorites.CONTAINER_HOTSEAT
                    isSmartFolderChild -> smartFolderMap.getValue(novaContainer)
                    else -> novaContainer
                }

                var title = getStringOrNull(cursor, NOVA_COL_TITLE)
                /* TODO: Lawnchair folder synchronization support
                 *        For nova parity:
                 *           Sync folder contents from allapps to workspace, or workspace to allapps
                 *        ImplNote:
                 *           Check folder with intent: #Intent;component=com.teslacoilsw.launcher/FOLDER%3A-20X;end
                 *           X being category ID that are defined in drawer_group table
                 */
                var rawIntent = getStringOrNull(cursor, NOVA_COL_INTENT)
                var drawerButton = false
                if (rawIntent?.contains(NOVA_SHORTCUT_HANDLER) == true) {
                    // Nova actions need Nova; only its app drawer button has a Lawnchair equivalent
                    if (!rawIntent.contains(NOVA_ACTION_APP_DRAWER)) continue
                    drawerButton = true
                    itemType = Favorites.ITEM_TYPE_SHORTCUT
                    rawIntent = DrawerButton.intentUri()
                    title = context.getString(R.string.dock_drawer_button)
                }
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
                val novaScreen = cursor.getInt(cursor.getColumnIndexOrThrow(NOVA_COL_SCREEN))
                // Nova keeps the dock page in screen and the slot in cellX; Lawnchair ranks them flat
                if (isHotseat && (novaScreen >= info.dockPages || cellX >= dockCols)) continue
                val screen = if (isHotseat) novaScreen * dockCols + cellX else novaScreen
                val spanX = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_SPAN_X)).toInt().coerceAtLeast(1)
                val spanY = cursor.getDouble(cursor.getColumnIndexOrThrow(NOVA_COL_SPAN_Y)).toInt().coerceAtLeast(1)
                val icon = if (drawerButton) drawerIcon else getBlobOrNull(cursor, NOVA_COL_ICON)
                // Apps that are not installed stay, greyed out, until they are
                val restored = if (itemType == Favorites.ITEM_TYPE_APPLICATION &&
                    !isPackageInstalled(launcherApps, intent)
                ) {
                    WorkspaceItemInfo.FLAG_RESTORED_ICON or WorkspaceItemInfo.FLAG_RESTORE_STARTED
                } else {
                    0
                }
                if (restored != 0) placeholderIds += novaId
                val appWidgetProvider = getStringOrNull(cursor, NOVA_COL_APP_WIDGET_PROVIDER)
                val rank = when {
                    isFolderChild || isSmartFolderChild -> calculateFolderRank(screen, cellX, cellY)
                    isHotseat -> screen
                    else -> 0
                }

                db.execSQL(
                    """INSERT INTO favorites (
                        _id, title, intent, container, screen, cellX, cellY,
                        spanX, spanY, itemType, appWidgetId, icon,
                        appWidgetProvider, modified, restored, profileId,
                        rank, options, appWidgetSource
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, -1, ?, ?, ?, ?, ?, ?, 0, -1)""",
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
                        restored,
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

    /** Whether the app an intent points to is installed; unknown targets count as installed. */
    private fun isPackageInstalled(launcherApps: LauncherApps?, intentUri: String?): Boolean {
        val pkg = try {
            intentUri?.let { Intent.parseUri(it, 0) }?.let { it.component?.packageName ?: it.`package` }
        } catch (_: URISyntaxException) {
            null
        } ?: return true
        return try {
            launcherApps?.isPackageEnabled(pkg, Process.myUserHandle()) ?: true
        } catch (_: Exception) {
            true
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

    /** Extracts [fileNames] from the backup zip into [destDir], rejecting entries outside it. */
    private fun extractFromZip(uri: Uri, destDir: File, fileNames: Set<String>) {
        // A stream works with every document provider; not all of them hand out file descriptors
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open backup URI")
        val destCanonical = destDir.canonicalPath
        input.use {
            it.buffered().use { fis ->
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

    /**
     * Get smart folder header rows from favorites table and builds a mapping from the smart folder
     * container ID (-20X) to the folder row's _id.
     *
     * X being category ID that are defined in drawer_group table
     */
    private fun buildSmartFolderMap(src: SQLiteDatabase): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        src.rawQuery(
            """SELECT $NOVA_COL_ID, $NOVA_COL_INTENT FROM $NOVA_TABLE_FAVORITES
                WHERE $NOVA_COL_ITEM_TYPE = ${Favorites.ITEM_TYPE_FOLDER}
                AND $NOVA_COL_CONTAINER = $NOVA_CONTAINER_DESKTOP
                AND $NOVA_COL_INTENT LIKE '%$NOVA_SMART_FOLDER_MARKER%'""",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val folderId = cursor.getInt(0)
                val intentUri = cursor.getString(1) ?: continue
                val smartContainerId = extractSmartFolderContainerId(intentUri)
                if (smartContainerId != null) {
                    map[smartContainerId] = folderId
                }
            }
        }
        return map
    }

    /**
     * Get smart folder container ID from a Nova folder intent URI.
     * `#Intent;component=com.teslacoilsw.launcher/FOLDER%3A-208;end` -> -208
     */
    private fun extractSmartFolderContainerId(intentUri: String): Int? {
        val marker = "FOLDER%3A"
        val idx = intentUri.indexOf(marker)
        if (idx < 0) return null
        val afterMarker = intentUri.substring(idx + marker.length)
        val numStr = afterMarker.takeWhile { it == '-' || it.isDigit() }
        return numStr.toIntOrNull()
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

    /*
     * Glossary: drawer_group
     *
     * The drawer_group table define app drawer tabs (groups) in Nova.
     * This table is the following category from category_id in drawer_group table:
     * 1. null (known as: "Apps", for items that belong to main profile)
     * 2. null (known as: "Work", for items that belong to work profile)
     * 3. ENTERTAINMENT
     * 4. FINANCE
     * 5. FOOD
     * 6. GAMES
     * 7. SHOPPING
     * 8. SOCIAL
     * 9. TRAVEL
     *
     * Note: This table is related to the appgroups table which is a table that already assigns apps
     *       to these categories.
     *
     * Note: Nova assigns items to these categories by crowdsourcing,
     *       unlike our Flowerpot (v1) Azalea system that needs to be constantly updated manually.
     */
}
