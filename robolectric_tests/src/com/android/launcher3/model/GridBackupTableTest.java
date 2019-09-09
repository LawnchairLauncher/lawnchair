package com.android.launcher3.model;


import static android.database.DatabaseUtils.queryNumEntries;

import static com.android.launcher3.LauncherSettings.Favorites.BACKUP_TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.graphics.Point;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link GridBackupTable}
 */
@RunWith(RobolectricTestRunner.class)
public class GridBackupTableTest extends BaseGridChangesTestCase {

    private static final int BACKUP_ITEM_COUNT = 12;

    @Before
    public void setupGridData() {
        createGrid(new int[][][]{{
                { APP_ICON, APP_ICON, SHORTCUT, SHORTCUT},
                { SHORTCUT, SHORTCUT, NO__ICON, NO__ICON},
                { NO__ICON, NO__ICON, SHORTCUT, SHORTCUT},
                { APP_ICON, SHORTCUT, SHORTCUT, APP_ICON},
        }});
        assertEquals(BACKUP_ITEM_COUNT, queryNumEntries(mDb, TABLE_NAME));
    }

    @Test
    public void backupTableCreated() {
        GridBackupTable backupTable = new GridBackupTable(mContext, mDb, 4, 4, 4);
        assertFalse(backupTable.backupOrRestoreAsNeeded());
        Settings.call(mContext.getContentResolver(), Settings.METHOD_REFRESH_BACKUP_TABLE);

        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));

        // One extra entry for properties
        assertEquals(BACKUP_ITEM_COUNT + 1, queryNumEntries(mDb, BACKUP_TABLE_NAME));
    }

    @Test
    public void backupTableRestored() {
        assertFalse(new GridBackupTable(mContext, mDb, 4, 4, 4).backupOrRestoreAsNeeded());
        Settings.call(mContext.getContentResolver(), Settings.METHOD_REFRESH_BACKUP_TABLE);

        // Delete entries
        mDb.delete(TABLE_NAME, null, null);
        assertEquals(0, queryNumEntries(mDb, TABLE_NAME));

        GridBackupTable backupTable = new GridBackupTable(mContext, mDb, 3, 3, 3);
        assertTrue(backupTable.backupOrRestoreAsNeeded());

        // Items have been restored
        assertEquals(BACKUP_ITEM_COUNT, queryNumEntries(mDb, TABLE_NAME));

        Point outSize = new Point();
        assertEquals(4, backupTable.getRestoreHotseatAndGridSize(outSize));
        assertEquals(4, outSize.x);
        assertEquals(4, outSize.y);
    }

    @Test
    public void backupTableRemovedOnAdd() {
        assertFalse(new GridBackupTable(mContext, mDb, 4, 4, 4).backupOrRestoreAsNeeded());
        Settings.call(mContext.getContentResolver(), Settings.METHOD_REFRESH_BACKUP_TABLE);

        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));

        addItem(1, 2, DESKTOP, 1, 1);
        assertFalse(tableExists(mDb, BACKUP_TABLE_NAME));
    }

    @Test
    public void backupTableRemovedOnDelete() {
        assertFalse(new GridBackupTable(mContext, mDb, 4, 4, 4).backupOrRestoreAsNeeded());
        Settings.call(mContext.getContentResolver(), Settings.METHOD_REFRESH_BACKUP_TABLE);

        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));

        mContext.getContentResolver().delete(Favorites.CONTENT_URI, null, null);
        assertFalse(tableExists(mDb, BACKUP_TABLE_NAME));
    }

    @Test
    public void backupTableRetainedOnUpdate() {
        assertFalse(new GridBackupTable(mContext, mDb, 4, 4, 4).backupOrRestoreAsNeeded());
        Settings.call(mContext.getContentResolver(), Settings.METHOD_REFRESH_BACKUP_TABLE);

        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));

        ContentValues values = new ContentValues();
        values.put(Favorites.RANK, 4);
        // Something was updated
        assertTrue(mContext.getContentResolver()
                .update(Favorites.CONTENT_URI, values, null, null) > 0);

        // Backup table remains
        assertTrue(tableExists(mDb, BACKUP_TABLE_NAME));
    }
}
