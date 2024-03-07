package com.android.launcher3.backuprestore

import android.content.Context
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.util.ResourceBasedOverride

/**
 * Wrapper for logging Restore event metrics for both success and failure to restore the Launcher
 * workspace from a backup.
 */
open class LauncherRestoreEventLogger : ResourceBasedOverride {

    companion object {
        const val TAG = "LauncherRestoreEventLogger"

        fun newInstance(context: Context?): LauncherRestoreEventLogger {
            return ResourceBasedOverride.Overrides.getObject(
                LauncherRestoreEventLogger::class.java,
                context,
                R.string.launcher_restore_event_logger_class
            )
        }
    }

    /**
     * For logging when multiple items of a given data type failed to restore.
     *
     * @param dataType The data type that was not restored.
     * @param count the number of data items that were not restored.
     * @param error error type for why the data was not restored.
     */
    open fun logLauncherItemsRestoreFailed(dataType: String, count: Int, error: String?) {
        // no-op
    }

    /**
     * For logging when multiple items of a given data type were successfully restored.
     *
     * @param dataType The data type that was restored.
     * @param count the number of data items restored.
     */
    open fun logLauncherItemsRestored(dataType: String, count: Int) {
        // no-op
    }

    /**
     * Helper to log successfully restoring a single item from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was restored.
     */
    open fun logSingleFavoritesItemRestored(favoritesId: Int) {
        // no-op
    }

    /**
     * Helper to log a failure to restore a single item from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param error error type for why the data was not restored.
     */
    open fun logSingleFavoritesItemRestoreFailed(favoritesId: Int, error: String?) {
        // no-op
    }

    /**
     * Helper to log a failure to restore items from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param count number of items that failed to restore.
     * @param error error type for why the data was not restored.
     */
    open fun logFavoritesItemsRestoreFailed(favoritesId: Int, count: Int, error: String?) {
        // no-op
    }

    /**
     * Uses the current [restoreEventLogger] to report its results to the [backupManager]. Use when
     * done restoring items for Launcher.
     */
    open fun reportLauncherRestoreResults() {
        // no-op
    }
}
