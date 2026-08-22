package app.lawnchair.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persists a user-picked photo as the app drawer's background, as a fallback to reading the live
 * wallpaper directly - WallpaperManager#getDrawable proved unreliable (silently returns null)
 * when called from a launcher on some devices/Android versions, with no exception to catch.
 * Stored full-bleed (not cropped to square, unlike CustomIconStore) since this is a full-screen
 * background, referenced by filename only so it keeps resolving across app updates/restores.
 */
object DrawerBackgroundImageStore {
    private const val TAG = "DrawerBackgroundImageStore"
    const val DIR_NAME = "drawer_backgrounds"
    private const val MAX_DIMENSION = 1440

    private fun storageDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Decodes, downscales, and persists [source]. Returns the stored filename, or null on failure. */
    fun saveImage(context: Context, source: Uri): String? {
        return try {
            val bitmap = decodeScaled(context, source) ?: return null
            val fileName = "bg_${UUID.randomUUID()}.jpg"
            FileOutputStream(File(storageDir(context), fileName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            fileName
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save drawer background image from $source", t)
            null
        }
    }

    fun loadBitmap(context: Context, fileName: String): Bitmap? {
        if (fileName.isEmpty()) return null
        val file = File(storageDir(context), fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun deleteImage(context: Context, fileName: String) {
        if (fileName.isEmpty()) return
        File(storageDir(context), fileName).delete()
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }

        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }
}
