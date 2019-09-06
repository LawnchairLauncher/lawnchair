/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherFiles
import com.android.launcher3.Utilities
import org.json.JSONArray
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LawnchairBackup(val context: Context, val uri: Uri) {

    val meta by lazy { readMeta() }

    private fun readMeta(): Meta? {
        var entry: ZipEntry?
        var meta: Meta? = null
        readZip { zipIs ->
            while (true) {
                entry = zipIs.nextEntry
                if (entry == null) break
                if (entry!!.name != Meta.FILE_NAME) continue
                meta = Meta.fromString(String(zipIs.readBytes(), StandardCharsets.UTF_8))
                break
            }
        }
        return meta
    }

    private fun readPreview(): Pair<Bitmap?, Bitmap?>? {
        var entry: ZipEntry?
        var screenshot: Bitmap? = null
        var wallpaper: Bitmap? = null
        readZip { zipIs ->
            while (true) {
                entry = zipIs.nextEntry
                if (entry == null) break
                if (entry!!.name == "screenshot.png") {
                    screenshot = BitmapFactory.decodeStream(zipIs)
                } else if (entry!!.name == WALLPAPER_FILE_NAME) {
                    wallpaper = BitmapFactory.decodeStream(zipIs)
                }
            }
        }
        if (screenshot == wallpaper) return null // both are null
        return Pair(Utilities.getScaledDownBitmap(screenshot, 1000, false),
                Utilities.getScaledDownBitmap(wallpaper, 1000, false))
    }

    private inline fun readZip(body: (ZipInputStream) -> Unit) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val inStream = FileInputStream(pfd.fileDescriptor)
            val zipIs = ZipInputStream(inStream)
            try {
                body(zipIs)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to read zip for $uri", t)
            } finally {
                zipIs.close()
                inStream.close()
                pfd.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read zip for $uri", t)
        }
    }

    fun restore(contents: Int): Boolean {
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
                        context.deleteDatabase(dbFile.path)
                        dbFile
                    } else if (entry.name.endsWith("_preferences.xml")) {
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

    fun delete(): Boolean {
        return context.contentResolver.delete(uri, null, null) != 0
    }

    class MetaLoader(val backup: LawnchairBackup) {

        var callback: Callback? = null
        var meta: Meta? = null
        var withPreview = false
        var loaded = false
        private var loading = false

        fun loadMeta(withPreview: Boolean = false) {
            if (loading) return
            if (!loaded) {
                loading = true
                this.withPreview = withPreview
                LoadMetaTask().execute()
            } else {
                callback?.onMetaLoaded()
            }
        }

        @SuppressLint("StaticFieldLeak")
        inner class LoadMetaTask : AsyncTask<Void, Void, Meta?>() {

            override fun doInBackground(vararg params: Void?): Meta? {
                backup.meta
                if (withPreview) {
                    backup.meta?.preview = backup.readPreview()
                }
                return backup.meta
            }

            override fun onPostExecute(result: Meta?) {
                meta = result
                loaded = true
                callback?.onMetaLoaded()
            }
        }

        interface Callback {

            fun onMetaLoaded()
        }
    }

    data class Meta(val name: String, val contents: Int, val timestamp: String) {

        val localizedTimestamp = SimpleDateFormat.getDateTimeInstance().format(timestampFormat.parse(timestamp))
        var preview: Pair<Bitmap?, Bitmap?>? = null

        override fun toString(): String {
            val arr = JSONArray()
            arr.put(VERSION)
            arr.put(name)
            arr.put(contents)
            arr.put(timestamp)
            return arr.toString()
        }

        fun recycle() {
            preview?.first?.recycle()
            preview?.second?.recycle()
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
        const val INCLUDE_SCREENSHOT = 1 shl 3

        const val BUFFER = 2018

        const val EXTENSION = "shed"
        const val MIME_TYPE = "application/vnd.lawnchair.backup"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        const val WALLPAPER_FILE_NAME = "wallpaper.png"

        val timestampFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

        fun getFolder(): File {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/backup")
            Log.d(TAG, "path: $folder")
            if (!folder.exists()) {
                folder.mkdirs()
            }
            return folder
        }

        fun listLocalBackups(context: Context): List<LawnchairBackup> {
            return getFolder().listFiles { file -> file.extension == EXTENSION }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", it) }
                    ?.map { LawnchairBackup(context, it) }
                    ?: Collections.emptyList()
        }

        private fun prepareConfig(context: Context) {
            Utilities.getLawnchairPrefs(context).blockingEdit {
                restoreSuccess = true
                developerOptionsEnabled = false
            }
        }

        private fun cleanupConfig(context: Context, devOptionsEnabled: Boolean) {
            Utilities.getLawnchairPrefs(context).blockingEdit {
                restoreSuccess = false
                developerOptionsEnabled = devOptionsEnabled
            }
        }

        fun create(context: Context, name: String, location: Uri, contents: Int): Exception? {
            val contextWrapper = ContextWrapper(context)
            val files: MutableList<File> = ArrayList()

            if (contents and INCLUDE_HOMESCREEN != 0) {
                files.add(contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB))
            }
            if (contents and INCLUDE_SETTINGS != 0) {
                val dir = contextWrapper.cacheDir.parent
                files.add(File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml"))
            }

            val prefs = Utilities.getLawnchairPrefs(context)
            val includeScreenshot: Boolean

            if (prefs.backupScreenshot) {
                val screenshotFile = File(context.filesDir, "tmp/screenshot.png")
                if (screenshotFile.exists()) screenshotFile.delete()
                LawnchairLauncher.takeScreenshotSync(context)
                includeScreenshot = screenshotFile.exists()
                if (includeScreenshot) files.add(screenshotFile)
            } else {
                includeScreenshot = false
            }

            val devOptionsEnabled = prefs.developerOptionsEnabled
            prepareConfig(context)
            val pfd = context.contentResolver.openFileDescriptor(location, "w")
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val out = ZipOutputStream(BufferedOutputStream(outStream))
            val data = ByteArray(BUFFER)
            var exception: Exception? = null
            try {
                val metaEntry = ZipEntry(Meta.FILE_NAME)
                out.putNextEntry(metaEntry)
                val actualContents = if (includeScreenshot) contents or INCLUDE_SCREENSHOT else contents
                out.write(getMeta(name, actualContents).toString().toByteArray())
                if (contents and INCLUDE_WALLPAPER != 0) {
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
            } catch (e: Exception) {
                exception = e
                Log.e(TAG, "Failed to create backup", e)
            } finally {
                out.close()
                outStream.close()
                pfd.close()
                cleanupConfig(context, devOptionsEnabled)
                return exception
            }
        }

        private fun getMeta(name: String, contents: Int) = Meta(
                name = name,
                contents = contents,
                timestamp = getTimestamp()
        )

        private fun getTimestamp(): String {
            return timestampFormat.format(Date())
        }
    }
}
