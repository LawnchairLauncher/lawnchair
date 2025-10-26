package com.android.launcher3.backuprestore

import android.content.Context
import androidx.annotation.StringDef
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.util.ResourceBasedOverride

/**
 * Wrapper for logging Restore event metrics for both success and failure to restore the Launcher
 * workspace from a backup.
 */
open class LauncherRestoreEventLogger : ResourceBasedOverride {

    /** Enumeration of potential errors returned to calls of pause/resume app updates. */
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        RestoreError.PROFILE_DELETED,
        RestoreError.MISSING_WIDGET_PROVIDER,
        RestoreError.OVERLAPPING_ITEM,
        RestoreError.INVALID_WIDGET_SIZE,
        RestoreError.INVALID_WIDGET_CONTAINER,
        RestoreError.SHORTCUT_NOT_FOUND,
        RestoreError.APP_NO_TARGET_PACKAGE,
        RestoreError.APP_NO_DB_INTENT,
        RestoreError.APP_NO_LAUNCH_INTENT,
        RestoreError.APP_NOT_RESTORED_OR_INSTALLING,
        RestoreError.APP_NOT_INSTALLED_EXTERNAL_MEDIA,
        RestoreError.WIDGETS_DISABLED,
        RestoreError.PROFILE_NOT_RESTORED,
        RestoreError.WIDGET_REMOVED,
        RestoreError.DATABASE_FILE_NOT_RESTORED,
        RestoreError.GRID_MIGRATION_FAILURE,
        RestoreError.NO_SEARCH_WIDGET,
        RestoreError.INVALID_WIDGET_ID,
        RestoreError.OTHER_WIDGET_INFLATION_FAIL,
        RestoreError.UNSPECIFIED_WIDGET_INFLATION_RESULT,
        RestoreError.UNRESTORED_PENDING_WIDGET,
        RestoreError.INVALID_CUSTOM_WIDGET_ID,
    )
    annotation class RestoreError {
        companion object {
            const val PROFILE_DELETED = "user_profile_deleted"
            const val MISSING_WIDGET_PROVIDER = "missing_widget_provider"
            const val OVERLAPPING_ITEM = "overlapping_item"
            const val INVALID_WIDGET_SIZE = "invalid_widget_size"
            const val INVALID_WIDGET_CONTAINER = "invalid_widget_container"
            const val SHORTCUT_NOT_FOUND = "shortcut_not_found"
            const val APP_NO_TARGET_PACKAGE = "app_no_target_package"
            const val APP_NO_DB_INTENT = "app_no_db_intent"
            const val APP_NO_LAUNCH_INTENT = "app_no_launch_intent"
            const val APP_NOT_RESTORED_OR_INSTALLING = "app_not_restored_or_installed"
            const val APP_NOT_INSTALLED_EXTERNAL_MEDIA = "app_not_installed_external_media"
            const val WIDGETS_DISABLED = "widgets_disabled"
            const val PROFILE_NOT_RESTORED = "profile_not_restored"
            const val DATABASE_FILE_NOT_RESTORED = "db_file_not_restored"
            const val WIDGET_REMOVED = "widget_not_found"
            const val GRID_MIGRATION_FAILURE = "grid_migration_failed"
            const val NO_SEARCH_WIDGET = "no_search_widget"
            const val INVALID_WIDGET_ID = "invalid_widget_id"
            const val OTHER_WIDGET_INFLATION_FAIL = "other_widget_fail"
            const val UNSPECIFIED_WIDGET_INFLATION_RESULT = "unspecified_widget_inflation_result"
            const val UNRESTORED_PENDING_WIDGET = "unrestored_pending_widget"
            const val INVALID_CUSTOM_WIDGET_ID = "invalid_custom_widget_id"
        }
    }

    companion object {
        const val TAG = "LauncherRestoreEventLogger"

        fun newInstance(context: Context?): LauncherRestoreEventLogger {
            return ResourceBasedOverride.Overrides.getObject(
                LauncherRestoreEventLogger::class.java,
                context,
                R.string.launcher_restore_event_logger_class,
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
     * Helper to log successfully restoring multiple items from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was restored.
     * @param count number of items that restored.
     */
    open fun logFavoritesItemsRestored(favoritesId: Int, count: Int) {
        // no-op
    }

    /**
     * Helper to log a failure to restore a single item from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param error error type for why the data was not restored.
     */
    open fun logSingleFavoritesItemRestoreFailed(favoritesId: Int, @RestoreError error: String?) {
        // no-op
    }

    /**
     * Helper to log a failure to restore items from the Favorites table.
     *
     * @param favoritesId The id of the item type from [Favorites] that was not restored.
     * @param count number of items that failed to restore.
     * @param error error type for why the data was not restored.
     */
    open fun logFavoritesItemsRestoreFailed(
        favoritesId: Int,
        count: Int,
        @RestoreError error: String?,
    ) {
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
