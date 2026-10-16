package app.lawnchair.omni.experience

import android.content.ContentValues
import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ContentWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** On-demand Home operations. Model reads/writes run on Launcher3's model executor, never Main. */
internal object HomeLayoutController {
    suspend fun execute(context: Context, name: String, payload: JsonObject, dryRun: Boolean, checkAccess: () -> Unit): JsonObject {
        val visible = OmniAppInventory.get(context).visible().map { it.toComponentKey().toString() }.toSet()
        val app = withContext(Dispatchers.Main) {
            check(LawnchairLauncher.instance != null) { "home_unavailable" }
            LauncherAppState.getInstance(context)
        }
        val model = app.model
        return withModel(model) { data, active ->
            checkAccess()
            check(active()) { "deadline_exceeded" }
            val idp = app.invariantDeviceProfile
            val settings = PreferenceManager.getInstance(context)
            check(settings.workspaceRows.get() == idp.numRows && settings.workspaceColumns.get() == idp.numColumns) { "model_loading" }
            val items = data.itemsIdMap.filter { it.container == Favorites.CONTAINER_DESKTOP }
            val cells = items.map { item ->
                val key = item.targetComponent?.let { com.android.launcher3.util.ComponentKey(it, item.user).toString() }.orEmpty()
                LayoutCell(
                    item.id,
                    item.screenId,
                    item.cellX,
                    item.cellY,
                    item.spanX,
                    item.spanY,
                    item is WorkspaceItemInfo && item.itemType == Favorites.ITEM_TYPE_APPLICATION && key in visible,
                    key,
                )
            }
            val revision = HomeLayoutPolicy.revision(idp.numRows, idp.numColumns, model.lastLoadId, cells)
            fun integer(key: String): Int = (payload[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("invalid_payload")
            fun text(key: String): String = (payload[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("invalid_payload")
            val screens = data.collectWorkspaceScreens().toArray().toSet()
            if (name == "launcher.get_layout") {
                val offset = if ("offset" in payload) integer("offset") else 0
                val limit = if ("limit" in payload) integer("limit") else 40
                require(offset >= 0 && limit in 1..80)
                val page = cells.sortedBy { it.id }.drop(offset).take(limit)
                buildJsonObject {
                    put("revision", revision)
                    put("rows", idp.numRows)
                    put("columns", idp.numColumns)
                    put("hotseatColumns", idp.numDatabaseHotseatIcons)
                    put("screens", buildJsonArray { screens.sorted().forEach { add(JsonPrimitive(it)) } })
                    put(
                        "items",
                        buildJsonArray {
                            page.forEach { cell ->
                                add(
                                    buildJsonObject {
                                        put("screenId", cell.screenId)
                                        put("cellX", cell.x)
                                        put("cellY", cell.y)
                                        put("spanX", cell.spanX)
                                        put("spanY", cell.spanY)
                                        put("movable", cell.movable)
                                        val item = items.first { it.id == cell.id }
                                        if (item is FolderInfo) {
                                            put("kind", "folder")
                                            put("id", item.id)
                                            put("title", item.title?.toString().orEmpty().take(80))
                                            put(
                                                "visibleAppCount",
                                                item.contents.count { child ->
                                                    child.targetComponent?.let { com.android.launcher3.util.ComponentKey(it, child.user).toString() in visible } == true
                                                },
                                            )
                                        }
                                        // Hidden apps/widgets disclose only occupancy; folders expose no hidden child identities.
                                        if (cell.movable) {
                                            put("id", cell.id)
                                            put("componentKey", cell.identity)
                                            put("label", items.first { it.id == cell.id }.title?.toString().orEmpty().take(200))
                                        }
                                    },
                                )
                            }
                        },
                    )
                    put("total", cells.size)
                    put("nextOffset", if (offset + page.size < cells.size) offset + page.size else -1)
                }
            } else {
                check(text("expectedRevision") == revision) { "stale_layout" }
                if (name == "launcher.create_home_folder") {
                    val title = text("title").trim().also { require(it.isNotBlank() && it.length <= 80) }
                    val ids = (payload["itemIds"] as? JsonArray ?: error("invalid_payload")).map {
                        (it as? JsonPrimitive)?.takeUnless { value -> value.isString }?.intOrNull ?: error("invalid_payload")
                    }
                    val members = HomeLayoutPolicy.folderMembers(cells, ids)
                    val anchor = members.first()
                    HomeLayoutPolicy.validate(
                        idp.numRows,
                        idp.numColumns,
                        cells.filter { it.id !in ids.drop(1) },
                        listOf(LayoutMove(anchor.id, anchor.screenId, anchor.x, anchor.y)),
                        screens,
                    )
                    var folderId = -1
                    if (!dryRun) {
                        checkAccess()
                        check(active()) { "deadline_exceeded" }
                        val folder = FolderInfo().apply {
                            id = model.modelDbController.generateNewItemId()
                            this.title = title
                            container = Favorites.CONTAINER_DESKTOP
                            screenId = anchor.screenId
                            cellX = anchor.x
                            cellY = anchor.y
                            spanX = 1
                            spanY = 1
                        }
                        val writer = ContentWriter(context)
                        folder.onAddToDatabase(writer)
                        writer.put(Favorites._ID, folder.id)
                        val columns = idp.numFolderColumns[0].coerceAtLeast(1)
                        model.modelDbController.newTransaction().use { transaction ->
                            check(model.modelDbController.insert(Favorites.TABLE_NAME, writer.getValues(context)) >= 0) { "storage_failed" }
                            ids.forEachIndexed { index, id ->
                                checkAccess()
                                val values = ContentValues().apply {
                                    put(Favorites.CONTAINER, folder.id)
                                    put(Favorites.SCREEN, 0)
                                    put(Favorites.CELLX, index % columns)
                                    put(Favorites.CELLY, index / columns)
                                    put(Favorites.RANK, index)
                                }
                                check(model.modelDbController.update(Favorites.TABLE_NAME, values, "_id = ?", arrayOf(id.toString())) == 1) { "stale_layout" }
                            }
                            check(active()) { "deadline_exceeded" }
                            checkAccess()
                            transaction.commit()
                        }
                        folderId = folder.id
                        model.forceReload()
                    }
                    buildJsonObject {
                        put("folderId", folderId)
                        put("title", title)
                        put("memberCount", members.size)
                        put("reloadScheduled", !dryRun)
                    }
                } else if (name == "launcher.move_items") {
                    val input = payload["moves"] as? JsonArray ?: error("invalid_payload")
                    val moves = input.map { raw ->
                        val move = raw as? JsonObject ?: error("invalid_payload")
                        require(move.keys == setOf("id", "screenId", "cellX", "cellY"))
                        fun number(key: String) = (move[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("invalid_payload")
                        LayoutMove(number("id"), number("screenId"), number("cellX"), number("cellY"))
                    }
                    HomeLayoutPolicy.validate(idp.numRows, idp.numColumns, cells, moves, screens)
                    if (!dryRun) {
                        checkAccess()
                        check(active()) { "deadline_exceeded" }
                        // ModelWriter queues writes and cannot report transaction failure. Use its existing
                        // DB controller on the same model thread to acknowledge only a committed atomic batch.
                        model.modelDbController.newTransaction().use { transaction ->
                            moves.forEach { move ->
                                checkAccess()
                                val values = ContentValues().apply {
                                    put(Favorites.SCREEN, move.screenId)
                                    put(Favorites.CELLX, move.x)
                                    put(Favorites.CELLY, move.y)
                                }
                                check(model.modelDbController.update(Favorites.TABLE_NAME, values, "_id = ?", arrayOf(move.id.toString())) == 1) { "stale_layout" }
                            }
                            check(active()) { "deadline_exceeded" }
                            checkAccess()
                            transaction.commit()
                        }
                        moves.forEach { move ->
                            items.first { it.id == move.id }.apply {
                                screenId = move.screenId
                                cellX = move.x
                                cellY = move.y
                            }
                        }
                        model.forceReload()
                    }
                    buildJsonObject {
                        put("moved", if (dryRun) 0 else moves.size)
                        put("validated", moves.size)
                        put("reloadScheduled", !dryRun)
                    }
                } else {
                    require(name == "launcher.set_grid")
                    val rows = integer("rows")
                    val columns = integer("columns")
                    val prefs = PreferenceManager.getInstance(context)
                    val max = if (prefs.workspaceIncreaseMaxGridSize.get()) 20 else 10
                    require(rows in 3..max && columns in 3..max)
                    if (!dryRun) {
                        checkAccess()
                        check(active()) { "deadline_exceeded" }
                        // Commit both dimensions before the standard IDP migration/reload is scheduled.
                        check(prefs.sp.edit().putInt("pref_workspaceRows", rows).putInt("pref_workspaceColumns", columns).commit()) { "storage_failed" }
                        prefs.workspaceRows.invalidate()
                        prefs.workspaceColumns.invalidate()
                        idp.onPreferencesChanged(context)
                    }
                    buildJsonObject {
                        put("rows", rows)
                        put("columns", columns)
                        put("reloadScheduled", !dryRun)
                    }
                }
            }
        }
    }

    /** Late queued work is discarded after cancellation/timeout before any transaction is started. */
    private suspend fun <T> withModel(model: LauncherModel, block: (BgDataModel, () -> Boolean) -> T): T {
        val result = CompletableDeferred<T>()
        try {
            model.loadAsync { data ->
                if (!result.isActive) return@loadAsync
                if (data == null) {
                    result.completeExceptionally(IllegalStateException("model_loading"))
                } else {
                    runCatching { synchronized(data) { block(data) { result.isActive } } }
                        .fold(result::complete, result::completeExceptionally)
                }
            }
            return withTimeout(5_000) { result.await() }
        } finally {
            result.cancel()
        }
    }
}
