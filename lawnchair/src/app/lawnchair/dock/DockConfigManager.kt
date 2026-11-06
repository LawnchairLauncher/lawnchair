package app.lawnchair.dock

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import android.util.Base64
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.PreferenceManager
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.io.ByteArrayOutputStream
import java.net.URISyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Reads and replaces the dock (hotseat) rows of the launcher database. */
class DockConfigManager(private val context: Context) {

    data class ImportResult(val placed: Int, val notInstalled: List<String>)

    private val prefs = PreferenceManager.getInstance(context)
    private val model get() = LauncherAppState.getInstance(context).model
    private val modelDispatcher = MODEL_EXECUTOR.asCoroutineDispatcher()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Reads the current dock, including folder contents and icons for every app. */
    suspend fun export(): DockConfig = withContext(modelDispatcher) {
        val columns = prefs.hotseatColumns.get()
        val rows = prefs.hotseatRows.get()
        val pages = prefs.dockPages.get()
        val slotsPerPage = columns * rows
        val db = model.modelDbController

        val slots = query(db, "${Favorites.CONTAINER} = ${Favorites.CONTAINER_HOTSEAT}", Favorites.SCREEN)
            .mapNotNull { row ->
                val page = row.screen / slotsPerPage
                if (page >= pages) return@mapNotNull null
                val local = row.screen % slotsPerPage
                val items = if (row.itemType == Favorites.ITEM_TYPE_FOLDER) {
                    query(db, "${Favorites.CONTAINER} = ${row.id}", Favorites.RANK).mapNotNull { toSlot(it, emptyList()) }
                } else {
                    emptyList()
                }
                toSlot(row, items)?.copy(page = page, x = local % columns, y = local / columns)
            }
        DockConfig(columns = columns, rows = rows, pages = pages, defaultPage = prefs.dockDefaultPage.get(), slots = slots)
    }

    /** Replaces the dock with [config] as a static dock, switching the dock grid first when its size differs. */
    suspend fun import(config: DockConfig): ImportResult {
        // An imported dock is a fixed layout; suggestions would fill and rearrange its slots
        if (prefs.dockSuggestions.get()) withContext(Dispatchers.Main) { prefs.dockSuggestions.set(false) }
        if (prefs.hotseatColumns.get() != config.columns || prefs.hotseatRows.get() != config.rows ||
            prefs.dockPages.get() != config.pages
        ) {
            withContext(Dispatchers.Main) {
                prefs.hotseatColumns.set(config.columns)
                prefs.hotseatRows.set(config.rows)
                prefs.dockPages.set(config.pages)
            }
            awaitGrid(config)
        }
        val result = withContext(modelDispatcher) { writeDock(config) }
        withContext(Dispatchers.Main) {
            model.forceReload()
            config.defaultPage?.let { prefs.dockDefaultPage.set(it.coerceIn(0, config.pages - 1)) }
        }
        return result
    }

    /**
     * The database file name contains the dock column count, so a new size switches databases and
     * migrates on the next model load. Writing before that finishes would be lost.
     */
    private suspend fun awaitGrid(config: DockConfig) = withTimeout(GRID_TIMEOUT_MS) {
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val expectedDb = DeviceProfileOverrides.DBGridInfo(prefs).dbFile
        val slots = config.columns * config.rows * config.pages
        while (idp.dbFile != expectedDb || idp.numDatabaseHotseatIcons < slots) delay(POLL_MS)
        withContext(Dispatchers.Main) { model.forceReload() }
        delay(POLL_MS)
        while (!model.isModelLoaded()) delay(POLL_MS)
    }

