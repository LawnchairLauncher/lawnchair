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
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Intent
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites.CELLX
import com.android.launcher3.LauncherSettings.Favorites.CELLY
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ContentWriter
import java.util.Objects

class DbEntry : ItemInfo(), Comparable<DbEntry> {
    @JvmField var mIntent: String? = null
    @JvmField var mProvider: String? = null
    @JvmField var mFolderItems: MutableMap<String, Set<Int>> = HashMap()

    /** Id of the specific widget. */
    @JvmField var appWidgetId: Int = NO_ID

    /** Comparator according to the reading order */
    override fun compareTo(other: DbEntry): Int {
        if (screenId != other.screenId) {
            return screenId.compareTo(other.screenId)
        }
        if (cellY != other.cellY) {
            return cellY.compareTo(other.cellY)
        }
        return cellX.compareTo(other.cellX)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DbEntry) return false
        return getEntryMigrationId() == other.getEntryMigrationId()
    }

    override fun hashCode(): Int = Objects.hash(getEntryMigrationId())

    /**
     * Puts the updated DbEntry values into ContentValues which we then use to insert the entry to
     * the DB.
     */
    fun updateContentValues(values: ContentValues) =
        values.apply {
            put(SCREEN, screenId)
            put(CELLX, cellX)
            put(CELLY, cellY)
            put(SPANX, spanX)
            put(SPANY, spanY)
        }

    override fun writeToValues(writer: ContentWriter) {
        super.writeToValues(writer)
        writer.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId)
    }

    override fun readFromValues(values: ContentValues) {
        super.readFromValues(values)
        appWidgetId = values.getAsInteger(LauncherSettings.Favorites.APPWIDGET_ID)
    }

    /**
     * This id is not used in the DB is only used while doing the migration and it identifies an
     * entry on each workspace. For example two calculator icons would have the same migration id
     * even thought they have different database ids.
     */
    private fun getEntryMigrationId(): String? {
        when (itemType) {
            ITEM_TYPE_FOLDER,
            ITEM_TYPE_APP_PAIR -> return getFolderMigrationId()
            ITEM_TYPE_APPWIDGET ->
                // mProvider is the app the widget belongs to and appWidgetId it's the unique
                // is of the widget, we need both because if you remove a widget and then add it
                // again, then it can change and the WidgetProvider would not know the widget.
                return mProvider + appWidgetId
            ITEM_TYPE_APPLICATION -> {
                val intentStr = mIntent?.let { cleanIntentString(it) }
                try {
                    val i = Intent.parseUri(intentStr, 0)
                    return Objects.requireNonNull(i.component).toString()
                } catch (e: Exception) {
                    return intentStr
                }
            }

            else -> return mIntent?.let { cleanIntentString(it) }
        }
    }

    /**
     * This method should return an id that should be the same for two folders containing the same
     * elements.
     */
    private fun getFolderMigrationId(): String =
        mFolderItems.keys
            .map { intentString: String ->
                mFolderItems[intentString]?.size.toString() + cleanIntentString(intentString)
            }
            .sorted()
            .joinToString(",")

    /**
     * This is needed because sourceBounds can change and make the id of two equal items different.
     */
    private fun cleanIntentString(intentStr: String): String {
        try {
            return Intent.parseUri(intentStr, 0).apply { sourceBounds = null }.toURI()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to parse Intent string: $intentStr", e)
            return intentStr
        }
    }

    companion object {
        private const val TAG = "DbEntry"
    }
}
