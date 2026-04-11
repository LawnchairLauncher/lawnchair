/*
 * Copyright (C) 2025 Lawnchair
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

package app.lawnchair.widget

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Manages widget stack persistence and queries
 */
object WidgetStackManager {
    private const val TAG = "WidgetStackManager"

    /**
     * Explicit JSON adapter so [WidgetStackInfo.widgetIds] order round-trips reliably.
     * Gson's default reflection serializer for Kotlin data classes can omit or mishandle
     * list fields, which made reordered stacks fall back to cursor order after restart.
     */
    private val widgetStackInfoTypeAdapter = object : JsonSerializer<WidgetStackInfo>, JsonDeserializer<WidgetStackInfo> {
        override fun serialize(
            src: WidgetStackInfo,
            @Suppress("UNUSED_PARAMETER") typeOfSrc: Type,
            @Suppress("UNUSED_PARAMETER") context: JsonSerializationContext,
        ): JsonElement {
            val o = JsonObject()
            o.addProperty("stackId", src.stackId)
            val ids = JsonArray()
            src.widgetIds.forEach { id -> ids.add(JsonPrimitive(id)) }
            o.add("widgetIds", ids)
            o.addProperty("currentIndex", src.currentIndex)
            o.addProperty("autoRotate", src.autoRotate)
            o.addProperty("container", src.container)
            o.addProperty("screenId", src.screenId)
            o.addProperty("cellX", src.cellX)
            o.addProperty("cellY", src.cellY)
            o.addProperty("spanX", src.spanX)
            o.addProperty("spanY", src.spanY)
            return o
        }

        override fun deserialize(
            json: JsonElement,
            @Suppress("UNUSED_PARAMETER") typeOfT: Type,
            @Suppress("UNUSED_PARAMETER") context: JsonDeserializationContext,
        ): WidgetStackInfo {
            val obj = json.asJsonObject
            return WidgetStackInfo(
                stackId = obj.get("stackId").asLong,
                widgetIds = obj.getAsJsonArray("widgetIds").map { it.asInt },
                currentIndex = obj.get("currentIndex")?.asInt ?: 0,
                autoRotate = obj.get("autoRotate")?.asBoolean ?: false,
                container = obj.get("container")?.asInt
                    ?: LauncherSettings.Favorites.CONTAINER_DESKTOP,
                screenId = obj.get("screenId")?.asInt ?: 0,
                cellX = obj.get("cellX")?.asInt ?: 0,
                cellY = obj.get("cellY")?.asInt ?: 0,
                spanX = obj.get("spanX")?.asInt ?: 2,
                spanY = obj.get("spanY")?.asInt ?: 2,
            )
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(WidgetStackInfo::class.java, widgetStackInfoTypeAdapter)
        .create()

    // Thread-safe: accessed from UI, activity results, and model paths.
    private val pendingStackInfo = ConcurrentHashMap<Int, WidgetStackInfo>()

    // Per-provider queue: same provider can have multiple pendings (e.g. two stack adds) before IDs exist.
    // FIFO — first stored pending matches the first completed bind for that provider.
    private val pendingStackInfoByProvider =
        ConcurrentHashMap<String, ConcurrentLinkedDeque<WidgetStackInfo>>()

    /**
     * While the widget tray is open from [WidgetStackDialog], holds stack UI state so we can resume
     * the dialog if the user dismisses the picker without choosing a widget.
     */
    private val stackDialogPickerLock = Any()
    private var suspendedStackDialogInfo: WidgetStackInfo? = null
    private var suspendedStackDialogIsEditing: Boolean = false

    /**
     * Call before closing the stack sheet to open [WidgetsFullSheet]. Paired with
     * [takeSuspendedStackDialogForPickerSession] on pick (after validating the selection) or on
     * sheet close (resume dialog).
     */
    @JvmStatic
    fun prepareStackDialogForWidgetPicker(info: WidgetStackInfo, isEditing: Boolean) {
        synchronized(stackDialogPickerLock) {
            suspendedStackDialogInfo = info
            suspendedStackDialogIsEditing = isEditing
        }
    }

    /**
     * Removes and returns suspended stack dialog state if present. Invoke from the widget pick
     * callback only after the selection is valid, or from the picker's close listener to resume
     * the stack dialog when the user cancelled.
     */
    @JvmStatic
    fun takeSuspendedStackDialogForPickerSession(): Pair<WidgetStackInfo, Boolean>? {
        synchronized(stackDialogPickerLock) {
            val info = suspendedStackDialogInfo ?: return null
            suspendedStackDialogInfo = null
            return Pair(info, suspendedStackDialogIsEditing)
        }
    }

    /**
     * Stores pending stack info for a widget that's waiting for permission
     * Use this when you know the widget ID
     */
    @JvmStatic
    fun storePendingStackInfo(appWidgetId: Int, stackInfo: WidgetStackInfo) {
        pendingStackInfo[appWidgetId] = stackInfo
        Log.d(TAG, "Stored pending stack info for widget $appWidgetId, stackId: ${stackInfo.stackId}")
    }

    /**
     * Stores pending stack info by provider component name
     * Use this when widget ID hasn't been allocated yet
     */
    @JvmStatic
    fun storePendingStackInfoByProvider(provider: android.content.ComponentName, stackInfo: WidgetStackInfo) {
        val key = provider.flattenToString()
        pendingStackInfoByProvider
            .computeIfAbsent(key) { ConcurrentLinkedDeque() }
            .addLast(stackInfo)
        Log.d(TAG, "Stored pending stack info for provider $provider, stackId: ${stackInfo.stackId}")
    }

    /**
     * Gets and removes pending stack info for a widget
     */
    @JvmStatic
    fun getAndRemovePendingStackInfo(appWidgetId: Int): WidgetStackInfo? {
        val info = pendingStackInfo.remove(appWidgetId)
        if (info != null) {
            Log.d(TAG, "Retrieved pending stack info for widget $appWidgetId, stackId: ${info.stackId}")
        }
        return info
    }

    /**
     * Gets and removes pending stack info by provider component name
     * Use this when widget ID is allocated and we need to transfer the stack info
     */
    @JvmStatic
    fun getAndRemovePendingStackInfoByProvider(provider: android.content.ComponentName): WidgetStackInfo? {
        val key = provider.flattenToString()
        val deque = pendingStackInfoByProvider[key] ?: return null
        val info = deque.pollFirst()
        if (deque.isEmpty()) {
            pendingStackInfoByProvider.remove(key, deque)
        }
        if (info != null) {
            Log.d(TAG, "Retrieved pending stack info for provider $provider, stackId: ${info.stackId}")
        }
        return info
    }

    /**
     * Transfers pending stack info from provider-based storage to widget ID-based storage
     * Call this when widget ID is allocated
     */
    @JvmStatic
    fun transferPendingStackInfo(provider: android.content.ComponentName, appWidgetId: Int) {
        val stackInfo = getAndRemovePendingStackInfoByProvider(provider)
        if (stackInfo != null) {
            storePendingStackInfo(appWidgetId, stackInfo)
            Log.d(TAG, "Transferred pending stack info from provider $provider to widget $appWidgetId")
        }
    }

    /**
     * Clears pending stack info for a widget (e.g., if permission was denied)
     */
    @JvmStatic
    fun clearPendingStackInfo(appWidgetId: Int) {
        pendingStackInfo.remove(appWidgetId)
        Log.d(TAG, "Cleared pending stack info for widget $appWidgetId")
    }

    /**
     * Clears pending stack info by provider (e.g., if permission was denied)
     */
    @JvmStatic
    fun clearPendingStackInfoByProvider(provider: android.content.ComponentName) {
        val key = provider.flattenToString()
        val deque = pendingStackInfoByProvider[key] ?: return
        // Drop one pending (typically the bind that was just cancelled); last = most recently queued.
        deque.pollLast()
        if (deque.isEmpty()) {
            pendingStackInfoByProvider.remove(key, deque)
        }
        Log.d(TAG, "Cleared pending stack info entry for provider $provider")
    }

    /**
     * Stores stack information in the database (CREATE/UPDATE operation)
     * IMPORTANT: This only updates WIDGET_STACK_ID and WIDGET_STACK_DATA columns.
     * It does NOT modify other widget properties to avoid corrupting widget data.
     *
     * Row updates run in a single SQLite transaction so a crash mid-save cannot leave some
     * members on the new JSON and others on stale data.
     */
    @JvmStatic
    fun saveStack(db: SQLiteDatabase, stackInfo: WidgetStackInfo) {
        if (stackInfo.widgetIds.isEmpty()) {
            Log.w(TAG, "Cannot save stack ${stackInfo.stackId}: no widgets in stack")
            return
        }

        val stackJson = gson.toJson(stackInfo)
        var successCount = 0

        SQLiteTransaction(db).use { transaction ->
            val dbx = transaction.db
            stackInfo.widgetIds.forEach { widgetId ->
                val values = ContentValues().apply {
                    put(LauncherSettings.Favorites.WIDGET_STACK_ID, stackInfo.stackId)
                    put(LauncherSettings.Favorites.WIDGET_STACK_DATA, stackJson)
                }
                val rowsUpdated = dbx.update(
                    LauncherSettings.Favorites.TABLE_NAME,
                    values,
                    "${LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                    arrayOf(widgetId.toString()),
                )
                if (rowsUpdated > 0) {
                    successCount++
                } else {
                    Log.w(TAG, "Widget $widgetId not found in DB when saving stack ${stackInfo.stackId}")
                }
            }
            transaction.commit()
        }

        if (successCount > 0) {
            Log.d(TAG, "Saved stack ${stackInfo.stackId}: $successCount/${stackInfo.widgetIds.size} widgets")
        } else {
            Log.e(TAG, "Failed to save stack ${stackInfo.stackId}: no widgets updated")
        }
    }

    /**
     * Loads stack information from the database (READ operation)
     * Looks for any widget with this stackId that has the data.
     * Returns null if stack not found or corrupted.
     */
    @JvmStatic
    fun loadStack(db: SQLiteDatabase, stackId: Long): WidgetStackInfo? {
        return try {
            // Strategy 1: load the full JSON blob stored alongside any widget in the stack
            db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(LauncherSettings.Favorites.WIDGET_STACK_DATA),
                "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ? AND ${LauncherSettings.Favorites.WIDGET_STACK_DATA} IS NOT NULL",
                arrayOf(stackId.toString()),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val json = cursor.getString(0)
                    if (!json.isNullOrEmpty()) {
                        try {
                            val info = gson.fromJson(json, WidgetStackInfo::class.java)
                            if (info.stackId == stackId && info.widgetIds.isNotEmpty()) {
                                return info
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing stack JSON for $stackId", e)
                        }
                    }
                }
            }

            // Strategy 2: JSON missing/corrupt — reconstruct from WIDGET_STACK_ID column
            val widgetIds = mutableListOf<Int>()
            var container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            var screenId = 0
            var cellX = 0
            var cellY = 0
            var spanX = 2
            var spanY = 2
            db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(
                    LauncherSettings.Favorites.APPWIDGET_ID,
                    LauncherSettings.Favorites.CONTAINER,
                    LauncherSettings.Favorites.SCREEN,
                    LauncherSettings.Favorites.CELLX,
                    LauncherSettings.Favorites.CELLY,
                    LauncherSettings.Favorites.SPANX,
                    LauncherSettings.Favorites.SPANY,
                ),
                "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ?",
                arrayOf(stackId.toString()),
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    widgetIds.add(cursor.getInt(0))
                    if (widgetIds.size == 1) {
                        container = cursor.getInt(1)
                        screenId = cursor.getInt(2)
                        cellX = cursor.getInt(3)
                        cellY = cursor.getInt(4)
                        spanX = cursor.getInt(5)
                        spanY = cursor.getInt(6)
                    }
                }
            }

            if (widgetIds.isEmpty()) {
                Log.d(TAG, "No widgets found for stackId $stackId")
                return null
            }

            val reconstructed = WidgetStackInfo(
                stackId = stackId,
                widgetIds = widgetIds,
                container = container,
                screenId = screenId,
                cellX = cellX,
                cellY = cellY,
                spanX = spanX,
                spanY = spanY,
            )
            Log.d(TAG, "Reconstructed stack $stackId with ${widgetIds.size} widgets from WIDGET_STACK_ID")

            // Persist the reconstruction so future loads hit Strategy 1
            try {
                saveStack(db, reconstructed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist reconstructed stack $stackId; will reconstruct on next load", e)
            }

            reconstructed
        } catch (e: Exception) {
            Log.e(TAG, "Error loading widget stack $stackId", e)
            null
        }
    }

