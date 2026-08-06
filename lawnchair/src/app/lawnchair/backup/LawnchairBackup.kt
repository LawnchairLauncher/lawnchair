package app.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.util.PrivateSpaceUtils
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.PrivateProfileTracker
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
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
        val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
        val contents = selectedContents and info.contents
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            handlers.putAll(
                getFiles(context, forRestore = true).mapValues { entry ->
                    {
                        val file = entry.value
                        file.parentFile?.mkdirs()
                        it.copyTo(file.outputStream())
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
        context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.deleteRecursively()
        DeviceGridState(info.gridState).writeToPrefs(context, true)
        readZip(handlers)

        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            verifyRestoredDb(context)
        }

        var dbController = ModelDbController(context)
        RestoreDbTask.performRestore(context, dbController)
    }

    /**
     * Refuses to restore a database that did not survive the round trip intact.
     *
     * Archives written by older builds were plain copies of a live database and can be torn, so a
     * damaged file is a real possibility rather than a theoretical one. Importing it would replace a
     * working layout with a broken one, and the failure would surface later as missing or duplicated
     * icons rather than as a failed restore.
     */
    private fun verifyRestoredDb(context: Context) {
        val restoredDb = context.getDatabasePath(RESTORED_DB_FILE_NAME)
        if (!restoredDb.exists()) return
        val result = try {
            SQLiteDatabase.openDatabase(
                restoredDb.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Restored database could not be opened", t)
            null
        }
        if (result != "ok") {
            throw IOException("Backup contains a damaged launcher database (integrity: $result)")
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
        private const val TAG = "LawnchairBackup"
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

                        val launcherDb = launcherDbFile(context, forRestore = false)
                        val sanitisedDb = sanitiseLauncherDbIfLocked(context, launcherDb)
                        try {
                            getFiles(context, forRestore = false).entries.forEach {
                                if (!it.value.exists()) return@forEach
                                val source =
                                    if (it.value == launcherDb) sanitisedDb ?: it.value else it.value
                                out.putNextEntry(ZipEntry(it.key))
                                source.inputStream().copyTo(out)
                            }
                        } finally {
                            sanitisedDb?.let { deleteDbFiles(it) }
                        }
                    }
                }
            }
        }

        /**
         * Returns a scrubbed copy of the launcher database with private space rows removed, or null
         * when the database can be exported as-is.
         *
         * The favorites table stores each pinned app's component name in plain text, so an exported
         * backup otherwise discloses exactly which apps live in the private space to anyone holding
         * the unlocked phone - without them ever needing to unlock the space. Rows are kept only
         * when the space is currently unlocked, i.e. when the person exporting has already proved
         * they may see them; a backup made then still restores pinned private apps in full.
         */
        private fun sanitiseLauncherDbIfLocked(context: Context, launcherDb: File): File? {
            if (!launcherDb.exists()) return null
            val mustScrub = PrivateSpaceUtils.isPrivateSpaceLocked(context) &&
                PrivateProfileTracker.getKnownPrivateProfileSerial(context) !=
                PrivateProfileTracker.INVALID_SERIAL

            val snapshot = snapshotLauncherDb(context, launcherDb)
            if (snapshot == null) {
                // No consistent snapshot available. Exporting the raw file is the historic
                // behaviour and stays acceptable when there is nothing to hide, but never when we
                // were supposed to strip private rows.
                check(!mustScrub) { "Cannot scrub launcher database without a snapshot" }
                return null
            }
            if (!mustScrub) return snapshot

            val privateSerial = PrivateProfileTracker.getKnownPrivateProfileSerial(context)
            return try {
                SQLiteDatabase.openDatabase(
                    snapshot.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                ).use { db ->
                    val removed = db.delete(
                        Favorites.TABLE_NAME,
                        "${Favorites.PROFILE_ID} = ?",
                        arrayOf(privateSerial.toString()),
                    )
                    Log.d(TAG, "Excluded $removed private space item(s) from backup")
                }
                snapshot
            } catch (t: Throwable) {
                // Never fall back to exporting the unscrubbed database: failing to strip is exactly
                // the case where leaking would be silent.
                Log.e(TAG, "Unable to scrub launcher database for backup", t)
                deleteDbFiles(snapshot)
                throw t
            }
        }

        /**
         * Takes a point-in-time consistent copy of the live launcher database, or null when this
         * platform cannot and the caller should fall back to copying the file.
         *
         * A plain file copy of a database that is open for writing is unsafe twice over. The copy is
         * not atomic, so a page written while it runs is captured half-old and half-new, producing a
         * genuinely corrupt archive. And Android opens this database in WAL mode, so recently
         * committed rows live in the sibling `-wal` file that a single-file copy leaves behind -
         * quietly yielding a backup that is stale rather than broken, which is harder to notice.
         * Both matter more now that the model rewrites positions on load to relocate private items.
         *
         * `VACUUM INTO` reads through a proper read transaction and writes a fully checkpointed
         * database, so it is consistent by construction. It needs SQLite 3.27, which Android has had
         * since API 29; below that we fall back, and no private space can exist there to leak.
         */
        private fun snapshotLauncherDb(context: Context, launcherDb: File): File? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            // VACUUM INTO refuses to overwrite, so hand it a path that does not exist yet.
            val target = File(context.cacheDir, "launcher-backup-${System.nanoTime()}.db")
            deleteDbFiles(target)
            return try {
                SQLiteDatabase.openDatabase(
                    launcherDb.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    db.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
                }
                target
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to snapshot launcher database, falling back to a file copy", t)
                deleteDbFiles(target)
                null
            }
        }

        /** Removes a database along with any journal siblings it may have left behind. */
        private fun deleteDbFiles(db: File) {
            db.delete()
            File("${db.absolutePath}-wal").delete()
            File("${db.absolutePath}-shm").delete()
            File("${db.absolutePath}-journal").delete()
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
