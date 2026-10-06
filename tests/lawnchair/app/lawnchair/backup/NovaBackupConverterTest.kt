package app.lawnchair.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.android.launcher3.LauncherSettings.Favorites
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class NovaBackupConverterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun novaSettingsShortcutIsNotImported() {
        SQLiteDatabase.create(null).use { source ->
            SQLiteDatabase.create(null).use { target ->
                Favorites.addTableToDb(source, 0, false)
                Favorites.addTableToDb(target, 0, false)
                source.execSQL("INSERT INTO favorites (_id, itemType, container, screen, cellX, cellY, spanX, spanY, intent) VALUES (1, 0, -100, 0, 0, 0, 1, 1, ?)",
                    arrayOf("#Intent;action=android.intent.action.MAIN;component=com.teslacoilsw.launcher/.preferences.SettingsActivity;end"))
                NovaBackupConverter(RuntimeEnvironment.getApplication(), Uri.EMPTY)
                    .insertNovaItems(source, target, 0, mutableMapOf())
                target.rawQuery("SELECT COUNT(*) FROM favorites", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            }
        }
    }

}
