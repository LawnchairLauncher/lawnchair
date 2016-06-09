package com.android.launcher3.provider;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;

/**
 * Tests for {@link RestoreDbTask}
 */
@MediumTest
public class RestoreDbTaskTest extends AndroidTestCase {

    public void testGetProfileId() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(23).getWritableDatabase();
        assertEquals(23, new RestoreDbTask().getDefaultProfileId(db));
    }

    public void testMigrateProfileId() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(42).getWritableDatabase();
        // Add some dummy data
        for (int i = 0; i < 5; i++) {
            ContentValues values = new ContentValues();
            values.put(Favorites._ID, i);
            values.put(Favorites.TITLE, "item " + i);
            db.insert(Favorites.TABLE_NAME, null, values);
        }
        // Verify item add
        assertEquals(5, getCount(db, "select * from favorites where profileId = 42"));

        new RestoreDbTask().migrateProfileId(db, 33);

        // verify data migrated
        assertEquals(0, getCount(db, "select * from favorites where profileId = 42"));
        assertEquals(5, getCount(db, "select * from favorites where profileId = 33"));

        // Verify default value changed
        ContentValues values = new ContentValues();
        values.put(Favorites._ID, 100);
        values.put(Favorites.TITLE, "item 100");
        db.insert(Favorites.TABLE_NAME, null, values);
        assertEquals(6, getCount(db, "select * from favorites where profileId = 33"));
    }

    private int getCount(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.getCount();
        } finally {
            c.getCount();
        }
    }

    private class MyDatabaseHelper extends DatabaseHelper {

        private final long mProfileId;

        public MyDatabaseHelper(long profileId) {
            super(getContext(), null, null);
            mProfileId = profileId;
        }

        @Override
        public long getDefaultUserSerial() {
            return mProfileId;
        }

        protected void onEmptyDbCreated() { }
    }
}
