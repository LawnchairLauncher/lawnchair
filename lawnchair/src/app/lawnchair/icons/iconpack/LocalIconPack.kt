package app.lawnchair.icons.iconpack

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import app.lawnchair.icons.ClockMetadata
import app.lawnchair.icons.LocalAdaptiveIconDrawable
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconPickerCategory
import com.android.launcher3.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LocalIconPack(context: Context) : IconPack(context, PACKAGE_NAME) {

    override val label: String
        get() = context.getString(R.string.select_image_from_local_folder)

    init {
        startLoad()
    }

    override fun loadInternal() {
        // No metadata file to parse; icons are loaded dynamically from filesDir
    }

    override fun getIcon(componentName: ComponentName): IconEntry? = null

    override fun getCalendar(componentName: ComponentName): IconEntry? = null

    override fun getClock(entry: IconEntry): ClockMetadata? = null

    override fun getCalendars(): MutableSet<ComponentName> = mutableSetOf()

    override fun getClocks(): MutableSet<ComponentName> = mutableSetOf()

    override fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val customIconsDir = File(context.filesDir, CUSTOM_ICONS_DIR)
        val file = File(customIconsDir, iconEntry.name)
        if (!file.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            LocalAdaptiveIconDrawable(context, bitmap)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode local icon from ${file.absolutePath}", t)
            null
        }
    }

    override fun getAllIcons(): Flow<List<IconPickerCategory>> = emptyFlow()

    companion object {
        const val PACKAGE_NAME = "#local#"
        const val CUSTOM_ICONS_DIR = "custom_icons"
        private const val TAG = "LocalIconPack"

        fun loadBitmapForCropping(context: Context, uri: Uri): Bitmap? {
            return try {
                val bitmap = decodeSampledBitmap(context, uri, 1280, 1280) ?: return null
                val orientedBitmap = applyExifOrientation(context, uri, bitmap)
                if (orientedBitmap != bitmap) {
                    bitmap.recycle()
                }
                orientedBitmap
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load bitmap for cropping from $uri", t)
                null
            }
        }

        fun saveSquareBitmap(context: Context, bitmap: Bitmap): String? {
            return try {
                val finalBitmap = if (bitmap.width != 512 || bitmap.height != 512) {
                    Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                } else {
                    bitmap
                }
                val customIconsDir = File(context.filesDir, CUSTOM_ICONS_DIR).apply { mkdirs() }
                val fileName = "${UUID.randomUUID()}.png"
                val file = File(customIconsDir, fileName)
                var success = false
                FileOutputStream(file).use { out ->
                    success = finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (finalBitmap != bitmap) {
                    finalBitmap.recycle()
                }
                if (!success) {
                    file.delete()
                    return null
                }
                fileName
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save square bitmap", t)
                null
            }
        }

        fun saveLocalIcon(context: Context, uri: Uri): String? {
            return try {
                val bitmap = decodeSampledBitmap(context, uri, 512, 512) ?: return null
                val orientedBitmap = applyExifOrientation(context, uri, bitmap)
                val squareBitmap = centerCropToSquare(orientedBitmap)
                val customIconsDir = File(context.filesDir, CUSTOM_ICONS_DIR).apply { mkdirs() }
                val fileName = "${UUID.randomUUID()}.png"
                val file = File(customIconsDir, fileName)
                var success = false
                FileOutputStream(file).use { out ->
                    success = squareBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (orientedBitmap != bitmap) {
                    bitmap.recycle()
                }
                if (squareBitmap != orientedBitmap) {
                    orientedBitmap.recycle()
                }
                squareBitmap.recycle()
                if (!success) {
                    file.delete()
                    return null
                }
                fileName
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save local icon from $uri", t)
                null
            }
        }

        private fun decodeSampledBitmap(
            context: Context,
            uri: Uri,
            reqWidth: Int,
            reqHeight: Int,
        ): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            return context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int,
        ): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED,
                    )
                } ?: ExifInterface.ORIENTATION_UNDEFINED
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_UNDEFINED
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)

                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)

                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)

                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }

                else -> return bitmap
            }
            return try {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } catch (_: Throwable) {
                bitmap
            }
        }

        private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            if (width == height) return bitmap
            val size = minOf(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            return try {
                Bitmap.createBitmap(bitmap, x, y, size, size)
            } catch (_: Throwable) {
                bitmap
            }
        }
    }
}
