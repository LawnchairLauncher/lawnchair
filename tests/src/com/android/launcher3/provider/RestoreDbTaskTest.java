package com.android.launcher3.provider;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link RestoreDbTask}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RestoreDbTaskTest {

    @Test
    public void testGetProfileId() throws Exception {
        SQLiteDatabase db = new MyDatabaseHelper(23).getWritableDatabase();
        assertEquals(23, new RestoreDbTask().getDefaultProfileId(db));
    }

    @Test
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
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.getCount();
        }
    }

    private class MyDatabaseHelper extends DatabaseHelper {

        private final long mProfileId;

        MyDatabaseHelper(long profileId) {
            super(InstrumentationRegistry.getContext(), null, null);
            mProfileId = profileId;
        }

        @Override
        public long getDefaultUserSerial() {
            return mProfileId;
        }

        @Override
        protected void handleOneTimeDataUpgrade(SQLiteDatabase db) { }

        protected void onEmptyDbCreated() { }
    }
}
