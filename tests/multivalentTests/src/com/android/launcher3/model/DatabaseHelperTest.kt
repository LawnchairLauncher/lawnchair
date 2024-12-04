package com.android.launcher3.model

import android.database.sqlite.SQLiteDatabase
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites.TMP_TABLE
import com.android.launcher3.LauncherSettings.Favorites.addTableToDb
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils
import java.util.function.ToLongFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

private const val INSERTION_SQL = "databases/v30_workspace_items.sql"

private const val ICON_PACKAGE = "iconPackage"
private const val ICON_RESOURCE = "iconResource"

@SmallTest
@RunWith(AndroidJUnit4::class)
class DatabaseHelperTest {

    /**
     * b/304687723 occurred when a return was accidentally added to a case statement in
     * DatabaseHelper.onUpgrade, which stopped the final data migration from successfully occurring.
     * This test loads an in-memory db from a text file containing SQL statements, and then performs
     * the migration on the db, and verifies that the correct columns have been deleted.
     */
    @Test
    fun onUpgrade_to_version_32_from_30() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userSerialProvider =
            ToLongFunction<UserHandle> {
                UserCache.INSTANCE.get(context).getSerialNumberForUser(it)
            }
        val dbHelper = DatabaseHelper(context, null, userSerialProvider) {}
        val db = FactitiousDbController(context, INSERTION_SQL).inMemoryDb

        dbHelper.onUpgrade(db, 30, 32)

        assertFalse(hasFavoritesColumn(db, ICON_PACKAGE))
        assertFalse(hasFavoritesColumn(db, ICON_RESOURCE))
    }

    /**
     * b/304687723 causes a crash due to copying a table with 21 columns to a table with 19 columns.
     * This test loads an in-memory db from a text file containing SQL statements, and then copies
     * data from the created table into a temporary one, and verifies that no exception is thrown.
     */
    @Test
    fun after_migrating_from_db_v30_to_v32_copy_table() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = FactitiousDbController(context, INSERTION_SQL).inMemoryDb // v30 - 21 columns

        addTableToDb(db, 1, true, TMP_TABLE)
        LauncherDbUtils.copyTable(db, TABLE_NAME, db, TMP_TABLE, context)

        val c1 = db.query(TABLE_NAME, null, null, null, null, null, null)
        val c2 = db.query(TMP_TABLE, null, null, null, null, null, null)

        assertEquals(21, c1.columnCount)
        assertEquals(19, c2.columnCount)
        assertEquals(c1.count, c2.count)

        c1.close()
        c2.close()
    }

    private fun hasFavoritesColumn(db: SQLiteDatabase, columnName: String): Boolean {
        db.query(TABLE_NAME, null, null, null, null, null, null).use { c ->
            return c.getColumnIndex(columnName) >= 0
        }
    }
}
