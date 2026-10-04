package app.lawnchair.backup

import android.app.Application
import android.net.Uri
import app.lawnchair.LawnchairProto.BackupInfo
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupRestoreStagingTest {
    @Test
    fun incompleteArchiveDoesNotReplaceLiveSettingsOrDatabase() {
        val context = RuntimeEnvironment.getApplication()
        val liveDatabase = context.getDatabasePath("launcher.db").apply {
            parentFile!!.mkdirs()
            writeText("live layout")
        }
        val livePreferences = File(context.applicationInfo.dataDir, "shared_prefs/com.android.launcher3.prefs.xml").apply {
            parentFile!!.mkdirs()
            writeText("live preferences")
        }
        val archive = File(context.cacheDir, "incomplete.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("com.android.launcher3.prefs.xml"))
            it.write("replacement preferences".toByteArray())
        }
        val backup = LawnchairBackup(context, Uri.fromFile(archive)).apply {
            info = BackupInfo.newBuilder().setContents(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS).build()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { backup.restore(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS) }
        }
        assertEquals("Backup has no launcher layout", error.message)
        assertEquals("live layout", liveDatabase.readText())
        assertEquals("live preferences", livePreferences.readText())
    }
}