    /**
     * Checks if a widget is in a stack
     */
    fun isWidgetInStack(db: SQLiteDatabase, widgetId: Int): Boolean {
        return try {
            val cursor = db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(LauncherSettings.Favorites.WIDGET_STACK_ID),
                "${LauncherSettings.Favorites.APPWIDGET_ID} = ? AND ${LauncherSettings.Favorites.WIDGET_STACK_ID} IS NOT NULL",
                arrayOf(widgetId.toString()),
                null,
                null,
                null,
                "1",
            )

            cursor.use {
                it.moveToFirst() && !it.isNull(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if widget is in stack", e)
            false
        }
    }

    /**
     * Gets the stack ID for a widget
     */
    fun getStackIdForWidget(db: SQLiteDatabase, widgetId: Int): Long? {
        return try {
            val cursor = db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(LauncherSettings.Favorites.WIDGET_STACK_ID),
                "${LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                arrayOf(widgetId.toString()),
                null,
                null,
                null,
                "1",
            )

            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) {
                    it.getLong(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stack ID for widget", e)
            null
        }
    }

    /**
     * Removes a widget from a stack (UPDATE operation - removes widget from stack)
     * This clears the widget's stack reference but doesn't delete the widget itself.
     */
    fun removeWidgetFromStack(db: SQLiteDatabase, widgetId: Int) {
        try {
            val values = ContentValues().apply {
                putNull(LauncherSettings.Favorites.WIDGET_STACK_ID)
                putNull(LauncherSettings.Favorites.WIDGET_STACK_DATA)
            }
            val rowsUpdated = db.update(
                LauncherSettings.Favorites.TABLE_NAME,
                values,
                "${LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                arrayOf(widgetId.toString()),
            )
            if (rowsUpdated > 0) {
                Log.d(TAG, "Removed widget $widgetId from stack")
            } else {
                Log.w(TAG, "Widget $widgetId not found when removing from stack")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing widget $widgetId from stack", e)
            throw e
        }
    }

    /**
     * Deletes a stack (DELETE operation - removes stack references from all widgets)
     * This clears all stack references but doesn't delete the widgets themselves.
     */
    fun deleteStack(db: SQLiteDatabase, stackId: Long) {
        try {
            // First, get the list of widgets in the stack for logging
            val widgetIds = db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(LauncherSettings.Favorites.APPWIDGET_ID),
                "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ?",
                arrayOf(stackId.toString()),
                null,
                null,
                null,
            ).use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext()) {
                    ids.add(cursor.getInt(0))
                }
                ids
            }

            val values = ContentValues().apply {
                putNull(LauncherSettings.Favorites.WIDGET_STACK_ID)
                putNull(LauncherSettings.Favorites.WIDGET_STACK_DATA)
            }
            val rowsUpdated = db.update(
                LauncherSettings.Favorites.TABLE_NAME,
                values,
                "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ?",
                arrayOf(stackId.toString()),
            )

            if (rowsUpdated > 0) {
                Log.d(TAG, "Deleted stack $stackId (removed references from $rowsUpdated widgets)")
            } else {
                Log.w(TAG, "No widgets found with stackId: $stackId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting widget stack $stackId", e)
            throw e
        }
    }
}
