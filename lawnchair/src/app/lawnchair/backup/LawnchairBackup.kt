package app.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.provider.RestoreDbTask
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class LawnchairBackup(
    private val context: Context,
    private val uri: Uri,
) {
    lateinit var info: BackupInfo
    var screenshot: Bitmap? = null
    var wallpaper: Bitmap? = null

    suspend fun readInfoAndPreview() {
        var tmpScreenshot: Bitmap? = null
        var tmpWallpaper: Bitmap? = null
        readZip(
            mapOf(
                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },
                SCREENSHOT_FILE_NAME to { tmpScreenshot = BitmapFactory.decodeStream(it) },
                WALLPAPER_FILE_NAME to { tmpWallpaper = BitmapFactory.decodeStream(it) },
            ),
        )
        val size = max(info.previewWidth, info.previewHeight).coerceAtMost(4000)
        screenshot = tmpScreenshot?.scaleDownTo(size)
        wallpaper = tmpWallpaper?.scaleDownToDisplaySize(context)
    }

    suspend fun restore(selectedContents: Int) {
        val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
        val contents = selectedContents and info.contents
        var prefsEditor: SharedPreferences.Editor? = null
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            // The launcher's SharedPreferences stay cached in this process, and the cached
            // instance writes its whole pre-restore map back to disk on the next commit. Stage the
            // backed up values through it instead of overwriting the file, before anything is
            // deleted, so a malformed file aborts the restore with nothing changed.
            val prefs = LauncherPrefs.getPrefs(context)
            readZip(mapOf(PREFS_FILE_NAME to { prefsEditor = stageSharedPrefs(prefs, it) }))
            handlers.putAll(
                (getFiles(context, forRestore = true) - PREFS_FILE_NAME).mapValues { entry ->
                    {
                        val file = entry.value
                        file.parentFile?.mkdirs()
                        file.outputStream().use { out -> it.copyTo(out) }
                    }
                },
            )
        }
        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
            handlers[WALLPAPER_FILE_NAME] = {
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(BitmapFactory.decodeStream(it))
            }
        }
        // Files restored by copying (DataStore, Room prefs DB, launcher DB) must not be written
        // in-process before restartLauncher().
        context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.deleteRecursively()
        readZip(handlers)
        prefsEditor?.let { check(it.commit()) }
        // Written after the backed up preferences, which would otherwise replace the grid state.
        DeviceGridState(info.gridState).writeToPrefs(context, true)

        val dbController = ModelDbController(context)
        RestoreDbTask.performRestore(context, dbController)
    }

    private suspend fun readZip(handlers: Map<String, suspend (InputStream) -> Unit>) {
        withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
            pfd.use {
                FileInputStream(it.fileDescriptor).use { inStream ->
                    ZipInputStream(inStream).use { zipIs ->
                        var entry: ZipEntry?
                        while (true) {
                            entry = zipIs.nextEntry
                            if (entry == null) break
                            handlers[entry.name]?.invoke(zipIs)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val BACKUP_VERSION = 1
        private const val PREFS_FILE_NAME = "${LauncherFiles.SHARED_PREFERENCES_KEY}.xml"
        private const val PREFS_DB_FILE_NAME = "preferences"
        private const val PREFS_DATASTORE_FILE_NAME = "preferences.preferences_pb"

        const val INFO_FILE_NAME = "info.pb"
        const val WALLPAPER_FILE_NAME = "wallpaper.png"
        const val SCREENSHOT_FILE_NAME = "screenshot.png"
        const val LAUNCHER_DB_FILE_NAME = "launcher.db"
        const val RESTORED_DB_FILE_NAME = "restored.db"

        const val INCLUDE_LAYOUT_AND_SETTINGS = 1 shl 0
        const val INCLUDE_WALLPAPER = 1 shl 1

        const val MIME_TYPE = "application/zip"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        val contentOptions = listOf(
            INCLUDE_LAYOUT_AND_SETTINGS to R.string.backup_content_layout_and_settings,
            INCLUDE_WALLPAPER to R.string.backup_content_wallpaper,
        )

        fun generateBackupFileName(): String {
            val fileName = "Lawnchair_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"
            return "$fileName.lawnchairbackup"
        }

        fun getFiles(context: Context, forRestore: Boolean): Map<String, File> {
            return mapOf(
                LAUNCHER_DB_FILE_NAME to launcherDbFile(context, forRestore),
                PREFS_FILE_NAME to prefsFile(context),
                PREFS_DB_FILE_NAME to prefsDbFile(context),
                PREFS_DATASTORE_FILE_NAME to prefsDataStoreFile(context),
            )
        }

        @SuppressLint("MissingPermission")
        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap, fileUri: Uri) {
            val idp = LauncherAppState.getIDP(context)
            val createdAt = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
            val colorHints = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.colorHints ?: 0
            val wallpaperSupportsDarkText = (colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0
            val info = BackupInfo.newBuilder()
                .setLawnchairVersion(BuildConfig.VERSION_CODE)
                .setBackupVersion(BACKUP_VERSION)
                .setCreatedAt(createdAt)
                .setContents(contents)
                .setGridState(DeviceGridState(idp).toProtoMessage())
                .setPreviewWidth(screenshotBitmap.width)
                .setPreviewHeight(screenshotBitmap.height)
                .setPreviewDarkText(wallpaperSupportsDarkText)
                .build()

            val pfd = context.contentResolver.openFileDescriptor(fileUri, "w")!!
            withContext(Dispatchers.IO) {
                pfd.use {
                    ZipOutputStream(FileOutputStream(pfd.fileDescriptor).buffered()).use { out ->
                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))
                        info.writeTo(out)

                        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                            val wallpaperManager = WallpaperManager.getInstance(context)
                            val wallpaperBitmap = wallpaperManager.drawable?.toBitmap()
                            if (wallpaperBitmap != null) {
                                out.putNextEntry(ZipEntry(WALLPAPER_FILE_NAME))
                                wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }

                        getFiles(context, forRestore = false).entries.forEach {
                            if (!it.value.exists()) return@forEach
                            out.putNextEntry(ZipEntry(it.key))
                            it.value.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                }
            }
        }

        /**
         * Parses a SharedPreferences XML file into an uncommitted editor of [prefs] that sets every
         * backed up value and removes the keys missing from the backup. Throws on any entry that
         * SharedPreferences could not have written.
         */
        private fun stageSharedPrefs(prefs: SharedPreferences, xml: InputStream): SharedPreferences.Editor {
            val editor = prefs.edit()
            val backupKeys = mutableSetOf<String>()
            val parser = Xml.newPullParser()
            parser.setInput(xml, null)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "map")
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                val tag = parser.name
                val key = requireNotNull(parser.getAttributeValue(null, "name")) { "<$tag> without a name" }

                // Reads the value attribute and moves to the end tag of the empty element.
                fun value() = requireNotNull(parser.getAttributeValue(null, "value")) { "$key without a value" }
                    .also { parser.nextTag() }
                when (tag) {
                    "string" -> editor.putString(key, parser.nextText())
                    "int" -> editor.putInt(key, value().toInt())
                    "long" -> editor.putLong(key, value().toLong())
                    "float" -> editor.putFloat(key, value().toFloat())
                    "boolean" -> editor.putBoolean(key, value().toBooleanStrict())
                    "set" -> editor.putStringSet(key, readStringSet(parser))
                    else -> throw IllegalArgumentException("Unknown entry <$tag> for $key")
                }
                parser.require(XmlPullParser.END_TAG, null, tag)
                backupKeys += key
            }
            parser.require(XmlPullParser.END_TAG, null, "map")
            // nextTag() stops at </map>, so parse the rest too: only whitespace may follow
            // (next() skips comments), and a malformed tail throws here.
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                require(parser.eventType == XmlPullParser.TEXT && parser.isWhitespace) {
                    "Unexpected content after </map>"
                }
            }
            (prefs.all.keys - backupKeys).forEach { editor.remove(it) }
            return editor
        }

        private fun readStringSet(parser: XmlPullParser): Set<String?> = buildSet {
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                val tag = parser.name
                when (tag) {
                    "string" -> add(parser.nextText())

                    "null" -> {
                        add(null)
                        parser.nextTag()
                    }

                    else -> throw IllegalArgumentException("Unknown set element <$tag>")
                }
                parser.require(XmlPullParser.END_TAG, null, tag)
            }
        }

        private fun launcherDbFile(context: Context, forRestore: Boolean): File {
            val dbName = if (forRestore) RESTORED_DB_FILE_NAME else LauncherAppState.getIDP(context).dbFile
            return context.getDatabasePath(dbName)
        }

        private fun prefsFile(context: Context): File {
            val dir = context.cacheDir.parent
            return File(dir, "shared_prefs/$PREFS_FILE_NAME")
        }

        private fun prefsDbFile(context: Context): File {
            return context.getDatabasePath(PREFS_DB_FILE_NAME)
        }

        private fun prefsDataStoreFile(context: Context): File {
            return File(context.filesDir, "datastore/${PREFS_DATASTORE_FILE_NAME}")
        }
    }
}
