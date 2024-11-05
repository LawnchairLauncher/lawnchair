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

package com.android.launcher3.model.gridmigration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.celllayout.board.CellLayoutBoard

class MockSet(override val size: Int) : Set<String> {
    override fun contains(element: String): Boolean = true
    override fun containsAll(elements: Collection<String>): Boolean = true
    override fun isEmpty(): Boolean = false
    override fun iterator(): Iterator<String> = listOf<String>().iterator()
}

fun itemListToBoard(itemsArg: List<WorkspaceItem>, boardSize: Point): List<CellLayoutBoard> {
    val items = itemsArg.filter { it.container != Favorites.CONTAINER_HOTSEAT }
    val boardList =
        List(items.maxOf { it.screenId + 1 }) { CellLayoutBoard(boardSize.x, boardSize.y) }
    items.forEach {
        when (it.type) {
            Favorites.ITEM_TYPE_FOLDER,
            Favorites.ITEM_TYPE_APP_PAIR -> throw Exception("Not implemented")
            Favorites.ITEM_TYPE_APPWIDGET ->
                boardList[it.screenId].addWidget(it.x, it.y, it.spanX, it.spanY)
            Favorites.ITEM_TYPE_APPLICATION -> boardList[it.screenId].addIcon(it.x, it.y)
        }
    }
    return boardList
}

fun insertIntoDb(tableName: String, entry: WorkspaceItem, db: SQLiteDatabase) {
    val values = ContentValues()
    values.put(Favorites.SCREEN, entry.screenId)
    values.put(Favorites.CELLX, entry.x)
    values.put(Favorites.CELLY, entry.y)
    values.put(Favorites.SPANX, entry.spanX)
    values.put(Favorites.SPANY, entry.spanY)
    values.put(Favorites.TITLE, entry.title)
    values.put(Favorites.INTENT, entry.intent)
    values.put(Favorites.APPWIDGET_PROVIDER, entry.appWidgetProvider)
    values.put(Favorites.APPWIDGET_ID, entry.appWidgetId)
    values.put(Favorites.CONTAINER, entry.container)
    values.put(Favorites.ITEM_TYPE, entry.type)
    values.put(Favorites._ID, entry.id)
    db.insert(tableName, null, values)
}

fun readDb(tableName: String, db: SQLiteDatabase): List<WorkspaceItem> {
    val result = mutableListOf<WorkspaceItem>()
    val cursor = db.query(tableName, null, null, null, null, null, null)
    val indexCellX: Int = cursor.getColumnIndexOrThrow(Favorites.CELLX)
    val indexCellY: Int = cursor.getColumnIndexOrThrow(Favorites.CELLY)
    val indexSpanX: Int = cursor.getColumnIndexOrThrow(Favorites.SPANX)
    val indexSpanY: Int = cursor.getColumnIndexOrThrow(Favorites.SPANY)
    val indexId: Int = cursor.getColumnIndexOrThrow(Favorites._ID)
    val indexScreen: Int = cursor.getColumnIndexOrThrow(Favorites.SCREEN)
    val indexTitle: Int = cursor.getColumnIndexOrThrow(Favorites.TITLE)
    val indexAppWidgetId: Int = cursor.getColumnIndexOrThrow(Favorites.APPWIDGET_ID)
    val indexWidgetProvider: Int = cursor.getColumnIndexOrThrow(Favorites.APPWIDGET_PROVIDER)
    val indexIntent: Int = cursor.getColumnIndexOrThrow(Favorites.INTENT)
    val indexItemType: Int = cursor.getColumnIndexOrThrow(Favorites.ITEM_TYPE)
    val container: Int = cursor.getColumnIndexOrThrow(Favorites.CONTAINER)
    while (cursor.moveToNext()) {
        result.add(
            WorkspaceItem(
                x = cursor.getInt(indexCellX),
                y = cursor.getInt(indexCellY),
                spanX = cursor.getInt(indexSpanX),
                spanY = cursor.getInt(indexSpanY),
                id = cursor.getInt(indexId),
                screenId = cursor.getInt(indexScreen),
                title = cursor.getString(indexTitle),
                appWidgetId = cursor.getInt(indexAppWidgetId),
                appWidgetProvider = cursor.getString(indexWidgetProvider),
                intent = cursor.getString(indexIntent),
                type = cursor.getInt(indexItemType),
                container = cursor.getInt(container)
            )
        )
    }
    return result
}

data class WorkspaceItem(
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
    val id: Int,
    val screenId: Int,
    val title: String,
    val appWidgetId: Int,
    val appWidgetProvider: String,
    val intent: String,
    val type: Int,
    val container: Int,
)
