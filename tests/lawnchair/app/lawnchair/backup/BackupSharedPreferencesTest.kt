package app.lawnchair.backup

import android.app.Application
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.model.DeviceGridState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupSharedPreferencesTest {
    @Test
    fun restoringSettingsPreservesPresentAndAbsentLiveDatabaseSelectionKeys() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, 0)
        listOf(true, false).forEach { hasSelection ->
            preferences.edit().clear().apply {
                if (hasSelection) {
                    putString(DeviceGridState.KEY_DB_FILE, "live.db")
                    putString(DeviceGridState.KEY_WORKSPACE_SIZE, "4,6")
                    putInt(DeviceGridState.KEY_HOTSEAT_COUNT, 4)
                    putInt(DeviceGridState.KEY_DEVICE_TYPE, 0)
                }
            }.commit()
            val liveSelection = preferences.all
            preferences.edit().putString("icon-pack", "old-pack").putBoolean("obsolete", true).commit()

            BackupSharedPreferences.apply(
                preferences,
                mapOf(
                    DeviceGridState.KEY_DB_FILE to "missing-archive.db",
                    DeviceGridState.KEY_WORKSPACE_SIZE to "8,9",
                    DeviceGridState.KEY_HOTSEAT_COUNT to 8,
                    DeviceGridState.KEY_DEVICE_TYPE to 1,
                    "icon-pack" to "restored-pack",
                ),
            )

            val expected = liveSelection + ("icon-pack" to "restored-pack")
            assertEquals(expected, preferences.all)
            val xml = File(context.applicationInfo.dataDir, "shared_prefs/${LauncherFiles.SHARED_PREFERENCES_KEY}.xml")
            assertEquals(expected, BackupSharedPreferences.read(xml))
        }
    }

    @Test
    fun restoringCachedPreferenceSurvivesSubsequentGridMetadataWrite() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, 0)
        preferences.edit().putString("icon-pack", "saved-pack").commit()
        assertEquals("saved-pack", preferences.getString("icon-pack", null))
        val saved = File(context.cacheDir, "saved.xml").apply {
            writeText("<map><string name='icon-pack'>saved-pack</string></map>")
        }
        preferences.edit().putString("icon-pack", "changed-pack").putBoolean("temporary-setting", true).commit()
        BackupSharedPreferences.apply(preferences, BackupSharedPreferences.read(saved))
        DeviceGridState(GridState.newBuilder().setGridSize("5,7").setHotseatCount(5).setDeviceType(0).build())
            .writeToPrefs(context, true)
        assertEquals("saved-pack", preferences.getString("icon-pack", null))
        assertFalse(preferences.contains("temporary-setting"))
        val persisted = File(context.applicationInfo.dataDir, "shared_prefs/${LauncherFiles.SHARED_PREFERENCES_KEY}.xml")
        assertEquals("saved-pack", BackupSharedPreferences.read(persisted)["icon-pack"])
    }

    @Test
    fun allSupportedPreferenceTypesAndEscapedStringsRoundTrip() {
        val context = RuntimeEnvironment.getApplication()
        val saved = File(context.cacheDir, "saved.xml").apply {
            writeText("""<map>
                <boolean name="boolean" value="true" />
                <int name="int" value="42" />
                <long name="long" value="9000000000" />
                <float name="float" value="1.5" />
                <string name="string"> A &amp; B </string>
                <set name="set"><string>first</string><string>second</string></set>
                <null name="null" />
                </map>""")
        }
        val preferences = context.getSharedPreferences("test", 0)
        BackupSharedPreferences.apply(preferences, BackupSharedPreferences.read(saved))
        assertEquals(true, preferences.getBoolean("boolean", false))
        assertEquals(42, preferences.getInt("int", 0))
        assertEquals(9000000000L, preferences.getLong("long", 0))
        assertEquals(1.5f, preferences.getFloat("float", 0f))
        assertEquals(" A & B ", preferences.getString("string", null))
        assertEquals(setOf("first", "second"), preferences.getStringSet("set", null))
        assertFalse(preferences.contains("null"))
    }

    @Test
    fun invalidPreferenceArchiveDoesNotClearLivePreferences() {
        val context = RuntimeEnvironment.getApplication()
        val saved = File(context.cacheDir, "bad.xml").apply {
            writeText("<map><int name='count' value='not-a-number'/></map>")
        }
        val preferences = context.getSharedPreferences("test", 0)
        preferences.edit().putInt("count", 42).commit()
        assertThrows(NumberFormatException::class.java) {
            BackupSharedPreferences.apply(preferences, BackupSharedPreferences.read(saved))
        }
        assertEquals(42, preferences.getInt("count", 0))
    }
}
