package com.android.launcher3.util

import android.content.ContentValues
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_SOURCE
import com.android.launcher3.LauncherSettings.Favorites.CELLX
import com.android.launcher3.LauncherSettings.Favorites.CELLY
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.INTENT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID
import com.android.launcher3.LauncherSettings.Favorites.RESTORED
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.LauncherSettings.Favorites.TITLE
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelDbController

object ModelTestExtensions {
    /** Clears and reloads Launcher db to cleanup the workspace */
    fun LauncherModel.clearModelDb() {
        // Load the model once so that there is no pending migration:
        loadModelSync()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            modelDbController.run {
                tryMigrateDB(null /* restoreEventLogger */)
                createEmptyDB()
                clearEmptyDbFlag()
            }
        }
        // Reload model
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { forceReload() }
        loadModelSync()
    }

    /** Loads the model in memory synchronously */
    fun LauncherModel.loadModelSync() {
        val mockCb: BgDataModel.Callbacks = object : BgDataModel.Callbacks {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { addCallbacksAndLoad(mockCb) }
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {}
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { removeCallbacks(mockCb) }
    }

    /** Adds and commits a new item to Launcher.db */
    fun LauncherModel.addItem(
        title: String = "LauncherTestApp",
        intent: String =
            "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.google.android.apps.nexuslauncher.tests/com.android.launcher3.testcomponent.BaseTestingActivity;launchFlags=0x10200000;end",
        type: Int = ITEM_TYPE_APPLICATION,
        restoreFlags: Int = 0,
        screen: Int = 0,
        container: Int = CONTAINER_DESKTOP,
        x: Int,
        y: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        id: Int = 0,
        profileId: Int = 0,
        tableName: String = Favorites.TABLE_NAME,
        appWidgetId: Int = -1,
        appWidgetSource: Int = -1,
        appWidgetProvider: String? = null
    ) {
        loadModelSync()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            val controller: ModelDbController = modelDbController
            controller.tryMigrateDB(null /* restoreEventLogger */)
            modelDbController.newTransaction().use { transaction ->
                val values =
                    ContentValues().apply {
                        values[_ID] = id
                        values[TITLE] = title
                        values[PROFILE_ID] = profileId
                        values[CONTAINER] = container
                        values[SCREEN] = screen
                        values[CELLX] = x
                        values[CELLY] = y
                        values[SPANX] = spanX
                        values[SPANY] = spanY
                        values[ITEM_TYPE] = type
                        values[RESTORED] = restoreFlags
                        values[INTENT] = intent
                        values[APPWIDGET_ID] = appWidgetId
                        values[APPWIDGET_SOURCE] = appWidgetSource
                        values[APPWIDGET_PROVIDER] = appWidgetProvider
                    }
                // Migrate any previous data so that the DB state is correct
                controller.insert(tableName, values)
                transaction.commit()
            }
        }
    }
}
