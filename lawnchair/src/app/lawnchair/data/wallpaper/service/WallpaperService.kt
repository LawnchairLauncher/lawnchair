package app.lawnchair.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.util.bitmapToByteArray
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

class WallpaperService(val context: Context) : SafeCloseable {

    val dao = AppDatabase.Companion.INSTANCE.get(context).wallpaperDao()

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        try {
            val wallpaperDrawable = wallpaperManager.drawable
            val currentBitmap = (wallpaperDrawable as BitmapDrawable).toBitmap()

            val byteArray = bitmapToByteArray(currentBitmap)

            saveWallpaper(byteArray)
        } catch (e: Exception) {
            Log.e("WallpaperChange", "Error detecting wallpaper change: ${e.message}")
        }
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) {
        val timestamp = System.currentTimeMillis()

        val checksum = calculateChecksum(imageData)

        val existingWallpapers = dao.getTopWallpapers()

        if (existingWallpapers.any { it.checksum == checksum }) {
            Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
            return
        }
        val imagePath = saveImageToAppStorage(imageData)
        if (existingWallpapers.size < 4) {
            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = existingWallpapers.size,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
        } else {
            val lowestRankedWallpaper = existingWallpapers.minByOrNull { it.timestamp }

            if (lowestRankedWallpaper != null) {
                dao.deleteWallpaper(lowestRankedWallpaper.id)
                deleteWallpaperFile(lowestRankedWallpaper.imagePath)
            }

            for (wallpaper in existingWallpapers) {
                if (wallpaper.rank >= (lowestRankedWallpaper?.rank ?: 0)) {
                    dao.updateRank(wallpaper.rank)
                }
            }

            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = 0,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
        }
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) {
        val topWallpapers = dao.getTopWallpapers()
        val currentTime = System.currentTimeMillis()

        dao.updateWallpaper(selectedWallpaper.id, rank = 0, timestamp = currentTime)

        for (wallpaper in topWallpapers) {
            if (wallpaper.id != selectedWallpaper.id) {
                dao.updateRank(wallpaper.rank)
            }
        }
    }

    fun getTopWallpapers(): List<Wallpaper> = runBlocking {
        val wallpapers = dao.getTopWallpapers()
        wallpapers.ifEmpty { emptyList() }
    }

    private fun deleteWallpaperFile(imagePath: String) {
        val file = File(imagePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageHash = imageData.hashCode().toString()
        val imageFile = File(storageDir, "wallpaper_$imageHash.jpg")

        if (!imageFile.exists()) {
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }
        }

        return imageFile.absolutePath
    }

    override fun close() {
        TODO("Not yet implemented")
    }
    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::WallpaperService)
    }
}
