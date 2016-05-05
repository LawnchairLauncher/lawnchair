package com.android.launcher3;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.ParcelFileDescriptor;

import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.logging.FileLog;

import java.io.InvalidObjectException;

public class LauncherBackupAgent extends BackupAgent {

    private static final String TAG = "LauncherBackupAgent";

    private static final String INFO_COLUMN_NAME = "name";
    private static final String INFO_COLUMN_DEFAULT_VALUE = "dflt_value";

    @Override
    public void onRestore(
            BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) {
        // Doesn't do incremental backup/restore
    }

    @Override
    public void onBackup(
            ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
        // Doesn't do incremental backup/restore
    }

    @Override
    public void onRestoreFinished() {
        DatabaseHelper helper = new DatabaseHelper(this, null, LauncherFiles.LAUNCHER_DB);

        if (!sanitizeDBSafely(helper)) {
            helper.createEmptyDB(helper.getWritableDatabase());
        }

        try {
            // Flush all logs before the process is killed.
            FileLog.flushAll(null);
        } catch (Exception e) { }
    }

    private boolean sanitizeDBSafely(DatabaseHelper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            sanitizeDB(helper, db);
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to verify db", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Makes the following changes in the provider DB.
     *   1. Removes all entries belonging to a managed profile as managed profiles
     *      cannot be restored.
     *   2. Marks all entries as restored. The flags are updated during first load or as
     *      the restored apps get installed.
     *   3. If the user serial for primary profile is different than that of the previous device,
     *      update the entries to the new profile id.
     */
    private void sanitizeDB(DatabaseHelper helper, SQLiteDatabase db) throws Exception {
        long oldProfileId = getDefaultProfileId(db);
        // Delete all entries which do not belong to the main user
        int itemsDeleted = db.delete(
                Favorites.TABLE_NAME, "profileId != ?", new String[]{Long.toString(oldProfileId)});
        if (itemsDeleted > 0) {
            FileLog.d(TAG, itemsDeleted + " items belonging to a managed profile, were deleted");
        }

        // Mark all items as restored.
        ContentValues values = new ContentValues();
        values.put(Favorites.RESTORED, 1);
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Mark widgets with appropriate restore flag
        values.put(Favorites.RESTORED,
                LauncherAppWidgetInfo.FLAG_ID_NOT_VALID |
                LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY |
                LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
        db.update(Favorites.TABLE_NAME, values, "itemType = ?",
                new String[]{Integer.toString(Favorites.ITEM_TYPE_APPWIDGET)});

        long myProfileId = helper.getDefaultUserSerial();
        if (Utilities.longCompare(oldProfileId, myProfileId) != 0) {
            FileLog.d(TAG, "Changing primary user id from " + oldProfileId + " to " + myProfileId);
            migrateProfileId(db, myProfileId);
        }
    }

    /**
     * Updates profile id of all entries and changes the default value for the column.
     */
    protected void migrateProfileId(SQLiteDatabase db, long newProfileId) {
        // Update existing entries.
        ContentValues values = new ContentValues();
        values.put(Favorites.PROFILE_ID, newProfileId);
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Change default value of the column.
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_old;");
        Favorites.addTableToDb(db, newProfileId, false);
        db.execSQL("INSERT INTO favorites SELECT * FROM favorites_old;");
        db.execSQL("DROP TABLE favorites_old;");
    }

    /**
     * Returns the profile id for used in the favorites table of the provided db.
     */
    protected long getDefaultProfileId(SQLiteDatabase db) throws Exception {
        try (Cursor c = db.rawQuery("PRAGMA table_info (favorites)", null)){
            int nameIndex = c.getColumnIndex(INFO_COLUMN_NAME);
            while (c.moveToNext()) {
                if (Favorites.PROFILE_ID.equals(c.getString(nameIndex))) {
                    return c.getLong(c.getColumnIndex(INFO_COLUMN_DEFAULT_VALUE));
                }
            }
            throw new InvalidObjectException("Table does not have a profile id column");
        }
    }
}
