package ch.deletescape.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.support.v4.content.FileProvider
import android.util.Log
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.preferences.blockingEdit
import org.json.JSONArray
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.charset.StandardCharsets

open class LawnchairBackup(val context: Context, val uri: Uri?) {

    val meta by lazy { readMeta() }

    protected open fun readMeta(): Meta? {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val inStream = FileInputStream(pfd.fileDescriptor)
            val zipIs = ZipInputStream(inStream)
            var entry: ZipEntry?
            var meta: Meta? = null
            try {
                while (true) {
                    entry = zipIs.nextEntry
                    if (entry == null) break
                    if (entry.name != Meta.FILE_NAME) continue
                    meta = Meta.fromString(String(zipIs.readBytes(), StandardCharsets.UTF_8))
                    break
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to read meta for $uri", t)
            } finally {
                zipIs.close()
                inStream.close()
                pfd.close()
                return meta
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read meta for $uri", t)
            return null
        }
    }

    open fun restore(contents: Int): Boolean {
        try {
            val contextWrapper = ContextWrapper(context)
            val dbFile = contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB)
            val dir = contextWrapper.cacheDir.parent
            val settingsFile = File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")

            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val inStream = FileInputStream(pfd.fileDescriptor)
            val zipIs = ZipInputStream(inStream)
            val data = ByteArray(BUFFER)
            var entry: ZipEntry?
            var success = false
            try {
                while (true) {
                    entry = zipIs.nextEntry
                    if (entry == null) break
                    Log.d(TAG, "Found entry ${entry.name}")
                    val file = if (entry.name == dbFile.name) {
                        if (contents and INCLUDE_HOMESCREEN == 0) continue
                        dbFile
                    } else if (entry.name == settingsFile.name) {
                        if (contents and INCLUDE_SETTINGS == 0) continue
                        settingsFile
                    } else if (entry.name == WALLPAPER_FILE_NAME) {
                        if (contents and INCLUDE_WALLPAPER == 0) continue
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        wallpaperManager.setBitmap(BitmapFactory.decodeStream(zipIs))
                        continue
                    } else {
                        continue
                    }
                    val out = FileOutputStream(file)
                    Log.d(TAG, "Restoring ${entry.name} to ${file.absolutePath}")
                    var count: Int
                    while (true) {
                        count = zipIs.read(data, 0, BUFFER)
                        if (count == -1) break
                        out.write(data, 0, count)
                    }
                    out.close()
                }
                success = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore $uri", t)
            } finally {
                zipIs.close()
                inStream.close()
                pfd.close()
                return success
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to restore $uri", t)
            return false
        }
    }

    class LegacyBackup(context: Context) : LawnchairBackup(context, null) {

        override fun readMeta(): Meta? {
            var contents = 0
            if (DumbImportExportTask.getDbBackupFile().exists()) {
                contents = contents or LawnchairBackup.INCLUDE_HOMESCREEN
            }
            if (DumbImportExportTask.getSettingsBackupFile().exists()) {
                contents = contents or LawnchairBackup.INCLUDE_SETTINGS
            }
            return Meta(context.getString(R.string.legacy_backup), contents, getTimestamp())
        }

        override fun restore(contents: Int): Boolean {
            if (contents or LawnchairBackup.INCLUDE_HOMESCREEN != 0) {
                val file = context.getDatabasePath(LauncherFiles.LAUNCHER_DB)
                val backup = DumbImportExportTask.getDbBackupFile()
                if (!restoreFile(backup, file)) return false
            }
            if (contents or LawnchairBackup.INCLUDE_SETTINGS != 0) {
                val dir = context.cacheDir.parent
                val file = File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")
                val backup = DumbImportExportTask.getSettingsBackupFile()
                if (!restoreFile(backup, file)) return false
            }
            return true
        }

        private fun restoreFile(backup: File, file: File): Boolean {
            if (backup.exists()) {
                if (file.exists()) {
                    file.delete()
                }
                return DumbImportExportTask.copy(backup, file)
            }
            return true
        }
    }

    class MetaLoader(val backup: LawnchairBackup) {

        var callback: Callback? = null
        var meta: Meta? = null

        fun loadMeta() {
            LoadMetaTask().execute()
        }

        @SuppressLint("StaticFieldLeak")
        inner class LoadMetaTask : AsyncTask<Void, Void, Meta?>() {

            override fun doInBackground(vararg params: Void?) = backup.meta

            override fun onPostExecute(result: Meta?) {
                meta = result
                callback?.onMetaLoaded()
            }
        }

        interface Callback {

            fun onMetaLoaded()
        }
    }

    data class Meta(val name: String, val contents: Int, val timestamp: String) {

        override fun toString(): String {
            val arr = JSONArray()
            arr.put(VERSION)
            arr.put(name)
            arr.put(contents)
            arr.put(timestamp)
            return arr.toString()
        }

        companion object {

            const val VERSION = 1

            const val FILE_NAME = "lcbkp"

            @Suppress("unused")
            private const val VERSION_INDEX = 0
            private const val NAME_INDEX = 1
            private const val CONTENTS_INDEX = 2
            private const val TIMESTAMP_INDEX = 3

            fun fromString(string: String): Meta {
                val arr = JSONArray(string)
                return Meta(
                        name = arr.getString(NAME_INDEX),
                        contents = arr.getInt(CONTENTS_INDEX),
                        timestamp = arr.getString(TIMESTAMP_INDEX)
                )
            }
        }
    }

    companion object {
        const val TAG = "LawnchairBackup"

        const val INCLUDE_HOMESCREEN = 1 shl 0
        const val INCLUDE_SETTINGS = 1 shl 1
        const val INCLUDE_WALLPAPER = 1 shl 2

        const val BUFFER = 2018

        const val EXTENSION = "shed"
        const val MIME_TYPE = "application/vnd.lawnchair.backup"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        const val WALLPAPER_FILE_NAME = "wallpaper.png"

        fun getFolder(): File {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/backup")
            Log.d(TAG, "path: $folder")
            if (!folder.exists()) {
                folder.mkdirs()
            }
            return folder
        }

        fun listLocalBackups(context: Context): List<LawnchairBackup> {
            val backupList = getFolder().listFiles { file -> file.extension == EXTENSION }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", it) }
                    ?.map { LawnchairBackup.fromUri(context, it) }
                    ?: Collections.emptyList()
            val legacyBackup = LawnchairBackup.fromUri(context, null)
            return if (legacyBackup.meta!!.contents != 0) {
                listOf(legacyBackup) + backupList
            } else {
                backupList
            }
        }

        private fun prepareConfig(context: Context) {
            Utilities.getPrefs(context).blockingEdit {
                restoreSuccess = true
            }
        }

        private fun cleanupConfig(context: Context) {
            Utilities.getPrefs(context).blockingEdit {
                restoreSuccess = false
            }
        }

        fun create(context: Context, name: String, location: Uri, contents: Int): Boolean {
            val contextWrapper = ContextWrapper(context)
            val files: MutableList<File> = ArrayList()
            if (contents or INCLUDE_HOMESCREEN != 0) {
                files.add(contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB))
            }
            if (contents or INCLUDE_SETTINGS != 0) {
                val dir = contextWrapper.cacheDir.parent
                files.add(File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml"))
            }

            prepareConfig(context)
            val pfd = context.contentResolver.openFileDescriptor(location, "w")
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val out = ZipOutputStream(BufferedOutputStream(outStream))
            val data = ByteArray(BUFFER)
            var success = false
            try {
                val metaEntry = ZipEntry(Meta.FILE_NAME)
                out.putNextEntry(metaEntry)
                out.write(getMeta(name, contents).toString().toByteArray())
                if (contents or INCLUDE_WALLPAPER != 0) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val wallpaperDrawable = wallpaperManager.drawable
                    val wallpaperBitmap = Utilities.drawableToBitmap(wallpaperDrawable)
                    if (wallpaperBitmap != null) {
                        val wallpaperEntry = ZipEntry(WALLPAPER_FILE_NAME)
                        out.putNextEntry(wallpaperEntry)
                        wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                files.forEach { file ->
                    val input = BufferedInputStream(FileInputStream(file), BUFFER)
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    var count: Int
                    while (true) {
                        count = input.read(data, 0, BUFFER)
                        if (count == -1) break
                        out.write(data, 0, count)
                    }
                    input.close()
                }
                success = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create backup", t)
            } finally {
                out.close()
                outStream.close()
                pfd.close()
                cleanupConfig(context)
                return success
            }
        }

        private fun getMeta(name: String, contents: Int) = Meta(
                name = name,
                contents = contents,
                timestamp = getTimestamp()
        )

        private fun getTimestamp(): String {
            val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy hh:mm:ss", Locale.US)
            return simpleDateFormat.format(Date())
        }

        fun fromUriString(context: Context, uri: String?): LawnchairBackup {
            return if (uri == null) {
                LegacyBackup(context)
            } else {
                LawnchairBackup(context, Uri.parse(uri))
            }
        }

        fun fromUri(context: Context, uri: Uri?): LawnchairBackup {
            return if (uri == null) {
                LegacyBackup(context)
            } else {
                LawnchairBackup(context, uri)
            }
        }
    }
}
