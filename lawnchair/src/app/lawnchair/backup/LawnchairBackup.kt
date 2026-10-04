package app.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.PreferencesFileSerializer
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.data.AppDatabase
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

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
        val contents = selectedContents and info.contents
        val restoreLayout = contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)
        val staging = createTempDirectory(context.cacheDir.toPath(), "restore-").toFile()
        try {
            val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
            val destinations = if (restoreLayout) getFiles(context, forRestore = true) else emptyMap()
            destinations.keys.forEach { name ->
                handlers[name] = { input -> File(staging, name).outputStream().use { input.copyTo(it) } }
            }
            if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                handlers[WALLPAPER_FILE_NAME] = {
                    File(staging, WALLPAPER_FILE_NAME).outputStream().use { output -> it.copyTo(output) }
                }
            }
            // Finish reading before touching the live layout or its bound widget IDs.
            readZip(handlers)
            val restoredWallpaper = if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                checkNotNull(BitmapFactory.decodeFile(File(staging, WALLPAPER_FILE_NAME).path)) { "Backup has no valid wallpaper" }
            } else {
                null
            }
            if (restoreLayout) {
                val layout = File(staging, LAUNCHER_DB_FILE_NAME)
                check(layout.isFile) { "Backup has no launcher layout" }
                withContext(Dispatchers.IO) { BackupLayoutDatabase.validate(context, layout, info.gridState) }
                val preferencesFile = File(staging, PREFS_FILE_NAME)
                val preferences = if (preferencesFile.isFile) {
                    withContext(Dispatchers.IO) { BackupSharedPreferences.read(preferencesFile) }
                } else {
                    null
                }
                val dataStoreFile = File(staging, PREFS_DATASTORE_FILE_NAME)
                val dataStorePreferences = if (dataStoreFile.isFile) {
                    withContext(Dispatchers.IO) { dataStoreFile.inputStream().use { PreferencesFileSerializer.readFrom(it) } }
                } else {
                    null
                }
                val preferencesDatabase = File(staging, PREFS_DB_FILE_NAME)
                if (preferencesDatabase.isFile) {
                    val database = withContext(Dispatchers.Main) { AppDatabase.INSTANCE.get(context) }
                    withContext(Dispatchers.IO) { BackupPreferencesDatabase.restore(context, preferencesDatabase, database) }
                }
                withContext(Dispatchers.Main) {
                    if (dataStorePreferences != null) {
                        PreferenceManager2.getInstance(context).restorePreferences(dataStorePreferences)
                    }
                    if (preferences != null) {
                        val manager = PreferenceManager.getInstance(context)
                        manager.batchEdit {
                            // Clear also removes keys without per-key notifications on newer Android.
                            manager.prefsMap.values.forEach { it.invalidate() }
                            BackupSharedPreferences.apply(manager.sp, preferences)
                        }
                    }
                    InvariantDeviceProfile.INSTANCE.get(context).onPreferencesChanged(context)
                }
                // Let grid changes finish against the old layout before installing the backup.
                withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
                    BackupLayoutDatabase.install(context, layout, info.gridState, LauncherAppState.getIDP(context).dbFile)
                    val dbController = ModelDbController(context)
                    val database = dbController.db
                    database.beginTransaction()
                    try {
                        check(RestoreDbTask.performRestore(context, dbController)) { "Unable to restore launcher layout" }
                        database.setTransactionSuccessful()
                    } finally {
                        database.endTransaction()
                        database.close()
                    }
                    dbController.clearEmptyDbFlag()
                }
            }
            if (restoredWallpaper != null) {
                withContext(Dispatchers.IO) { WallpaperManager.getInstance(context).setBitmap(restoredWallpaper) }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
        }
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

        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap?, fileUri: Uri) {
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(fileUri, "wt")
                    ?: error("Unable to open backup destination")
                output.use { create(context, contents, screenshotBitmap, it) }
            }
        }

        /** Writes and closes [output]. A preview is optional for unattended exports. */
        @SuppressLint("MissingPermission")
        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap?, output: OutputStream) {
            withContext(Dispatchers.IO) {
                val idp = LauncherAppState.getIDP(context)
                val colorHints = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.colorHints ?: 0
                val info = BackupInfo.newBuilder()
                    .setLawnchairVersion(BuildConfig.VERSION_CODE)
                    .setBackupVersion(BACKUP_VERSION)
                    .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000))
                    .setContents(contents)
                    .setGridState(DeviceGridState(idp).toProtoMessage())
                    .setPreviewWidth(screenshotBitmap?.width ?: 1)
                    .setPreviewHeight(screenshotBitmap?.height ?: 1)
                    .setPreviewDarkText((colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0)
                    .build()
                val staging = createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
                try {
                    val files = if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                        check(launcherDbFile(context, forRestore = false).isFile) { "Launcher layout is not ready for backup" }
                        getFiles(context, forRestore = false).mapValues { (name, source) ->
                            val snapshot = File(staging, name)
                            if (source.exists()) {
                                if (name == LAUNCHER_DB_FILE_NAME || name == PREFS_DB_FILE_NAME) {
                                    BackupDatabaseSnapshot.create(source, snapshot)
                                } else {
                                    source.copyTo(snapshot)
                                }
                            }
                            snapshot
                        }
                    } else {
                        emptyMap()
                    }
                    ZipOutputStream(output.buffered()).use { out ->
                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))
                        info.writeTo(out)
                        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                            val wallpaper = WallpaperManager.getInstance(context).drawable?.toBitmap()
                                ?: error("Wallpaper is unavailable")
                            out.putNextEntry(ZipEntry(WALLPAPER_FILE_NAME))
                            check(wallpaper.compress(Bitmap.CompressFormat.PNG, 100, out))
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS) && screenshotBitmap != null) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            check(screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out))
                        }
                        files.forEach { (name, file) ->
                            if (file.exists()) {
                                out.putNextEntry(ZipEntry(name))
                                file.inputStream().use { it.copyTo(out) }
                            }
                        }
                    }
                } finally {
                    staging.deleteRecursively()
                }
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