    /** Deletes the current dock rows and inserts [config] in one transaction on the model thread. */
    private fun writeDock(config: DockConfig): ImportResult {
        val db = model.modelDbController
        val profileId = UserCache.INSTANCE.get(context).getSerialNumberForUser(Process.myUserHandle())
        val drawerIcon = GraphicsUtils.flattenBitmap(DrawerButton.icon(context).icon)
        val slotsPerPage = config.columns * config.rows
        val notInstalled = mutableListOf<String>()
        var placed = 0

        /** Inserts one slot or folder child; returns the new id or -1 when the slot is unusable. */
        fun insert(slot: DockSlot, container: Int, screen: Int, x: Int, y: Int, rank: Int): Int {
            val values = ContentValues().apply {
                put(Favorites.CONTAINER, container)
                put(Favorites.SCREEN, screen)
                put(Favorites.CELLX, x)
                put(Favorites.CELLY, y)
                put(Favorites.RANK, rank)
                put(Favorites.SPANX, 1)
                put(Favorites.SPANY, 1)
                put(Favorites.APPWIDGET_ID, -1)
                put(Favorites.APPWIDGET_SOURCE, -1)
                put(Favorites.OPTIONS, 0)
                put(Favorites.PROFILE_ID, profileId)
                put(Favorites.TITLE, slot.title ?: "")
            }
            when (slot.type) {
                SlotType.FOLDER -> values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER)
                SlotType.DRAWER -> {
                    values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_SHORTCUT)
                    values.put(Favorites.INTENT, DrawerButton.intentUri())
                    values.put(Favorites.ICON, drawerIcon)
                }
                SlotType.APP -> {
                    val cn = slot.component?.let(ComponentName::unflattenFromString) ?: return -1
                    values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    values.put(Favorites.INTENT, AppInfo.makeLaunchIntent(cn).toUri(0))
                    if (!isInstalled(cn)) {
                        // Restored items survive loading without their package and show greyed out
                        values.put(
                            Favorites.RESTORED,
                            WorkspaceItemInfo.FLAG_RESTORED_ICON or WorkspaceItemInfo.FLAG_RESTORE_STARTED,
                        )
                        slot.icon?.let { values.put(Favorites.ICON, Base64.decode(it, Base64.DEFAULT)) }
                        notInstalled += slot.title ?: cn.packageName
                    }
                }
            }
            val id = db.generateNewItemId()
            values.put(Favorites._ID, id)
            return if (db.insert(values) >= 0) id else -1
        }

        db.newTransaction().use { transaction ->
            db.delete(
                "${Favorites.CONTAINER} IN (SELECT ${Favorites._ID} FROM ${Favorites.TABLE_NAME} " +
                    "WHERE ${Favorites.CONTAINER} = ${Favorites.CONTAINER_HOTSEAT} " +
                    "AND ${Favorites.ITEM_TYPE} = ${Favorites.ITEM_TYPE_FOLDER})",
                null,
            )
            db.delete("${Favorites.CONTAINER} = ${Favorites.CONTAINER_HOTSEAT}", null)

            for (slot in config.slots) {
                if (slot.page !in 0 until config.pages || slot.x !in 0 until config.columns ||
                    slot.y !in 0 until config.rows
                ) {
                    continue
                }
                val screen = slot.page * slotsPerPage + slot.y * config.columns + slot.x
                val id = insert(slot, Favorites.CONTAINER_HOTSEAT, screen, slot.x, slot.y, 0)
                if (id < 0) continue
                placed++
                if (slot.type == SlotType.FOLDER) {
                    slot.items.forEachIndexed { rank, child ->
                        insert(child, id, 0, 0, 0, rank)
                    }
                }
            }
            transaction.commit()
        }
        return ImportResult(placed, notInstalled)
    }

    /** Whether the app's package is installed and enabled for this user. */
    private fun isInstalled(cn: ComponentName): Boolean = try {
        launcherApps.isPackageEnabled(cn.packageName, Process.myUserHandle())
    } catch (e: Exception) {
        false
    }

    private class Row(
        val id: Int,
        val title: String?,
        val intent: String?,
        val screen: Int,
        val itemType: Int,
        val icon: ByteArray?,
    )

    /** Rows matching [selection], ordered by [order]. */
    private fun query(db: ModelDbController, selection: String, order: String): List<Row> =
        db.query(
            arrayOf(Favorites._ID, Favorites.TITLE, Favorites.INTENT, Favorites.SCREEN, Favorites.ITEM_TYPE, Favorites.ICON),
            selection,
            null,
            order,
        ).use { c -> generateSequence { if (c.moveToNext()) c.toRow() else null }.toList() }

    /** Maps the projection used by [query] to a [Row]. */
    private fun Cursor.toRow() = Row(
        id = getInt(0),
        title = getString(1),
        intent = getString(2),
        screen = getInt(3),
        itemType = getInt(4),
        icon = getBlob(5),
    )

    /** Converts a database row to a slot, or null for item types a dock config cannot hold. */
    private fun toSlot(row: Row, items: List<DockSlot>): DockSlot? {
        val intent = row.intent?.let {
            try {
                Intent.parseUri(it, 0)
            } catch (e: URISyntaxException) {
                null
            }
        }
        return when {
            row.itemType == Favorites.ITEM_TYPE_FOLDER ->
                DockSlot(SlotType.FOLDER, title = row.title, items = items)
            DrawerButton.isDrawerButton(intent) -> DockSlot(SlotType.DRAWER, title = row.title)
            row.itemType == Favorites.ITEM_TYPE_APPLICATION && intent?.component != null -> {
                val cn = intent.component!!
                DockSlot(SlotType.APP, component = cn.flattenToString(), title = row.title, icon = iconFor(cn, row.icon))
            }
            else -> null
        }
    }

    /** Base64 PNG for [cn]: the stored icon when valid, otherwise the app's current icon. */
    private fun iconFor(cn: ComponentName, stored: ByteArray?): String? {
        val png = stored?.takeIf { BitmapFactory.decodeByteArray(it, 0, it.size) != null } ?: try {
            val bitmap = context.packageManager.getActivityIcon(cn).toBitmap(EXPORT_ICON_PX, EXPORT_ICON_PX)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            return null
        }
        return Base64.encodeToString(png, Base64.NO_WRAP)
    }

    companion object {
        private const val GRID_TIMEOUT_MS = 20_000L
        private const val POLL_MS = 100L
        private const val EXPORT_ICON_PX = 192
    }
}
