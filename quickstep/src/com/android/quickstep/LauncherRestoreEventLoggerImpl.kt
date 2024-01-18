package com.android.quickstep

import android.app.backup.BackupManager
import android.app.backup.BackupRestoreEventLogger
import android.app.backup.BackupRestoreEventLogger.BackupRestoreDataType
import android.app.backup.BackupRestoreEventLogger.BackupRestoreError
import android.content.Context
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger

/**
 * Concrete implementation for wrapper to log Restore event metrics for both success and failure to
 * restore Launcher workspace from a backup. This implementation accesses SystemApis so is only
 * available to QuickStep/NexusLauncher.
 */
class LauncherRestoreEventLoggerImpl(val context: Context) : LauncherRestoreEventLogger() {
    companion object {
        const val TAG = "LauncherRestoreEventLoggerImpl"

        // Generic type for any possible workspace items, when specific type is not known.
        @BackupRestoreDataType private const val DATA_TYPE_LAUNCHER_ITEM = "launcher_item"
        // Specific workspace item types, based off of Favorites Table.
        @BackupRestoreDataType private const val DATA_TYPE_APPLICATION = "application"
        @BackupRestoreDataType private const val DATA_TYPE_FOLDER = "folder"
        @BackupRestoreDataType private const val DATA_TYPE_APPWIDGET = "widget"
        @BackupRestoreDataType private const val DATA_TYPE_CUSTOM_APPWIDGET = "custom_widget"
        @BackupRestoreDataType private const val DATA_TYPE_DEEP_SHORTCUT = "deep_shortcut"
        @BackupRestoreDataType private const val DATA_TYPE_APP_PAIR = "app_pair"
    }

    private val backupManager: BackupManager = BackupManager(context)
    private val restoreEventLogger: BackupRestoreEventLogger = backupManager.delayedRestoreLogger

    /**
     * For logging when multiple items of a given data type failed to restore.
     *
     * @param dataType The data type that was not restored.
     * @param count the number of data items that were not restored.
     * @param error error type for why the data was not restored.
     */
    override fun logLauncherItemsRestoreFailed(
        @BackupRestoreDataType dataType: String,
        count: Int,
        @BackupRestoreError error: String?
    ) {
        if (Flags.enableLauncherBrMetrics()) {
            restoreEventLogger.logItemsRestoreFailed(dataType, count, error)
        }
    }

    /**
     * For logging when multiple items of a given data type were successfully restored.
     *
     * @param dataType The data type that was restored.
     * @param count the number of data items restored.
     */
    override fun logLauncherItemsRestored(@BackupRestoreDataType dataType: String, count: Int) {
        if (Flags.enableLauncherBrMetrics()) {
            restoreEventLogger.logItemsRestored(dataType, count)
        }
    }

    /**
     * Helper to log successfully restoring a single item from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was restored.
     */
    override fun logSingleFavoritesItemRestored(favoritesId: Int) {
        if (Flags.enableLauncherBrMetrics()) {
            restoreEventLogger.logItemsRestored(favoritesIdToDataType(favoritesId), 1)
        }
    }

    /**
     * Helper to log a failure to restore a single item from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param error error type for why the data was not restored.
     */
    override fun logSingleFavoritesItemRestoreFailed(
        favoritesId: Int,
        @BackupRestoreError error: String?
    ) {
        if (Flags.enableLauncherBrMetrics()) {
            restoreEventLogger.logItemsRestoreFailed(favoritesIdToDataType(favoritesId), 1, error)
        }
    }

    /**
     * Helper to log a failure to restore items from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param count number of items that failed to restore.
     * @param error error type for why the data was not restored.
     */
    override fun logFavoritesItemsRestoreFailed(
        favoritesId: Int,
        count: Int,
        @BackupRestoreError error: String?
    ) {
        if (Flags.enableLauncherBrMetrics()) {
            restoreEventLogger.logItemsRestoreFailed(
                favoritesIdToDataType(favoritesId),
                count,
                error
            )
        }
    }

    /**
     * Uses the current [restoreEventLogger] to report its results to the [backupManager]. Use when
     * done restoring items for Launcher.
     */
    override fun reportLauncherRestoreResults() {
        if (Flags.enableLauncherBrMetrics()) {
            backupManager.reportDelayedRestoreResult(restoreEventLogger)
        }
    }

    /**
     * Helper method to convert item types from [Favorites] to B&R data types for logging. Also to
     * avoid direct usage of @BackupRestoreDataType which is protected under @SystemApi.
     */
    @BackupRestoreDataType
    private fun favoritesIdToDataType(favoritesId: Int): String =
        when (favoritesId) {
            Favorites.ITEM_TYPE_APPLICATION -> DATA_TYPE_APPLICATION
            Favorites.ITEM_TYPE_FOLDER -> DATA_TYPE_FOLDER
            Favorites.ITEM_TYPE_APPWIDGET -> DATA_TYPE_APPWIDGET
            Favorites.ITEM_TYPE_CUSTOM_APPWIDGET -> DATA_TYPE_CUSTOM_APPWIDGET
            Favorites.ITEM_TYPE_DEEP_SHORTCUT -> DATA_TYPE_DEEP_SHORTCUT
            Favorites.ITEM_TYPE_APP_PAIR -> DATA_TYPE_APP_PAIR
            else -> DATA_TYPE_LAUNCHER_ITEM
        }
}
