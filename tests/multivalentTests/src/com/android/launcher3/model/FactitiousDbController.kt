package com.android.launcher3.model

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader

private val All_COLUMNS =
    arrayOf(
        "_id",
        "title",
        "intent",
        "container",
        "screen",
        "cellX",
        "cellY",
        "spanX",
        "spanY",
        "itemType",
        "appWidgetId",
        "icon",
        "appWidgetProvider",
        "modified",
        "restored",
        "profileId",
        "rank",
        "options",
        "appWidgetSource"
    )

class FactitiousDbController(context: Context, insertFile: String) : ModelDbController(context) {

    val inMemoryDb: SQLiteDatabase by lazy {
        SQLiteDatabase.createInMemory(SQLiteDatabase.OpenParams.Builder().build()).also { db ->
            BufferedReader(
                    InputStreamReader(
                        InstrumentationRegistry.getInstrumentation().context.assets.open(insertFile)
                    )
                )
                .lines()
                .forEach { sqlStatement -> db.execSQL(sqlStatement) }
        }
    }

    override fun query(
        table: String,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return inMemoryDb.query(table, All_COLUMNS, selection, selectionArgs, null, null, sortOrder)
    }

    override fun loadDefaultFavoritesIfNecessary() {
        // No-Op
    }
}
