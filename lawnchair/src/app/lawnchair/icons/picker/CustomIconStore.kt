package app.lawnchair.icons.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persists a user-picked photo as a launcher icon override. Images are stored as a downscaled,
 * center-cropped square bitmap under the app's private storage, referenced by filename only (not
 * full path) so overrides keep resolving correctly across app updates/restores that could change
 * the data directory.
 */
object CustomIconStore {
    private const val TAG = "CustomIconStore"
    private const val DIR_NAME = "custom_icons"
    private const val MAX_DIMENSION = 512

    private fun storageDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Decodes, center-crops to square, downscales, and persists [source]. Returns the stored filename, or null on failure. */
    fun saveIcon(context: Context, source: Uri): String? {
        return try {
            val bitmap = decodeSquare(context, source) ?: return null
            val fileName = "icon_${UUID.randomUUID()}.png"
            FileOutputStream(File(storageDir(context), fileName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            fileName
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save custom icon from $source", t)
            null
        }
    }

    fun loadIcon(context: Context, fileName: String): Drawable? {
        val file = File(storageDir(context), fileName)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    fun deleteIcon(context: Context, fileName: String) {
        File(storageDir(context), fileName).delete()
    }

    private fun decodeSquare(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null

        val cropSize = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(
            decoded,
            (decoded.width - cropSize) / 2,
            (decoded.height - cropSize) / 2,
            cropSize,
            cropSize,
        )
        if (cropped !== decoded) decoded.recycle()

        val targetSize = minOf(cropSize, MAX_DIMENSION)
        if (targetSize == cropSize) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }
}
