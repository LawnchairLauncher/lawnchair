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
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.google.gson.Gson

/**
 * Manages widget stack persistence and queries
 */
object WidgetStackManager {
    private const val TAG = "WidgetStackManager"
    private val gson: Gson = Gson()

    // Store pending stack info for widgets waiting for permission
    // Key: appWidgetId, Value: WidgetStackInfo
    private val pendingStackInfo = mutableMapOf<Int, WidgetStackInfo>()

    // Store pending stack info by provider component name for widgets that haven't been allocated yet
    // This is used when addPendingItem is called before widget ID is allocated
    // Key: ComponentName.toString(), Value: WidgetStackInfo
    private val pendingStackInfoByProvider = mutableMapOf<String, WidgetStackInfo>()

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
        pendingStackInfoByProvider[provider.toString()] = stackInfo
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
        val info = pendingStackInfoByProvider.remove(provider.toString())
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
        pendingStackInfoByProvider.remove(provider.toString())
        Log.d(TAG, "Cleared pending stack info for provider $provider")
    }

    /**
     * Stores stack information in the database (CREATE/UPDATE operation)
     * IMPORTANT: This only updates WIDGET_STACK_ID and WIDGET_STACK_DATA columns.
     * It does NOT modify other widget properties to avoid corrupting widget data.
     *
     * This method ensures atomic updates and validates widget existence before updating.
     */
    @JvmStatic
    fun saveStack(db: SQLiteDatabase, stackInfo: WidgetStackInfo) {
        if (stackInfo.widgetIds.isEmpty()) {
            Log.w(TAG, "Cannot save stack ${stackInfo.stackId}: no widgets in stack")
            return
        }

        try {
            // Convert stack info to JSON
            val stackJson = gson.toJson(stackInfo)

            // Use a transaction to ensure all updates are atomic
            db.beginTransaction()
            try {
                var successCount = 0
                val missingWidgetIds = mutableListOf<Int>()

                // Update all widgets in the stack to reference the stack ID and store the data
                // Only update WIDGET_STACK_ID and WIDGET_STACK_DATA to avoid overwriting other fields
                stackInfo.widgetIds.forEach { widgetId ->
                    // First, verify the widget exists in the database
                    val widgetExists = db.query(
                        LauncherSettings.Favorites.TABLE_NAME,
                        arrayOf(LauncherSettings.Favorites._ID),
                        "${LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                        arrayOf(widgetId.toString()),
                        null,
                        null,
                        null,
                        "1",
                    ).use { it.moveToFirst() }

                    if (widgetExists) {
                        val values = ContentValues().apply {
                            put(LauncherSettings.Favorites.WIDGET_STACK_ID, stackInfo.stackId)
                            // Store the stack data in ALL widgets for redundancy
                            put(LauncherSettings.Favorites.WIDGET_STACK_DATA, stackJson)
                        }
                        val rowsUpdated = db.update(
                            LauncherSettings.Favorites.TABLE_NAME,
                            values,
                            "${LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                            arrayOf(widgetId.toString()),
                        )

                        if (rowsUpdated > 0) {
                            successCount++
                        } else {
                            Log.w(TAG, "Failed to update widget $widgetId in stack ${stackInfo.stackId}")
                            missingWidgetIds.add(widgetId)
                        }
                    } else {
                        Log.w(TAG, "Widget $widgetId not found in database when saving stack ${stackInfo.stackId}")
                        missingWidgetIds.add(widgetId)
                    }
                }

                // Only commit if at least one widget was updated successfully
                // This prevents creating empty or invalid stacks
                if (successCount > 0) {
                    db.setTransactionSuccessful()
                    Log.d(TAG, "Saved stack ${stackInfo.stackId} with $successCount/${stackInfo.widgetIds.size} widgets")
                    if (missingWidgetIds.isNotEmpty()) {
                        Log.w(TAG, "Stack ${stackInfo.stackId} missing ${missingWidgetIds.size} widgets: $missingWidgetIds")
                    }
                } else {
                    Log.e(TAG, "Failed to save stack ${stackInfo.stackId}: no widgets could be updated")
                }
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving widget stack ${stackInfo.stackId}", e)
            throw e // Re-throw to allow callers to handle the error
        }
    }

    /**
     * Loads stack information from the database (READ operation)
     * Looks for any widget with this stackId that has the data.
     * Returns null if stack not found or corrupted.
     */
    fun loadStack(db: SQLiteDatabase, stackId: Long): WidgetStackInfo? {
        return try {
            // Query for any widget with this stackId that has stack data
            val cursor = db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                arrayOf(LauncherSettings.Favorites.WIDGET_STACK_DATA),
                "${LauncherSettings.Favorites.WIDGET_STACK_ID} = ? AND ${LauncherSettings.Favorites.WIDGET_STACK_DATA} IS NOT NULL",
                arrayOf(stackId.toString()),
                null,
                null,
                null,
                "1", // Limit to 1 result (all widgets in stack have same data)
            )

            cursor.use {
                if (it.moveToFirst()) {
                    val stackJson = it.getString(0)
                    if (stackJson != null && stackJson.isNotEmpty()) {
                        try {
                            val stackInfo = gson.fromJson(stackJson, WidgetStackInfo::class.java)
                            // Validate loaded stack info
                            if (stackInfo.stackId == stackId && stackInfo.widgetIds.isNotEmpty()) {
                                Log.d(TAG, "Loaded stack $stackId with ${stackInfo.widgetIds.size} widgets")
                                return stackInfo
                            } else {
                                Log.w(TAG, "Invalid stack data for stackId: $stackId (stackId mismatch or empty widgetIds)")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing stack JSON for stackId: $stackId", e)
                            null
                        }
                    } else {
                        Log.w(TAG, "Stack data is null or empty for stackId: $stackId")
                        null
                    }
                } else {
                    Log.d(TAG, "No widget found with stackId: $stackId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading widget stack for stackId: $stackId", e)
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
